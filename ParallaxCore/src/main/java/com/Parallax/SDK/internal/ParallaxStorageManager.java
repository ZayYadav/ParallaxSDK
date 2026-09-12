package com.Parallax.SDK.internal;

import android.os.Environment;
import java.io.File;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;
import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class ParallaxStorageManager {
    
    public static File obtainAppExternalStorageDir() {
        File containerPath = Environment.getExternalStorageDirectory();
        ParallaxFileUtils.mkdirs(containerPath);
        return containerPath;
    }
    
    public static File getObbContainerPath(String packageName) {
        try {
            return new File(obtainAppExternalStorageDir() + "/Android/obb", packageName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static File getDataContainerPath(String packageName) {
        try {
            return new File(obtainAppExternalStorageDir() + "/Android/data", packageName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}