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

-keep class top.niunaijun.blackbox.core.system.api.MetaActivationManager { *; }
-keep class top.niunaijun.blackbox.** {*; }
-keep class top.niunaijun.jnihook.** {*; }
-keep class black.** {*; }
-keep class android.** {*; }
-keep class com.android.** {*; }

# Keep the BlackBoxCore class and all its methods
-keep class top.niunaijun.blackbox.BlackBoxCore {
    *;
}

# Keep native methods so they aren't removed or renamed
-keepclassmembers class top.niunaijun.blackbox.BlackBoxCore {
    public native *;
}

# Prevent BthreadMain and other sensitive methods from being renamed
-keepclassmembers class top.niunaijun.blackbox.BlackBoxCore {
    
    
    public boolean BthreadMain();
    public boolean launchApk(java.lang.String, int);
    public boolean installPackageAsUser(java.lang.String, int);
}

-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

