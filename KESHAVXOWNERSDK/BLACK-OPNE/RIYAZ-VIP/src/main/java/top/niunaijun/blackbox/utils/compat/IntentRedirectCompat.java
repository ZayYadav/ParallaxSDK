package top.niunaijun.blackbox.utils.compat;

import android.content.Intent;
import android.os.Build;

import java.lang.reflect.Method;

/**
 * Android 16 intent-redirection compatibility helper.
 *
 * Parallax sometimes creates a replacement/shadow Intent below the normal
 * Instrumentation preparation layer and then sends that new Intent to the
 * platform. Android 16 expects top-level outgoing Intents to have their nested
 * Intent keys collected so system_server can attach creator tokens to those
 * nested Intents. Normal framework launches do this from Intent's internal
 * prepare-to-leave-process path; replacement Intents created by hooks need the
 * equivalent collection step explicitly.
 *
 * This helper preserves Android's launch-security protection. It deliberately
 * does not call removeLaunchSecurityProtection().
 */
public final class IntentRedirectCompat {
    private static final int ANDROID_16_API = 36;

    private static volatile boolean sCollectorResolved;
    private static volatile Method sCollectExtraIntentKeys;

    private IntentRedirectCompat() {
    }

    public static void collectNestedIntentKeys(Intent intent) {
        if (intent == null || Build.VERSION.SDK_INT < ANDROID_16_API) {
            return;
        }

        try {
            Method collector = getCollector();
            if (collector != null) {
                collector.invoke(intent);
            }
        } catch (Throwable ignored) {
            // Best effort for OEM variants. BlackBoxCore unseals hidden APIs
            // during attach; if an OEM removes/renames this method we leave the
            // Intent untouched rather than weakening launch security.
        }
    }

    private static Method getCollector() {
        if (sCollectorResolved) {
            return sCollectExtraIntentKeys;
        }

        synchronized (IntentRedirectCompat.class) {
            if (sCollectorResolved) {
                return sCollectExtraIntentKeys;
            }
            try {
                Method method = Intent.class.getDeclaredMethod("collectExtraIntentKeys");
                method.setAccessible(true);
                sCollectExtraIntentKeys = method;
            } catch (Throwable ignored) {
                sCollectExtraIntentKeys = null;
            } finally {
                sCollectorResolved = true;
            }
            return sCollectExtraIntentKeys;
        }
    }
}
