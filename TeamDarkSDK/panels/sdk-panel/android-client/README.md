# Android API v3 client

The production client is compiled directly into the SDK at:

`BLACK-OPNE/RIYAZ-VIP/src/main/java/android/MetaCore/SecureSdkApiClient.kt`

It uses ephemeral P-256 ECDH, AES-256-GCM, signed panel responses, an exact
HTTPS endpoint, app-signing-certificate binding, and a per-install Android
Keystore proof key. Do not restore the old shared AES key client: an APK cannot
keep a symmetric server key secret.
