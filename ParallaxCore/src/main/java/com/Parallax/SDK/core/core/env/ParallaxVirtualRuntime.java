package com.Parallax.SDK.core.core.env;

import android.content.pm.ApplicationInfo;

import com.Parallax.SDK.mirror.android.ddm.BRDdmHandleAppName;
import com.Parallax.SDK.mirror.android.os.BRProcess;

public class ParallaxVirtualRuntime {

    private static String sInitialPackageName;
    private static String sProcessName;

    public static String getProcessName() {
        return sProcessName;
    }

    public static String getInitialPackageName() {
        return sInitialPackageName;
    }

    public static void setupRuntime(String processName, ApplicationInfo appInfo) {
        if (sProcessName != null) {
            return;
        }
        sInitialPackageName = appInfo.packageName;
        sProcessName = processName;
        BRProcess.get().setArgV0(processName);
        BRDdmHandleAppName.get().setAppName(processName, 0);
    }
}
