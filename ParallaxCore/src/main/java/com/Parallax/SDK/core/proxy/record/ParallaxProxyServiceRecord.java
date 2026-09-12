package com.Parallax.SDK.core.proxy.record;

import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

import com.Parallax.SDK.core.utils.compat.ParallaxBundleCompat;

/**
 * Created by Milk on 4/1/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxProxyServiceRecord {
    public Intent mServiceIntent;
    public ServiceInfo mServiceInfo;
    public IBinder mToken;
    public int mUserId;
    public int mStartId;

    public ParallaxProxyServiceRecord(Intent serviceIntent, ServiceInfo serviceInfo, IBinder token, int userId, int startId) {
        mServiceIntent = serviceIntent;
        mServiceInfo = serviceInfo;
        mUserId = userId;
        mStartId = startId;
        mToken = token;
    }

    public static void saveStub(Intent shadow, Intent target, ServiceInfo serviceInfo, IBinder token, int userId, int startId) {
        shadow.putExtra("_G_|_target_", target);
        shadow.putExtra("_G_|_service_info_", serviceInfo);
        shadow.putExtra("_G_|_user_id_", userId);
        shadow.putExtra("_G_|_start_id_", startId);
        ParallaxBundleCompat.putBinder(shadow, "_G_|_token_", token);
    }

    public static ParallaxProxyServiceRecord create(Intent intent) {
        Intent target = intent.getParcelableExtra("_G_|_target_");
        ServiceInfo serviceInfo = intent.getParcelableExtra("_G_|_service_info_");
        int userId = intent.getIntExtra("_G_|_user_id_", 0);
        int startId = intent.getIntExtra("_G_|_start_id_", 0);
        IBinder token = ParallaxBundleCompat.getBinder(intent, "_G_|_token_");
        return new ParallaxProxyServiceRecord(target, serviceInfo, token, userId, startId);
    }
}
