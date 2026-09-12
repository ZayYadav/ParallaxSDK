package com.Parallax.SDK.core.core.system.pm.installer;

import com.Parallax.SDK.core.core.env.ParallaxEnvironment;
import com.Parallax.SDK.core.core.system.pm.ParallaxPackageSettings;
import com.Parallax.SDK.core.entity.pm.ParallaxInstallOption;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;

public class ParallaxRemoveUserExecutor implements ParallaxExecutor {
    public int exec(ParallaxPackageSettings ps, ParallaxInstallOption option, int userId) {
        String packageName = ps.pkg.packageName;
        ParallaxFileUtils.deleteDir(ParallaxEnvironment.getDataDir(packageName, userId));
        ParallaxFileUtils.deleteDir(ParallaxEnvironment.getDeDataDir(packageName, userId));
        //ParallaxFileUtils.deleteDir(ParallaxEnvironment.getExternalDataDir(packageName));
        ParallaxFileUtils.deleteDir(ParallaxEnvironment.getExternalObbDir(packageName));
        return 0;
    }
}

