package com.Parallax.SDK.core.proxy;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.proxy.record.ParallaxProxyPendingRecord;
import com.Parallax.SDK.core.utils.ParallaxSlog;

/**
 * Created by Milk on 3/28/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxProxyPendingActivity extends Activity {
    public static final String TAG = "ParallaxProxyPendingActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
        ParallaxProxyPendingRecord pendingActivityRecord = ParallaxProxyPendingRecord.create(getIntent());
        ParallaxSlog.d(TAG, "ParallaxProxyPendingActivity: " + pendingActivityRecord);
        if (pendingActivityRecord.mTarget == null)
            return;
        pendingActivityRecord.mTarget.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        pendingActivityRecord.mTarget.setExtrasClassLoader(ParallaxActivityThread.getApplication().getClassLoader());
        startActivity(pendingActivityRecord.mTarget);
    }

    public static class P0 extends ParallaxProxyPendingActivity { }

    public static class P1 extends ParallaxProxyPendingActivity { }

    public static class P2 extends ParallaxProxyPendingActivity { }

    public static class P3 extends ParallaxProxyPendingActivity { }

    public static class P4 extends ParallaxProxyPendingActivity { }

    public static class P5 extends ParallaxProxyPendingActivity { }

    public static class P6 extends ParallaxProxyPendingActivity { }

    public static class P7 extends ParallaxProxyPendingActivity { }

    public static class P8 extends ParallaxProxyPendingActivity { }

    public static class P9 extends ParallaxProxyPendingActivity { }

    public static class P10 extends ParallaxProxyPendingActivity { }

    public static class P11 extends ParallaxProxyPendingActivity { }

    public static class P12 extends ParallaxProxyPendingActivity { }

    public static class P13 extends ParallaxProxyPendingActivity { }

    public static class P14 extends ParallaxProxyPendingActivity { }

    public static class P15 extends ParallaxProxyPendingActivity { }

    public static class P16 extends ParallaxProxyPendingActivity { }

    public static class P17 extends ParallaxProxyPendingActivity { }

    public static class P18 extends ParallaxProxyPendingActivity { }

    public static class P19 extends ParallaxProxyPendingActivity { }

    public static class P20 extends ParallaxProxyPendingActivity { }

    public static class P21 extends ParallaxProxyPendingActivity { }

    public static class P22 extends ParallaxProxyPendingActivity { }

    public static class P23 extends ParallaxProxyPendingActivity { }

    public static class P24 extends ParallaxProxyPendingActivity { }

    
}
