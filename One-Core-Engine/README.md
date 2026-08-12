# One Core Engine loader

Android loader application for the RIYAZ-VIP SDK.

## Compatibility

| Area | Support |
| --- | --- |
| Android runtime | API 24 through API 36 |
| Compile/target SDK | API 36 |
| Loader ABI | `arm64-v8a` only |
| Native page sizes | 4 KB and 16 KB |
| Build toolchain | AGP 8.11.1, Gradle 8.13, JDK 17, NDK 27.2 |

The OneCore Engine loader intentionally produces a single 64-bit ARM APK. Its
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
AAR first, copy it to `app/libs/Bcore-release.aar`, and then run:

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
- Release builds validate the installed signing-certificate SHA-256 digest,
  minify resources and code, remove verbose logs, and block screen capture on
  sensitive activities.
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
