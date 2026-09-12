package com.Parallax.SDK.core.proxy;

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

import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.compat.auth.ParallaxExternalAuthRouter;
import com.Parallax.SDK.core.entity.ParallaxAppConfig;
import com.Parallax.SDK.core.fake.frameworks.ParallaxActivityManager;
import com.Parallax.SDK.core.utils.compat.ParallaxBundleCompat;

/**
 * Created by Milk on 3/30/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxProxyContentProvider extends ContentProvider {
    private static final String TAG = "ParallaxProxyContentProvider";

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
                    extras.setClassLoader(ParallaxAppConfig.class.getClassLoader());
                    ParallaxAppConfig appConfig = extras.getParcelable(ParallaxAppConfig.KEY);

                    if (appConfig != null) {
                        ParallaxActivityThread activityThread = ParallaxActivityThread.currentActivityThread();
                        if (activityThread != null) {
                            activityThread.initProcess(appConfig);
                            Bundle bundle = new Bundle();
                            ParallaxBundleCompat.putBinder(bundle, "_Black_|_client_", activityThread);
                            return bundle;
                        } else {
                            Log.e(TAG, "ParallaxActivityThread is null");
                        }
                    } else {
                        Log.e(TAG, "ParallaxAppConfig is null");
                    }
                } else {
                    Log.e(TAG, "Extras is null");
                }
            }

            if (ParallaxExternalAuthRouter.METHOD_DELIVER_ACTIVITY_RESULT.equals(method)) {
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
        response.putBoolean(ParallaxExternalAuthRouter.EXTRA_RESULT_DELIVERED, false);

        if (extras == null) {
            return response;
        }

        try {
            extras.setClassLoader(getClass().getClassLoader());

            String virtualPackage = extras.getString(ParallaxExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE);
            int expectedBpid = extras.getInt(ParallaxExternalAuthRouter.EXTRA_BPID, -1);
            int requestCode = extras.getInt(ParallaxExternalAuthRouter.EXTRA_REQUEST_CODE, -1);
            int resultCode = extras.getInt(ParallaxExternalAuthRouter.EXTRA_RESULT_CODE, 0);
            String resultWho = extras.getString(ParallaxExternalAuthRouter.EXTRA_RESULT_WHO);
            IBinder resultTo = ParallaxBundleCompat.getBinder(
                    extras, ParallaxExternalAuthRouter.EXTRA_RESULT_BINDER);

            String activePackage = ParallaxActivityThread.getAppPackageName();
            int activeBpid = ParallaxActivityThread.getAppPid();
            if (resultTo == null
                    || requestCode < 0
                    || virtualPackage == null
                    || !virtualPackage.equals(activePackage)
                    || expectedBpid < 0
                    || expectedBpid != activeBpid) {
                Log.w(TAG, "Rejected external auth result relay: target mismatch");
                return response;
            }

            Intent data = extras.getParcelable(ParallaxExternalAuthRouter.EXTRA_RESULT_DATA);
            if (data != null && ParallaxActivityThread.getApplication() != null) {
                data.setExtrasClassLoader(
                        ParallaxActivityThread.getApplication().getClassLoader());
            }

            // This provider executes inside the exact :pN process selected by the
            // bridge. ParallaxActivityManager therefore schedules ActivityResultItem on
            // the correct local ActivityThread without exposing or rewriting any
            // provider result payload.
            ParallaxActivityManager.get().sendActivityResult(
                    resultTo, resultWho, requestCode, data, resultCode);
            response.putBoolean(ParallaxExternalAuthRouter.EXTRA_RESULT_DELIVERED, true);
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

    public static class P0 extends ParallaxProxyContentProvider { }
    public static class P1 extends ParallaxProxyContentProvider { }
    public static class P2 extends ParallaxProxyContentProvider { }
    public static class P3 extends ParallaxProxyContentProvider { }
    public static class P4 extends ParallaxProxyContentProvider { }
    public static class P5 extends ParallaxProxyContentProvider { }
    public static class P6 extends ParallaxProxyContentProvider { }
    public static class P7 extends ParallaxProxyContentProvider { }
    public static class P8 extends ParallaxProxyContentProvider { }
    public static class P9 extends ParallaxProxyContentProvider { }
    public static class P10 extends ParallaxProxyContentProvider { }
    public static class P11 extends ParallaxProxyContentProvider { }
    public static class P12 extends ParallaxProxyContentProvider { }
    public static class P13 extends ParallaxProxyContentProvider { }
    public static class P14 extends ParallaxProxyContentProvider { }
    public static class P15 extends ParallaxProxyContentProvider { }
    public static class P16 extends ParallaxProxyContentProvider { }
    public static class P17 extends ParallaxProxyContentProvider { }
    public static class P18 extends ParallaxProxyContentProvider { }
    public static class P19 extends ParallaxProxyContentProvider { }
    public static class P20 extends ParallaxProxyContentProvider { }
    public static class P21 extends ParallaxProxyContentProvider { }
    public static class P22 extends ParallaxProxyContentProvider { }
    public static class P23 extends ParallaxProxyContentProvider { }
    public static class P24 extends ParallaxProxyContentProvider { }
}
