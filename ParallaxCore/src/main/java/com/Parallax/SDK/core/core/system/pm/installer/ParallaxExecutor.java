package com.Parallax.SDK.core.core.system.pm.installer;

import com.Parallax.SDK.core.core.system.pm.ParallaxPackageSettings;
import com.Parallax.SDK.core.entity.pm.ParallaxInstallOption;

public interface ParallaxExecutor {
    public static final String TAG = "InstallExecutor";

    int exec(ParallaxPackageSettings bPackageSettings, ParallaxInstallOption installOption, int i);
}
