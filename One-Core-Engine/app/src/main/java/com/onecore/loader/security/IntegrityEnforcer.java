package com.onecore.loader.security;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.onecore.loader.R;
import com.onecore.loader.activity.SplashActivity;
import com.onecore.loader.utils.FLog;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Revalidates the APK signing identity whenever the application returns to foreground. */
public final class IntegrityEnforcer implements Application.ActivityLifecycleCallbacks {
    private static final long RECHECK_INTERVAL_MS = 2_000L;

    private final Application application;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<Activity, Boolean> blockedActivities =
            Collections.synchronizedMap(new WeakHashMap<>());

    private volatile long lastCheckElapsed;
    private volatile AppIntegrity.Verification lastVerification;

    private IntegrityEnforcer(Application application) {
        this.application = application;
    }

    /** Installs process-wide enforcement and performs the first fail-closed verification. */
    public static boolean install(Application application) {
        IntegrityEnforcer enforcer = new IntegrityEnforcer(application);
        application.registerActivityLifecycleCallbacks(enforcer);
        AppIntegrity.Verification verification = enforcer.verifyNow();
        return verification.isValid();
    }

    private AppIntegrity.Verification verifyNow() {
        AppIntegrity.Verification verification = AppIntegrity.verify(application);
        lastVerification = verification;
        lastCheckElapsed = android.os.SystemClock.elapsedRealtime();
        if (!verification.isValid()) {
            FLog.error("APK signing identity rejected: " + verification.status().name());
        }
        return verification;
    }

    private AppIntegrity.Verification currentVerification() {
        AppIntegrity.Verification cached = lastVerification;
        long age = android.os.SystemClock.elapsedRealtime() - lastCheckElapsed;
        if (cached == null || !cached.isValid() || age >= RECHECK_INTERVAL_MS) {
            return verifyNow();
        }
        return cached;
    }

    private void enforce(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        AppIntegrity.Verification verification = currentVerification();
        if (verification.isValid() || activity instanceof SplashActivity) {
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

        mainHandler.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
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
        });
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
        // No action required.
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
    }
}
