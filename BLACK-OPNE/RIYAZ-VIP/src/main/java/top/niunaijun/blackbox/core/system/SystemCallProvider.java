package top.niunaijun.blackbox.core.system;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.BundleCompat;

/**
 * System service provider for Parallax Virtual.
 *
 * Provider creation happens before the first Activity is shown. A virtual-core
 * compatibility failure must therefore never kill the host process here. The
 * host Application/dashboard can surface the degraded-core state safely.
 */
public class SystemCallProvider extends ContentProvider {
    public static final String TAG = "SystemCallProvider";
    private static volatile boolean systemReady;
    private static volatile String startupError = "";

    @Override
    public boolean onCreate() {
        return initSystem();
    }

    private boolean initSystem() {
        try {
            BlackBoxSystem.getSystem().startup();
            systemReady = true;
            startupError = "";
            return true;
        } catch (Throwable throwable) {
            systemReady = false;
            String message = throwable.getMessage();
            startupError = throwable.getClass().getSimpleName()
                    + (message == null || message.trim().isEmpty() ? "" : ": " + message.trim());
            Log.e(TAG, "Virtual system startup failed; keeping host process alive", throwable);
            return false;
        }
    }

    public static boolean isSystemReady() {
        return systemReady;
    }

    public static String getStartupError() {
        return startupError;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        Slog.d(TAG, "call: " + method + ", " + extras);
        if ("VM".equals(method)) {
            Bundle bundle = new Bundle();
            if (!systemReady) {
                bundle.putString("_PV_|_startup_error_", startupError);
                return bundle;
            }
            if (extras != null) {
                String name = extras.getString("_G_|_server_name_");
                BundleCompat.putBinder(bundle, "_G_|_server_", ServiceManager.getService(name));
            }
            return bundle;
        }
        return super.call(method, arg, extras);
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        return 0;
    }
}
