package top.niunaijun.blackbox.core;

import android.util.Log;

import top.niunaijun.blackbox.BlackBoxCore;

/**
 * Created by Milk on 4/30/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "CrashHandler";
    private final Thread.UncaughtExceptionHandler mDefaultHandler;

    public static void create() {
        new CrashHandler();
    }

    public CrashHandler() {
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            if (BlackBoxCore.get().getExceptionHandler() != null) {
                BlackBoxCore.get().getExceptionHandler().uncaughtException(t, e);
            }
        } catch (Throwable handlerError) {
            Log.e(TAG, "App exception handler failed", handlerError);
        }

        if (mDefaultHandler != null && mDefaultHandler != this) {
            mDefaultHandler.uncaughtException(t, e);
            return;
        }

        Log.e(TAG, "Uncaught exception in virtual process", e);
    }
}
