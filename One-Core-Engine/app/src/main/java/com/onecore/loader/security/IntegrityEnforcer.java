package com.onecore.loader.security;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.WindowManager;

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
 * Process-wide fail-closed signing enforcement.
 *
 * <p>Heavy APK cryptographic work remains off the main thread. A rejected identity clears local
 * license state, blocks the foreground activity, shows one non-cancelable security message, and
 * terminates the process even if the dialog is not acknowledged.</p>
 */
public final class IntegrityEnforcer implements Application.ActivityLifecycleCallbacks {
    private static final long RECHECK_INTERVAL_MS = 60 * 1000L;
    private static final long TERMINATION_GRACE_MS = 3500L;

    private final Application application;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService verifierExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "OneCore-Integrity");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean verificationRunning = new AtomicBoolean(false);
    private final AtomicBoolean terminationScheduled = new AtomicBoolean(false);
    private final Map<Activity, Boolean> blockedActivities =
            Collections.synchronizedMap(new WeakHashMap<>());

    private volatile long lastCheckElapsed;
    private volatile AppIntegrity.Verification lastVerification;
    private volatile WeakReference<Activity> foregroundActivity = new WeakReference<>(null);

    private IntegrityEnforcer(Application application) {
        this.application = application;
    }

    /** Installs process-wide enforcement; expensive archive work starts off the UI thread. */
    public static boolean install(Application application) {
        IntegrityEnforcer enforcer = new IntegrityEnforcer(application);
        application.registerActivityLifecycleCallbacks(enforcer);
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
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                } catch (Throwable ignored) {
                    // Thread priority is only a performance hint.
                }

                AppIntegrity.Verification verification = AppIntegrity.verify(application);
                lastVerification = verification;
                lastCheckElapsed = SystemClock.elapsedRealtime();

                if (!verification.isValid()) {
                    FLog.error("APK signing identity rejected: " + verification.status().name());
                    clearLicenseFailClosed();
                    Activity target = preferred.get();
                    if (!isUsable(target)) {
                        WeakReference<Activity> foreground = foregroundActivity;
                        target = foreground == null ? null : foreground.get();
                    }
                    final Activity activityToBlock = target;
                    if (isUsable(activityToBlock) && !(activityToBlock instanceof SplashActivity)) {
                        mainHandler.post(() -> blockActivity(activityToBlock, verification));
                    } else {
                        scheduleHardTermination(null);
                    }
                }
            } catch (Throwable error) {
                // An integrity verifier failure is itself unsafe. Do not silently continue with a
                // previously trusted state if the verifier can no longer execute.
                FLog.error("Background APK integrity verification failed", error);
                clearLicenseFailClosed();
                scheduleHardTermination(null);
            } finally {
                verificationRunning.set(false);
            }
        });
    }

    private void clearLicenseFailClosed() {
        try {
            new HostedLicenseClient(application).clearLicense();
        } catch (Throwable ignored) {
            // Process termination does not depend on storage cleanup succeeding.
        }
    }

    private static boolean isUsable(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private void enforce(Activity activity) {
        if (!isUsable(activity)) {
            return;
        }

        foregroundActivity = new WeakReference<>(activity);

        // Keep first-frame work off the launcher splash. Login/license verification has its own
        // mandatory native attestation before any server trust decision can succeed.
        if (activity instanceof SplashActivity) {
            return;
        }

        AppIntegrity.Verification cached = lastVerification;
        if (cached != null && !cached.isValid()) {
            blockActivity(activity, cached);
            return;
        }

        if (isVerificationStale()) {
            scheduleVerification(activity);
        }
    }

    private void blockActivity(Activity activity, AppIntegrity.Verification verification) {
        if (!isUsable(activity) || activity instanceof SplashActivity) {
            scheduleHardTermination(activity);
            return;
        }

        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> blockActivity(activity, verification));
            return;
        }

        if (blockedActivities.put(activity, Boolean.TRUE) != null || !isUsable(activity)) {
            scheduleHardTermination(activity);
            return;
        }

        try {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Throwable ignored) {
            // Cosmetic only.
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

        try {
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle(R.string.security_warning_title)
                    .setMessage(R.string.security_warning_signature)
                    .setCancelable(false)
                    .setPositiveButton(R.string.close_app, (ignoredDialog, which) ->
                            hardTerminate(activity))
                    .create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        } catch (Throwable error) {
            FLog.error("Unable to render integrity block dialog", error);
        }
        scheduleHardTermination(activity);
    }

    private void scheduleHardTermination(Activity activity) {
        if (!terminationScheduled.compareAndSet(false, true)) {
            return;
        }
        mainHandler.postDelayed(() -> hardTerminate(activity), TERMINATION_GRACE_MS);
    }

    private void hardTerminate(Activity activity) {
        clearLicenseFailClosed();
        try {
            if (isUsable(activity)) {
                activity.finishAffinity();
            } else {
                WeakReference<Activity> foreground = foregroundActivity;
                Activity current = foreground == null ? null : foreground.get();
                if (isUsable(current)) current.finishAffinity();
            }
        } catch (Throwable ignored) {
            // Continue into process termination.
        }
        try {
            Process.killProcess(Process.myPid());
        } finally {
            System.exit(10);
        }
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
