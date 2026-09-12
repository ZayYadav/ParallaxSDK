package com.Parallax.SDK.core.core.system.am;

import android.content.Intent;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by Milk on 4/9/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxTaskRecord {
    public int id;
    public int userId;
    public String taskAffinity;
    public Intent rootIntent;
    public final List<ParallaxActivityRecord> activities = new LinkedList<>();

    public ParallaxTaskRecord(int id, int userId, String taskAffinity) {
        this.id = id;
        this.userId = userId;
        this.taskAffinity = taskAffinity;
    }

    public boolean needNewTask() {
        for (ParallaxActivityRecord activity : activities) {
            if (!activity.finished) {
                return false;
            }
        }
        return true;
    }

    public void addTopActivity(ParallaxActivityRecord record) {
        activities.add(record);
    }

    public void removeActivity(ParallaxActivityRecord record) {
        activities.remove(record);
    }

    public ParallaxActivityRecord getTopActivityRecord() {
        for (int i = activities.size() - 1; i >= 0; i--) {
            ParallaxActivityRecord activityRecord = activities.get(i);
            if (!activityRecord.finished) {
                return activityRecord;
            }
        }
        return null;
    }
}
