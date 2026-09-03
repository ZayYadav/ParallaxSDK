package top.niunaijun.blackbox.compat.oauth;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import top.niunaijun.blackbox.compat.auth.ExternalAuthRouter;
import top.niunaijun.blackbox.proxy.ProxyManifest;
import top.niunaijun.blackbox.utils.compat.BundleCompat;
import top.niunaijun.blackbox.utils.provider.ProviderCall;

/**
 * Short-lived in-process state for OAuth callbacks that return from the real
 * Twitter/X application to the host and then back to the original virtual app.
 *
 * <p>The exported callback Activity never trusts package/user/result-target data
 * supplied by the callback URI. It can only claim an already active session that
 * was created immediately before launching an allow-listed real Twitter/X app.</p>
 */
public final class TwitterOAuthSessionStore {
    private static final Object LOCK = new Object();
    private static final long SESSION_TTL_MS = 3L * 60L * 1000L;
    private static final long COMPLETED_TTL_MS = 10_000L;
    private static final int MAX_SESSIONS = 4;

    private static final Set<String> HOST_CAPTURE_SCHEMES = new HashSet<>(Arrays.asList(
            "twittersdk",
            "twitterkit",
            "twitter",
            "twitterauth",
            "oauth-twitter",
            "xauth",
            "x"
    ));

    private static final List<Session> SESSIONS = new ArrayList<>();
    private static long nextGeneration = 1L;

    private TwitterOAuthSessionStore() {
    }

    /** True only for callback schemes that are explicitly registered by the host manifest. */
    public static boolean isHostCaptureSupported(Uri redirectUri) {
        if (redirectUri == null || redirectUri.getScheme() == null) {
            return false;
        }
        return HOST_CAPTURE_SCHEMES.contains(
                redirectUri.getScheme().toLowerCase(Locale.US));
    }

    static long begin(Intent bridgeIntent, Uri authUri, Uri expectedRedirectUri,
            String providerPackage) {
        if (bridgeIntent == null || authUri == null || expectedRedirectUri == null
                || !isHostCaptureSupported(expectedRedirectUri)
                || !ExternalAuthRouter.isTwitterProviderPackage(providerPackage)) {
            return -1L;
        }

        Bundle extras = bridgeIntent.getExtras();
        if (extras == null) {
            return -1L;
        }
        IBinder resultTo = extras.getBinder(ExternalAuthRouter.EXTRA_RESULT_BINDER);
        String resultWho = extras.getString(ExternalAuthRouter.EXTRA_RESULT_WHO);
        int requestCode = extras.getInt(ExternalAuthRouter.EXTRA_REQUEST_CODE, -1);
        int bpid = extras.getInt(ExternalAuthRouter.EXTRA_BPID, -1);
        int userId = extras.getInt(ExternalAuthRouter.EXTRA_USER_ID, -1);
        String virtualPackage = extras.getString(ExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE);
        if (resultTo == null || requestCode < 0 || bpid < 0 || bpid > 24 || userId < 0
                || virtualPackage == null || virtualPackage.trim().isEmpty()) {
            return -1L;
        }

        synchronized (LOCK) {
            long now = SystemClock.elapsedRealtime();
            purgeLocked(now);
            removeTargetLocked(virtualPackage, userId);
            while (SESSIONS.size() >= MAX_SESSIONS) {
                SESSIONS.remove(0);
            }
            long generation = nextGeneration++;
            if (nextGeneration <= 0L) {
                nextGeneration = 1L;
            }
            SESSIONS.add(new Session(
                    generation,
                    authUri,
                    expectedRedirectUri,
                    providerPackage,
                    virtualPackage,
                    userId,
                    bpid,
                    resultTo,
                    resultWho,
                    requestCode,
                    now));
            return generation;
        }
    }

    static Claim claim(Uri callbackUri) {
        if (callbackUri == null || !isHostCaptureSupported(callbackUri)
                || !hasOAuthResult(callbackUri)) {
            return null;
        }
        synchronized (LOCK) {
            long now = SystemClock.elapsedRealtime();
            purgeLocked(now);
            Session matched = null;
            for (Session session : SESSIONS) {
                if (session.claimed || session.completed) {
                    continue;
                }
                if (!OAuthCallbackValidator.matches(
                        session.authUri, session.expectedRedirectUri, callbackUri)) {
                    continue;
                }
                if (matched != null) {
                    // Never guess which guest owns an ambiguous browser/provider callback.
                    return null;
                }
                matched = session;
            }
            if (matched == null) {
                return null;
            }
            matched.claimed = true;
            return matched.toClaim();
        }
    }

    static boolean deliver(Claim claim, int resultCode, Intent data) {
        if (claim == null) {
            return false;
        }
        try {
            Bundle relay = new Bundle();
            BundleCompat.putBinder(
                    relay, ExternalAuthRouter.EXTRA_RESULT_BINDER, claim.resultTo);
            relay.putString(ExternalAuthRouter.EXTRA_RESULT_WHO, claim.resultWho);
            relay.putInt(ExternalAuthRouter.EXTRA_REQUEST_CODE, claim.requestCode);
            relay.putInt(ExternalAuthRouter.EXTRA_RESULT_CODE, resultCode);
            relay.putInt(ExternalAuthRouter.EXTRA_BPID, claim.bpid);
            relay.putInt(ExternalAuthRouter.EXTRA_USER_ID, claim.userId);
            relay.putString(
                    ExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE, claim.virtualPackage);
            if (data != null) {
                relay.putParcelable(
                        ExternalAuthRouter.EXTRA_RESULT_DATA, new Intent(data));
            }
            Bundle response = ProviderCall.callSafely(
                    ProxyManifest.getProxyAuthorities(claim.bpid),
                    ExternalAuthRouter.METHOD_DELIVER_ACTIVITY_RESULT,
                    null,
                    relay);
            return response != null
                    && response.getBoolean(
                    ExternalAuthRouter.EXTRA_RESULT_DELIVERED, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void complete(long generation) {
        synchronized (LOCK) {
            long now = SystemClock.elapsedRealtime();
            purgeLocked(now);
            Session session = findGenerationLocked(generation);
            if (session == null) {
                return;
            }
            session.claimed = true;
            session.completed = true;
            session.completedAt = now;
        }
    }

    static void release(long generation) {
        synchronized (LOCK) {
            Session session = findGenerationLocked(generation);
            if (session != null && !session.completed) {
                session.claimed = false;
            }
        }
    }

    static boolean isCompleted(String virtualPackage, int userId) {
        if (virtualPackage == null || userId < 0) {
            return false;
        }
        synchronized (LOCK) {
            long now = SystemClock.elapsedRealtime();
            purgeLocked(now);
            for (Session session : SESSIONS) {
                if (session.completed
                        && session.userId == userId
                        && session.virtualPackage.equals(virtualPackage)) {
                    return true;
                }
            }
            return false;
        }
    }

    static void clear(String virtualPackage, int userId) {
        if (virtualPackage == null || userId < 0) {
            return;
        }
        synchronized (LOCK) {
            removeTargetLocked(virtualPackage, userId);
        }
    }

    private static boolean hasOAuthResult(Uri callbackUri) {
        try {
            return hasQuery(callbackUri, "oauth_token")
                    || hasQuery(callbackUri, "oauth_verifier")
                    || hasQuery(callbackUri, "code")
                    || hasQuery(callbackUri, "denied")
                    || hasQuery(callbackUri, "error");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasQuery(Uri uri, String name) {
        String value = uri.getQueryParameter(name);
        return value != null && !value.isEmpty();
    }

    private static Session findGenerationLocked(long generation) {
        for (Session session : SESSIONS) {
            if (session.generation == generation) {
                return session;
            }
        }
        return null;
    }

    private static void removeTargetLocked(String virtualPackage, int userId) {
        Iterator<Session> iterator = SESSIONS.iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (session.userId == userId && session.virtualPackage.equals(virtualPackage)) {
                iterator.remove();
            }
        }
    }

    private static void purgeLocked(long now) {
        Iterator<Session> iterator = SESSIONS.iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            long age = now - session.startedAt;
            if (age < 0L || age > SESSION_TTL_MS) {
                iterator.remove();
                continue;
            }
            if (session.completed) {
                long completedAge = now - session.completedAt;
                if (completedAge < 0L || completedAge > COMPLETED_TTL_MS) {
                    iterator.remove();
                }
            }
        }
    }

    static final class Claim {
        final long generation;
        final String providerPackage;
        final String virtualPackage;
        final int userId;
        final int bpid;
        final IBinder resultTo;
        final String resultWho;
        final int requestCode;

        Claim(long generation, String providerPackage, String virtualPackage,
                int userId, int bpid, IBinder resultTo, String resultWho,
                int requestCode) {
            this.generation = generation;
            this.providerPackage = providerPackage;
            this.virtualPackage = virtualPackage;
            this.userId = userId;
            this.bpid = bpid;
            this.resultTo = resultTo;
            this.resultWho = resultWho;
            this.requestCode = requestCode;
        }
    }

    private static final class Session {
        final long generation;
        final Uri authUri;
        final Uri expectedRedirectUri;
        final String providerPackage;
        final String virtualPackage;
        final int userId;
        final int bpid;
        final IBinder resultTo;
        final String resultWho;
        final int requestCode;
        final long startedAt;
        boolean claimed;
        boolean completed;
        long completedAt;

        Session(long generation, Uri authUri, Uri expectedRedirectUri,
                String providerPackage, String virtualPackage, int userId, int bpid,
                IBinder resultTo, String resultWho, int requestCode, long startedAt) {
            this.generation = generation;
            this.authUri = authUri;
            this.expectedRedirectUri = expectedRedirectUri;
            this.providerPackage = providerPackage;
            this.virtualPackage = virtualPackage;
            this.userId = userId;
            this.bpid = bpid;
            this.resultTo = resultTo;
            this.resultWho = resultWho;
            this.requestCode = requestCode;
            this.startedAt = startedAt;
        }

        Claim toClaim() {
            return new Claim(
                    generation, providerPackage, virtualPackage, userId, bpid,
                    resultTo, resultWho, requestCode);
        }
    }
}
