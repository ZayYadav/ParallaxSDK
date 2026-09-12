package com.Parallax.SDK.core.jnihook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.Parallax.SDK.core.jnihook.jni.ParallaxJniHook;

/**
 * Created by @RIYAZXERO on 3/7/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxReflectCore {

    public static void set(Class<?> clazz) {
        try {
            Field accessFlags = Class.class.getDeclaredField("accessFlags");
            accessFlags.setAccessible(true);
            int o = (int) accessFlags.get(clazz);
            accessFlags.set(clazz, o | 0x0001);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        for (Method declaredMethod : clazz.getDeclaredMethods()) {
            ParallaxJniHook.setAccessible(clazz, declaredMethod);
        }
        for (Field declaredField : clazz.getDeclaredFields()) {
            ParallaxJniHook.setAccessible(clazz, declaredField);
        }
        for (Class<?> declaredClass : clazz.getDeclaredClasses()) {
            set(declaredClass);
        }
    }
}
