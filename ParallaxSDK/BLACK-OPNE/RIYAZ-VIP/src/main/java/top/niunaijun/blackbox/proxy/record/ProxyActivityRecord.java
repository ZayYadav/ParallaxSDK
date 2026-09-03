package top.niunaijun.blackbox.proxy.record;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.IBinder;

import top.niunaijun.blackbox.utils.compat.BundleCompat;
import top.niunaijun.blackbox.utils.compat.IntentRedirectCompat;

/**
 * Created by @RIYAZXERO on 3/31/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ProxyActivityRecord {
    public int mUserId;
    public ActivityInfo mActivityInfo;
    public Intent mTarget;
    public IBinder mActivityRecord;

    public ProxyActivityRecord(int userId, ActivityInfo activityInfo, Intent target, IBinder activityRecord) {
        mUserId = userId;
        mActivityInfo = activityInfo;
        mTarget = target;
        mActivityRecord = activityRecord;
    }

    public static void saveStub(Intent shadow, Intent target, ActivityInfo activityInfo, IBinder activityRecord, int userId) {
        shadow.putExtra("_G_|_user_id_", userId);
        shadow.putExtra("_G_|_activity_info_", activityInfo);
        shadow.putExtra("_G_|_target_", target);
        BundleCompat.putBinder(shadow, "_G_|_activity_record_v_", activityRecord);

        // Android 16's intent-redirection hardening expects the top-level
        // outgoing Intent to identify every nested Intent extra before it is
        // handed to system_server. Shadow Intents are sometimes created below
        // Instrumentation's normal prepare-to-leave-process path, so collect the
        // nested target key here without opting out of launch protection.
        IntentRedirectCompat.collectNestedIntentKeys(shadow);
    }

    public static ProxyActivityRecord create(Intent intent) {
        int userId = intent.getIntExtra("_G_|_user_id_", 0);
        ActivityInfo activityInfo = intent.getParcelableExtra("_G_|_activity_info_");
        Intent target = intent.getParcelableExtra("_G_|_target_");
        IBinder activityRecord = BundleCompat.getBinder(intent, "_G_|_activity_record_v_");
        return new ProxyActivityRecord(userId, activityInfo, target, activityRecord);
    }
}
