package net_62v.external;

import android.os.Environment;
import java.io.File;
import top.niunaijun.blackbox.utils.FileUtils;
import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class MetaStorageManager {
    
    public static File obtainAppExternalStorageDir() {
        File containerPath = Environment.getExternalStorageDirectory();
        FileUtils.mkdirs(containerPath);
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