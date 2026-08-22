# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ============================================================
# DEBUG / STACKTRACE ATTRIBUTES
# ============================================================

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
# ============================================================

-keep class com.onecore.loader.BoxApplication { *; }
-keep class * extends android.app.Application { *; }

# ============================================================
# BLACKBOX / VIRTUALIZATION CORE
# ============================================================

-keep class top.niunaijun.blackbox.** { *; }
-keep class top.niunaijun.blackbox.core.NativeCore { *; }
-keep class top.niunaijun.blackbox.BlackBoxCore { *; }
-keep class top.niunaijun.blackbox.app.BActivityThread { *; }
-keep class top.niunaijun.blackbox.core.system.api.MetaActivationManager { *; }
-dontwarn top.niunaijun.**

# ============================================================
# HIDDEN API / REFLECTION BRIDGES
# ============================================================

-keep class org.lsposed.hiddenapibypass.** { *; }
-keep class top.niunaijun.jnihook.** { *; }
-keep class black.** { *; }

# Android framework classes are library classes. Keep reflective framework references stable,
# but do not blanket-keep com.android.**: that namespace also contains the bundled APK verifier
# and previously prevented R8 from removing its unreachable implementation code.
-keep class android.** { *; }
-dontwarn com.android.apksig.**

# ============================================================
# LEGACY FLOATING COMPONENT COMPATIBILITY
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
# ============================================================

-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================================
# ANDROID COMPONENTS
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

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

-keepclassmembers class * {
    public void *(android.view.View);
}

# ============================================================
# SECURITY / EARLY STARTUP
# ============================================================

-keep class com.onecore.loader.security.IntegrityEnforcer { *; }
-keep class com.onecore.loader.security.SecurityThreatDetector { *; }
-keep class com.onecore.loader.security.HostedLicenseClient { *; }
-keep class com.onecore.loader.utils.CrashHandler { *; }
-keep class com.onecore.loader.utils.FLog { *; }
-keep class com.onecore.loader.utils.NetworkConnection { *; }
