package com.Parallax.SDK.core.core.system.pm.installer;

import com.Parallax.SDK.core.core.env.ParallaxEnvironment;
import com.Parallax.SDK.core.core.system.pm.ParallaxPackageSettings;
import com.Parallax.SDK.core.entity.pm.ParallaxInstallOption;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;


public class ParallaxCreatePackageExecutor implements ParallaxExecutor {
    public int exec(ParallaxPackageSettings ps, ParallaxInstallOption option, int userId) {
        ParallaxFileUtils.deleteDir(ParallaxEnvironment.getAppDir(ps.pkg.packageName));
        ParallaxFileUtils.mkdirs(ParallaxEnvironment.getAppDir(ps.pkg.packageName));
        ParallaxFileUtils.mkdirs(ParallaxEnvironment.getAppLibDir(ps.pkg.packageName));
        return 0;
    }
}
