package com.Parallax.SDK.core.core.system.pm;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.RemoteException;
import android.text.TextUtils;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.core.ParallaxGmsCore;
import com.Parallax.SDK.core.core.env.ParallaxEnvironment;
import com.Parallax.SDK.core.core.system.ParallaxProcessManagerService;
import com.Parallax.SDK.core.core.system.IParallaxSystemService;
import com.Parallax.SDK.core.core.system.ParallaxProcessRecord;
import com.Parallax.SDK.core.core.system.user.ParallaxUserHandle;
import com.Parallax.SDK.core.core.system.user.ParallaxUserInfo;
import com.Parallax.SDK.core.core.system.user.ParallaxUserManagerService;
import com.Parallax.SDK.core.entity.pm.ParallaxInstallOption;
import com.Parallax.SDK.core.entity.pm.ParallaxInstallResult;
import com.Parallax.SDK.core.entity.pm.ParallaxInstalledPackage;
import com.Parallax.SDK.core.utils.ParallaxAbiUtils;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;
import com.Parallax.SDK.core.utils.ParallaxPermissionUtils;
import com.Parallax.SDK.core.utils.ParallaxSlog;
import com.Parallax.SDK.core.utils.compat.ParallaxPackageParserCompat;
import com.Parallax.SDK.core.utils.compat.ParallaxXposedParserCompat;

import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE;


/**
 * Created by @RIYAZXERO on 4/1/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxPackageManagerService extends IParallaxPackageManagerService.Stub implements IParallaxSystemService {
    public static final String TAG = "ParallaxPackageManagerService";
    public static ParallaxPackageManagerService sService = new ParallaxPackageManagerService();
    private final ParallaxSettings mSettings = new ParallaxSettings();      // 等同于 PackageCacheManager
    private final ParallaxComponentResolver mComponentResolver;
    private static final ParallaxUserManagerService sUserManager = ParallaxUserManagerService.get();
    private final List<ParallaxPackageMonitor> mPackageMonitors = new ArrayList<>();
    private final HashMap<String, ParallaxPackage.Permission> mPermissions = new HashMap<>();

    final Map<String, ParallaxPackageSettings> mPackages = mSettings.mPackages;
    private final Map<String, String[]> mDangerousPermissions = new HashMap<>();

    final Object mInstallLock = new Object();

    public static ParallaxPackageManagerService get() {
        return sService;
    }

    public ParallaxPackageManagerService() {
        mComponentResolver = new ParallaxComponentResolver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.PACKAGE_ADDED");
        filter.addAction("android.intent.action.PACKAGE_REMOVED");
        filter.addDataScheme("package");
        ParallaxCore.getContext()
                .registerReceiver(mPackageChangedHandler, filter);
    }

    private final BroadcastReceiver mPackageChangedHandler = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!TextUtils.isEmpty(action)) {
                if ("android.intent.action.PACKAGE_ADDED".equals(action) || "android.intent.action.PACKAGE_REMOVED".equals(action)) {
                    mSettings.scanPackage();
                }
            }
        }
    };

    @Override
    public ApplicationInfo getApplicationInfo(String packageName, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        if (Objects.equals(packageName, ParallaxCore.getHostPkg())) {
            try {
                return ParallaxCore.getPackageManager().getApplicationInfo(packageName, flags);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }
        flags = updateFlags(flags, userId);
        // reader
        synchronized (mPackages) {
            // Normalize package name to handle renamed packages and static libs
            ParallaxPackageSettings ps = mPackages.get(packageName);
            if (ps != null) {
                ParallaxPackage p = ps.pkg;
                return ParallaxPackageManagerCompat.generateApplicationInfo(p, flags, ps.readUserState(userId), userId);
            }
        }
        return null;
    }

    @Override
    public ResolveInfo resolveService(Intent intent, int flags, String resolvedType, int userId) {
        if (!sUserManager.exists(userId)) return null;
        List<ResolveInfo> query = queryIntentServicesInternal(
                intent, resolvedType, flags, userId);
        if (query != null) {
            if (query.size() >= 1) {
                // If there is more than one service with the same priority,
                // just arbitrarily pick the first one.
                return query.get(0);
            }
        }
        return null;
    }

    private List<ResolveInfo> queryIntentServicesInternal(Intent intent, String resolvedType, int flags, int userId) {
        ComponentName comp = intent.getComponent();
        if (comp == null) {
            if (intent.getSelector() != null) {
                intent = intent.getSelector();
                comp = intent.getComponent();
            }
        }
        if (comp != null) {
            final List<ResolveInfo> list = new ArrayList<>(1);
            final ServiceInfo si = getServiceInfo(comp, flags, userId);
            if (si != null) {
                // When specifying an explicit component, we prevent the service from being
                // used when either 1) the service is in an instant application and the
                // caller is not the same instant application or 2) the calling package is
                // ephemeral and the activity is not visible to ephemeral applications.
                final ResolveInfo ri = new ResolveInfo();
                ri.serviceInfo = si;
                list.add(ri);
            }
            return list;
        }

        // reader
        synchronized (mPackages) {
            String pkgName = intent.getPackage();
            if (pkgName != null) {
                ParallaxPackageSettings bPackageSettings = mPackages.get(pkgName);
                if (bPackageSettings != null) {
                    final ParallaxPackage pkg = bPackageSettings.pkg;
                    return mComponentResolver.queryServices(intent, resolvedType, flags, pkg.services,
                            userId);
                }
            } else {
               return mComponentResolver.queryServices(intent, resolvedType, flags, userId);
            }
            return Collections.emptyList();
        }
    }

    @Override
    public ResolveInfo resolveActivity(Intent intent, int flags, String resolvedType, int userId) {
        if (!sUserManager.exists(userId)) return null;
        List<ResolveInfo> resolves = queryIntentActivities(intent, resolvedType, flags, userId);
        return chooseBestActivity(intent, resolvedType, flags, resolves);
    }

    @Override
    public ProviderInfo resolveContentProvider(String authority, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        return mComponentResolver.queryProvider(authority, flags, userId);
    }

    @Override
    public ResolveInfo resolveIntent(Intent intent, String resolvedType, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        List<ResolveInfo> resolves = queryIntentActivities(intent, resolvedType, flags, userId);
        return chooseBestActivity(intent, resolvedType, flags, resolves);
    }

    private ResolveInfo chooseBestActivity(Intent intent, String resolvedType,
                                           int flags, List<ResolveInfo> query) {
        if (query != null) {
            final int N = query.size();
            if (N == 1) {
                return query.get(0);
            } else if (N > 1) {
                // If there is more than one activity with the same priority,
                // then let the user decide between them.
                ResolveInfo r0 = query.get(0);
                ResolveInfo r1 = query.get(1);
                // If the first activity has a higher priority, or a different
                // default, then it is always desirable to pick it.
                if (r0.priority != r1.priority
                        || r0.preferredOrder != r1.preferredOrder
                        || r0.isDefault != r1.isDefault) {
                    return query.get(0);
                }
            }
        }
        return null;
    }

    private List<ResolveInfo> queryIntentActivities(Intent intent,
                                                    String resolvedType, int flags, int userId) {
        ComponentName comp = intent.getComponent();
        if (comp == null) {
            if (intent.getSelector() != null) {
                intent = intent.getSelector();
                comp = intent.getComponent();
            }
        }

        if (comp != null) {
            final List<ResolveInfo> list = new ArrayList<>(1);
            final ActivityInfo ai = getActivity(comp, flags, userId);
            if (ai != null) {
                // When specifying an explicit component, we prevent the activity from being
                // used when either 1) the calling package is normal and the activity is within
                // an ephemeral application or 2) the calling package is ephemeral and the
                // activity is not visible to ephemeral applications.
                final ResolveInfo ri = new ResolveInfo();
                ri.activityInfo = ai;
                list.add(ri);
                return list;
            }
        }

        // reader
        synchronized (mPackages) {
            return mComponentResolver.queryActivities(intent, resolvedType, flags, userId);
        }
    }

    @Override
    public List<ResolveInfo> queryIntentServices(
            Intent intent, int flags, int userId) {
        final String resolvedType = intent.resolveTypeIfNeeded(ParallaxCore.getContext().getContentResolver());
        return this.queryIntentServicesInternal(intent, resolvedType, flags, userId);
    }

    private ActivityInfo getActivity(ComponentName component, int flags,
                                     int userId) {
        flags = updateFlags(flags, userId);
        synchronized (mPackages) {
            ParallaxPackage.Activity a = mComponentResolver.getActivity(component);

            if (a != null) {
                ParallaxPackageSettings ps = mSettings.mPackages.get(component.getPackageName());
                if (ps == null) return null;
                return ParallaxPackageManagerCompat.generateActivityInfo(a, flags, ps.readUserState(userId), userId);
            }
        }
        return null;
    }

    @Override
    public PackageInfo getPackageInfo(String packageName, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        if (Objects.equals(packageName, ParallaxCore.getHostPkg())) {
            try {
                return ParallaxCore.getPackageManager().getPackageInfo(packageName, flags);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }

        flags = updateFlags(flags, userId);
        ParallaxPackageSettings ps = null;
        // reader
        synchronized (mPackages) {
            // Normalize package name to handle renamed packages and static libs
            ps = mPackages.get(packageName);
        }
        if (ps != null) {
            return ParallaxPackageManagerCompat.generatePackageInfo(ps, flags, ps.readUserState(userId), userId);
        }
        return null;
    }
    
    @Override
    public ServiceInfo getServiceInfo(ComponentName component, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        synchronized (mPackages) {
            ParallaxPackage.Service s = mComponentResolver.getService(component);
            if (s != null) {
                ParallaxPackageSettings ps = mPackages.get(component.getPackageName());
                if (ps == null) return null;
                return ParallaxPackageManagerCompat.generateServiceInfo(
                        s, flags, ps.readUserState(userId), userId);
            }
        }
        return null;
    }

    @Override
    public ActivityInfo getReceiverInfo(ComponentName component, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        synchronized (mPackages) {
            ParallaxPackage.Activity a = mComponentResolver.getReceiver(component);
            if (a != null) {
                ParallaxPackageSettings ps = mPackages.get(component.getPackageName());
                if (ps == null) return null;
                return ParallaxPackageManagerCompat.generateActivityInfo(
                        a, flags, ps.readUserState(userId), userId);
            }
        }
        return null;
    }

    @Override
    public ActivityInfo getActivityInfo(ComponentName component, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        synchronized (mPackages) {
            ParallaxPackage.Activity a = mComponentResolver.getActivity(component);

            if (a != null) {
                ParallaxPackageSettings ps = mPackages.get(component.getPackageName());
                if (ps == null) return null;
                return ParallaxPackageManagerCompat.generateActivityInfo(
                        a, flags, ps.readUserState(userId), userId);
            }
        }
        return null;
    }

    @Override
    public ProviderInfo getProviderInfo(ComponentName component, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        synchronized (mPackages) {
            ParallaxPackage.Provider p = mComponentResolver.getProvider(component);
            if (p != null) {
                ParallaxPackageSettings ps = mPackages.get(component.getPackageName());
                if (ps == null) return null;
                return ParallaxPackageManagerCompat.generateProviderInfo(
                        p, flags, ps.readUserState(userId), userId);
            }
        }
        return null;
    }

    @Override
    public List<ApplicationInfo> getInstalledApplications(int flags, int userId) {
        return getInstalledApplicationsListInternal(flags, userId, Binder.getCallingUid());
    }

    @Override
    public List<PackageInfo> getInstalledPackages(int flags, int userId) {
        final int callingUid = Binder.getCallingUid();
//        if (getInstantAppPackageName(callingUid) != null) {
//            return ParceledListSlice.emptyList();
//        }
        if (!sUserManager.exists(userId)) return Collections.emptyList();

        // writer
        synchronized (mPackages) {
            ArrayList<PackageInfo> list;
            list = new ArrayList<>(mPackages.size());
            for (ParallaxPackageSettings ps : mPackages.values()) {
//                if (filterSharedLibPackageLPr(ps, callingUid, userId, flags)) {
//                    continue;
//                }
//                if (filterAppAccessLPr(ps, callingUid, userId)) {
//                    continue;
//                }
                PackageInfo pi = getPackageInfo(ps.pkg.packageName, flags, userId);
                if (pi != null) {
                    list.add(pi);
                }
            }
            return new ArrayList<>(list);
        }
    }

    private List<ApplicationInfo> getInstalledApplicationsListInternal(int flags, int userId,int callingUid) {
        if (!sUserManager.exists(userId)) return Collections.emptyList();
        // writer
        synchronized (mPackages) {
            ArrayList<ApplicationInfo> list;
            list = new ArrayList<>(mPackages.size());
            Collection<ParallaxPackageSettings> packageSettings = mPackages.values();
            for (ParallaxPackageSettings ps : packageSettings) {
                if (ParallaxGmsCore.isGoogleAppOrService(ps.pkg.packageName))
                    continue;
                ApplicationInfo ai = ParallaxPackageManagerCompat.generateApplicationInfo(ps.pkg, flags,ps.readUserState(userId), userId);
                if (ai != null) {
                    list.add(ai);
                }
            }
            return list;
        }
    }

    @Override
    public List<ResolveInfo> queryIntentActivities(Intent intent, int flags, String resolvedType, int userId) throws RemoteException {
        if (!sUserManager.exists(userId)) return Collections.emptyList();
        final String pkgName = intent.getPackage();
        ComponentName comp = intent.getComponent();
        if (comp == null) {
            if (intent.getSelector() != null) {
                intent = intent.getSelector();
                comp = intent.getComponent();
            }
        }

        if (comp != null) {
            final List<ResolveInfo> list = new ArrayList<>(1);
            final ActivityInfo ai = getActivityInfo(comp, flags, userId);
            if (ai != null) {
                // When specifying an explicit component, we prevent the activity from being
                // used when either 1) the calling package is normal and the activity is within
                // an ephemeral application or 2) the calling package is ephemeral and the
                // activity is not visible to ephemeral applications.
                final ResolveInfo ri = new ResolveInfo();
                ri.activityInfo = ai;
                list.add(ri);
            }
            return list;
        }

        // reader
        List<ResolveInfo> result;
        synchronized (mPackages) {
            if (pkgName != null) {
                ParallaxPackageSettings bPackageSettings = mPackages.get(pkgName);
                result = null;
                if (bPackageSettings != null) {
                    final ParallaxPackage pkg = bPackageSettings.pkg;

                    result = mComponentResolver.queryActivities(
                            intent, resolvedType, flags, pkg.activities, userId);
                }
                if (result == null || result.size() == 0) {
                    // the caller wants to resolve for a particular package; however, there
                    // were no installed results, so, try to find an ephemeral result
                    if (result == null) {
                        result = new ArrayList<>();
                    }
                }
                return result;
            }
        }
        return Collections.emptyList();
    }

    @Override
    public List<ResolveInfo> queryBroadcastReceivers(Intent intent, int flags, String resolvedType, int userId) throws RemoteException {
        if (!sUserManager.exists(userId)) return Collections.emptyList();

        ComponentName comp = intent.getComponent();
        if (comp == null) {
            if (intent.getSelector() != null) {
                intent = intent.getSelector();
                comp = intent.getComponent();
            }
        }
        if (comp != null) {
            final List<ResolveInfo> list = new ArrayList<>(1);
            final ActivityInfo ai = getReceiverInfo(comp, flags, userId);
            if (ai != null) {
                // When specifying an explicit component, we prevent the activity from being
                // used when either 1) the calling package is normal and the activity is within
                // an instant application or 2) the calling package is ephemeral and the
                // activity is not visible to instant applications.
                ResolveInfo ri = new ResolveInfo();
                ri.activityInfo = ai;
                list.add(ri);
            }
            return list;
        }

        // reader
        synchronized (mPackages) {
            String pkgName = intent.getPackage();
            ParallaxPackageSettings bPackageSettings = mPackages.get(pkgName);
            if (bPackageSettings != null) {
                final ParallaxPackage pkg = bPackageSettings.pkg;
                return mComponentResolver.queryReceivers(
                        intent, resolvedType, flags, pkg.receivers, userId);
            } else {
                return mComponentResolver.queryReceivers(intent, resolvedType, flags, userId);
            }
        }
    }

    @Override
    public List<ProviderInfo> queryContentProviders(String processName, int uid, int flags, int userId) throws RemoteException {
        if (!sUserManager.exists(userId)) return Collections.emptyList();

        List<ProviderInfo> providers = new ArrayList<>();
        if (TextUtils.isEmpty(processName))
            return providers;
        providers.addAll(mComponentResolver.queryProviders(processName, null, flags, userId));
        return providers;
    }

    @Override
    public ParallaxInstallResult installPackageAsUser(String file, ParallaxInstallOption option, int userId) {
        synchronized (mInstallLock) {
            return installPackageAsUserLocked(file, option, userId);
        }
    }

    @Override
    public void uninstallPackageAsUser(String packageName, int userId) throws RemoteException {
        synchronized (mInstallLock) {
            synchronized (mPackages) {
                ParallaxPackageSettings ps = mPackages.get(packageName);
                if (ps == null)
                    return;
                if (ps.installOption.isFlag(ParallaxInstallOption.FLAG_XPOSED) && userId != ParallaxUserHandle.USER_XPOSED) {
                    return;
                }
                if (!isInstalled(packageName, userId)) {
                    return;
                }
                boolean removeApp = ps.getUserState().size() <= 1;
                ParallaxProcessManagerService.get().killPackageAsUser(packageName, userId);
                int i = ParallaxPackageInstallerService.get().uninstallPackageAsUser(ps, removeApp, userId);
                if (i < 0) {
                    // todo
                }

                if (removeApp) {
                    mSettings.removePackage(packageName);
                    mComponentResolver.removeAllComponents(ps.pkg);
                } else {
                    ps.removeUser(userId);
                    ps.save();
                }
                onPackageUninstalled(packageName, removeApp, userId);
            }
        }
    }

    @Override
    public void uninstallPackage(String packageName) {
        synchronized (mInstallLock) {
            synchronized (mPackages) {
                ParallaxPackageSettings ps = mPackages.get(packageName);
                if (ps == null)
                    return;
                ParallaxProcessManagerService.get().killAllByPackageName(packageName);
                if (ps.installOption.isFlag(ParallaxInstallOption.FLAG_XPOSED)) {
                    for (ParallaxUserInfo user : ParallaxUserManagerService.get().getAllUsers()) {
                        int i = ParallaxPackageInstallerService.get().uninstallPackageAsUser(ps, true, user.id);
                        if (i < 0) {
                            continue;
                        }
                        onPackageUninstalled(packageName, true, user.id);
                    }
                } else {
                    for (Integer userId : ps.getUserIds()) {
                        int i = ParallaxPackageInstallerService.get().uninstallPackageAsUser(ps, true, userId);
                        if (i < 0) {
                            continue;
                        }
                        onPackageUninstalled(packageName, true, userId);
                    }
                }
                mSettings.removePackage(packageName);
                mComponentResolver.removeAllComponents(ps.pkg);
            }
        }
    }

    @Override
    public void clearPackage(String packageName, int userId) {
        if (!isInstalled(packageName, userId)) {
            return;
        }
        ParallaxProcessManagerService.get().killPackageAsUser(packageName, userId);
        ParallaxPackageSettings ps = mPackages.get(packageName);
        if (ps == null)
            return;
        int i = ParallaxPackageInstallerService.get().clearPackage(ps, userId);
    }

    @Override
    public void stopPackage(String packageName, int userId) {
        ParallaxProcessManagerService.get().killPackageAsUser(packageName, userId);
    }
    
    @Override
	public boolean isAppRunning(String packageName, int userId) {
		ActivityManager activityManager = (ActivityManager) ParallaxCore.getContext().getSystemService(Context.ACTIVITY_SERVICE);
		List<ActivityManager.RunningAppProcessInfo> processes = activityManager.getRunningAppProcesses();
		if (processes == null) return false;

		for (ActivityManager.RunningAppProcessInfo process : processes) {
			if (Arrays.asList(process.pkgList).contains(packageName)) {
				return true;
			}
		}
		return false;
	}

    @Override
    public void deleteUser(int userId) throws RemoteException {
        synchronized (mPackages) {
            for (ParallaxPackageSettings ps : mPackages.values()) {
                uninstallPackageAsUser(ps.pkg.packageName, userId);
            }
        }
    }

    @Override
    public boolean isInstalled(String packageName, int userId) {
        if (!sUserManager.exists(userId)) return false;
        synchronized (mPackages) {
            ParallaxPackageSettings ps = mPackages.get(packageName);
            if (ps == null)
                return false;
            return ps.getInstalled(userId);
        }
    }

    @Override
    public List<ParallaxInstalledPackage> getInstalledPackagesAsUser(int userId) {
        if (!sUserManager.exists(userId)) return Collections.emptyList();
        synchronized (mPackages) {
            List<ParallaxInstalledPackage> installedPackages = new ArrayList<>();
            for (ParallaxPackageSettings ps : mPackages.values()) {
                if (ps.getInstalled(userId) && !ParallaxGmsCore.isGoogleAppOrService(ps.pkg.packageName)) {
                    ParallaxInstalledPackage installedPackage = new ParallaxInstalledPackage();
                    installedPackage.userId = userId;
                    installedPackage.packageName = ps.pkg.packageName;
                    installedPackages.add(installedPackage);
                }
            }
            return installedPackages;
        }
    }

    @Override
    public String[] getPackagesForUid(int uid, int userId) throws RemoteException {
        if (!sUserManager.exists(userId)) return new String[]{};
        synchronized (mPackages) {
            List<String> packages = new ArrayList<>();
            for (ParallaxPackageSettings ps : mPackages.values()) {
                String packageName = ps.pkg.packageName;
                if (ps.getInstalled(userId) && getAppId(packageName) == uid) {
                    packages.add(packageName);
                }
            }
            if (packages.isEmpty()) {
                ParallaxProcessRecord processByPid = ParallaxProcessManagerService.get().findProcessByPid(getCallingPid());
                if (processByPid != null) {
                    packages.add(processByPid.getPackageName());
                }
            }
            return packages.toArray(new String[]{});
        }
    }

    public int checkUidPermission(String permission, int uid,String packageName) throws RemoteException {
        PermissionInfo info = getPermissionInfo(permission, 0);
        if (info != null) {
            return 0;
        }
        return ParallaxCore.getPackageManager().checkPermission(permission,packageName);
    }

    @Override
    public int checkPermission(String permName, String pkgName, int userId) throws RemoteException {
        if ("android.permission.INTERACT_ACROSS_USERS".equals(permName)
                || "android.permission.INTERACT_ACROSS_USERS_FULL".equals(permName)) {
            return PackageManager.PERMISSION_DENIED;
        }
        PermissionInfo permissionInfo = getPermissionInfo(permName, 0);
        if (permissionInfo != null) {
            return PackageManager.PERMISSION_GRANTED;
        }
        return ParallaxCore.getPackageManager().checkPermission(permName,pkgName);
    }

    @Override
    public PermissionInfo getPermissionInfo(String name, int flags) throws RemoteException {
        synchronized (mPackages) {
            ParallaxPackage.Permission p = mPermissions.get(name);
            if (p != null) {
                return new PermissionInfo(p.info);
            }
        }
        return null;
    }

    private ParallaxInstallResult installPackageAsUserLocked(String file, ParallaxInstallOption option, int userId) {
        long l = System.currentTimeMillis();
        ParallaxInstallResult result = new ParallaxInstallResult();
        File apkFile = null;
        try {
            if (!sUserManager.exists(userId)) {
                sUserManager.createUser(userId);
            }
            if (option.isFlag(ParallaxInstallOption.FLAG_URI_FILE)) {
                apkFile = new File(ParallaxEnvironment.getCacheDir(), UUID.randomUUID().toString() + ".apk");
                InputStream inputStream = ParallaxCore.getContext().getContentResolver().openInputStream(Uri.parse(file));
                ParallaxFileUtils.copyFile(inputStream, apkFile);
            } else {
                apkFile = new File(file);
            }

            if (option.isFlag(ParallaxInstallOption.FLAG_XPOSED) && userId != ParallaxUserHandle.USER_XPOSED) {
                return new ParallaxInstallResult().installError("Please install the XP module in XP module management");
            }
            if (option.isFlag(ParallaxInstallOption.FLAG_XPOSED) && !ParallaxXposedParserCompat.isXPModule(apkFile.getAbsolutePath())) {
                return new ParallaxInstallResult().installError("not a XP module");
            }

            PackageInfo packageArchiveInfo = ParallaxCore.getPackageManager().getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
            if (packageArchiveInfo == null) {
                return result.installError("getPackageArchiveInfo error.Please check whether APK is normal.");
            }

            boolean support = ParallaxAbiUtils.isSupport(apkFile);
            if (!support) {
                String msg = packageArchiveInfo.applicationInfo.loadLabel(ParallaxCore.getPackageManager()) + "[" + packageArchiveInfo.packageName + "]";
                return result.installError(packageArchiveInfo.packageName,
                        msg + (ParallaxCore.is64Bit() ? " not support armeabi-v7a abi" : "not support arm64-v8a abi"));
            }
            PackageParser.Package aPackage = parserApk(apkFile.getAbsolutePath());
            if (aPackage == null) {
                return result.installError("parser apk error.");
            }
            result.packageName = aPackage.packageName;

            if (option.isFlag(ParallaxInstallOption.FLAG_SYSTEM)) {
                aPackage.applicationInfo = ParallaxCore.getPackageManager().getPackageInfo(aPackage.packageName, 0).applicationInfo;
            }
            ParallaxPackageSettings bPackageSettings = mSettings.getPackageLPw(aPackage.packageName, aPackage, option);

            // stop pkg
            ParallaxProcessManagerService.get().killPackageAsUser(aPackage.packageName, userId);

            int i = ParallaxPackageInstallerService.get().installPackageAsUser(bPackageSettings, userId);
            if (i < 0) {
                return result.installError("install apk error.");
            }
            synchronized (mPackages) {
                bPackageSettings.setInstalled(true, userId);
                bPackageSettings.save();
            }
            mComponentResolver.removeAllComponents(bPackageSettings.pkg);
            mComponentResolver.addAllComponents(bPackageSettings.pkg);
            mSettings.scanPackage(aPackage.packageName);
            onPackageInstalled(bPackageSettings.pkg.packageName, userId);
            return result;
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            if (apkFile != null && option.isFlag(ParallaxInstallOption.FLAG_URI_FILE)) {
                ParallaxFileUtils.deleteDir(apkFile);
            }
            ParallaxSlog.d(TAG, "install finish: " + (System.currentTimeMillis() - l) + "ms");
        }
        return result;
    }

    private PackageParser.Package parserApk(String file) {
        try {
            PackageParser parser = ParallaxPackageParserCompat.createParser(new File(file));
            PackageParser.Package aPackage = ParallaxPackageParserCompat.parsePackage(parser, new File(file), 0);
            ParallaxPackageParserCompat.collectCertificates(parser, aPackage, 0);
            return aPackage;
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }

    static String fixProcessName(String defProcessName, String processName) {
        if (processName == null) {
            return defProcessName;
        }
        return processName;
    }

    /**
     * Update given flags based on encryption status of current user.
     */
    private int updateFlags(int flags, int userId) {
        if ((flags & (PackageManager.MATCH_DIRECT_BOOT_UNAWARE
                | PackageManager.MATCH_DIRECT_BOOT_AWARE)) != 0) {
            // Caller expressed an explicit opinion about what encryption
            // aware/unaware components they want to see, so fall through and
            // give them what they want
        } else {
            // Caller expressed no opinion, so match based on user state
            flags |= PackageManager.MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE;
        }
        return flags;
    }

    public int getAppId(String packageName) {
        ParallaxPackageSettings bPackageSettings = mPackages.get(packageName);
        if (bPackageSettings != null)
            return bPackageSettings.appId;
        return -1;
    }

    ParallaxSettings getSettings() {
        return mSettings;
    }

    public void addPackageMonitor(ParallaxPackageMonitor monitor) {
        mPackageMonitors.add(monitor);
    }

    public void removePackageMonitor(ParallaxPackageMonitor monitor) {
        mPackageMonitors.remove(monitor);
    }

    void onPackageUninstalled(String packageName, boolean isRemove, int userId) {
        for (ParallaxPackageMonitor packageMonitor : mPackageMonitors) {
            packageMonitor.onPackageUninstalled(packageName, isRemove, userId);
        }
        ParallaxSlog.d(TAG, "onPackageUninstalled: " + packageName + ", userId: " + userId);
    }

    void onPackageInstalled(String packageName, int userId) {
        for (ParallaxPackageMonitor packageMonitor : mPackageMonitors) {
            packageMonitor.onPackageInstalled(packageName, userId);
        }
        ParallaxSlog.d(TAG, "onPackageInstalled: " + packageName + ", userId: " + userId);
    }

    public ParallaxPackageSettings getBPackageSetting(String packageName) {
        return mPackages.get(packageName);
    }

    public List<ParallaxPackageSettings> getBPackageSettings() {
        return new ArrayList<>(mPackages.values());
    }

    @Override
    public void systemReady() {
        mSettings.scanPackage();
        for (ParallaxPackageSettings value : mPackages.values()) {
            mComponentResolver.removeAllComponents(value.pkg);
            mComponentResolver.addAllComponents(value.pkg);
        }
    }

    // 20240801 add request permission add start 0
    public String[] getDangerousPermissions(String packageName) {
        synchronized (mDangerousPermissions) {
            return mDangerousPermissions.get(packageName);
        }
    }

    public void analyzePackageLocked(ParallaxPackageSettings bPackageSettings) {
        synchronized (mDangerousPermissions) {
            mDangerousPermissions.put(bPackageSettings.pkg.packageName, ParallaxPermissionUtils.findDangerousPermissions(bPackageSettings.pkg.requestedPermissions));
        }
    }
    // 20240801 add request permission add end 0
}