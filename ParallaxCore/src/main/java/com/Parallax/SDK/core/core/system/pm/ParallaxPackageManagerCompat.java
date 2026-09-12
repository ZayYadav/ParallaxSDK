package com.Parallax.SDK.core.core.system.pm;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.InstrumentationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;

import java.util.HashSet;
import java.util.Set;

import com.Parallax.SDK.mirror.android.content.pm.BRApplicationInfoL;
import com.Parallax.SDK.mirror.android.content.pm.BRApplicationInfoN;
import com.Parallax.SDK.mirror.android.content.pm.BRPackageParserSigningDetails;
import com.Parallax.SDK.mirror.android.content.pm.BRSigningInfo;
import com.Parallax.SDK.mirror.android.content.res.BRAssetManager;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.core.ParallaxGmsCore;
import com.Parallax.SDK.core.core.env.ParallaxAppSystemEnv;
import com.Parallax.SDK.core.core.env.ParallaxEnvironment;
import com.Parallax.SDK.core.entity.pm.ParallaxInstallOption;
import com.Parallax.SDK.core.utils.ParallaxArrayUtils;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;

/**
 * Created by Milk on 4/15/21.
 */
@SuppressLint({"SdCardPath", "NewApi"})
public class ParallaxPackageManagerCompat {

    public static PackageInfo generatePackageInfo(ParallaxPackageSettings ps, int flags, ParallaxPackageUserState state, int userId) {
        if (ps == null) {
            return null;
        }
        ParallaxPackage p = ps.pkg;
        if (p != null) {
            PackageInfo packageInfo = null;
            try {
                packageInfo = generatePackageInfo(p, flags, 0, 0, state, userId);
            } catch (Throwable ignored) {
            }
            return packageInfo;
        }
        return null;
    }

    public static PackageInfo generatePackageInfo(ParallaxPackage p, int flags, long firstInstallTime, long lastUpdateTime, ParallaxPackageUserState state, int userId) {
        if (!checkUseInstalledOrHidden(flags, state, p.applicationInfo)) {
            return null;
        }

        PackageInfo pi = new PackageInfo();
        pi.packageName = p.packageName;
        pi.versionCode = p.mVersionCode;
        pi.versionName = p.mVersionName;
        pi.sharedUserId = p.mSharedUserId;
        pi.sharedUserLabel = p.mSharedUserLabel;
        pi.applicationInfo = generateApplicationInfo(p, flags, state, userId);

        pi.firstInstallTime = firstInstallTime;
        pi.lastUpdateTime = lastUpdateTime;
        if (!p.requestedPermissions.isEmpty()) {
            String[] requestedPermissions = new String[p.requestedPermissions.size()];
            p.requestedPermissions.toArray(requestedPermissions);
            pi.requestedPermissions = requestedPermissions;
        }

        if ((flags & ParallaxFileUtils.FileMode.MODE_IRUSR) != 0) {
            pi.gids = new int[0];
        }

        if ((flags & PackageManager.GET_CONFIGURATIONS) != 0) {
            int N = p.configPreferences != null ? p.configPreferences.size() : 0;
            if (N > 0) {
                pi.configPreferences = new ConfigurationInfo[N];
                p.configPreferences.toArray(pi.configPreferences);
            }
            N = p.reqFeatures != null ? p.reqFeatures.size() : 0;
            if (N > 0) {
                pi.reqFeatures = new FeatureInfo[N];
                p.reqFeatures.toArray(pi.reqFeatures);
            }
        }
        if ((flags & PackageManager.GET_ACTIVITIES) != 0) {
            pi.activities = null;
            final int N = p.activities.size();
            if (N > 0) {
                int num = 0;
                final ActivityInfo[] res = new ActivityInfo[N];
                for (int i = 0; i < N; i++) {
                    final ParallaxPackage.Activity a = p.activities.get(i);
                    ActivityInfo activityInfo = generateActivityInfo(a, flags, state, userId);
                    if (activityInfo != null) {
                        res[num++] = activityInfo;
                    }
                }
                pi.activities = ParallaxArrayUtils.trimToSize(res, num);
            }
        }
        if ((flags & PackageManager.GET_RECEIVERS) != 0) {
            pi.receivers = null;
            final int N = p.receivers.size();
            if (N > 0) {
                int num = 0;
                final ActivityInfo[] res = new ActivityInfo[N];
                for (int i = 0; i < N; i++) {
                    final ParallaxPackage.Activity a = p.receivers.get(i);
                    ActivityInfo receiverInfo = generateActivityInfo(a, flags, state, userId);
                    if (receiverInfo != null) {
                        res[num++] = receiverInfo;
                    }
                }
                pi.receivers = ParallaxArrayUtils.trimToSize(res, num);
            }
        }
        if ((flags & PackageManager.GET_SERVICES) != 0) {
            pi.services = null;
            final int N = p.services.size();
            if (N > 0) {
                int num = 0;
                final ServiceInfo[] res = new ServiceInfo[N];
                for (int i = 0; i < N; i++) {
                    final ParallaxPackage.Service s = p.services.get(i);
                    ServiceInfo serviceInfo = generateServiceInfo(s, flags, state, userId);
                    if (serviceInfo != null) {
                        res[num++] = serviceInfo;
                    }
                }
                pi.services = ParallaxArrayUtils.trimToSize(res, num);
            }
        }
        if ((flags & PackageManager.GET_PROVIDERS) != 0) {
            pi.providers = null;
            final int N = p.providers.size();
            if (N > 0) {
                int num = 0;
                final ProviderInfo[] res = new ProviderInfo[N];
                for (int i = 0; i < N; i++) {
                    final ParallaxPackage.Provider pr = p.providers.get(i);
                    ProviderInfo providerInfo = generateProviderInfo(pr, flags, state, userId);
                    if (providerInfo != null) {
                        res[num++] = providerInfo;
                    }
                }
                pi.providers = ParallaxArrayUtils.trimToSize(res, num);
            }
        }
        if ((flags & PackageManager.GET_INSTRUMENTATION) != 0) {
            pi.instrumentation = null;
            int N = p.instrumentation.size();
            if (N > 0) {
                pi.instrumentation = new InstrumentationInfo[N];
                for (int i = 0; i < N; i++) {
                    pi.instrumentation[i] = generateInstrumentationInfo(
                            p.instrumentation.get(i), flags);
                }
            }
        }
        if ((flags & PackageManager.GET_PERMISSIONS) != 0) {
            pi.permissions = null;
            int N = p.permissions.size();
            if (N > 0) {
                pi.permissions = new PermissionInfo[N];
                for (int i = 0; i < N; i++) {
                    pi.permissions[i] = generatePermissionInfo(p.permissions.get(i), flags);
                }
            }
            pi.requestedPermissions = null;
            N = p.requestedPermissions.size();
            if (N > 0) {
                pi.requestedPermissions = new String[N];
                pi.requestedPermissionsFlags = new int[N];
                for (int i = 0; i < N; i++) {
                    final String perm = p.requestedPermissions.get(i);
                    pi.requestedPermissions[i] = perm;
                }
            }
        }
        PackageInfo base = null;
        try {
            base = ParallaxCore.getContext().getPackageManager().getPackageInfo(p.packageName, flags);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        if ((flags & PackageManager.GET_SIGNATURES) != 0) {
            if (base == null) {
                pi.signatures = p.mSignatures;
            } else {
                pi.signatures = base.signatures;
            }
        }
        if (ParallaxBuildCompat.isPie()) {
            if ((flags & PackageManager.GET_SIGNING_CERTIFICATES) != 0) {
                if (base == null) {
                    PackageParser.SigningDetails signingDetails = PackageParser.SigningDetails.UNKNOWN;
                    BRPackageParserSigningDetails.get(signingDetails)._set_signatures(p.mSigningDetails.signatures);
                    pi.signingInfo = BRSigningInfo.get()._new(signingDetails);
                } else {
                    pi.signingInfo = base.signingInfo;
                }
            }
        }
        return pi;
    }

    public static ActivityInfo generateActivityInfo(ParallaxPackage.Activity a, int flags, ParallaxPackageUserState state, int userId) {
        if (!checkUseInstalledOrHidden(flags, state, a.info.applicationInfo)) {
            return null;
        }
        ActivityInfo ai = new ActivityInfo(a.info);
        ai.metaData = a.metaData;
        ai.processName = ParallaxPackageManagerService.fixProcessName(ai.packageName, ai.processName);
        ai.applicationInfo = generateApplicationInfo(a.owner, flags, state, userId);
        return ParallaxGmsCore.applyGeneratedVirtualActivityGmsSafety(ai);
    }

    public static ServiceInfo generateServiceInfo(ParallaxPackage.Service s, int flags, ParallaxPackageUserState state, int userId) {
        if (!checkUseInstalledOrHidden(flags, state, s.info.applicationInfo)) {
            return null;
        }
        ServiceInfo si = new ServiceInfo(s.info);
        si.metaData = s.metaData;
        si.processName = ParallaxPackageManagerService.fixProcessName(si.packageName, si.processName);
        si.applicationInfo = generateApplicationInfo(s.owner, flags, state, userId);
        return ParallaxGmsCore.applyGeneratedVirtualServiceGmsSafety(si);
    }

    public static ProviderInfo generateProviderInfo(ParallaxPackage.Provider p, int flags, ParallaxPackageUserState state, int userId) {
        if (!checkUseInstalledOrHidden(flags, state, p.info.applicationInfo)) {
            return null;
        }
        ProviderInfo pi = new ProviderInfo(p.info);
        if (pi.authority == null) return null;
        pi.metaData = p.metaData;
        pi.processName = ParallaxPackageManagerService.fixProcessName(pi.packageName, pi.processName);
        if ((flags & ParallaxFileUtils.FileMode.MODE_ISUID) == 0) {
            pi.uriPermissionPatterns = null;
        }
        pi.applicationInfo = generateApplicationInfo(p.owner, flags, state, userId);
        return ParallaxGmsCore.applyGeneratedVirtualProviderGmsSafety(pi);
    }

    public static PermissionInfo generatePermissionInfo(ParallaxPackage.Permission p, int flags) {
        if (p == null) return null;
        if ((flags & ParallaxFileUtils.FileMode.MODE_IWUSR) == 0) {
            return p.info;
        }
        PermissionInfo pi = new PermissionInfo(p.info);
        pi.metaData = p.metaData;
        return pi;
    }

    public static InstrumentationInfo generateInstrumentationInfo(ParallaxPackage.Instrumentation i, int flags) {
        if (i == null) return null;
        if ((flags & PackageManager.GET_META_DATA) == 0) {
            return i.info;
        }
        InstrumentationInfo ii = new InstrumentationInfo(i.info);
        ii.metaData = i.metaData;
        return ii;
    }

    public static ApplicationInfo generateApplicationInfo(ParallaxPackage p, int flags, ParallaxPackageUserState state, int userId) {
        if (!checkUseInstalledOrHidden(flags, state, p.applicationInfo)) {
            return null;
        }
        ApplicationInfo baseApplication;
        try {
            baseApplication = ParallaxCore.getPackageManager().getApplicationInfo(ParallaxCore.getHostPkg(), flags);
        } catch (Exception e) {
            return null;
        }
        String sourceDir = p.baseCodePath;
        if (p.applicationInfo == null) {
            p.applicationInfo = ParallaxCore.getPackageManager().getPackageArchiveInfo(sourceDir, 0).applicationInfo;
        }
        ApplicationInfo ai = new ApplicationInfo(p.applicationInfo);
        if ((flags & PackageManager.GET_META_DATA) != 0) {
            ai.metaData = p.mAppMetaData;
        }

        ParallaxGmsCore.applyGeneratedVirtualAppGmsSafety(ai);

        ai.dataDir = ParallaxEnvironment.getDataDir(ai.packageName, userId).getAbsolutePath();
        if (!p.installOption.isFlag(ParallaxInstallOption.FLAG_SYSTEM)) {
            ai.nativeLibraryDir = ParallaxEnvironment.getAppLibDir(ai.packageName).getAbsolutePath();
        }
        ai.processName = ParallaxPackageManagerService.fixProcessName(p.packageName, ai.packageName);
        ai.publicSourceDir = sourceDir;
        ai.sourceDir = sourceDir;
        ai.uid = p.mExtras.appId;

        if (ParallaxBuildCompat.isL()) {
            BRApplicationInfoL.get(ai)._set_primaryCpuAbi(Build.CPU_ABI);
            BRApplicationInfoL.get(ai)._set_scanPublicSourceDir(BRApplicationInfoL.get(baseApplication).scanPublicSourceDir());
            BRApplicationInfoL.get(ai)._set_scanSourceDir(BRApplicationInfoL.get(baseApplication).scanSourceDir());
        }
        if (ParallaxBuildCompat.isN()) {
            ai.deviceProtectedDataDir = ParallaxEnvironment.getDeDataDir(p.packageName, userId).getAbsolutePath();

            if (BRApplicationInfoN.get(ai)._check_deviceEncryptedDataDir() != null) {
                BRApplicationInfoN.get(ai)._set_deviceEncryptedDataDir(ai.deviceProtectedDataDir);
            }
            if (BRApplicationInfoN.get(ai)._check_credentialEncryptedDataDir() != null) {
                BRApplicationInfoN.get(ai)._set_credentialEncryptedDataDir(ai.dataDir);
            }
            if (BRApplicationInfoN.get(ai)._check_deviceProtectedDataDir() != null) {
                BRApplicationInfoN.get(ai)._set_deviceProtectedDataDir(ai.deviceProtectedDataDir);
            }
            if (BRApplicationInfoN.get(ai)._check_credentialProtectedDataDir() != null) {
                BRApplicationInfoN.get(ai)._set_credentialProtectedDataDir(ai.dataDir);
            }
        }
        fixJar(ai);
        return ai;
    }

    private static boolean checkUseInstalledOrHidden(int flags, ParallaxPackageUserState state,
                                                     ApplicationInfo appInfo) {
        if (ParallaxAppSystemEnv.isBlackPackage(appInfo.packageName))
            return false;
        if (!state.installed || state.hidden) {
            return false;
        }
        return true;
    }

    private static void fixJar(ApplicationInfo info) {
        String APACHE_LEGACY_JAR = "/system/framework/org.apache.http.legacy.boot.jar";
        String APACHE_LEGACY_JAR_Q = "/system/framework/org.apache.http.legacy.jar";
        Set<String> sharedLibraryFileList = new HashSet<>();
        if (ParallaxBuildCompat.isQ()) {
            if (!ParallaxFileUtils.isExist(APACHE_LEGACY_JAR_Q)) {
                sharedLibraryFileList.add(APACHE_LEGACY_JAR);
            } else {
                sharedLibraryFileList.add(APACHE_LEGACY_JAR_Q);
            }
        } else {
            sharedLibraryFileList.add(APACHE_LEGACY_JAR);
        }
        info.sharedLibraryFiles = sharedLibraryFileList.toArray(new String[]{});
    }

    public static Resources getResources(Context context, ApplicationInfo appInfo) {
        ParallaxPackageSettings ps = ParallaxPackageManagerService.get().getBPackageSetting(appInfo.packageName);
        if (ps != null) {
            AssetManager assets = BRAssetManager.get()._new();
            BRAssetManager.get(assets).addAssetPath(ps.pkg.baseCodePath);
            Resources hostRes = context.getResources();
            return new Resources(assets, hostRes.getDisplayMetrics(), hostRes.getConfiguration());
        }
        return null;
    }
}
