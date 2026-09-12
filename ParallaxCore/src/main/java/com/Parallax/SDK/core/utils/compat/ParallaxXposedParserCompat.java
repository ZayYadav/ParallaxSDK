package com.Parallax.SDK.core.utils.compat;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.entity.pm.ParallaxInstalledModule;
import com.Parallax.SDK.core.utils.ParallaxCloseUtils;

/**
 * Created by @RIYAZXERO on 5/2/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxXposedParserCompat {

    public static ParallaxInstalledModule parseModule(ApplicationInfo applicationInfo) {
        try {
            PackageManager packageManager = ParallaxCore.getPackageManager();
            ParallaxInstalledModule module = new ParallaxInstalledModule();
            module.packageName = applicationInfo.packageName;
            module.enable = false;
            module.desc = applicationInfo.metaData.getString("xposeddescription");
            module.name = applicationInfo.loadLabel(packageManager).toString();
            module.main = readMain(applicationInfo.sourceDir);
            return module;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static boolean isXPModule(String file) {
        try {
            String s = readMain(file);
            return s != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String readMain(String apk) {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(new File(apk));
            ZipEntry entry = zipFile.getEntry("assets/xposed_init");
            if (entry == null) {
                throw new RuntimeException();
            }
            return getInputStreamContent(zipFile.getInputStream(entry)).trim();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            ParallaxCloseUtils.close(zipFile);
        }
        return null;
    }

    private static String getInputStreamContent(InputStream stream) {
        BufferedReader reader = null;
        StringBuilder builder = new StringBuilder();
        try {
            reader = new BufferedReader(new InputStreamReader(stream));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#"))
                    continue;
                builder.append(line).append("\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ParallaxCloseUtils.close(reader);
        }
        return builder.toString();
    }
}
