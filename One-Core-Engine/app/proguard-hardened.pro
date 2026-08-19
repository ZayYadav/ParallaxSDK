# Extra rules for the safe hardened distribution variant.
# Shared JNI/reflection keep rules remain in proguard-rules.pro.

# Allow R8 to fold accessors and private helper boundaries where it is safe.
-allowaccessmodification

# Remove non-essential verbose/info logging calls from optimized output.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

-assumenosideeffects class com.onecore.loader.utils.FLog {
    public static void debug(java.lang.String);
    public static void info(java.lang.String);
}

# Keep useful failure diagnostics; warning/error calls are intentionally retained.
