# Financial Integrations & Source Setup Guide

FinTrack enforces **strict real-source traceability**: it never fabricates transactions, balances, or phone numbers. Every integration method below operates on real authorized user data or clearly reports `Setup Required` when external credentials are absent.

## 1. RBI Account Aggregator (AA) Ecosystem (ReBIT AA v2.0)

India's RBI-regulated Account Aggregator framework allows users to share bank statements and live balances with explicit digital consent via Sahamati-certified TSPs/AAs (e.g., **Setu AA**, **Finvu**, **OneMoney**, **CAMSfinserv**).

### How to Enable Live Account Aggregator Sync
1. Obtain FIU (Financial Information User) sandbox or production credentials from a ReBIT-certified AA provider.
2. Open the **Secrets panel in AI Studio** (do not store credentials in `local.properties` or commit `.env`) and define:
   - `FINTRACK_AA_BASE_URL` (e.g. `https://fiu-sandbox.setu.co/`)
   - `FINTRACK_AA_CLIENT_ID`
   - `FINTRACK_AA_CLIENT_SECRET`
3. Uncomment those keys in `.env.example` if distributing a custom configured build so Gradle's Secrets plugin injects them into `BuildConfig`.
4. Deploy `/backend/main.py` as your signed JWS proxy if your AA provider requires server-side mTLS/JWS detached signatures.

Until configured, `AccountAggregatorClient.checkConfiguration()` returns `IntegrationHealth.SetupRequired`, and connected accounts never display fabricated balances.

## 2. Official Bank APIs

Most Indian retail banks (HDFC, SBI, ICICI, Axis, Kotak) do **not** offer public retail OAuth transaction APIs directly to consumer apps outside of:
- **RBI Account Aggregator** (recommended for all retail savings/current/credit card feeds), or
- **Corporate / Current Account API Banking** (HDFC API Banking, ICICI Connected Banking, Kotak APIs using client certificates & mTLS).

## 3. Payment Notification Listener (`NotificationListenerService`)

- **Apps Supported**: Google Pay (`com.google.android.apps.nbu.paisa.user`), PhonePe (`com.phonepe.app`), Paytm (`net.one97.paytm`), BHIM UPI (`in.org.npci.upiapp`), and Indian Bank SMS/App notifications.
- **User Activation**: Users grant access explicitly via Android Settings (`Notification Access` -> `FinTrack Transaction Alerts`) inside **Sources & Permissions**.
- **Privacy Guarantee**: Notification text is parsed locally on-device by `IndianTransactionParser`; non-financial notifications and OTP alerts are discarded immediately.

## 4. Transaction SMS Parsing & Zero-Permission Share Target

- **Consent-Gated SMS Inbox Scan (`READ_SMS`)**: Disabled by default. Only executes after the user grants the Android runtime permission AND turns on the in-app `Transaction SMS Inbox Parsing` consent toggle. Reads transactional senders only, skipping OTPs.
- **Zero-Permission Alternative (`ACTION_SEND`)**: FinTrack registers an Android `text/plain` share receiver so users who prefer not to grant `READ_SMS` can share or paste any bank/UPI SMS alert directly into FinTrack for instant structured parsing.

## 5. Bank Statement Import (PDF, CSV, Excel `.xlsx`)

- Uses Android's zero-permission **Storage Access Framework (`ActivityResultContracts.OpenDocument`)**.
- Supports standard Indian bank **CSV** statements (automatically skipping leading bank header metadata rows), **Excel `.xlsx`** OOXML workbooks, and unencrypted **PDF** text statements.
- Duplicate detection via `dedupFingerprint` ensures importing a monthly statement never double-counts transactions already captured via UPI notifications or SMS.

## 6. Unavailable Data Handling Rules

- **Balances**: Only displayed when an account has `supportsRealBalance == true` (from AA or user-entered balance). Notification, SMS, and manual accounts show `"Balance Unavailable"` rather than estimating or guessing.
- **Recipient Mobile Numbers**: Bank/UPI notifications usually expose VPAs (`name@okaxis`) or names, not 10-digit phone numbers. Unless legitimately present in the authorized payload, `recipientMobile` is stored strictly as `"Unavailable"`.
