# One Core Engine loader

Android loader application for the RIYAZ-VIP SDK.

## Compatibility

| Area | Support |
| --- | --- |
| Android runtime | API 24 through API 36 |
| Compile/target SDK | API 36 |
| Loader ABIs | `armeabi-v7a`, `arm64-v8a` |
| Native page sizes | 4 KB and 16 KB |
| Build toolchain | AGP 8.11.1, Gradle 8.13, JDK 17, NDK 27.2 |

The loader is intentionally limited to ARM because its checked-in curl and
OpenSSL prebuilts are available only for 32-bit and 64-bit ARM. Add matching
prebuilts before enabling x86 or x86_64.

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
- APK builds produce smaller ARM32 and ARM64 packages alongside a universal ARM
  compatibility APK. Install the matching ABI package when download size matters.

These controls are defense in depth; no client-side Android application can be
made impossible to inspect or modify. Server-side authorization should remain
the source of truth for licenses and entitlements.
