package com.Parallax.SDK.core.fake.delegate;

import android.content.IIntentReceiver;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import com.Parallax.SDK.mirror.android.content.BRIIntentReceiver;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.proxy.record.ParallaxProxyBroadcastRecord;

public class ParallaxInnerReceiverDelegate extends IIntentReceiver.Stub {
    public static final String TAG = "ParallaxInnerReceiverDelegate";

    private static final Map<IBinder, ParallaxInnerReceiverDelegate> sInnerReceiverDelegate = new HashMap<>();
    private final WeakReference<IIntentReceiver> mIntentReceiver;

    private ParallaxInnerReceiverDelegate(IIntentReceiver iIntentReceiver) {
        this.mIntentReceiver = new WeakReference<>(iIntentReceiver);
    }

    public static ParallaxInnerReceiverDelegate getDelegate(IBinder iBinder) {
        return sInnerReceiverDelegate.get(iBinder);
    }

    public static IIntentReceiver createProxy(IIntentReceiver base) {
        if (base instanceof ParallaxInnerReceiverDelegate) {
            return base;
        }
        final IBinder iBinder = base.asBinder();
        ParallaxInnerReceiverDelegate delegate = sInnerReceiverDelegate.get(iBinder);
        if (delegate == null) {
            try {
                iBinder.linkToDeath(new DeathRecipient() {
                    @Override
                    public void binderDied() {
                        sInnerReceiverDelegate.remove(iBinder);
                        iBinder.unlinkToDeath(this, 0);
                    }
                }, 0);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            delegate = new ParallaxInnerReceiverDelegate(base);
            sInnerReceiverDelegate.put(iBinder, delegate);
        }
        return delegate;
    }

    @Override
    public void performReceive(Intent intent, int resultCode, String data, Bundle extras, boolean ordered, boolean sticky, int sendingUser) throws RemoteException {
        intent.setExtrasClassLoader(ParallaxActivityThread.getApplication().getClassLoader());
        ParallaxProxyBroadcastRecord proxyBroadcastRecord = ParallaxProxyBroadcastRecord.create(intent);
        Intent perIntent;
        if (proxyBroadcastRecord.mIntent != null) {
            proxyBroadcastRecord.mIntent.setExtrasClassLoader(ParallaxActivityThread.getApplication().getClassLoader());
            perIntent = proxyBroadcastRecord.mIntent;
        } else {
            perIntent = intent;
        }
        IIntentReceiver iIntentReceiver = mIntentReceiver.get();
        if (iIntentReceiver != null) {
            BRIIntentReceiver.get(iIntentReceiver).performReceive(perIntent, resultCode, data, extras, ordered, sticky, sendingUser);
        }
    }
}
