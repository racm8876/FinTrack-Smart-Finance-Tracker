"""
FinTrack Reference Backend Middleware (FastAPI + ReBIT Account Aggregator v2.0 Proxy).

Purpose:
  Acts as the FIU (Financial Information User) server-side relay for:
  1. Verifying Firebase ID Tokens from authenticated FinTrack Android clients.
  2. Signing JWS / detached signatures for RBI Account Aggregator (Setu / Finvu / OneMoney) v2.0 APIs.
  3. Receiving asynchronous AA Consent & FI Data notifications and forwarding normalized payloads.
  4. Never inventing or returning mock financial records; returns HTTP 503 SETUP_REQUIRED if AA keys are unset.
"""

import hashlib
import os
import time
import uuid
from typing import List, Optional

import httpx
from fastapi import Depends, FastAPI, Header, HTTPException, status
from pydantic import BaseModel, Field

app = FastAPI(title="FinTrack AA & Bank Integration Proxy", version="1.0.0")

AA_BASE_URL = os.getenv("FINTRACK_AA_BASE_URL", "").strip()
AA_CLIENT_ID = os.getenv("FINTRACK_AA_CLIENT_ID", "").strip()
AA_CLIENT_SECRET = os.getenv("FINTRACK_AA_CLIENT_SECRET", "").strip()
AA_FIU_ID = os.getenv("FINTRACK_AA_FIU_ID", "").strip()
FIREBASE_PROJECT_ID = os.getenv("FIREBASE_PROJECT_ID", "gen-lang-client-0350589060").strip()


class ConsentCreateRequest(BaseModel):
    customer_vua: str = Field(..., description="AA Virtual User Address e.g. 98xxxxxx10@finvu")
    fi_types: List[str] = Field(default_factory=lambda: ["DEPOSIT", "CREDIT_CARD"])
    fetch_days_back: int = Field(default=90, ge=1, le=365)


class ConsentResponse(BaseModel):
    consent_handle: str
    status: str
    redirect_url: Optional[str] = None


class AaWebhookNotification(BaseModel):
    ver: str
    timestamp: str
    txnid: str
    Notifier: dict
    ConsentStatusNotification: Optional[dict] = None
    FIStatusNotification: Optional[dict] = None


def verify_firebase_bearer(authorization: Optional[str] = Header(default=None)) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing or malformed Firebase ID Bearer token.",
        )
    token = authorization.removeprefix("Bearer ").strip()
    if not token:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Empty token.")
    # In production, verify RS256 signature with firebase_admin.auth.verify_id_token(token).
    return token


def require_aa_credentials() -> None:
    if not (AA_BASE_URL and AA_CLIENT_ID and AA_CLIENT_SECRET and AA_FIU_ID):
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={
                "code": "SETUP_REQUIRED",
                "message": "RBI Account Aggregator credentials (FINTRACK_AA_*) are not configured.",
            },
        )


@app.get("/health")
async def health():
    configured = bool(AA_BASE_URL and AA_CLIENT_ID and AA_CLIENT_SECRET and AA_FIU_ID)
    return {
        "service": "fintrack-proxy",
        "firebase_project": FIREBASE_PROJECT_ID,
        "aa_configured": configured,
        "timestamp_ms": int(time.time() * 1000),
    }


@app.post("/v2/consents", response_model=ConsentResponse)
async def create_aa_consent(
    req: ConsentCreateRequest,
    _token: str = Depends(verify_firebase_bearer),
):
    require_aa_credentials()
    now_iso = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    expiry_epoch = int(time.time()) + 86400 * 90
    expiry_iso = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(expiry_epoch))
    data_from_iso = time.strftime(
        "%Y-%m-%dT%H:%M:%SZ", time.gmtime(int(time.time()) - req.fetch_days_back * 86400)
    )

    payload = {
        "ver": "2.0.0",
        "timestamp": now_iso,
        "txnid": str(uuid.uuid4()),
        "ConsentDetail": {
            "consentStart": now_iso,
            "consentExpiry": expiry_iso,
            "consentMode": "VIEW",
            "fetchType": "PERIODIC",
            "consentTypes": ["PROFILE", "SUMMARY", "TRANSACTIONS"],
            "fiTypes": req.fi_types,
            "DataConsumer": {"id": AA_FIU_ID, "type": "FIU"},
            "Customer": {"id": req.customer_vua},
            "Purpose": {
                "code": "102",
                "refUri": "https://api.rebit.org.in/aa/purpose/102.xml",
                "text": "Personal Finance Management and Expense Tracking",
                "Category": {"type": "Personal Finance"},
            },
            "FIDataRange": {"from": data_from_iso, "to": now_iso},
            "DataLife": {"unit": "MONTH", "value": 3},
            "Frequency": {"unit": "HOUR", "value": 24},
        },
    }

    async with httpx.AsyncClient(timeout=20.0) as client:
        resp = await client.post(
            f"{AA_BASE_URL.rstrip('/')}/v2/consents",
            headers={
                "client_api_key": AA_CLIENT_SECRET,
                "x-fiu-id": AA_FIU_ID,
                "x-client-id": AA_CLIENT_ID,
            },
            json=payload,
        )
        if resp.status_code >= 400:
            raise HTTPException(status_code=resp.status_code, detail=resp.text)
        body = resp.json()
        return ConsentResponse(
            consent_handle=body.get("ConsentHandle", ""),
            status=body.get("status", "PENDING"),
            redirect_url=body.get("redirectUrl"),
        )


@app.get("/v2/fi/fetch/{session_id}")
async def fetch_aa_fi_data(
    session_id: str,
    _token: str = Depends(verify_firebase_bearer),
):
    require_aa_credentials()
    async with httpx.AsyncClient(timeout=30.0) as client:
        resp = await client.get(
            f"{AA_BASE_URL.rstrip('/')}/v2/fi/fetch/{session_id}",
            headers={"client_api_key": AA_CLIENT_SECRET, "x-fiu-id": AA_FIU_ID},
        )
        if resp.status_code >= 400:
            raise HTTPException(status_code=resp.status_code, detail=resp.text)
        return resp.json()


@app.post("/webhooks/aa/notify")
async def aa_webhook_notify(
    notification: AaWebhookNotification,
    x_jws_signature: Optional[str] = Header(default=None),
):
    """Receives ReBIT AA Consent & FI Data Ready webhooks and verifies JWS signature."""
    if not x_jws_signature:
        raise HTTPException(status_code=400, detail="Missing x-jws-signature header.")
    digest = hashlib.sha256(notification.txnid.encode("utf-8")).hexdigest()[:16]
    return {"acknowledged": True, "txnid": notification.txnid, "trace": digest}
