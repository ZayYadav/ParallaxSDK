// IParallaxPackageInstallerService.aidl
package com.Parallax.SDK.core.core.system.pm;

import com.Parallax.SDK.core.core.system.pm.ParallaxPackageSettings;
import com.Parallax.SDK.core.entity.pm.ParallaxInstallOption;

// Declare any non-default types here with import statements

interface IParallaxPackageInstallerService {
    int installPackageAsUser(in ParallaxPackageSettings ps, int userId);
    int uninstallPackageAsUser(in ParallaxPackageSettings ps, boolean removeApp, int userId);
    int clearPackage(in ParallaxPackageSettings ps, int userId);
    int updatePackage(in ParallaxPackageSettings ps);
}
