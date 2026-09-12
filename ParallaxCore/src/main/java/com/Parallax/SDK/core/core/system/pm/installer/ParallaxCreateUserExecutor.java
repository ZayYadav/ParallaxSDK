package com.Parallax.SDK.core.core.system.pm.installer;

import com.Parallax.SDK.core.core.env.ParallaxEnvironment;
import com.Parallax.SDK.core.core.system.pm.ParallaxPackageSettings;
import com.Parallax.SDK.core.entity.pm.ParallaxInstallOption;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;

public class ParallaxCreateUserExecutor implements ParallaxExecutor {
    public int exec(ParallaxPackageSettings ps, ParallaxInstallOption option, int userId) {
        String packageName = ps.pkg.packageName;
        ParallaxFileUtils.mkdirs(ParallaxEnvironment.getDataDir(packageName, userId));
        ParallaxFileUtils.mkdirs(ParallaxEnvironment.getDeDataDir(packageName, userId));
        ParallaxFileUtils.mkdirs(ParallaxEnvironment.getDataCacheDir(packageName, userId));
        ParallaxFileUtils.mkdirs(ParallaxEnvironment.getDataDatabasesDir(packageName, userId));
        ParallaxFileUtils.mkdirs(ParallaxEnvironment.getDataFilesDir(packageName, userId));
        ParallaxFileUtils.mkdirs(ParallaxEnvironment.getExternalDataCacheDir(packageName));
        ParallaxFileUtils.mkdirs(ParallaxEnvironment.getExternalDataFilesDir(packageName));
        return 0;
    }
}
