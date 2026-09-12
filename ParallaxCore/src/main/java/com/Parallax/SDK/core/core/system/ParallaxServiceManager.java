package com.Parallax.SDK.core.core.system;

import android.os.IBinder;

import java.util.HashMap;
import java.util.Map;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.core.system.accounts.ParallaxAccountManagerService;
import com.Parallax.SDK.core.core.system.am.ParallaxActivityManagerService;
import com.Parallax.SDK.core.core.system.am.ParallaxJobManagerService;
import com.Parallax.SDK.core.core.system.location.ParallaxLocationManagerService;
import com.Parallax.SDK.core.core.system.notification.ParallaxNotificationManagerService;
import com.Parallax.SDK.core.core.system.os.ParallaxStorageManagerService;
import com.Parallax.SDK.core.core.system.pm.ParallaxPackageManagerService;
import com.Parallax.SDK.core.core.system.pm.ParallaxXposedManagerService;
import com.Parallax.SDK.core.core.system.user.ParallaxUserManagerService;

/**
 * Created by Milk on 3/31/21.
 */
public class ParallaxServiceManager {
    private static ParallaxServiceManager sServiceManager = null;
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

    public static ParallaxServiceManager get() {
        if (sServiceManager == null) {
            synchronized (ParallaxServiceManager.class) {
                if (sServiceManager == null) {
                    sServiceManager = new ParallaxServiceManager();
                }
            }
        }
        return sServiceManager;
    }

    public static IBinder getService(String name) {
        return get().getServiceInternal(name);
    }

    private ParallaxServiceManager() {
        mCaches.put(ACTIVITY_MANAGER, ParallaxActivityManagerService.get());
        mCaches.put(JOB_MANAGER, ParallaxJobManagerService.get());
        mCaches.put(PACKAGE_MANAGER, ParallaxPackageManagerService.get());
        mCaches.put(STORAGE_MANAGER, ParallaxStorageManagerService.get());
        mCaches.put(USER_MANAGER, ParallaxUserManagerService.get());
        mCaches.put(XPOSED_MANAGER, ParallaxXposedManagerService.get());
        mCaches.put(ACCOUNT_MANAGER, ParallaxAccountManagerService.get());
        mCaches.put(LOCATION_MANAGER, ParallaxLocationManagerService.get());
        mCaches.put(NOTIFICATION_MANAGER, ParallaxNotificationManagerService.get());
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
        if (ParallaxCore.get().isMainProcess()) {
            return;
        }

        ParallaxCore.get().getService(ACTIVITY_MANAGER);
        ParallaxCore.get().getService(JOB_MANAGER);
        ParallaxCore.get().getService(PACKAGE_MANAGER);
        ParallaxCore.get().getService(STORAGE_MANAGER);
        ParallaxCore.get().getService(USER_MANAGER);
        ParallaxCore.get().getService(XPOSED_MANAGER);
        ParallaxCore.get().getService(ACCOUNT_MANAGER);
        ParallaxCore.get().getService(LOCATION_MANAGER);
        ParallaxCore.get().getService(NOTIFICATION_MANAGER);
    }
}
