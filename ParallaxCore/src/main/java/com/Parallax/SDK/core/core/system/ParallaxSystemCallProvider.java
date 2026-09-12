package com.Parallax.SDK.core.core.system;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.Parallax.SDK.core.utils.ParallaxSlog;
import com.Parallax.SDK.core.utils.compat.ParallaxBundleCompat;

/**
 * Created by @RIYAZXERO on 3/31/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxSystemCallProvider extends ContentProvider {
    public static final String TAG = "ParallaxSystemCallProvider";

    @Override
    public boolean onCreate() {
        return initSystem();
    }

    private boolean initSystem() {
        ParallaxSystem.getSystem().startup();
        return true;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        ParallaxSlog.d(TAG, "call: " + method + ", " + extras);
        if ("VM".equals(method)) {
            Bundle bundle = new Bundle();
            if (extras != null) {
                String name = extras.getString("_G_|_server_name_");
                ParallaxBundleCompat.putBinder(bundle, "_G_|_server_", ParallaxServiceManager.getService(name));
            }
            return bundle;
        }
        return super.call(method, arg, extras);
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
}
