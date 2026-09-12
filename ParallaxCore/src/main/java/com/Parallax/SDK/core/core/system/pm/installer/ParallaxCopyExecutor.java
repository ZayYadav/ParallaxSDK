package com.Parallax.SDK.core.core.system.pm.installer;


import java.io.File;
import java.io.IOException;

import com.Parallax.SDK.core.core.env.ParallaxEnvironment;
import com.Parallax.SDK.core.core.system.pm.ParallaxPackageSettings;
import com.Parallax.SDK.core.entity.pm.ParallaxInstallOption;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;
import com.Parallax.SDK.core.utils.ParallaxNativeUtils;

/**
 * Created by Milk on 4/24/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 * 拷贝文件相关
 */
public class ParallaxCopyExecutor implements ParallaxExecutor {

    @Override
    public int exec(ParallaxPackageSettings ps, ParallaxInstallOption option, int userId) {
        try {
            if (!option.isFlag(ParallaxInstallOption.FLAG_SYSTEM)) {
                ParallaxNativeUtils.copyNativeLib(new File(ps.pkg.baseCodePath), ParallaxEnvironment.getAppLibDir(ps.pkg.packageName));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        if (option.isFlag(ParallaxInstallOption.FLAG_STORAGE)) {
            // 外部安装
            File origFile = new File(ps.pkg.baseCodePath);
            File newFile = ParallaxEnvironment.getBaseApkDir(ps.pkg.packageName);
            try {
                if (option.isFlag(ParallaxInstallOption.FLAG_URI_FILE)) {
                    boolean b = ParallaxFileUtils.renameTo(origFile, newFile);
                    if (!b) {
                        ParallaxFileUtils.copyFile(origFile, newFile);
                    }
                } else {
                    ParallaxFileUtils.copyFile(origFile, newFile);
                }
                // update baseCodePath
                ps.pkg.baseCodePath = newFile.getAbsolutePath();
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
        } else if (option.isFlag(ParallaxInstallOption.FLAG_SYSTEM)) {
            // 系统安装
        }
        return 0;
    }
}
