# ParallaxCore Android SDK

ParallaxCore is the reusable Android library consumed by the Parallax Loader application.
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

## Build

Install JDK 17, Android SDK Platform 36, Build Tools 36.0.0, and NDK
27.2.12479018, then run:

```bash
./gradlew :ParallaxCore:assembleRelease
```

The release AAR is written to
`RIYAZ-VIP/build/outputs/aar/ParallaxCore-release.aar`.
