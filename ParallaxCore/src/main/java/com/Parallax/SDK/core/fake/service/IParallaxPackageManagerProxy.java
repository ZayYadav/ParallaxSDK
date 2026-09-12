package com.Parallax.SDK.core.fake.service;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.content.pm.VersionedPackage;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.Parallax.SDK.core.fake.frameworks.ParallaxPackageManager;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.Parallax.SDK.mirror.android.app.BRActivityThread;
import com.Parallax.SDK.mirror.android.app.BRContextImpl;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.core.ParallaxGmsCore;
import com.Parallax.SDK.core.core.env.ParallaxAppSystemEnv;

import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.fake.service.base.ParallaxPkgMethodProxy;
import com.Parallax.SDK.core.fake.service.base.ParallaxValueMethodProxy;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;
import com.Parallax.SDK.core.utils.ParallaxReflector;
import com.Parallax.SDK.core.utils.ParallaxSlog;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;
import com.Parallax.SDK.core.utils.compat.ParallaxParceledListSliceCompat;
import com.Parallax.SDK.core.utils.compat.ParallaxVirtualPermissionCompat;

/**
 * Created by Milk on 3/30/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class IParallaxPackageManagerProxy extends ParallaxBinderInvocationStub {
    public static final String TAG = "PackageManagerStub";

    public IParallaxPackageManagerProxy() {
        super(BRActivityThread.get().sPackageManager().asBinder());
    }

    @Override
    protected Object getWho() {
        return BRActivityThread.get().sPackageManager();
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        BRActivityThread.get()._set_sPackageManager(proxyInvocation);
        replaceSystemService("package");
        Object systemContext = BRActivityThread.get(ParallaxCore.mainThread()).getSystemContext();
        BRContextImpl.get(systemContext).getPackageManager();
        PackageManager mPackageManager = BRContextImpl.get(systemContext).mPackageManager();
        if (mPackageManager != null) {
            try {
                ParallaxReflector.on("android.app.ApplicationPackageManager").field("mPM").set(mPackageManager, proxyInvocation);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new ParallaxValueMethodProxy("addOnPermissionsChangeListener", 0));
        addMethodHook(new ParallaxValueMethodProxy("removeOnPermissionsChangeListener", 0));
        addMethodHook(new ParallaxPkgMethodProxy("shouldShowRequestPermissionRationale"));
        if (ParallaxBuildCompat.isT()) {
            return;
        }
    }
    
    @ParallaxProxyMethod("resolveIntent")
    public static class ResolveIntent extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = (Intent) args[0];
            String resolvedType = (String) args[1];
            int flags = Integer.parseInt(args[2] + "");
            ResolveInfo resolveInfo = ParallaxCore.getBPackageManager().resolveIntent(intent, resolvedType, flags, ParallaxActivityThread.getUserId());
            if (resolveInfo != null) {
                return resolveInfo;
            }
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("checkPermission")
    public static class CheckPermission extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String permission = args != null && args.length > 0 && args[0] instanceof String
                    ? (String) args[0] : null;
            String requestedPackage = args != null && args.length > 1
                    && args[1] instanceof String ? (String) args[1] : null;
            if (requestedPackage != null
                    && ParallaxVirtualPermissionCompat.shouldGrantDeclaredNetworkPermission(
                    permission, requestedPackage)) {
                return PackageManager.PERMISSION_GRANTED;
            }
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("resolveService")
    public static class ResolveService extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = (Intent) args[0];
            String resolvedType = (String) args[1];
            int flags = Integer.parseInt(args[2] + "");
            ResolveInfo resolveInfo = ParallaxCore.getBPackageManager().resolveService(intent, flags, resolvedType, ParallaxActivityThread.getUserId());
            if (resolveInfo != null) {
                return resolveInfo;
            }
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("setComponentEnabledSetting")
    public static class SetComponentEnabledSetting extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    @ParallaxProxyMethod("getPackageInfo")
    public static class GetPackageInfo extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String packageName = (String) args[0];
            int flags = ParallaxMethodParameterUtils.toInt(args[1]);
			if (ParallaxGmsCore.isGoogleAppOrService(packageName)) {
				return method.invoke(who, args);
			}
            PackageInfo packageInfo = ParallaxCore.getBPackageManager().getPackageInfo(packageName, flags, ParallaxActivityThread.getUserId());
            if (packageInfo != null) {
                return packageInfo;
            }
            
            if (ParallaxAppSystemEnv.isOpenPackage(packageName)) {
                return method.invoke(who, args);
            }
            return null;
        }
    }

    @ParallaxProxyMethod("getPackageUid")
    public static class GetPackageUid extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }
    
    @ParallaxProxyMethod("setApplicationBlockedSettingAsUser")
    public static class setApplicationBlockedSettingAsUser extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            ParallaxMethodParameterUtils.replaceLastUserId(args);
            return method.invoke(who, args);
        }
    }
    
    @ParallaxProxyMethod("getPackageUidEtc")
    public static class getPackageUidEtc extends GetPackageUid {

    }

    @ParallaxProxyMethod("getProviderInfo")
    public static class GetProviderInfo extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ComponentName componentName = (ComponentName) args[0];
            int flags = ParallaxMethodParameterUtils.toInt(args[1]);
            ProviderInfo providerInfo = ParallaxCore.getBPackageManager().getProviderInfo(componentName, flags, ParallaxActivityThread.getUserId());
            if (providerInfo != null) return providerInfo;
            if (ParallaxAppSystemEnv.isOpenPackage(componentName)) {
                return method.invoke(who, args);
            }
            return null;
        }
    }

    @ParallaxProxyMethod("getReceiverInfo")
    public static class GetReceiverInfo extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ComponentName componentName = (ComponentName) args[0];
            int flags = ParallaxMethodParameterUtils.toInt(args[1]);
            ActivityInfo receiverInfo = ParallaxCore.getBPackageManager().getReceiverInfo(componentName, flags, ParallaxActivityThread.getUserId());
            if (receiverInfo != null) return receiverInfo;
            if (ParallaxAppSystemEnv.isOpenPackage(componentName)) {
                return method.invoke(who, args);
            }
            return null;
        }
    }

    @ParallaxProxyMethod("getActivityInfo")
    public static class GetActivityInfo extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ComponentName componentName = (ComponentName) args[0];
            int flags = ParallaxMethodParameterUtils.toInt(args[1]);
            ActivityInfo activityInfo = ParallaxCore.getBPackageManager().getActivityInfo(componentName, flags, ParallaxActivityThread.getUserId());
            if (activityInfo != null) return activityInfo;
            if (ParallaxAppSystemEnv.isOpenPackage(componentName)) {
                return method.invoke(who, args);
            }
            return null;
        }
    }

    @ParallaxProxyMethod("getServiceInfo")
    public static class GetServiceInfo extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ComponentName componentName = (ComponentName) args[0];
            int flags = ParallaxMethodParameterUtils.toInt(args[1]);
            ServiceInfo serviceInfo = ParallaxCore.getBPackageManager().getServiceInfo(componentName, flags, ParallaxActivityThread.getUserId());
            if (serviceInfo != null) return serviceInfo;
            if (ParallaxAppSystemEnv.isOpenPackage(componentName)) {
                return method.invoke(who, args);
            }
            return null;
        }
    }

    @ParallaxProxyMethod("getInstalledApplications")
    public static class GetInstalledApplications extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int flags = ParallaxMethodParameterUtils.toInt(args[0]);
            List<ApplicationInfo> installedApplications = ParallaxCore.getBPackageManager().getInstalledApplications(flags, ParallaxActivityThread.getUserId());
            return ParallaxParceledListSliceCompat.create(installedApplications);
        }
    }

    @ParallaxProxyMethod("getInstalledPackages")
    public static class GetInstalledPackages extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int flags = ParallaxMethodParameterUtils.toInt(args[0]);
            List<PackageInfo> installedPackages = ParallaxCore.getBPackageManager().getInstalledPackages(flags, ParallaxActivityThread.getUserId());
            return ParallaxParceledListSliceCompat.create(installedPackages);
        }
    }

    @ParallaxProxyMethod("getApplicationInfo")
    public static class GetApplicationInfo extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String packageName = (String) args[0];
            int flags = ParallaxMethodParameterUtils.toInt(args[1]);
            ApplicationInfo applicationInfo = ParallaxCore.getBPackageManager().getApplicationInfo(packageName, flags, ParallaxActivityThread.getUserId());
            if (ParallaxGmsCore.isGoogleAppOrService(packageName)) {
				return method.invoke(who, args);
			}
            if (applicationInfo != null) {
				return applicationInfo;
			}
            if (ParallaxAppSystemEnv.isOpenPackage(packageName)) {
                return method.invoke(who, args);
            }
            return null;
        }
    }

    @ParallaxProxyMethod("queryContentProviders")
    public static class QueryContentProviders extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int flags = ParallaxMethodParameterUtils.toInt(args[2]);
            List<ProviderInfo> providers = ParallaxCore.getBPackageManager().queryContentProviders(ParallaxActivityThread.getAppProcessName(), ParallaxActivityThread.getBUid(), flags, ParallaxActivityThread.getUserId());
            return ParallaxParceledListSliceCompat.create(providers);
        }
    }

    @ParallaxProxyMethod("queryIntentReceivers")
    public static class QueryBroadcastReceivers extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = ParallaxMethodParameterUtils.getFirstParam(args, Intent.class);
            String type = ParallaxMethodParameterUtils.getFirstParam(args, String.class);
            Integer flags = ParallaxMethodParameterUtils.getFirstParam(args, Integer.class);
            int flagValue = flags != null ? flags.intValue() : 0;
            List<ResolveInfo> resolves = ParallaxCore.getBPackageManager().queryBroadcastReceivers(intent, flagValue, type, ParallaxActivityThread.getUserId());
            ParallaxSlog.d(TAG, "queryIntentReceivers: " + resolves);

            // http://androidxref.com/7.0.0_r1/xref/frameworks/base/core/java/android/app/ApplicationPackageManager.java#872
            if (ParallaxBuildCompat.isN()) {
                return ParallaxParceledListSliceCompat.create(resolves);
            }

            // http://androidxref.com/6.0.1_r10/xref/frameworks/base/core/java/android/app/ApplicationPackageManager.java#699
            return resolves;
        }
    }

    @ParallaxProxyMethod("resolveContentProvider")
    public static class ResolveContentProvider extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String authority = (String) args[0];
            int flags = ParallaxMethodParameterUtils.toInt(args[1]);
            ProviderInfo providerInfo = ParallaxCore.getBPackageManager().resolveContentProvider(authority, flags, ParallaxActivityThread.getUserId());
            if (providerInfo == null) {
                return method.invoke(who, args);
            }
            return providerInfo;
        }
    }

    @ParallaxProxyMethod("canRequestPackageInstalls")
    public static class CanRequestPackageInstalls extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("getPackagesForUid")
    public static class GetPackagesForUid extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int uid = ParallaxMethodParameterUtils.toInt(args[0]);
            if (uid == ParallaxCore.getHostUid()) {
                args[0] = ParallaxActivityThread.getBUid();
                uid = ParallaxMethodParameterUtils.toInt(args[0]);
            }
            String[] packagesForUid = ParallaxCore.getBPackageManager().getPackagesForUid(uid);
            ParallaxSlog.d(TAG, args[0] + " , " + ParallaxActivityThread.getAppProcessName() + " GetPackagesForUid: " + Arrays.toString(packagesForUid));
            return packagesForUid;
        }
    }

    @ParallaxProxyMethod("getInstallerPackageName")
    public static class GetInstallerPackageName extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            // FIX: Google Play Store ke liye
            if (args != null && args.length > 0 && args[0] instanceof String) {
                String packageName = (String) args[0];
                if (ParallaxGmsCore.VENDING_PKG.equals(packageName)) {
                    return "com.android.vending";
                }
            }
            return ParallaxGmsCore.VENDING_PKG;
        }
    }

    @ParallaxProxyMethod("getSharedLibraries")
    public static class GetSharedLibraries extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            // todo
            return ParallaxParceledListSliceCompat.create(new ArrayList<>());
        }
    }

    @ParallaxProxyMethod("getComponentEnabledSetting")
    public static class getComponentEnabledSetting extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ComponentName componentName = (ComponentName) args[0];
            String packageName = componentName.getPackageName();

            ApplicationInfo applicationInfo = ParallaxCore.getBPackageManager().getApplicationInfo(packageName,0, ParallaxActivityThread.getUserId());
            if(applicationInfo != null){
                return PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
            }
            if (ParallaxAppSystemEnv.isOpenPackage(componentName)) {
                return method.invoke(who, args);
            }
            throw new IllegalArgumentException();
        }
    }
    
    @ParallaxProxyMethod("addPackageToPreferred")
    public static class addPackageToPreferred extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }
    
    @ParallaxProxyMethod("setSplashScreenTheme")
    public static class SetSplashScreenTheme extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String packageName = args.length > 0 ? (String) args[0] : "unknown";
            ParallaxSlog.d(TAG, "SetSplashScreenTheme: Bypassing UID check for package: " + packageName);
            boolean isXiaomi = ParallaxBuildCompat.isMIUI() || Build.MANUFACTURER.toLowerCase().contains("xiaomi") || Build.BRAND.toLowerCase().contains("xiaomi") || Build.DISPLAY.toLowerCase().contains("hyperos");
            if (isXiaomi) { ParallaxSlog.d(TAG, "SetSplashScreenTheme: Detected Xiaomi/HyperOS, using enhanced bypass"); return null; }
            try {
                return method.invoke(who, args);
            } catch (SecurityException e) {
                ParallaxSlog.w(TAG, "SetSplashScreenTheme: SecurityException caught, bypassing: " + e.getMessage());
                return null;
            } catch (Exception e) {
                if (e.getCause() instanceof SecurityException) {
                    ParallaxSlog.w(TAG, "SetSplashScreenTheme: SecurityException (wrapped) caught, bypassing: " + e.getCause().getMessage());
                    return null;
                }
                throw e;
            }
        }
    }
    
    @ParallaxProxyMethod("checkSignatures")
    public static class checkSignatures extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args.length == 2) {
                if (args[0] instanceof String && args[1] instanceof String) {
                    String pkg1 = (String) args[0];
                    String pkg2 = (String) args[1];
                    PackageManager pm = ParallaxCore.getPackageManager();
                    try {
                        PackageInfo pkgInfo1 = pm.getPackageInfo(pkg1, PackageManager.GET_SIGNATURES);
                        PackageInfo pkgInfo2 = pm.getPackageInfo(pkg2, PackageManager.GET_SIGNATURES);
                        Signature[] sigs1 = pkgInfo1.signatures;
                        Signature[] sigs2 = pkgInfo2.signatures;
                        if (sigs1 == null || sigs1.length == 0) {
                            return (sigs2 == null || sigs2.length == 0) ? 1 : -1;
                        }
                        if (sigs2 == null || sigs2.length == 0) {
                            return -2;
                        }
                        return Arrays.equals(sigs1, sigs2) ? 0 : -3;
                    } catch (Exception e) {
                        // Fall through
                    }
                }
            }
            return method.invoke(who, args);
        }
    }
    
    @ParallaxProxyMethod("checkUidSignatures")
    public static class checkUidSignatures extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            // FIX: Google Play Services ke liye
            return PackageManager.SIGNATURE_MATCH;
        }
    }
    
    @ParallaxProxyMethod("clearPackagePersistentPreferredActivities")
    public static class clearPackagePersistentPreferredActivities extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }
    
    @ParallaxProxyMethod("clearPackagePreferredActivities")
    public static class clearPackagePreferredActivities extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }
    
    @ParallaxProxyMethod("getApplicationBlockedSettingAsUser")
    public static class getApplicationBlockedSettingAsUser extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }
    
    @ParallaxProxyMethod("setPackageStoppedState")
    public static class setPackageStoppedState extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }
    
}
