package com.Parallax.SDK.core.utils.compat;

import java.util.ArrayList;
import java.util.List;

import dalvik.system.DexFile;
import com.Parallax.SDK.core.utils.ParallaxReflector;

/**
 * Created by Milk on 2021/5/16.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxDexFileCompat {

    public static List<Long> getCookies(ClassLoader classLoader) {
        List<Long> cookies = new ArrayList<>();
        List<DexFile> dexFiles = getDexFiles(classLoader);
        for (DexFile dexFile : dexFiles) {
            cookies.addAll(getCookies(dexFile));
        }
        return cookies;
    }

    public static List<Long> getCookies(DexFile dexFile) {
        List<Long> cookies = new ArrayList<>();
        if (dexFile == null)
            return cookies;
        try {
            Object object = ParallaxReflector.with(dexFile)
                    .field("mCookie")
                    .get();
            if (ParallaxBuildCompat.isM()) {
                for (long l : (long[]) object) {
                    cookies.add(l);
                }
            } else {
                cookies.add((long) object);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cookies;
    }

    private static List<DexFile> getDexFiles(ClassLoader classLoader) {
        List<DexFile> dexFiles = new ArrayList<>();
        Object[] dexElements = getDexElements(classLoader);
        for (Object dexElement : dexElements) {
            try {
                dexFiles.add(ParallaxReflector.with(dexElement)
                        .field("dexFile")
                        .<DexFile>get());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return dexFiles;
    }

    private static Object[] getDexElements(ClassLoader classLoader) {
        Object dexPathList = getDexPathList(classLoader);
        if (dexPathList == null) {
            return new Object[]{};
        }
        try {
            return ParallaxReflector.with(dexPathList)
                    .field("dexElements")
                    .get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Object[]{};
    }

    private static Object getDexPathList(ClassLoader classLoader) {
        try {
            return ParallaxReflector.on("dalvik.system.BaseDexClassLoader")
                    .field("pathList")
                    .get(classLoader);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
