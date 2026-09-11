package top.niunaijun.blackbox.utils.compat;

import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

public class BundleCompat {
    public static IBinder getBinder(Bundle bundle, String key) {
        if (bundle == null) return null;
        try {
            return bundle.getBinder(key);
        } catch (Exception e) {
            return null;
        }
    }

    public static void putBinder(Bundle bundle, String key, IBinder value) {
        if (bundle == null) return;
        try {
            bundle.putBinder(key, value);
        } catch (Exception e) {
        }
    }

    public static void putBinder(Intent intent, String key, IBinder value) {
        if (intent == null) return;
        try {
            Bundle bundle = new Bundle();
            putBinder(bundle, "binder", value);
            intent.putExtra(key, bundle);
        } catch (Exception e) {
        }
    }

    public static IBinder getBinder(Intent intent, String key) {
        if (intent == null) return null;
        try {
            Bundle bundle = intent.getBundleExtra(key);
            return bundle != null ? getBinder(bundle, "binder") : null;
        } catch (Exception e) {
            return null;
        }
    }
}