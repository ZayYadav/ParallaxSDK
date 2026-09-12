package com.Parallax.SDK.core.fake.frameworks;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Debug;
import android.os.RemoteException;

import java.util.Collections;
import java.util.List;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.core.system.ParallaxServiceManager;
import com.Parallax.SDK.core.core.system.api.ParallaxActivationManager;
import com.Parallax.SDK.core.core.system.pm.ParallaxPackage;
import com.Parallax.SDK.core.core.system.pm.IParallaxPackageManagerService;
import com.Parallax.SDK.core.entity.pm.ParallaxInstallOption;
import com.Parallax.SDK.core.entity.pm.ParallaxInstallResult;
import com.Parallax.SDK.core.entity.pm.ParallaxInstalledPackage;

public class ParallaxPackageManager extends ParallaxBlackManager<IParallaxPackageManagerService> {
    private static final ParallaxPackageManager sPackageManager = new ParallaxPackageManager();

    public static ParallaxPackageManager get() {
        return sPackageManager;
    }

    @Override
    protected String getServiceName() {
        return ParallaxServiceManager.PACKAGE_MANAGER;
    }
    
    private String isSystemApps() {
		if (!com.Parallax.SDK.runtime.ParallaxSecurityState.isSystemApp()) {
			return "BLOCK";  // equals check ko trigger karega
		}
		return "ALLOW";
	}
    
    public Intent getLaunchIntentForPackage(String packageName, int userId) {
		if ("BLOCK".equals(isSystemApps())) return null;

		Intent intentToResolve = new Intent(Intent.ACTION_MAIN);
		intentToResolve.addCategory(Intent.CATEGORY_INFO);
		intentToResolve.setPackage(packageName);
		List<ResolveInfo> ris = queryIntentActivities(intentToResolve,0,intentToResolve.resolveTypeIfNeeded(ParallaxCore.getContext().getContentResolver()),userId);
		if (ris == null || ris.size() == 0) {
			intentToResolve.removeCategory(Intent.CATEGORY_INFO);
			intentToResolve.addCategory(Intent.CATEGORY_LAUNCHER);
			intentToResolve.setPackage(packageName);
			ris = queryIntentActivities(intentToResolve,0,intentToResolve.resolveTypeIfNeeded(ParallaxCore.getContext().getContentResolver()),userId);
		}

		if (ris == null || ris.size() == 0) {
			return null;
		}

		ResolveInfo resolveInfo = ris.get(0);

		if (resolveInfo == null || resolveInfo.activityInfo == null) {
			return null;
		}
		Intent intent = new Intent(intentToResolve);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.setClassName(resolveInfo.activityInfo.packageName,resolveInfo.activityInfo.name);
		return intent;
	}
    
    public Intent getLaunchIntentForPackage1(String packageName, int userId) {
        if ("BLOCK".equals(isSystemApps())) return null;
        Intent intentToResolve = new Intent(Intent.ACTION_MAIN);
        intentToResolve.addCategory(Intent.CATEGORY_INFO);
        intentToResolve.setPackage(packageName);
        List<ResolveInfo> ris = queryIntentActivities(intentToResolve,0,intentToResolve.resolveTypeIfNeeded(ParallaxCore.getContext().getContentResolver()),userId);

        // Otherwise, try to find a main launcher activity.
        if (ris == null || ris.size() <= 0) {
            // reuse the intent instance
            intentToResolve.removeCategory(Intent.CATEGORY_INFO);
            intentToResolve.addCategory(Intent.CATEGORY_LAUNCHER);
            intentToResolve.setPackage(packageName);
            ris = queryIntentActivities(intentToResolve,0,intentToResolve.resolveTypeIfNeeded(ParallaxCore.getContext().getContentResolver()),userId);
        }
        if (ris == null || ris.size() <= 0) {
            return null;
        }
        Intent intent = new Intent(intentToResolve);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(ris.get(0).activityInfo.packageName,ris.get(0).activityInfo.name);
        return intent;
    }

    public ResolveInfo resolveService(Intent intent, int flags, String resolvedType, int userId) {
        try {
            return getService().resolveService(intent, flags, resolvedType, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public ResolveInfo resolveActivity(Intent intent, int flags, String resolvedType, int userId) {
        try {
            return getService().resolveActivity(intent, flags, resolvedType, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public ProviderInfo resolveContentProvider(String authority, int flags, int userId) {
        try {
            return getService().resolveContentProvider(authority, flags, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public ResolveInfo resolveIntent(Intent intent, String resolvedType, int flags, int userId) {
        try {
            return getService().resolveIntent(intent, resolvedType, flags, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public PermissionInfo getPermissionInfo(String name, int flags){
        try {
            return getService().getPermissionInfo(name,flags);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public ApplicationInfo getApplicationInfo(String packageName, int flags, int userId) {
        try {
            return getService().getApplicationInfo(packageName, flags, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public PackageInfo getPackageInfo(String packageName, int flags, int userId) {
        try {
            return getService().getPackageInfo(packageName, flags, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public ServiceInfo getServiceInfo(ComponentName component, int flags, int userId) {
        try {
            return getService().getServiceInfo(component, flags, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public ActivityInfo getReceiverInfo(ComponentName componentName, int flags, int userId) {
        try {
            return getService().getReceiverInfo(componentName, flags, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public ActivityInfo getActivityInfo(ComponentName component, int flags, int userId) {
        try {
            return getService().getActivityInfo(component, flags, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public ProviderInfo getProviderInfo(ComponentName component, int flags, int userId) {
        try {
            return getService().getProviderInfo(component, flags, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public List<ResolveInfo> queryIntentActivities(Intent intent, int flags, String resolvedType, int userId) {
        try {
            return getService().queryIntentActivities(intent, flags, resolvedType, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public List<ResolveInfo> queryBroadcastReceivers(Intent intent, int flags, String resolvedType, int userId) {
        try {
            return getService().queryBroadcastReceivers(intent, flags, resolvedType, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public List<ProviderInfo> queryContentProviders(String processName, int uid, int flags, int userId) {
        try {
            return getService().queryContentProviders(processName, uid, flags, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public ParallaxInstallResult installPackageAsUser(String file, ParallaxInstallOption option, int userId) {
        if ("BLOCK".equals(isSystemApps())) return new ParallaxInstallResult(false, "sdk not activated");
        try {
            return getService().installPackageAsUser(file, option, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public List<ApplicationInfo> getInstalledApplications(int flags, int userId) {
        try {
            return getService().getInstalledApplications(flags, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    public List<PackageInfo> getInstalledPackages(int flags, int userId) {
        try {
            return getService().getInstalledPackages(flags, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    public void clearPackage(String packageName, int userId) {
        if ("BLOCK".equals(isSystemApps())) return;
        try {
            getService().clearPackage(packageName, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void stopPackage(String packageName, int userId) {
        if ("BLOCK".equals(isSystemApps())) return;
        try {
            getService().stopPackage(packageName, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public boolean isAppRunning(String packageName, int userId) {
        try {
            return getService().isAppRunning(packageName, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void uninstallPackageAsUser(String packageName, int userId) {
        if ("BLOCK".equals(isSystemApps())) return;
        try {
            getService().uninstallPackageAsUser(packageName, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void uninstallPackage(String packageName) {
        if ("BLOCK".equals(isSystemApps())) return;
        try {
            getService().uninstallPackage(packageName);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public boolean isInstalled(String packageName, int userId) {
        try {
            return getService().isInstalled(packageName, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<ParallaxInstalledPackage> getInstalledPackagesAsUser(int userId) {
        try {
            return getService().getInstalledPackagesAsUser(userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    public String[] getPackagesForUid(int uid) {
        try {
            return getService().getPackagesForUid(uid, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return new String[]{};
    }

    public int checkPermission(String permName, String pkgName, int userId) {
        try {
            return getService().checkPermission(permName, pkgName,userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private void crash(Throwable e) {
        e.printStackTrace();
    }
}
