package com.Parallax.SDK.core.core.system.am;

import android.os.IBinder;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Milk on 4/25/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxUserSpace {
    public final ParallaxActiveServices mActiveServices = new ParallaxActiveServices();
    public final ParallaxActivityStack mStack = new ParallaxActivityStack();
    public final Map<IBinder, ParallaxPendingIntentRecord> mIntentSenderRecords = new HashMap<>();
}
