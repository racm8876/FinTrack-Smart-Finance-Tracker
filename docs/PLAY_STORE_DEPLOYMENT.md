# FinTrack Google Play Store Deployment & Compliance Checklist

## 1. Package & Signing Configuration
- **Application ID**: `com.aistudio.fintrack.rvkmpq`
- **Target SDK / Min SDK**: `targetSdk = 36`, `minSdk = 24`
- **App Title**: `FinTrack` (8 characters — compliant with the 30-character Play Store limit; no emojis, all-caps, or promotional claims).
- **Backup Policy**: `android:allowBackup="false"` in `AndroidManifest.xml` to protect sensitive financial tokens and local Room ledger caches.
- **Release Bundle**: Generate a signed Android App Bundle (`.aab`) using Google Play App Signing (`upload` key alias configured via `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` environment variables).

## 2. Permissions & Google Play Policy Compliance

| Permission / API | Usage in FinTrack | Play Store Requirement |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Cloud Firestore sync, Google Sign-In, AA API calls | Normal permission (no runtime prompt) |
| `USE_BIOMETRIC` | `BiometricPrompt` app unlock gate | Declared in manifest; optional user toggle |
| `POST_NOTIFICATIONS` | Real-time capture & budget threshold alerts | Runtime permission requested on Android 13+ |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | `FinTrackNotificationListenerService` for UPI/bank alerts | Prominent in-app disclosure + explicit Settings toggle |
| `READ_SMS` | Optional inbox scan for bank/UPI transaction alerts | **Restricted Permission**: Requires Play Console SMS/Call Log Declaration Form justifying core expense-tracking functionality, OR remove `<uses-permission android:name="android.permission.READ_SMS" />` if distributing a zero-SMS variant relying on Notification Listener + `ACTION_SEND` + SAF Statements + RBI AA |
| Photo Picker / SAF (`OpenDocument`) | Statement import (CSV/XLSX/PDF) | Zero broad storage permissions (`READ_EXTERNAL_STORAGE` / `READ_MEDIA_*` omitted) |
| `FileProvider` | Secure export sharing (`.csv`, `.xlsx`, `.pdf`) | Restricted to `<cache-path name="exports" path="exports/" />` |

## 3. Financial Features & Data Safety Declaration
1. **Google Sign-In SHA Fingerprints**: Ensure your Play App Signing certificate SHA-1 & SHA-256 are registered in Firebase Console alongside the upload/debug fingerprints so `CredentialManager` (`GetSignInWithGoogleOption`) succeeds on Play Store installs.
2. **Firestore Security Rules**: Deploy `/firestore.rules` before release (`rpc_action` / Firebase CLI) so all user collections strictly enforce `request.auth.uid == userId` and schema validation.
3. **Data Safety Form**:
   - **Data Collected**: Name/email (Firebase Auth), User-consented financial transaction records & budgets.
   - **Encryption in Transit**: TLS 1.2+ enforced for Firebase & ReBIT AA HTTPS endpoints.
   - **Account & Data Deletion**: Supported directly in-app via **Account Management -> Delete Account & Wipe Cloud Data** (wipes Room, DataStore preferences, Firestore documents, and Firebase Auth account).
4. **No Dynamic Code Loading**: FinTrack bundles all parsers (CSV, `.xlsx`, PDF, Indian SMS/UPI rules) statically in Kotlin without loading external `.dex`, `.jar`, or `.so` code.
