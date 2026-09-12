package com.Parallax.SDK.core.proxy.record;

import android.content.Intent;

/**
 * Created by @RIYAZXERO on 3/31/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxProxyPendingRecord {
    public int mUserId;
    public Intent mTarget;

    public ParallaxProxyPendingRecord(Intent target, int userId) {
        mUserId = userId;
        mTarget = target;
    }

    public static void saveStub(Intent shadow, Intent target, int userId) {
        shadow.putExtra("_G_|_P_user_id_", userId);
        shadow.putExtra("_G_|_P_target_", target);
    }

    public static ParallaxProxyPendingRecord create(Intent intent) {
        int userId = intent.getIntExtra("_G_|_P_user_id_", 0);
        Intent target = intent.getParcelableExtra("_G_|_P_target_");
        return new ParallaxProxyPendingRecord(target, userId);
    }

    @Override
    public String toString() {
        return "ProxyPendingActivityRecord{" + "mUserId=" + mUserId + ", mTarget=" + mTarget + '}';
    }
}
