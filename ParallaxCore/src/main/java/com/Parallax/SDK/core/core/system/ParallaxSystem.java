package com.Parallax.SDK.core.core.system;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.core.env.ParallaxAppSystemEnv;
import com.Parallax.SDK.core.core.env.ParallaxEnvironment;
import com.Parallax.SDK.core.core.system.accounts.ParallaxAccountManagerService;
import com.Parallax.SDK.core.core.system.am.ParallaxActivityManagerService;
import com.Parallax.SDK.core.core.system.am.ParallaxJobManagerService;
import com.Parallax.SDK.core.core.system.location.ParallaxLocationManagerService;
import com.Parallax.SDK.core.core.system.notification.ParallaxNotificationManagerService;
import com.Parallax.SDK.core.core.system.os.ParallaxStorageManagerService;
import com.Parallax.SDK.core.core.system.pm.ParallaxPackageInstallerService;
import com.Parallax.SDK.core.core.system.pm.ParallaxPackageManagerService;
import com.Parallax.SDK.core.core.system.pm.ParallaxXposedManagerService;
import com.Parallax.SDK.core.core.system.user.ParallaxUserHandle;
import com.Parallax.SDK.core.core.system.user.ParallaxUserManagerService;
import com.Parallax.SDK.core.entity.pm.ParallaxInstallOption;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;

public class ParallaxSystem {

    private static volatile ParallaxSystem sBlackBoxSystem;
    private final List<IParallaxSystemService> mServices = new ArrayList<>();
    private static final AtomicBoolean isStartup = new AtomicBoolean(false);

    private ParallaxSystem() { }

    public static ParallaxSystem getSystem() {
        if (sBlackBoxSystem == null) {
            synchronized (ParallaxSystem.class) {
                if (sBlackBoxSystem == null) {
                    sBlackBoxSystem = new ParallaxSystem();
                }
            }
        }
        return sBlackBoxSystem;
    }

    public void startup() {
        if (isStartup.getAndSet(true)) {
            return;
        }
        // Load virtual environment
        ParallaxEnvironment.load();
        // Register core system services
        mServices.add(ParallaxPackageManagerService.get());
        mServices.add(ParallaxUserManagerService.get());
        mServices.add(ParallaxActivityManagerService.get());
        mServices.add(ParallaxJobManagerService.get());
        mServices.add(ParallaxStorageManagerService.get());
        mServices.add(ParallaxPackageInstallerService.get());
        mServices.add(ParallaxXposedManagerService.get());
        mServices.add(ParallaxProcessManagerService.get());
        mServices.add(ParallaxAccountManagerService.get());
        mServices.add(ParallaxLocationManagerService.get());
        mServices.add(ParallaxNotificationManagerService.get());
        // Notify system ready
        for (IParallaxSystemService service : mServices) {
            try {
                service.systemReady();
            } catch (Throwable ignored) {
                // Never break virtual startup
            }
        }

        // Pre-install system apps
        List<String> preInstallPackages = ParallaxAppSystemEnv.getPreInstallPackages();
        for (String pkg : preInstallPackages) {
            try {
                if (!ParallaxPackageManagerService.get().isInstalled(pkg, ParallaxUserHandle.USER_ALL)) {
                    PackageInfo info = ParallaxCore.getPackageManager().getPackageInfo(pkg, 0);
                    ParallaxPackageManagerService.get().installPackageAsUser(info.applicationInfo.sourceDir,ParallaxInstallOption.installBySystem(),ParallaxUserHandle.USER_ALL);
                }
            } catch (PackageManager.NameNotFoundException ignored) {
            } catch (Throwable ignored) {
            }
        }
        // Init jar environment (SAFE)
        //initJarEnv();
    }
    
    private void initJarEnv() {
        // OPTIONAL: junit.jar (ignore if missing)
        try {
            InputStream junit = ParallaxCore.getContext().getAssets().open("junit.jar");
            ParallaxFileUtils.copyFile(junit,com.Parallax.SDK.runtime.ParallaxRemoteManager.JUNIT_JAR);
        } catch (Throwable ignored) {
            // junit.jar not present → safe to ignore
        }

        // REQUIRED: empty.jar
        try {
            InputStream empty = ParallaxCore.getContext().getAssets().open("empty.jar");
            ParallaxFileUtils.copyFile(empty,com.Parallax.SDK.runtime.ParallaxRemoteManager.EMPTY_JAR);
        } catch (Throwable e) {
            // empty.jar missing is a REAL problem
            e.printStackTrace();
        }
    }
}