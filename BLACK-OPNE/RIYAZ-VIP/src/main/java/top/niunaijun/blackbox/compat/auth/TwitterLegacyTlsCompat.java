package top.niunaijun.blackbox.compat.auth;

import android.app.Activity;
import android.os.SystemClock;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Compatibility shim for the archived Twitter Kit OAuth 1.0a flow.
 *
 * <p>Twitter Kit 3.x hard-codes a SHA-1 {@code CertificatePinner} for
 * {@code *.twitter.com}. Those roots are no longer the chain served by
 * api.twitter.com, so old guest applications fail before the OAuth WebView can
 * receive a request token. This shim is intentionally narrow: it only touches
 * the OkHttp client owned by Twitter Kit's OAuthActivity/OAuth1aService and it
 * only removes a collection that is positively identified as Twitter certificate
 * pins. Android's normal TLS trust manager and OkHttp hostname verification stay
 * enabled.</p>
 */
public final class TwitterLegacyTlsCompat {
    private static final String TAG = "ParallaxTwitterCompat";
    private static final String TWITTER_OAUTH_ACTIVITY =
            "com.twitter.sdk.android.core.identity.OAuthActivity";
    private static final String TWITTER_OAUTH_CONTROLLER_SUFFIX = ".OAuthController";
    private static final String TWITTER_OAUTH_SERVICE_SUFFIX = ".OAuth1aService";
    private static final String RETROFIT_CLASS = "retrofit2.Retrofit";
    private static final String OKHTTP_PREFIX = "okhttp3.";

    private static final long PATCH_WINDOW_MS = 1500L;
    private static final int MAX_GRAPH_DEPTH = 5;

    private TwitterLegacyTlsCompat() {
    }

    /**
     * Starts a very short-lived watcher before OAuthActivity.onCreate().
     * OAuth1aService creates and queues its request from onCreate(), so arming
     * before the callback closes the race between client construction and the
     * first TLS connection.
     */
    public static void armBeforeCreate(Activity activity) {
        if (!isLegacyTwitterOAuthActivity(activity)) {
            return;
        }

        final WeakReference<Activity> activityRef = new WeakReference<>(activity);
        Thread patchThread = new Thread(() -> {
            final long start = SystemClock.elapsedRealtime();
            final long deadline = start + PATCH_WINDOW_MS;
            while (SystemClock.elapsedRealtime() < deadline) {
                Activity target = activityRef.get();
                if (target == null) {
                    return;
                }
                if (patchActivity(target)) {
                    return;
                }

                // During the first part of onCreate() prefer yielding over a
                // coarse sleep so the shim can catch OAuthService immediately
                // after it constructs its Retrofit/OkHttp client.
                if (SystemClock.elapsedRealtime() - start < 120L) {
                    Thread.yield();
                } else {
                    SystemClock.sleep(4L);
                }
            }
        }, "Parallax-TwitterTlsCompat");
        patchThread.setDaemon(true);
        patchThread.start();
    }

    /**
     * Synchronous second chance used immediately after OAuthActivity.onCreate().
     */
    public static void patchAfterCreate(Activity activity) {
        if (isLegacyTwitterOAuthActivity(activity)) {
            patchActivity(activity);
        }
    }

    private static boolean isLegacyTwitterOAuthActivity(Activity activity) {
        return activity != null
                && TWITTER_OAUTH_ACTIVITY.equals(activity.getClass().getName());
    }

    private static boolean patchActivity(Activity activity) {
        try {
            Object controller = findFieldValueByClassSuffix(
                    activity, TWITTER_OAUTH_CONTROLLER_SUFFIX);
            if (controller == null) {
                return false;
            }

            Object oauthService = findFieldValueByClassSuffix(
                    controller, TWITTER_OAUTH_SERVICE_SUFFIX);
            if (oauthService == null) {
                return false;
            }

            Object retrofit = findFieldValueByExactClass(oauthService, RETROFIT_CLASS);
            if (retrofit == null) {
                return false;
            }

            IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
            for (Field field : allFields(retrofit.getClass())) {
                Object value = readField(field, retrofit);
                if (value == null || !isOkHttpObject(value)) {
                    continue;
                }
                if (clearTwitterPins(value, 0, visited)) {
                    Log.i(TAG, "Legacy Twitter OAuth pins removed; platform TLS/hostname verification remain enabled");
                    return true;
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "Unable to apply legacy Twitter OAuth TLS compatibility", error);
        }
        return false;
    }

    private static boolean clearTwitterPins(Object node, int depth,
                                            IdentityHashMap<Object, Boolean> visited) {
        if (node == null || depth > MAX_GRAPH_DEPTH || visited.put(node, Boolean.TRUE) != null) {
            return false;
        }

        for (Field field : allFields(node.getClass())) {
            Object value = readField(field, node);
            if (value == null) {
                continue;
            }

            if (value instanceof Collection) {
                Collection<?> collection = (Collection<?>) value;
                if (looksLikeTwitterPinCollection(collection)
                        && clearCollection(node, field, collection)) {
                    return true;
                }
                continue;
            }

            if (isOkHttpObject(value)
                    && clearTwitterPins(value, depth + 1, visited)) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeTwitterPinCollection(Collection<?> values) {
        if (values.isEmpty() || values.size() > 128) {
            return false;
        }

        boolean twitterPattern = false;
        boolean certificateHash = false;
        int inspected = 0;
        for (Object value : values) {
            if (value == null || inspected++ >= 128) {
                continue;
            }
            String text;
            try {
                text = String.valueOf(value);
            } catch (Throwable ignored) {
                text = "";
            }
            String normalized = text.toLowerCase(java.util.Locale.US);
            twitterPattern |= normalized.contains("twitter.com");
            certificateHash |= normalized.contains("sha1/") || normalized.contains("sha256/");

            // Some R8 builds shorten Pin.toString(). Fall back to inspecting
            // String fields on the pin object for the hostname pattern.
            if (!twitterPattern && isOkHttpObject(value)) {
                twitterPattern = objectContainsTwitterHost(value);
            }

            if (twitterPattern && certificateHash) {
                return true;
            }
        }
        return false;
    }

    private static boolean objectContainsTwitterHost(Object value) {
        for (Field field : allFields(value.getClass())) {
            Object nested = readField(field, value);
            if (nested instanceof String
                    && ((String) nested).toLowerCase(java.util.Locale.US).contains("twitter.com")) {
                return true;
            }
        }
        return false;
    }

    private static boolean clearCollection(Object owner, Field field, Collection<?> collection) {
        try {
            collection.clear();
            if (collection.isEmpty()) {
                return true;
            }
        } catch (Throwable ignored) {
            // Most OkHttp versions use an immutable Set; replace the field below.
        }

        Object replacement = emptyCollectionFor(field.getType());
        if (replacement == null) {
            return false;
        }
        try {
            field.setAccessible(true);
            field.set(owner, replacement);
            Object updated = field.get(owner);
            return updated instanceof Collection && ((Collection<?>) updated).isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object emptyCollectionFor(Class<?> fieldType) {
        if (Set.class.isAssignableFrom(fieldType)) {
            return Collections.emptySet();
        }
        if (List.class.isAssignableFrom(fieldType)) {
            return Collections.emptyList();
        }
        if (Collection.class.equals(fieldType)) {
            return Collections.emptyList();
        }
        return null;
    }

    private static Object findFieldValueByClassSuffix(Object owner, String classSuffix) {
        if (owner == null) {
            return null;
        }
        for (Field field : allFields(owner.getClass())) {
            Object value = readField(field, owner);
            if (value != null && value.getClass().getName().endsWith(classSuffix)) {
                return value;
            }
        }
        return null;
    }

    private static Object findFieldValueByExactClass(Object owner, String className) {
        if (owner == null) {
            return null;
        }
        for (Field field : allFields(owner.getClass())) {
            Object value = readField(field, owner);
            if (value != null && className.equals(value.getClass().getName())) {
                return value;
            }
        }
        return null;
    }

    private static boolean isOkHttpObject(Object value) {
        return value != null && value.getClass().getName().startsWith(OKHTTP_PREFIX);
    }

    private static Field[] allFields(Class<?> type) {
        java.util.ArrayList<Field> fields = new java.util.ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Collections.addAll(fields, current.getDeclaredFields());
            } catch (Throwable ignored) {
                // Keep walking parent classes if one class cannot be reflected.
            }
            current = current.getSuperclass();
        }
        return fields.toArray(new Field[0]);
    }

    private static Object readField(Field field, Object owner) {
        try {
            field.setAccessible(true);
            return field.get(owner);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
