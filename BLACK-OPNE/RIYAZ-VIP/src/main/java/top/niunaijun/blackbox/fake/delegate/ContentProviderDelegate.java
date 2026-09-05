package top.niunaijun.blackbox.fake.delegate;

import android.content.ContentProviderClient;
import android.net.Uri;
import android.os.Build;
import android.os.IInterface;
import android.util.ArrayMap;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import black.android.app.BRActivityThread;
import black.android.app.BRActivityThreadProviderClientRecordP;
import black.android.app.BRIActivityManagerContentProviderHolder;
import black.android.content.BRContentProviderHolderOreo;
import black.android.providers.BRSettingsContentProviderHolder;
import black.android.providers.BRSettingsGlobal;
import black.android.providers.BRSettingsNameValueCache;
import black.android.providers.BRSettingsNameValueCacheOreo;
import black.android.providers.BRSettingsSecure;
import black.android.providers.BRSettingsSystem;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.GmsCore;
import top.niunaijun.blackbox.fake.service.context.providers.ContentProviderStub;
import top.niunaijun.blackbox.fake.service.context.providers.GmsDynamiteProviderStub;
import top.niunaijun.blackbox.fake.service.context.providers.SystemProviderStub;
import top.niunaijun.blackbox.utils.compat.BuildCompat;

/** Created by @RIYAZXERO on 3/31/21. */
public class ContentProviderDelegate {
    public static final String TAG = "ContentProviderDelegate";
    private static final Set<String> sInjected = new HashSet<>();

    // Keep one stable reference for the process lifetime so ActivityThread keeps
    // the wrapped GMS chimera provider cached after we install the proxy.
    private static volatile ContentProviderClient sGmsDynamiteClient;

    public static void update(Object holder, String auth) {
        IInterface iInterface;
        if (BuildCompat.isOreo()) {
            iInterface = BRContentProviderHolderOreo.get(holder).provider();
        } else {
            iInterface = BRIActivityManagerContentProviderHolder.get(holder).provider();
        }

        if (iInterface == null || iInterface instanceof Proxy) {
            return;
        }
        IInterface bContentProvider;
        if (GmsCore.GMS_DYNAMITE_AUTHORITY.equals(auth)) {
            bContentProvider = new GmsDynamiteProviderStub()
                    .wrapper(iInterface, BlackBoxCore.getHostPkg());
        } else if ("settings".equals(auth)) {
            bContentProvider = new SystemProviderStub()
                    .wrapper(iInterface, BlackBoxCore.getHostPkg());
        } else {
            bContentProvider = new ContentProviderStub()
                    .wrapper(iInterface, BlackBoxCore.getHostPkg());
        }
        if (BuildCompat.isOreo()) {
            BRContentProviderHolderOreo.get(holder)._set_provider(bContentProvider);
        } else {
            BRIActivityManagerContentProviderHolder.get(holder)._set_provider(bContentProvider);
        }
    }

    public static void init() {
        clearSettingProvider();

        BlackBoxCore.getContext().getContentResolver().call(
                Uri.parse("content://settings"), "", null, null);

        // Prime GMS Dynamite under the real host identity so the provider exists
        // in ActivityThread's cache and can be wrapped before the virtual
        // Application.onCreate/UE4 startup can explicitly request Analytics.
        if (Build.VERSION.SDK_INT >= 36 && sGmsDynamiteClient == null) {
            try {
                sGmsDynamiteClient = BlackBoxCore.getContext()
                        .getContentResolver()
                        .acquireContentProviderClient(GmsCore.GMS_DYNAMITE_AUTHORITY);
            } catch (Throwable ignored) {
                // Play Services may be absent on some devices. Auth/browser paths
                // continue without making provider setup fatal.
            }
        }

        Object activityThread = BlackBoxCore.mainThread();
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

            IInterface wrapper = GmsCore.GMS_DYNAMITE_AUTHORITY.equals(providerName)
                    ? new GmsDynamiteProviderStub().wrapper(iInterface, BlackBoxCore.getHostPkg())
                    : new ContentProviderStub().wrapper(iInterface, BlackBoxCore.getHostPkg());
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
        if (BuildCompat.isOreo()) {
            Object holder = BRSettingsNameValueCacheOreo.get(cache).mProviderHolder();
            if (holder != null) {
                BRSettingsContentProviderHolder.get(holder)._set_mContentProvider(null);
            }
        } else {
            BRSettingsNameValueCache.get(cache)._set_mContentProvider(null);
        }
    }
}
