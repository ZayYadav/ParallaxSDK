package com.onecore.loader.security;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.onecore.loader.R;
import com.onecore.loader.activity.SplashActivity;
import com.onecore.loader.utils.FLog;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Revalidates APK signing identity without blocking the Android main thread.
 *
 * <p>ApkVerifier reads and cryptographically verifies the installed APK archive, which can take
 * noticeable time for a large loader. Running that work from Application.onCreate() or lifecycle
 * callbacks can prevent the first frame from being drawn and can trigger an ANR. Verification is
 * therefore serialized on a background worker and the last result is cached.</p>
 */
public final class IntegrityEnforcer implements Application.ActivityLifecycleCallbacks {
    private static final long RECHECK_INTERVAL_MS = 5 * 60 * 1000L;

    private final Application application;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService verifierExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "OneCore-Integrity");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean verificationRunning = new AtomicBoolean(false);
    private final Map<Activity, Boolean> blockedActivities =
            Collections.synchronizedMap(new WeakHashMap<>());

    private volatile long lastCheckElapsed;
    private volatile AppIntegrity.Verification lastVerification;
    private volatile WeakReference<Activity> foregroundActivity = new WeakReference<>(null);

    private IntegrityEnforcer(Application application) {
        this.application = application;
    }

    /**
     * Installs process-wide enforcement and schedules the first verification asynchronously.
     *
     * <p>The boolean return value is retained for source compatibility. Installation itself does
     * not block startup; an invalid result is enforced as soon as a non-splash activity is active.</p>
     */
    public static boolean install(Application application) {
        IntegrityEnforcer enforcer = new IntegrityEnforcer(application);
        application.registerActivityLifecycleCallbacks(enforcer);
        enforcer.scheduleVerification(null);
        return true;
    }

    private boolean isVerificationStale() {
        AppIntegrity.Verification cached = lastVerification;
        if (cached == null) {
            return true;
        }
        return SystemClock.elapsedRealtime() - lastCheckElapsed >= RECHECK_INTERVAL_MS;
    }

    private void scheduleVerification(Activity preferredActivity) {
        if (!isVerificationStale() || !verificationRunning.compareAndSet(false, true)) {
            return;
        }

        WeakReference<Activity> preferred = new WeakReference<>(preferredActivity);
        verifierExecutor.execute(() -> {
            try {
                try {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                } catch (Throwable ignored) {
                    // Thread priority is only a performance hint.
                }

                AppIntegrity.Verification verification = AppIntegrity.verify(application);
                lastVerification = verification;
                lastCheckElapsed = SystemClock.elapsedRealtime();

                if (!verification.isValid()) {
                    FLog.error("APK signing identity rejected: " + verification.status().name());
                    Activity target = preferred.get();
                    if (!isUsable(target)) {
                        WeakReference<Activity> foreground = foregroundActivity;
                        target = foreground == null ? null : foreground.get();
                    }
                    final Activity activityToBlock = target;
                    if (isUsable(activityToBlock) && !(activityToBlock instanceof SplashActivity)) {
                        mainHandler.post(() -> blockActivity(activityToBlock, verification));
                    }
                }
            } catch (Throwable error) {
                FLog.error("Background APK integrity verification failed", error);
            } finally {
                verificationRunning.set(false);
            }
        });
    }

    private static boolean isUsable(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private void enforce(Activity activity) {
        if (!isUsable(activity)) {
            return;
        }

        foregroundActivity = new WeakReference<>(activity);
        AppIntegrity.Verification cached = lastVerification;

        if (cached != null && !cached.isValid()) {
            if (!(activity instanceof SplashActivity)) {
                blockActivity(activity, cached);
            }
            return;
        }

        if (isVerificationStale()) {
            scheduleVerification(activity);
        }
    }

    private void blockActivity(Activity activity, AppIntegrity.Verification verification) {
        if (!isUsable(activity) || activity instanceof SplashActivity) {
            return;
        }
        if (blockedActivities.put(activity, Boolean.TRUE) != null) {
            return;
        }

        new Thread(() -> {
            try {
                new HostedLicenseClient(application).reportSecurityEvent(
                        "INVALID_SIGNATURE_" + verification.status().name(),
                        "critical");
            } catch (Throwable ignored) {
                // Local enforcement does not depend on telemetry availability.
            }
        }, "IntegrityReport").start();

        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> blockActivity(activity, verification));
            return;
        }

        if (!isUsable(activity)) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.security_warning_title)
                .setMessage(R.string.security_warning_signature)
                .setCancelable(false)
                .setPositiveButton(R.string.close_app, (dialog, which) -> {
                    dialog.dismiss();
                    activity.finishAffinity();
                })
                .show();
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        enforce(activity);
    }

    @Override
    public void onActivityStarted(Activity activity) {
        // onActivityResumed is the foreground enforcement point.
    }

    @Override
    public void onActivityResumed(Activity activity) {
        enforce(activity);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        // No blocking work on lifecycle callbacks.
    }

    @Override
    public void onActivityStopped(Activity activity) {
        // No action required.
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        // No action required.
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        blockedActivities.remove(activity);
        WeakReference<Activity> foreground = foregroundActivity;
        if (foreground != null && foreground.get() == activity) {
            foregroundActivity = new WeakReference<>(null);
        }
    }
}
