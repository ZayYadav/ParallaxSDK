package top.niunaijun.blackbox.proxy;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.compat.auth.ExternalAuthRouter;
import top.niunaijun.blackbox.entity.AppConfig;
import top.niunaijun.blackbox.fake.frameworks.BActivityManager;
import top.niunaijun.blackbox.utils.compat.BundleCompat;

/**
 * Created by Milk on 3/30/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ProxyContentProvider extends ContentProvider {
    private static final String TAG = "ProxyContentProvider";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        try {
            if ("_Black_|_init_process_".equals(method)) {
                if (extras != null) {
                    extras.setClassLoader(AppConfig.class.getClassLoader());
                    AppConfig appConfig = extras.getParcelable(AppConfig.KEY);

                    if (appConfig != null) {
                        BActivityThread activityThread = BActivityThread.currentActivityThread();
                        if (activityThread != null) {
                            activityThread.initProcess(appConfig);
                            Bundle bundle = new Bundle();
                            BundleCompat.putBinder(bundle, "_Black_|_client_", activityThread);
                            return bundle;
                        } else {
                            Log.e(TAG, "BActivityThread is null");
                        }
                    } else {
                        Log.e(TAG, "AppConfig is null");
                    }
                } else {
                    Log.e(TAG, "Extras is null");
                }
            }

            if (ExternalAuthRouter.METHOD_DELIVER_ACTIVITY_RESULT.equals(method)) {
                return deliverExternalAuthResult(extras);
            }

            return super.call(method, arg, extras);
        } catch (Exception e) {
            Log.e(TAG, "Error in call method: " + e.getMessage());
            return new Bundle();
        }
    }

    private Bundle deliverExternalAuthResult(@Nullable Bundle extras) {
        Bundle response = new Bundle();
        response.putBoolean(ExternalAuthRouter.EXTRA_RESULT_DELIVERED, false);

        if (extras == null) {
            return response;
        }

        try {
            extras.setClassLoader(getClass().getClassLoader());

            String virtualPackage = extras.getString(ExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE);
            int expectedBpid = extras.getInt(ExternalAuthRouter.EXTRA_BPID, -1);
            int requestCode = extras.getInt(ExternalAuthRouter.EXTRA_REQUEST_CODE, -1);
            int resultCode = extras.getInt(ExternalAuthRouter.EXTRA_RESULT_CODE, 0);
            String resultWho = extras.getString(ExternalAuthRouter.EXTRA_RESULT_WHO);
            IBinder resultTo = BundleCompat.getBinder(
                    extras, ExternalAuthRouter.EXTRA_RESULT_BINDER);

            String activePackage = BActivityThread.getAppPackageName();
            int activeBpid = BActivityThread.getAppPid();
            if (resultTo == null
                    || requestCode < 0
                    || virtualPackage == null
                    || !virtualPackage.equals(activePackage)
                    || expectedBpid < 0
                    || expectedBpid != activeBpid) {
                Log.w(TAG, "Rejected external auth result relay: target mismatch");
                return response;
            }

            Intent data = extras.getParcelable(ExternalAuthRouter.EXTRA_RESULT_DATA);
            if (data != null && BActivityThread.getApplication() != null) {
                data.setExtrasClassLoader(
                        BActivityThread.getApplication().getClassLoader());
            }

            // This provider executes inside the exact :pN process selected by the
            // bridge. BActivityManager therefore schedules ActivityResultItem on
            // the correct local ActivityThread without exposing or rewriting any
            // provider result payload.
            BActivityManager.get().sendActivityResult(
                    resultTo, resultWho, requestCode, data, resultCode);
            response.putBoolean(ExternalAuthRouter.EXTRA_RESULT_DELIVERED, true);
            Log.i(TAG, "External auth result relayed to virtual activity");
            return response;
        } catch (Throwable error) {
            Log.w(TAG, "External auth result relay failed: "
                    + error.getClass().getSimpleName());
            return response;
        }
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
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
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    public static class P0 extends ProxyContentProvider { }
    public static class P1 extends ProxyContentProvider { }
    public static class P2 extends ProxyContentProvider { }
    public static class P3 extends ProxyContentProvider { }
    public static class P4 extends ProxyContentProvider { }
    public static class P5 extends ProxyContentProvider { }
    public static class P6 extends ProxyContentProvider { }
    public static class P7 extends ProxyContentProvider { }
    public static class P8 extends ProxyContentProvider { }
    public static class P9 extends ProxyContentProvider { }
    public static class P10 extends ProxyContentProvider { }
    public static class P11 extends ProxyContentProvider { }
    public static class P12 extends ProxyContentProvider { }
    public static class P13 extends ProxyContentProvider { }
    public static class P14 extends ProxyContentProvider { }
    public static class P15 extends ProxyContentProvider { }
    public static class P16 extends ProxyContentProvider { }
    public static class P17 extends ProxyContentProvider { }
    public static class P18 extends ProxyContentProvider { }
    public static class P19 extends ProxyContentProvider { }
    public static class P20 extends ProxyContentProvider { }
    public static class P21 extends ProxyContentProvider { }
    public static class P22 extends ProxyContentProvider { }
    public static class P23 extends ProxyContentProvider { }
    public static class P24 extends ProxyContentProvider { }
}
