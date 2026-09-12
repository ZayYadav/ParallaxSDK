package com.Parallax.SDK.core.fake.delegate;

import android.content.ContentProviderClient;
import android.net.Uri;
import android.os.Build;
import android.os.IInterface;
import android.util.ArrayMap;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import com.Parallax.SDK.mirror.android.app.BRActivityThread;
import com.Parallax.SDK.mirror.android.app.BRActivityThreadProviderClientRecordP;
import com.Parallax.SDK.mirror.android.app.BRIActivityManagerContentProviderHolder;
import com.Parallax.SDK.mirror.android.content.BRContentProviderHolderOreo;
import com.Parallax.SDK.mirror.android.providers.BRSettingsContentProviderHolder;
import com.Parallax.SDK.mirror.android.providers.BRSettingsGlobal;
import com.Parallax.SDK.mirror.android.providers.BRSettingsNameValueCache;
import com.Parallax.SDK.mirror.android.providers.BRSettingsNameValueCacheOreo;
import com.Parallax.SDK.mirror.android.providers.BRSettingsSecure;
import com.Parallax.SDK.mirror.android.providers.BRSettingsSystem;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.core.ParallaxGmsCore;
import com.Parallax.SDK.core.fake.service.context.providers.ParallaxContentProviderStub;
import com.Parallax.SDK.core.fake.service.context.providers.ParallaxGmsDynamiteProviderStub;
import com.Parallax.SDK.core.fake.service.context.providers.ParallaxSystemProviderStub;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;

/** Created by @RIYAZXERO on 3/31/21. */
public class ParallaxContentProviderDelegate {
    public static final String TAG = "ParallaxContentProviderDelegate";
    private static final Set<String> sInjected = new HashSet<>();

    // Keep one stable reference for the process lifetime so ActivityThread keeps
    // the wrapped GMS chimera provider cached after we install the proxy.
    private static volatile ContentProviderClient sGmsDynamiteClient;

    public static void update(Object holder, String auth) {
        IInterface iInterface;
        if (ParallaxBuildCompat.isOreo()) {
            iInterface = BRContentProviderHolderOreo.get(holder).provider();
        } else {
            iInterface = BRIActivityManagerContentProviderHolder.get(holder).provider();
        }

        if (iInterface == null || iInterface instanceof Proxy) {
            return;
        }
        IInterface bContentProvider;
        if (ParallaxGmsCore.GMS_DYNAMITE_AUTHORITY.equals(auth)) {
            bContentProvider = new ParallaxGmsDynamiteProviderStub()
                    .wrapper(iInterface, ParallaxCore.getHostPkg());
        } else if ("settings".equals(auth)) {
            bContentProvider = new ParallaxSystemProviderStub()
                    .wrapper(iInterface, ParallaxCore.getHostPkg());
        } else {
            bContentProvider = new ParallaxContentProviderStub()
                    .wrapper(iInterface, ParallaxCore.getHostPkg());
        }
        if (ParallaxBuildCompat.isOreo()) {
            BRContentProviderHolderOreo.get(holder)._set_provider(bContentProvider);
        } else {
            BRIActivityManagerContentProviderHolder.get(holder)._set_provider(bContentProvider);
        }
    }

    public static void init() {
        clearSettingProvider();

        ParallaxCore.getContext().getContentResolver().call(
                Uri.parse("content://settings"), "", null, null);

        // FirebaseInitProvider is removed from Android 16 virtual packages before
        // this point. Prime GMS Dynamite now, under the real host identity, so the
        // provider exists in ActivityThread's cache and can be wrapped before the
        // virtual Application.onCreate/UE4 startup can explicitly request Analytics.
        if (Build.VERSION.SDK_INT >= 36 && sGmsDynamiteClient == null) {
            try {
                sGmsDynamiteClient = ParallaxCore.getContext()
                        .getContentResolver()
                        .acquireContentProviderClient(ParallaxGmsCore.GMS_DYNAMITE_AUTHORITY);
            } catch (Throwable ignored) {
                // Play Services may be absent on some devices. Auth/browser paths
                // continue without making provider setup fatal.
            }
        }

        Object activityThread = ParallaxCore.mainThread();
        ArrayMap<Object, Object> map = (ArrayMap<Object, Object>)
                BRActivityThread.get(activityThread).mProviderMap();

        for (Object value : map.values()) {
            String[] mNames = BRActivityThreadProviderClientRecordP.get(value).mNames();
            if (mNames == null || mNames.length <= 0) {
                continue;
            }
            String providerName = mNames[0];
            if (sInjected.contains(providerName)) {
                continue;
            }

            sInjected.add(providerName);
            final IInterface iInterface = BRActivityThreadProviderClientRecordP.get(value).mProvider();
            if (iInterface == null || iInterface instanceof Proxy) {
                continue;
            }

            IInterface wrapper = ParallaxGmsCore.GMS_DYNAMITE_AUTHORITY.equals(providerName)
                    ? new ParallaxGmsDynamiteProviderStub().wrapper(iInterface, ParallaxCore.getHostPkg())
                    : new ParallaxContentProviderStub().wrapper(iInterface, ParallaxCore.getHostPkg());
            BRActivityThreadProviderClientRecordP.get(value)._set_mProvider(wrapper);
            BRActivityThreadProviderClientRecordP.get(value)._set_mNames(new String[]{providerName});
        }
    }

    public static void clearSettingProvider() {
        Object cache;
        cache = BRSettingsSystem.get().sNameValueCache();
        if (cache != null) {
            clearContentProvider(cache);
        }
        cache = BRSettingsSecure.get().sNameValueCache();
        if (cache != null) {
            clearContentProvider(cache);
        }
        if (BRSettingsGlobal.getRealClass() != null) {
            cache = BRSettingsGlobal.get().sNameValueCache();
            if (cache != null) {
                clearContentProvider(cache);
            }
        }
    }

    private static void clearContentProvider(Object cache) {
        if (ParallaxBuildCompat.isOreo()) {
            Object holder = BRSettingsNameValueCacheOreo.get(cache).mProviderHolder();
            if (holder != null) {
                BRSettingsContentProviderHolder.get(holder)._set_mContentProvider(null);
            }
        } else {
            BRSettingsNameValueCache.get(cache)._set_mContentProvider(null);
        }
    }
}
