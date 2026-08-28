package top.niunaijun.blackbox.compat.firebase;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Best-effort compatibility fallback for guest applications whose
 * FirebaseInitProvider was not installed by the virtual ActivityThread.
 *
 * This class never supplies credentials or configuration. FirebaseApp.initializeApp
 * reads the guest application's own bundled resources/metadata exactly as the
 * normal FirebaseInitProvider would. If no Firebase SDK/config is present this is
 * a no-op.
 */
public final class FirebaseCompat {
    private static final String TAG = "ParallaxFirebase";
    private static final String FIREBASE_APP = "com.google.firebase.FirebaseApp";

    private FirebaseCompat() {
    }

    public static void ensureDefaultApp(Application application) {
        if (application == null || application.getClassLoader() == null) {
            return;
        }

        try {
            Class<?> firebaseApp = Class.forName(
                    FIREBASE_APP, false, application.getClassLoader());

            if (hasDefaultApp(firebaseApp)) {
                return;
            }

            Method initializeApp = firebaseApp.getMethod(
                    "initializeApp", Context.class);
            Object initialized = initializeApp.invoke(null, application);
            Log.i(TAG, initialized != null
                    ? "default_app_initialized_from_guest_resources"
                    : "default_app_options_unavailable");
        } catch (ClassNotFoundException ignored) {
            // Guest does not use Firebase.
        } catch (Throwable error) {
            // Keep startup compatible with old/new Firebase versions. Do not log
            // resource values, app IDs, API keys or any other configuration data.
            Log.w(TAG, "default_app_initialization_failed: "
                    + error.getClass().getSimpleName());
        }
    }

    private static boolean hasDefaultApp(Class<?> firebaseApp) {
        try {
            Method getInstance = firebaseApp.getMethod("getInstance");
            return getInstance.invoke(null) != null;
        } catch (InvocationTargetException notInitialized) {
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
