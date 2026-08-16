# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see:
# http://developer.android.com/guide/developing/tools/proguard.html


# ============================================================
# DEBUG / STACKTRACE ATTRIBUTES
# ============================================================

# Remove source path while preserving useful stack-trace line mapping.
-renamesourcefileattribute SourceFile

-keepattributes LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod


# ============================================================
# REMOVE RELEASE LOGGING
# ============================================================

-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}


# ============================================================
# APPLICATION ENTRY POINT
# IMPORTANT:
# BoxApplication was causing release VerifyError after obfuscation.
# Keep this framework entry point intact.
# ============================================================

-keep class com.onecore.loader.BoxApplication { *; }

# Extra protection for Android Application subclasses.
-keep class * extends android.app.Application { *; }


# ============================================================
# BLACKBOX / VIRTUALIZATION CORE
# ============================================================

-keep class top.niunaijun.blackbox.** { *; }
-keep class top.niunaijun.blackbox.core.NativeCore { *; }
-keep class top.niunaijun.blackbox.BlackBoxCore { *; }
-keep class top.niunaijun.blackbox.app.BActivityThread { *; }

# MetaActivationManager is called directly from BoxApplication.
-keep class top.niunaijun.blackbox.core.system.api.MetaActivationManager { *; }

-dontwarn top.niunaijun.**


# ============================================================
# HIDDEN API BYPASS
# ============================================================

-keep class org.lsposed.hiddenapibypass.** { *; }


# ============================================================
# JNI HOOK / BLACK REFLECTION
# ============================================================

-keep class top.niunaijun.jnihook.** { *; }
-keep class black.** { *; }


# ============================================================
# ANDROID FRAMEWORK
# NOTE:
# These are broad keep rules. They are retained because your existing
# project depends heavily on reflection/virtualization behavior.
# ============================================================

-keep class android.** { *; }
-keep class com.android.** { *; }


# ============================================================
# JAGDISH LOADER / FLOATING COMPONENTS
# ============================================================

-keep class com.Jagdish.Loader.** { *; }
-keep class com.Jagdish.Loader.floating.** { *; }
-keep class com.Jagdish.Loader.floating.FloatLogo { *; }
-keep class com.Jagdish.Loader.floating.Overlay { *; }
-keep class com.Jagdish.Loader.floating.ESPView { *; }


# ============================================================
# SLF4J
# ============================================================

-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**


# ============================================================
# NATIVE SIGNING VERIFIER
# JNI resolves this method by exact class/member names.
# ============================================================

-keep class com.onecore.loader.security.NativeSigningVerifier {
    private static native boolean verifySigningIdentity(
        byte[][],
        byte[][],
        java.lang.String,
        java.lang.String
    );
}

-keepnames class com.onecore.loader.security.NativeSigningVerifier


# ============================================================
# JNI NATIVE METHODS
# Keep native method names stable where JNI may resolve by name.
# ============================================================

-keepclasseswithmembernames class * {
    native <methods>;
}


# ============================================================
# ANDROID COMPONENTS
# Protect framework-instantiated components from shrinking/renaming.
# ============================================================

-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.content.ContentProvider { *; }


# ============================================================
# SERIALIZATION / JSON / REFLECTION SAFETY
# ============================================================

-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations


# ============================================================
# KEEP ENUM HELPERS
# ============================================================

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}


# ============================================================
# KEEP PARCELABLE CREATOR
# ============================================================

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}


# ============================================================
# VIEW / XML CALLBACK METHODS
# ============================================================

-keepclassmembers class * {
    public void *(android.view.View);
}


# ============================================================
# SECURITY STARTUP CLASSES
# Keep startup-critical integrity classes stable.
# ============================================================

-keep class com.onecore.loader.security.IntegrityEnforcer { *; }
-keep class com.onecore.loader.security.SecurityThreatDetector { *; }


# ============================================================
# NETWORK / LICENSE CLIENT
# Keep class structure stable while debugging release startup.
# You can relax this later if desired.
# ============================================================

-keep class com.onecore.loader.security.HostedLicenseClient { *; }


# ============================================================
# APPLICATION UTILITIES USED DURING EARLY STARTUP
# ============================================================

-keep class com.onecore.loader.utils.CrashHandler { *; }
-keep class com.onecore.loader.utils.FLog { *; }
-keep class com.onecore.loader.utils.NetworkConnection { *; }
