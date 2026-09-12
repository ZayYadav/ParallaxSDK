package com.Parallax.SDK.core.proxy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.entity.am.ParallaxPendingResultData;
import com.Parallax.SDK.core.proxy.record.ParallaxProxyBroadcastRecord;

/**
 * Created by BlackBox on 2022/2/25.
 */
public class ParallaxProxyBroadcastReceiver extends BroadcastReceiver {
    public static final String TAG = "ParallaxProxyBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        intent.setExtrasClassLoader(context.getClassLoader());
        ParallaxProxyBroadcastRecord record = ParallaxProxyBroadcastRecord.create(intent);
        if (record.mIntent == null) {
            return;
        }
        PendingResult pendingResult = goAsync();
        try {
            ParallaxCore.getBActivityManager().scheduleBroadcastReceiver(record.mIntent, new ParallaxPendingResultData(pendingResult), record.mUserId);
        } catch (RemoteException e) {
            pendingResult.finish();
        }
    }
}