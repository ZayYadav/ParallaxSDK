package com.Parallax.SDK.core.fake;

import com.Parallax.SDK.core.jnihook.ParallaxReflectCore;

/**
 * Created by @RIYAZXERO on 3/7/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxFakeCore {
    public static void init() {
        ParallaxReflectCore.set(android.app.ActivityThread.class);
    }
}
