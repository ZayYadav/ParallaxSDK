package top.niunaijun.blackbox.core.system;

import android.os.IBinder;

import java.util.HashMap;
import java.util.Map;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.system.accounts.BAccountManagerService;
import top.niunaijun.blackbox.core.system.am.BActivityManagerService;
import top.niunaijun.blackbox.core.system.am.BJobManagerService;
import top.niunaijun.blackbox.core.system.location.BLocationManagerService;
import top.niunaijun.blackbox.core.system.notification.BNotificationManagerService;
import top.niunaijun.blackbox.core.system.os.BStorageManagerService;
import top.niunaijun.blackbox.core.system.pm.BPackageManagerService;
import top.niunaijun.blackbox.core.system.pm.BXposedManagerService;
import top.niunaijun.blackbox.core.system.user.BUserManagerService;

/**
 * Created by Milk on 3/31/21.
 */
public class ServiceManager {
    private static ServiceManager sServiceManager = null;
    public static final String ACTIVITY_MANAGER = "activity_manager";
    public static final String JOB_MANAGER = "job_manager";
    public static final String PACKAGE_MANAGER = "package_manager";
    public static final String STORAGE_MANAGER = "storage_manager";
    public static final String USER_MANAGER = "user_manager";
    public static final String XPOSED_MANAGER = "xposed_manager";
    public static final String ACCOUNT_MANAGER = "account_manager";
    public static final String LOCATION_MANAGER = "location_manager";
    public static final String NOTIFICATION_MANAGER = "notification_manager";

    private final Map<String, IBinder> mCaches = new HashMap<>();

    public static ServiceManager get() {
        if (sServiceManager == null) {
            synchronized (ServiceManager.class) {
                if (sServiceManager == null) {
                    sServiceManager = new ServiceManager();
                }
            }
        }
        return sServiceManager;
    }

    public static IBinder getService(String name) {
        return get().getServiceInternal(name);
    }

    private ServiceManager() {
        mCaches.put(ACTIVITY_MANAGER, BActivityManagerService.get());
        mCaches.put(JOB_MANAGER, BJobManagerService.get());
        mCaches.put(PACKAGE_MANAGER, BPackageManagerService.get());
        mCaches.put(STORAGE_MANAGER, BStorageManagerService.get());
        mCaches.put(USER_MANAGER, BUserManagerService.get());
        mCaches.put(XPOSED_MANAGER, BXposedManagerService.get());
        mCaches.put(ACCOUNT_MANAGER, BAccountManagerService.get());
        mCaches.put(LOCATION_MANAGER, BLocationManagerService.get());
        mCaches.put(NOTIFICATION_MANAGER, BNotificationManagerService.get());
    }

    public IBinder getServiceInternal(String name) {
        return mCaches.get(name);
    }

    /**
     * Warm virtual services only in virtual-app processes.
     *
     * <p>The host/loader main process does not need all nine Binder services just to draw its
     * splash/login UI. Eagerly resolving every service performs synchronous provider/Binder calls
     * during Application.onCreate() and can stall startup while the server process is coming up.
     * BlackManager already resolves each service lazily when a loader action actually needs it.</p>
     */
    public static void initBlackManager() {
        if (BlackBoxCore.get().isMainProcess()) {
            return;
        }

        BlackBoxCore.get().getService(ACTIVITY_MANAGER);
        BlackBoxCore.get().getService(JOB_MANAGER);
        BlackBoxCore.get().getService(PACKAGE_MANAGER);
        BlackBoxCore.get().getService(STORAGE_MANAGER);
        BlackBoxCore.get().getService(USER_MANAGER);
        BlackBoxCore.get().getService(XPOSED_MANAGER);
        BlackBoxCore.get().getService(ACCOUNT_MANAGER);
        BlackBoxCore.get().getService(LOCATION_MANAGER);
        BlackBoxCore.get().getService(NOTIFICATION_MANAGER);
    }
}
