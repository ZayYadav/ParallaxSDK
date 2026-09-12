// IParallaxXposedManagerService.aidl

package com.Parallax.SDK.core.core.system.pm;

import java.util.List;
import com.Parallax.SDK.core.entity.pm.ParallaxInstalledModule;

interface IParallaxXposedManagerService {
    boolean isXPEnable();
    void setXPEnable(boolean enable);
    boolean isModuleEnable(String packageName);
    void setModuleEnable(String packageName, boolean enable);
    List<ParallaxInstalledModule> getInstalledModules();
}