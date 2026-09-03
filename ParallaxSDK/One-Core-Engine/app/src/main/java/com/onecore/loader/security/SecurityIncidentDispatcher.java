package com.onecore.loader.security;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import com.onecore.loader.ui.SecurityCinematicDialogV2;
import com.onecore.loader.utils.FLog;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/** Routes confirmed signature incidents to the composited security response. */
public final class SecurityIncidentDispatcher {
    public enum Reason {
        SIGNATURE,
        DEBUGGER,
        INTEGRITY
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
    private static volatile WeakReference<Activity> foreground = new WeakReference<>(null);
    private static volatile Reason pendingReason;
    private static volatile String pendingDetail;

    private SecurityIncidentDispatcher() {
    }

    public static void attach(Activity activity) {
        if (!usable(activity)) return;
        foreground = new WeakReference<>(activity);
        Reason reason = pendingReason;
        if (reason != null && !ACTIVE.get()) {
            String detail = pendingDetail;
            MAIN.post(() -> dispatch(activity, reason, detail));
        }
    }

    public static void detach(Activity activity) {
        WeakReference<Activity> reference = foreground;
        if (reference != null && reference.get() == activity) {
            foreground = new WeakReference<>(null);
        }
    }

    public static void raise(Reason reason, String detail) {
        raise(null, reason, detail);
    }

    public static void raise(Activity preferredActivity, Reason reason, String detail) {
        if (reason == null) reason = Reason.INTEGRITY;
        pendingReason = reason;
        pendingDetail = detail == null ? "" : detail;
        Activity target = usable(preferredActivity) ? preferredActivity : currentActivity();
        final Reason finalReason = reason;
        final String finalDetail = pendingDetail;
        if (usable(target)) {
            MAIN.post(() -> dispatch(target, finalReason, finalDetail));
        }
    }

    private static Activity currentActivity() {
        WeakReference<Activity> reference = foreground;
        return reference == null ? null : reference.get();
    }

    private static void dispatch(Activity activity, Reason reason, String detail) {
        if (!usable(activity)) return;
        pendingReason = null;
        pendingDetail = null;

        if (reason != Reason.SIGNATURE) {
            hardTerminate(activity, reason);
            return;
        }
        showSignatureCinematic(activity, detail);
    }

    private static void showSignatureCinematic(Activity activity, String detail) {
        if (!usable(activity) || !ACTIVE.compareAndSet(false, true)) return;
        try {
            SecurityCinematicDialogV2.show(
                    activity,
                    Reason.SIGNATURE,
                    detail,
                    () -> hardTerminate(activity, Reason.SIGNATURE));
        } catch (Throwable error) {
            FLog.error("Unable to start composited signature security cinematic", error);
            hardTerminate(activity, Reason.SIGNATURE);
        }
    }

    private static boolean usable(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private static void hardTerminate(Activity activity, Reason reason) {
        // Final close still goes through the existing obfuscated/native TerminationGate rather than
        // exposing a single direct Process.killProcess/System.exit call in this response class.
        int marker = 0x5A17 ^ ((reason == null ? 3 : reason.ordinal() + 1) * 0x1337);
        TerminationGate.close(activity, marker);
    }
}
