# One-Core Loader release rules.
# Keep only reflection/JNI/framework boundaries that truly require stable names;
# everything else is available to R8 for shrinking, optimization and obfuscation.

# Remove source paths and let R8 freely consolidate first-party class packages.
-renamesourcefileattribute SourceFile
-allowaccessmodification
-repackageclasses o

# Reflection-heavy virtualization code relies on these metadata attributes.
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Remove verbose release logging.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# Android framework entry point. This class initializes the virtualization core
# before normal app startup and intentionally remains structurally stable.
-keep class com.onecore.loader.BoxApplication { *; }

# BlackBox/virtualization internals use extensive reflection and generated proxy
# names. Keep this boundary intact; shrinking the surrounding Loader remains safe.
-keep class top.niunaijun.blackbox.** { *; }
-dontwarn top.niunaijun.**

# Hidden API/reflection/JNI hook boundaries.
-keep class org.lsposed.hiddenapibypass.** { *; }
-keep class top.niunaijun.jnihook.** { *; }
-keep class black.** { *; }

# Native signing verifier is resolved by its exact JNI class/member names.
-keep class com.onecore.loader.security.NativeSigningVerifier {
    private static native boolean verifySigningIdentity(
        byte[][],
        byte[][],
        java.lang.String,
        java.lang.String
    );
    private static native boolean verifyInstalledApkNative(
        java.lang.String,
        java.lang.String
    );
}
-keepnames class com.onecore.loader.security.NativeSigningVerifier

# Native licensing guard also owns conventional JNI entry points, including the independent APK
# attestation bridge used immediately before network license verification.
-keep class com.onecore.loader.security.NativeLicenseGuard {
    private static native <methods>;
}
-keepnames class com.onecore.loader.security.NativeLicenseGuard

# Preserve every class/member name that is bound through conventional JNI.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Parcelable CREATOR fields are looked up by the Android framework/Binder.
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Warnings from optional logging APIs are not actionable in the release APK.
-dontwarn org.slf4j.**


# WorkManager restores pending jobs by worker class name after process recreation/app relaunch.
# Keep this entry stable so resumable BGMI downloads survive background process death.
-keep class com.onecore.loader.server.ServerInstallWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
