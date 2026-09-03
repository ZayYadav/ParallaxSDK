package com.onecore.loader.utils;

import android.content.Context;
import android.util.Log;

import com.onecore.loader.BuildConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FLog {
    public static final String TAG = FLog.class.getSimpleName();
    private static final String LOG_FILE_NAME = "loader-debug.log";
    private static final Object FILE_LOCK = new Object();
    private static final SimpleDateFormat LOG_DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static volatile Context applicationContext;

    public static void initialize(Context context) {
        applicationContext = context.getApplicationContext();
    }

    public static void debug(String msg) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        Log.d(TAG, msg);
        writeToDownloadLog("DEBUG", msg);
    }

    public static void info(String msg) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        Log.i(TAG, msg);
        writeToDownloadLog("INFO", msg);
    }

    public static void warning(String msg) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        Log.w(TAG, msg);
        writeToDownloadLog("WARN", msg);
    }

    public static void error(String msg) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        Log.e(TAG, msg);
        writeToDownloadLog("ERROR", msg);
    }

    public static void error(String msg, Throwable throwable) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        Log.e(TAG, msg, throwable);
        writeToDownloadLog("ERROR", msg + "\n" + Log.getStackTraceString(throwable));
    }

    private static void writeToDownloadLog(String level, String msg) {
        synchronized (FILE_LOCK) {
            FileWriter writer = null;
            try {
                File logFile = getLogFile();
                if (logFile == null) {
                    return;
                }
                File parent = logFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    Log.w(TAG, "Unable to create log directory: " + parent.getAbsolutePath());
                    return;
                }

                writer = new FileWriter(logFile, true);
                writer.append(LOG_DATE_FORMAT.format(new Date()))
                        .append(" ")
                        .append(level)
                        .append("/")
                        .append(TAG)
                        .append(": ")
                        .append(msg == null ? "null" : msg)
                        .append('\n');
            } catch (IOException e) {
                Log.w(TAG, "Unable to write debug log file", e);
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException e) {
                        Log.w(TAG, "Unable to close debug log file", e);
                    }
                }
            }
        }
    }

    public static File getLogFile() {
        Context context = applicationContext;
        return context == null ? null : new File(context.getNoBackupFilesDir(), LOG_FILE_NAME);
    }
}
