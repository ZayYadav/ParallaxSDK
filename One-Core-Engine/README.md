# Parallax Loader

Android loader application for the ParallaxCore SDK.

## Compatibility

| Area | Support |
| --- | --- |
| Android runtime | API 24 through API 36 |
| Compile/target SDK | API 36 |
| Loader ABI | `arm64-v8a` only |
| Native page sizes | 4 KB and 16 KB |
| Build toolchain | AGP 8.11.1, Gradle 8.13, JDK 17, NDK 27.2 |

Parallax Loader intentionally produces a single 64-bit ARM APK. Its
SDK AAR can still contain additional architectures, but the installable loader
does not package 32-bit ARM, x86, or x86_64 native libraries.

## Native artifact contract

The primary downloaded artifact is named `Parallax.so`. Downloads must use
HTTPS and contain a valid ELF file with that exact name. The app stages accepted
artifacts in its private no-backup directory, applies owner-only permissions,
and commits copies atomically. Debug logs also remain in private app storage.

Android always records a loaded native library in process memory metadata. The
project does not attempt to tamper with linker or process inspection APIs.

## Build

Install JDK 17, Android SDK Platform 36, and NDK 27.2.12479018. Build the SDK
AAR first, copy it to `app/libs/ParallaxCore-release.aar`, and then run:

```bash
./gradlew :app:assembleRelease
```

The CI workflow performs both stages automatically.

## Loader experience and defensive security

- Responsive premium login and command-center layouts support portrait,
  landscape, cutout, and resizable-window devices without orientation locks.
- Runtime status shows the Android API level and the device ABI list.
- License values are encrypted with AES-GCM using an app-scoped Android
  Keystore key. Existing plaintext values are migrated after the first launch.
- Login is pinned to `https://parallaxserver.online/api/v2/connect` and the `PUBG`
  game identifier used by the OneCore Integrity legacy-key inventory. The
  endpoint and game cannot be replaced through Gradle properties or runtime
  configuration.
- Each request uses a fresh AES-256-GCM key wrapped to the panel's RSA public
  key. Responses are separately encrypted and bound to the request nonce and a
  random canary. Replays, tampering, stale timestamps, redirects, oversized
  payloads, and invalid expiry fail closed.
- OkHttp certificate pinning and `Proxy.NO_PROXY` protect the outer verified
  HTTPS connection against common proxy/MITM interception. The server private
  API key never enters the APK.
- The server-issued expiry is stored with a monotonic time anchor. Install,
  download, launch, and floating-service actions fail closed when the key
  expires, after a reboot, or when protected state cannot be decrypted.
- Release CI requires public repository variables
  `PARALLAX_API_PUBLIC_KEY_B64` and `PARALLAX_TLS_PINS`. The legacy token secret
  is no longer compiled into the loader.
- Release builds validate the exact active signing-certificate set across three
  independent paths: PackageManager metadata, a raw-file cryptographic APK
  verification using Android `apksig`, and native SHA-256 hashing of the raw
  signer certificates. Package/UID, canonical source path, APK structure,
  signing-block, and archive signer mismatches all fail closed.
- Signing identity is checked before SDK initialization and licensing, then
  checked again whenever an activity returns to the foreground. Invalid builds
  remain behind a non-cancelable security warning.
- Configure the production certificate independently with Gradle property
  `onecoreAllowedSigningSha256` or CI secret
  `ONECORE_ALLOWED_SIGNING_SHA256`. Multiple approved rotation certificates
  may be comma-separated. If omitted, the build derives the digest from its
  configured keystore.
- Release builds minify resources and code, remove verbose logs, and block
  screen capture on sensitive activities.
- Production release APKs additionally run BlackObfuscator control-flow
  flattening at depth 2 over first-party security, license, download, and
  activity code after R8. Generated binding/resource classes, the exact native
  signing entrypoint, and third-party libraries are excluded for runtime
  compatibility, as recommended by BlackObfuscator upstream.
- High-value license transport classes are also marked for the existing
  LSParanoid release string transformation; this conceals fixed endpoint,
  algorithm, binding, and parser strings in the packaged DEX without pretending
  that the whole DEX is cryptographically sealed at runtime.
- BlackObfuscator is disabled unless the release task explicitly sets
  `-PblackObfuscatorEnabled=true`. CI pins upstream commit
  `67aec4c457be0d2644224100fa85aed7eac87cb6`, rejects fewer than 50 transformed
  methods or conversion stack traces, validates every packaged DEX with
  `dexdump`, rejects plaintext high-value license strings, and verifies APK
  signing plus ZIP alignment. CI builds and uploads both release and debug APKs.
  When production transport variables or signing secrets are missing, artifact
  names contain `ci-nonproduction`; only fully configured artifacts are labeled
  `production`. BlackObfuscator remains enabled only for the release APK.
- Tapjacking protection rejects obscured touches on the license input, while
  WorkManager and notification registrations are delegated to their current
  AndroidX manifests for modern Android compatibility.
- Signature or VPN policy failures now stop at a non-cancelable warning in both
  the splash entry point and direct login flow.
- APK builds package only `arm64-v8a`, reducing native payload size and excluding
  32-bit devices by design.

These controls are defense in depth; no client-side Android application can be
made impossible to inspect or modify. Server-side authorization should remain
the source of truth for licenses and entitlements.
