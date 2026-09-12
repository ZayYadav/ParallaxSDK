package com.Parallax.SDK.core.core.system.accounts;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ServiceInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Bundle;

import com.Parallax.SDK.mirror.android.content.res.BRAssetManager;
import com.Parallax.SDK.core.core.system.pm.ParallaxPackageManagerService;
import com.Parallax.SDK.core.core.system.pm.ParallaxPackageSettings;
import com.Parallax.SDK.core.core.system.pm.ParallaxPackageManagerCompat;

public class ParallaxRegisteredServicesParser {

    public XmlResourceParser getParser(Context context, ServiceInfo serviceInfo, String name) {
        Bundle meta = serviceInfo.metaData;
        if (meta != null) {
            int xmlId = meta.getInt(name);
            if (xmlId != 0) {
                try {
                    Resources resources = getResources(context, serviceInfo.applicationInfo);
                    if (resources == null)
                        return null;
                    return resources.getXml(xmlId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public Resources getResources(Context context, ApplicationInfo appInfo) {
        return ParallaxPackageManagerCompat.getResources(context, appInfo);
    }
}