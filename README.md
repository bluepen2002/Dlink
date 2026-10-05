# Dlink Android — Offline Exchange

Dlink is an Android-first digital identity/contact exchange app built around an offline-first model.

## Offline fallback stack

1. **Nearby Connections** — primary phone-to-phone exchange.
2. **NFC HCE + ISO-DEP** — tap two NFC phones together; the sender exposes a Dlink HCE service and the receiver reads the temporary envelope locally.
3. **QR** — sender generates a temporary Dlink QR; receiver scans it using bundled on-device ML Kit.

No mobile data, Wi-Fi, cloud account, or internet connection is required to perform QR scanning or the NFC/nearby transport itself after the app and its bundled dependencies are installed.

## Dlink QR/NFC protocol

The QR/NFC envelope is `dlink://share?payload=...` and contains protocol version, application/type, session ID, expiry timestamp, profile type and only the fields selected for that temporary share. A SHA-256 checksum protects the envelope against accidental corruption/tampering. Expiry is enforced when decoding.

The default generated fallback envelope expires after 30 seconds.

### Privacy model

QR and NFC are proximity transports. They should not be treated as confidential channels merely because they are offline. Therefore the fallback envelope contains only explicitly selected share fields, and the default implementation keeps the payload short-lived. For sensitive fields, the production protocol should add an authenticated ephemeral ECDH session/PIN confirmation before release.

## Android requirements

- minSdk 26
- targetSdk 35
- NFC optional hardware
- Camera permission only for the custom QR scanner
- Bluetooth/Nearby permissions for Nearby Connections

The bundled ML Kit barcode scanner performs QR recognition on-device. This avoids depending on a network connection at scan time.

## Build

Open this directory in Android Studio, allow Gradle to resolve dependencies, then build an APK/AAB from the Android Studio Build menu.

This environment does not include an Android SDK/Gradle build toolchain, so the source is provided as an Android Studio project rather than falsely claiming a compiled APK.

## Secure offline exchange protocol (v2)
QR and NFC now use `DlinkSecureEnvelope` with AES-256-GCM authenticated encryption. A random 10-character temporary exchange code is generated on the sender. The receiver must enter that code after scanning/tapping. The key is derived with PBKDF2-HMAC-SHA256 (210,000 iterations) and a random salt; the AES-GCM nonce is random per session and the session metadata is authenticated as AAD. Payloads expire after 30 seconds.

This protects the profile content against passive observers and detects tampering. The exchange code must still be treated as secret. The legacy checksum-only envelope remains for backward compatibility and must not be used for sensitive production sharing.

### Security boundary
This v2 layer is complete for one-way QR/NFC encrypted payloads. Nearby Connections still needs the final explicit-acceptance handshake and the same authenticated session state machine before it should be advertised as production-secure. The next hardening step is a transport-independent authenticated session using ephemeral ECDH for Nearby/NFC and the secure-code mode for QR fallback.

## GitHub cloud build

This repository includes `.github/workflows/android-build.yml`. Push the project to GitHub, then open the repository's **Actions** tab. A successful `Dlink Android Build` run provides three downloadable artifacts: the debug APK, unsigned release APK, and unsigned release AAB.

The workflow uses JDK 17 and Gradle 8.9 through `gradle/actions/setup-gradle`, so a Gradle Wrapper is not required for the GitHub cloud build.
