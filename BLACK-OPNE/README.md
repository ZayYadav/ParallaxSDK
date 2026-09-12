# Parallax Virtual Android SDK

Parallax Virtual is the reusable Android virtualization SDK used by the Parallax Virtual loader. It provides an isolated multi-app container for cloning and launching applications that are already installed on the device.

## What this branch changes

- Product branding is **Parallax Virtual**.
- The virtual engine is generic: it is not tied to BGMI/PUBG or any single package.
- Installed applications can be imported with `BlackBoxCore.installPackageAsUser(packageName, userId)` and launched with `BlackBoxCore.launchApk(packageName, userId)`.
- Virtual copies can be enumerated with `BlackBoxCore.getInstalledPackages(...)`, stopped, cleared, and removed independently of the real installed app.
- The old `no_backup/native/Parallax.so` app-start auto-load path is removed. The SDK loads only its packaged virtualization JNI core.
- No ZIP download/extract pipeline is required for cloning an installed application.
- Root visibility can be selected by the host before a virtual app launch through the existing `BlackBoxCore.setHideRoot(...)` policy. This is a **sandbox visibility/compatibility option only**; it does not grant Linux root, device root, SELinux bypass, or elevated host privileges.

## Compatibility

| Area | Support |
| --- | --- |
| Android runtime | API 24 through API 36 |
| Compile/target SDK | API 36 |
| Native ABIs | `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` |
| Native page sizes | Flexible, including 16 KB devices |
| Java toolchain | Java 17 |
| Build toolchain | AGP 8.11.1 and Gradle 8.13 |

The internal Gradle module and packaged native ABI remain named `ParallaxCore` for binary/JNI compatibility with existing consumers. The project/product identity on this branch is Parallax Virtual.

## Generic installed-app cloning

Typical host flow:

```java
int userId = 0;
String packageName = "com.example.app";

InstallResult result = BlackBoxCore.get().installPackageAsUser(packageName, userId);
if (result != null && result.success) {
    BlackBoxCore.get().launchApk(packageName, userId);
}
```

The package comes from Android `PackageManager`; no remote APK/ZIP or game-specific shared library is needed.

For a per-app sandbox-privilege preference, the host should persist the choice per package and apply it immediately before launch:

```java
boolean sandboxPrivilege = /* host preference for this virtual package */;
BlackBoxCore.setHideRoot(!sandboxPrivilege);
BlackBoxCore.get().launchApk(packageName, userId);
```

Again, this changes only what the virtualized process can observe inside the compatibility layer. It does not elevate the process on the real Android device.

## Device social authentication

Parallax Virtual routes supported authentication surfaces to the authoritative app or browser installed on the phone. Existing device sessions can therefore be offered by the provider without copying cookies, passwords, account-manager data, or access tokens into the virtual app.

Provider-side package/signature/OAuth verification is not bypassed. Repackaging or cloning can change effective identity, so each integration must be configured with its provider correctly.

## Build

Install JDK 17, Android SDK Platform 36, Build Tools 36.0.0, and NDK `27.2.12479018`, then run:

```bash
./gradlew :ParallaxCore:assembleRelease
```

The release AAR remains compatible with existing ParallaxCore consumers while this branch carries the Parallax Virtual product behavior and branding.

## Security boundary

Virtualization is an application sandbox feature, not a device privilege escalation mechanism. Authorization decisions, secrets, license policy, and privileged operations must remain server-side or use Android-supported APIs. Parallax Virtual does not forge app signatures, defeat Play Integrity, extract another installation's private sessions, grant real root, or disable SELinux.
