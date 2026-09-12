# Parallax Virtual — opt-in debug library bridge

This bridge is for apps whose source you control. Parallax Virtual does **not** silently inject a shared library into unrelated app processes.

## What the loader does

When you choose **Developer Lab → Load / replace debug .so** for a cloned app, Parallax Virtual:

1. Copies the selected `.so` into the loader's private `debug-libs` directory.
2. Validates the file name, 64 MB size limit, and ELF magic.
3. On launch, creates a temporary read-only `content://parallax.VIRTUAL.debugfiles/...` URI.
4. Adds the URI plus target package/session metadata to the app's launch Intent.
5. Grants read access for that launch only.

The loader never calls `System.load()` inside another app on its own.

## Enable it in your own debug build

Copy `ParallaxDebugBootstrap.java` into your app source and call it very early in the launcher Activity:

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    ParallaxDebugBootstrap.LoadResult result =
            ParallaxDebugBootstrap.loadFromLaunchIntent(this, BuildConfig.DEBUG);

    // Continue normal app startup.
}
```

Keep the second parameter tied to your real debug/developer build flag. The bootstrap ignores requests in non-debug builds when you pass `false`.

## Safety checks

The bootstrap accepts only:

- a launch request addressed to its own package;
- `content://` URIs;
- the exact Parallax Virtual debug provider authority;
- files ending in `.so`;
- ELF files up to 64 MB.

It copies the library into the target app's own `codeCacheDir` before `System.load()`.

## Crash diagnostics

The loader branch also records host/virtual-process uncaught exceptions in its private `crash-reports` directory. Open **Developer Lab → Show latest crash report** to inspect the newest report without exposing it to other installed apps.

## Compatibility note

The current BLACK-OPNE package installer consumes one APK path. Apps installed as base APK + split APK modules should use a universal/single-APK debug build for deterministic cloning until a native split-package installer is implemented.
