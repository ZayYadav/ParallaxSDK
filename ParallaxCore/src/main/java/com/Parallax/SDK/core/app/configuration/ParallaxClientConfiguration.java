package com.Parallax.SDK.core.app.configuration;

import java.io.File;

public abstract class ParallaxClientConfiguration {
    
    public abstract String getHostPackageName();
    
    public boolean setHideRoot() {
        return false;
    }
    
    public boolean isEnableDaemonService() {
        return true;
    }

    public boolean isEnableLauncherActivity() {
        return true;
    }

    public boolean requestInstallPackage(File file) {
        return false;
    }
}
