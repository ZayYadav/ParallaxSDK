package top.niunaijun.blackbox.compat.oauth;

import android.net.Uri;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Short-lived in-process state for Facebook browser OAuth callbacks.
 *
 * The exported fbconnect trampoline must never derive a virtual package/user from
 * browser-controlled callback data. Instead it can only claim a callback that
 * matches an active bridge session, including OAuth state validation.
 */
final class FacebookOAuthSessionStore {
    private static final Object LOCK = new Object();
    private static final long SESSION_TTL_MS = 3L * 60L * 1000L;
    private static final long COMPLETED_TTL_MS = 10_000L;
    private static final int MAX_SESSIONS = 4;

    private static final List<Session> SESSIONS = new ArrayList<>();
    private static long nextGeneration = 1L;

    private FacebookOAuthSessionStore() {
    }

    static long begin(Uri authUri, Uri expectedRedirectUri, String virtualPackage, int userId) {
        if (authUri == null || expectedRedirectUri == null
                || virtualPackage == null || virtualPackage.trim().isEmpty() || userId < 0) {
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
                    virtualPackage,
                    userId,
                    now));
            return generation;
        }
    }

    static Claim claim(Uri callbackUri) {
        if (callbackUri == null) {
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
                // Ambiguous callback ownership is rejected instead of guessing.
                if (matched != null) {
                    return null;
                }
                matched = session;
            }
            if (matched == null) {
                return null;
            }
            matched.claimed = true;
            return new Claim(matched.generation, matched.virtualPackage, matched.userId);
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
        final String virtualPackage;
        final int userId;

        Claim(long generation, String virtualPackage, int userId) {
            this.generation = generation;
            this.virtualPackage = virtualPackage;
            this.userId = userId;
        }
    }

    private static final class Session {
        final long generation;
        final Uri authUri;
        final Uri expectedRedirectUri;
        final String virtualPackage;
        final int userId;
        final long startedAt;
        boolean claimed;
        boolean completed;
        long completedAt;

        Session(long generation,
                Uri authUri,
                Uri expectedRedirectUri,
                String virtualPackage,
                int userId,
                long startedAt) {
            this.generation = generation;
            this.authUri = authUri;
            this.expectedRedirectUri = expectedRedirectUri;
            this.virtualPackage = virtualPackage;
            this.userId = userId;
            this.startedAt = startedAt;
        }
    }
}
