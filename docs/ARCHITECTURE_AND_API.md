# FinTrack Architecture, Database Schema & API Documentation

## 1. System Architecture

FinTrack uses an **offline-first, privacy-preserving MVVM + Clean Domain Architecture** tailored for Indian personal finance (`₹ INR`, Indian numbering system `##,##,##0.00`, timezone `Asia/Kolkata`):

1. **Android Client (Kotlin + Jetpack Compose + Material 3)**
   - **Auth & Vault Gate**: Google Sign-In via Jetpack `CredentialManager` (`GetSignInWithGoogleOption`) exchanging an ID token with Firebase Auth.
   - **Local Encrypted/Private Cache (Room + DataStore)**: `FinTrackRoomDatabase` stores cached transactions, financial accounts, and categories for instant offline dashboard rendering, while `UserPreferencesRepository` stores salted SHA-256 PIN hashes, app lock state, consent toggles, and threshold preferences.
   - **Cloud Synchronization (Cloud Firestore)**: `FinTrackRepository` synchronizes user-scoped collections under `/users/{userId}/...` on database `ai-studio-android-fintrack-b8e6f809-8496-4e0a-9bac-30c6cfcde60c`, backed by Zero-Trust `firestore.rules` and `FirestoreErrorHandler`.
   - **Real-Time Ingestion Engines**:
     - `FinTrackNotificationListenerService`: On-device parser for UPI & bank alerts (`Google Pay`, `PhonePe`, `Paytm`, `BHIM`, HDFC/SBI/ICICI/Axis/Kotak) gated by explicit user permission and consent toggle.
     - `SmsTransactionReader`: Explicit-consent SMS inbox scanner reading `content://sms/inbox` locally without uploading raw message threads; paired with a zero-permission `ACTION_SEND` text share target.
     - `StatementParser`: SAF document importer supporting Indian bank **CSV**, **Excel (`.xlsx` OOXML)**, and unlocked **PDF (`FlateDecode` text stream)** statements.
     - `AccountAggregatorClient`: ReBIT AA v2.0 Retrofit/Moshi client reporting `IntegrationHealth.SetupRequired` when FIU credentials are not configured.
   - **Background Execution**: `FinTrackSyncWorker` (WorkManager) schedules periodic background consent/sync verification without foreground service abuse.

2. **Reference Backend Middleware (`/backend/main.py` & `/backend/schema.sql`)**
   - Optional FIU / Account Aggregator proxy for teams deploying their own Sahamati/ReBIT-licensed FIU backend with PostgreSQL.

## 2. Database Schema & Migrations

### Cloud Firestore Collections (`firebase-blueprint.json`)
| Collection Path | Purpose | Key Security Invariants |
|---|---|---|
| `/users/{userId}` | Profile, currency (`INR`), timezone (`Asia/Kolkata`), PIN/biometric flags | Only owner (`request.auth.uid == userId`) can read/write; `serverTimestamp()` enforced |
| `/users/{userId}/accounts/{accountId}` | Connected bank accounts, cards, UPI handles | `supportsRealBalance` separates AA/API balances from manual/statement accounts |
| `/users/{userId}/transactions/{transactionId}` | Normalized credit/debit records with UTR, VPA, source, dedup fingerprint | Missing mobile numbers stored as `"Unavailable"`; amount `> 0`; valid enums |
| `/users/{userId}/contacts/{contactId}` | People & counterparty metadata | Owner-only CRUD |
| `/users/{userId}/categories/{categoryId}` | 17 default + user custom categories | Owner-only CRUD |
| `/users/{userId}/budgets/{budgetId}` | Monthly/category/daily/weekly limits & savings goals | Owner-only CRUD |
| `/users/{userId}/notifications/{notificationId}` | Financial & budget alerts | Owner-only read/create/mark-read |
| `/users/{userId}/sync_logs/{logId}` | Immutable sync & import logs | Append-only audit logs (`allow update, delete: if false`) |
| `/users/{userId}/consents/{consentId}` | Granular consent toggles & purpose descriptions | Owner-only CRUD |
| `/users/{userId}/audit_logs/{auditId}` | Immutable history of category/note edits | Append-only audit trail |

### Room Database Migration Strategy
- `FinTrackRoomDatabase` currently runs on schema version `1`.
- Future column additions use explicit `Migration(1, 2)` implementations in `FinTrackRoomDatabase` rather than destructive fallback on user-authored financial records.

## 3. REST / Account Aggregator API Specification

Implemented in `AccountAggregatorApi.kt` and mirrored in `/backend/main.py`:

- `POST /v2/consents`: Creates an AA v2.0 data consent request (`VIEW` / `PERIODIC`, `DEPOSIT`, `CREDIT_CARD`). Headers: `client_api_key`, `x-fiu-id`, `Authorization: Bearer <Firebase_ID_Token>`.
- `GET /v2/consents/handle/{consentHandle}`: Polls consent approval status (`PENDING`, `ACTIVE`, `REJECTED`, `REVOKED`).
- `GET /v2/fi/fetch/{sessionId}`: Retrieves consented FIP balances and statement transactions mapped to `TransactionRecord` with deduplication fingerprint (`SHA-256(amount|dateIst|type|REF:utr)`).
- `POST /webhooks/aa/notify`: Server webhook verifying `x-jws-signature` for AA async consent & FI ready callbacks.
