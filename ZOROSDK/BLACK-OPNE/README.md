# KESHAVXOWNERCore Android SDK

KESHAVXOWNERCore is the reusable Android library consumed by the KESHAVXOWNER Loader application.
The project is configured for the current Android toolchain while retaining
runtime support back to Android 7.0 (API 24).

## Compatibility

| Area | Support |
| --- | --- |
| Android runtime | API 24 through API 36 |
| Compile/target SDK | API 36 |
| Native ABIs | `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` |
| Native page sizes | Flexible, including 16 KB devices |
| Java toolchain | Java 17 |
| Build toolchain | AGP 8.11.1 and Gradle 8.13 |

The four native ABIs allow the AAR to run on physical 32-bit and 64-bit ARM
devices as well as x86/x86_64 emulators. Consumers can still restrict packaged
ABIs in their own application when they have architecture-specific native code.
The native memory alignment logic uses the runtime page size instead of assuming
4 KB pages.

## Device social authentication

KESHAVXOWNERCore routes supported authentication surfaces to the authoritative app
or browser installed on the phone. Existing device sessions can therefore be
offered by the provider without copying cookies, passwords, account-manager data,
or access tokens into the virtual app.

| Provider / flow | SDK behavior | Required app configuration |
| --- | --- | --- |
| Google / Play Games / Credential Manager | Real Google Play services activities, services and provider-owned `IntentSender` account pickers are launched by a host proxy; the unchanged result is returned to the virtual activity. | The OAuth client must authorize the actual host package and signing certificate used for the build. Provider-side verification is not bypassed. |
| Facebook Login | Explicit installed Facebook app login intents are routed to the real app. HTTPS OAuth authorization pages use the device browser/Auth Tab and return only to a callback declared by the virtual package. | Configure the Android package/key hash and the exact valid OAuth redirect URI in the Meta developer console. |
| X / Twitter | Installed X/Twitter login activities and legacy `twittersdk` callbacks are supported. Browser OAuth uses the device session and returns through the declared virtual callback. | Register the exact callback URL. New integrations should use OAuth 2.0 Authorization Code with PKCE and `state`. |
| WhatsApp | Click-to-chat/share intents continue through Android normally. WhatsApp Business Embedded Signup pages hosted by Meta use the same secure browser callback route. | WhatsApp is not a general-purpose identity provider. A cloned WhatsApp account cannot import another installation's private login/session data; use WhatsApp's own phone/device verification. |

Browser OAuth is accepted only from HTTPS Google, Facebook/Meta, and X/Twitter
authorization hosts. The callback must use a non-web custom scheme declared by
the requesting virtual package. The bridge validates callback scheme, authority,
path, fixed query parameters, and OAuth `state` before dispatch. Arbitrary
external activities, PendingIntents, WebViews, and callback packages are rejected.

Authentication depends on provider policy and the client application's own
developer-console configuration. Repackaging or cloning changes the effective
Android package/signing identity; KESHAVXOWNERCore does not forge signatures, defeat
Play Integrity, or extract sessions from another app.

## Build

Install JDK 17, Android SDK Platform 36, Build Tools 36.0.0, and NDK
27.2.12479018, then run:

```bash
./gradlew :KESHAVXOWNERCore:assembleRelease
```

The release AAR is written to
`RIYAZ-VIP/build/outputs/aar/KESHAVXOWNERCore-release.aar`.

For encrypted storage or client delivery, generate a fresh 32-byte key per
client, Base64-encode it, keep it outside Git, and run:

```bash
export KESHAVXOWNER_AAR_ENCRYPTION_KEY="<base64-32-byte-client-key>"
./gradlew :KESHAVXOWNERCore:encryptReleaseAar
```

The authenticated AES-256-GCM envelope is written to
`RIYAZ-VIP/build/outputs/encrypted-aar/KESHAVXOWNERCore-v3-release.aar.pxa`.
Before Gradle can consume it, the authorized client must decrypt it locally:

```bash
export KESHAVXOWNER_AAR_ENCRYPTION_KEY="<same-client-key>"
./gradlew :KESHAVXOWNERCore:decryptReleaseAar \
  -PkeshavxownerEncryptedAar=/secure/path/KESHAVXOWNERCore-v3-release.aar.pxa
```

Never reuse the panel encryption/signing private keys as an AAR delivery key.
The decrypted AAR must be treated as a temporary build input: a usable AAR
cannot remain encrypted because Android Gradle Plugin must read its manifest,
classes, resources, and native libraries.

## Release hardening

Release AAR builds use LSParanoid 0.6.0 to transform string constants only in
explicitly annotated security and external-auth bridge classes. Startup,
reflection, Binder entry points, JNI-facing names, and generated Android classes
are deliberately left structurally unchanged to avoid the historical startup
and verification failures caused by broad transforms. The consuming Loader then
applies R8/resource shrinking and its first-party BlackObfuscator pass to the
final release APK.

The panel endpoint is not emitted as a plain `BuildConfig` or Kotlin string. It
is reconstructed from masked native bytes, checked in native code, and then
checked again against an independent Java SHA-256 trust value before use. This
prevents casual static string extraction, while HTTPS plus signed responses—not
endpoint secrecy—remains the actual panel-swap security boundary.

### Rotate the panel endpoint

Open `tools/panel-endpoint-generator.html` locally, enter the complete HTTPS API
endpoint, and select **Generate secure config**. The offline tool creates both
required replacements: the masked byte/mask/FNV block for `BoxCore.cpp` and the
independent SHA-256 byte array for `SecureSdkApiClient.kt`. Replace both blocks,
then rebuild and test the SDK. Updating only the native URL intentionally causes
Java trust verification to fail closed.

The downloaded rotation record contains the endpoint in plain text for the
operator's private audit trail. Do not commit that record or package it in an
AAR/APK. The generator performs no network requests and includes no server
private key, signing key, license key, or reusable AAR encryption key.

This protects high-value constants at rest and raises reverse-engineering cost;
it does not make client-side code or Android resources impossible to inspect.
Provider secrets and authorization decisions must remain on the server.
