# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Remove source paths and release logging while preserving stack-trace line mapping.
-renamesourcefileattribute SourceFile
-keepattributes LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

-keep class top.niunaijun.blackbox.** {*; }
-keep class top.niunaijun.blackbox.core.NativeCore { *; }
-keep class top.niunaijun.blackbox.BlackBoxCore { *; }
-keep class top.niunaijun.blackbox.app.BActivityThread { *; }
#-keep class top.niunaijun.blackbox.core.system.api.MetaActivationManager { *; }

-keep class org.lsposed.hiddenapibypass.** { *; }

-keep class top.niunaijun.jnihook.** {*; }
-keep class black.** {*; }
-keep class android.** {*; }
-keep class com.android.** {*; }

-keep class com.Jagdish.Loader.** { *; }
-keep class com.Jagdish.Loader.floating.** { *; }
-keep class com.Jagdish.Loader.floating.FloatLogo { *; }
-keep class com.Jagdish.Loader.floating.Overlay { *; }
-keep class com.Jagdish.Loader.floating.ESPView { *; }
-keep class android.** { *; }
-dontwarn top.niunaijun.**
# Keep SLF4J logging classes
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**
