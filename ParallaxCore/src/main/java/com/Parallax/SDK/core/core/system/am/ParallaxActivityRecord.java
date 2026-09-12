package com.Parallax.SDK.core.core.system.am;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Binder;
import android.os.IBinder;

import java.util.UUID;
import com.Parallax.SDK.core.core.system.ParallaxProcessRecord;


/**
 * Created by @RIYAZXERO on 4/9/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxActivityRecord extends Binder {
    public ParallaxTaskRecord task;
    public IBinder token;
    public IBinder resultTo;
    public ActivityInfo info;
    public ComponentName component;
    public Intent intent;
    public int userId;
    public boolean finished;
    public ParallaxProcessRecord processRecord;

    public static ParallaxActivityRecord create(Intent intent, ActivityInfo info, IBinder resultTo, int userId) {
        ParallaxActivityRecord record = new ParallaxActivityRecord();
        record.intent = intent;
        record.info = info;
        record.component = new ComponentName(info.packageName, info.name);
        record.resultTo = resultTo;
        record.userId = userId;
        return record;
    }


}