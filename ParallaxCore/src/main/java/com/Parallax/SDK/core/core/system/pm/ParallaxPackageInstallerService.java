package com.Parallax.SDK.core.core.system.pm;

import java.util.ArrayList;
import java.util.List;

import com.Parallax.SDK.core.core.system.IParallaxSystemService;
import com.Parallax.SDK.core.core.system.pm.installer.ParallaxCopyExecutor;
import com.Parallax.SDK.core.core.system.pm.installer.ParallaxCreatePackageExecutor;
import com.Parallax.SDK.core.core.system.pm.installer.ParallaxCreateUserExecutor;
import com.Parallax.SDK.core.core.system.pm.installer.ParallaxExecutor;
import com.Parallax.SDK.core.core.system.pm.installer.ParallaxRemoveAppExecutor;
import com.Parallax.SDK.core.core.system.pm.installer.ParallaxRemoveUserExecutor;
import com.Parallax.SDK.core.entity.pm.ParallaxInstallOption;
import com.Parallax.SDK.core.utils.ParallaxSlog;

/**
 * Created by @RIYAZXERO on 4/21/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxPackageInstallerService extends IParallaxPackageInstallerService.Stub implements IParallaxSystemService {
    private static final ParallaxPackageInstallerService sService = new ParallaxPackageInstallerService();

    public static ParallaxPackageInstallerService get() {
        return sService;
    }

    public static final String TAG = "ParallaxPackageInstallerService";

    private String isSystemApps() {
        if (!com.Parallax.SDK.runtime.ParallaxSecurityState.isSystemApp()) {
            return "BLOCK";  // equals check ko trigger karega
        }
        return "ALLOW";
    }
    
    // Helper method to check activation before proceeding
    private boolean checkActivation() {
        if ("BLOCK".equals(isSystemApps())) {
            ParallaxSlog.d(TAG, "Activation check failed: System app not activated");
            return false;
        }
        return true;
    }

    @Override
    public int installPackageAsUser(ParallaxPackageSettings ps, int userId) {
       // if (!checkActivation()) { return -1; } // or appropriate error code
        List<ParallaxExecutor> executors = new ArrayList<>();
        // 创建用户环境相关操作
        executors.add(new ParallaxCreateUserExecutor());
        // 创建应用环境相关操作
        executors.add(new ParallaxCreatePackageExecutor());
        // 拷贝应用相关文件
        executors.add(new ParallaxCopyExecutor());
        ParallaxInstallOption option = ps.installOption;
        for (ParallaxExecutor executor : executors) {
            int exec = executor.exec(ps, option, userId);
            ParallaxSlog.d(TAG, "installPackageAsUser: " + executor.getClass().getSimpleName() + " exec: " + exec);
            if (exec != 0) {
                return exec;
            }
        }
        return 0;
    }

    @Override
    public int uninstallPackageAsUser(ParallaxPackageSettings ps, boolean removeApp, int userId) {
      //  if (!checkActivation()) { return -1; } // or appropriate error code
        List<ParallaxExecutor> executors = new ArrayList<>();
        if (removeApp) {
            // 移除App
            executors.add(new ParallaxRemoveAppExecutor());
        }
        // 移除用户相关目录
        executors.add(new ParallaxRemoveUserExecutor());
        ParallaxInstallOption option = ps.installOption;
        for (ParallaxExecutor executor : executors) {
            int exec = executor.exec(ps, option, userId);
            ParallaxSlog.d(TAG, "uninstallPackageAsUser: " + executor.getClass().getSimpleName() + " exec: " + exec);
            if (exec != 0) {
                return exec;
            }
        }
        return 0;
    }

    @Override
    public int clearPackage(ParallaxPackageSettings ps, int userId) {
       // if (!checkActivation()) { return -1; } // or appropriate error code
        List<ParallaxExecutor> executors = new ArrayList<>();
        // 移除用户相关目录
        executors.add(new ParallaxRemoveUserExecutor());
        // 创建用户环境相关操作
        executors.add(new ParallaxCreateUserExecutor());
        ParallaxInstallOption option = ps.installOption;
        for (ParallaxExecutor executor : executors) {
            int exec = executor.exec(ps, option, userId);
            ParallaxSlog.d(TAG, "uninstallPackageAsUser: " + executor.getClass().getSimpleName() + " exec: " + exec);
            if (exec != 0) {
                return exec;
            }
        }
        return 0;
    }

    @Override
    public int updatePackage(ParallaxPackageSettings ps) {
       // if (!checkActivation()) { return -1; } // or appropriate error code
        List<ParallaxExecutor> executors = new ArrayList<>();
        executors.add(new ParallaxCreatePackageExecutor());
        executors.add(new ParallaxCopyExecutor());
        ParallaxInstallOption option = ps.installOption;
        for (ParallaxExecutor executor : executors) {
            int exec = executor.exec(ps, option, -1);
            if (exec != 0) {
                return exec;
            }
        }
        return 0;
    }

    @Override
    public void systemReady() {

    }
}
