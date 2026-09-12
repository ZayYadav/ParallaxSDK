package com.Parallax.SDK.core.fake.provider;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;

import java.io.File;
import java.util.List;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;

/**
 * Created by @RIYAZXERO on 4/18/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxFileProviderHandler {

    public static Uri convertFileUri(Context context, Uri uri) {
        if (ParallaxBuildCompat.isN()) {
            File file = convertFile(context, uri);
            if (file == null)
                return null;
            return ParallaxCore.getBStorageManager().getUriForFile(file.getAbsolutePath());
        }
        return uri;
    }

    public static File convertFile(Context context, Uri uri) {
        List<ProviderInfo> providers = ParallaxActivityThread.getProviders();
        for (ProviderInfo provider : providers) {
            try {
                File fileForUri = ParallaxFileProvider.getFileForUri(context, provider.authority, uri);
                if (fileForUri != null && fileForUri.exists()) {
                    return fileForUri;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
