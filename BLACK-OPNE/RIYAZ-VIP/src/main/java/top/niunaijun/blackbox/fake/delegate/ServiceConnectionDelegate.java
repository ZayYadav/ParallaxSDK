package top.niunaijun.blackbox.fake.delegate;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.HashMap;
import java.util.Map;

import black.android.app.BRIServiceConnectionO;
import top.niunaijun.blackbox.compat.auth.GmsBrokerCompat;
import top.niunaijun.blackbox.core.GmsCore;
import top.niunaijun.blackbox.utils.compat.BuildCompat;

public class ServiceConnectionDelegate extends IServiceConnection.Stub {
    private static final Map<IBinder, ServiceConnectionDelegate> sServiceConnectDelegate = new HashMap<>();
    private final IServiceConnection mConn;
    private final ComponentName mComponentName;
    private final String mTargetPackage;

    private ServiceConnectionDelegate(IServiceConnection mConn, Intent intent) {
        this.mConn = mConn;
        this.mComponentName = intent == null ? null : intent.getComponent();
        if (mComponentName != null) {
            this.mTargetPackage = mComponentName.getPackageName();
        } else {
            this.mTargetPackage = intent == null ? null : intent.getPackage();
        }
    }

    public static ServiceConnectionDelegate getDelegate(IBinder iBinder) {
        return sServiceConnectDelegate.get(iBinder);
    }

    public static IServiceConnection createProxy(IServiceConnection base, Intent intent) {
        final IBinder iBinder = base.asBinder();
        ServiceConnectionDelegate delegate = sServiceConnectDelegate.get(iBinder);
        if (delegate == null) {
            try {
                iBinder.linkToDeath(new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        sServiceConnectDelegate.remove(iBinder);
                        iBinder.unlinkToDeath(this, 0);
                    }
                }, 0);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            delegate = new ServiceConnectionDelegate(base, intent);
            sServiceConnectDelegate.put(iBinder, delegate);
        }
        return delegate;
    }

    @Override
    public void connected(ComponentName name, IBinder service) throws RemoteException {
        connected(name, service, false);
    }

    public void connected(ComponentName name, IBinder service, boolean dead) throws RemoteException {
        IBinder deliveredService = service;
        String callbackPackage = name != null ? name.getPackageName() : mTargetPackage;
        if (GmsCore.GMS_PKG.equals(callbackPackage)
                || GmsCore.GMS_PKG.equals(mTargetPackage)) {
            deliveredService = GmsBrokerCompat.wrap(service);
        }

        ComponentName callbackName = mComponentName != null ? mComponentName : name;
        if (BuildCompat.isOreo()) {
            BRIServiceConnectionO.get(mConn).connected(callbackName, deliveredService, dead);
        } else {
            mConn.connected(callbackName, deliveredService);
        }
    }
}
