package com.Parallax.SDK.core.app.dispatcher;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.IBinder;

import java.util.HashMap;
import java.util.Map;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.entity.ParallaxServiceRecord;
import com.Parallax.SDK.core.entity.ParallaxUnbindRecord;
import com.Parallax.SDK.core.proxy.record.ParallaxProxyServiceRecord;

import static android.app.Service.START_NOT_STICKY;


/**
 * Created by @RIYAZXERO on 4/1/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxAppServiceDispatcher {
    private static final ParallaxAppServiceDispatcher sServiceDispatcher = new ParallaxAppServiceDispatcher();
    private final Map<Intent.FilterComparison, ParallaxServiceRecord> mService = new HashMap<>();
    private final Handler mHandler = ParallaxCore.get().getHandler();

    public static ParallaxAppServiceDispatcher get() {
        return sServiceDispatcher;
    }

    public IBinder onBind(Intent proxyIntent) {
        ParallaxProxyServiceRecord serviceRecord = ParallaxProxyServiceRecord.create(proxyIntent);
        Intent intent = serviceRecord.mServiceIntent;
        ServiceInfo serviceInfo = serviceRecord.mServiceInfo;

        if (intent == null || serviceInfo == null) {
            return null;
        }

        Service service = getOrCreateService(serviceRecord);
        if (service == null) {
            return null;
        }
        intent.setExtrasClassLoader(service.getClassLoader());

        ParallaxServiceRecord record = findRecord(intent);
        record.incrementAndGetBindCount(intent);

        if (record.hasBinder(intent)) {
            if (record.isRebind()) {
                service.onRebind(intent);
                record.setRebind(false);
            }
            return record.getBinder(intent);
        }

        try {
            IBinder iBinder = service.onBind(intent);
            record.addBinder(intent, iBinder);
            return iBinder;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public void onStartCommand(Intent proxyIntent) {
        ParallaxProxyServiceRecord stubRecord = ParallaxProxyServiceRecord.create(proxyIntent);
        if (stubRecord.mServiceIntent == null || stubRecord.mServiceInfo == null) {
            return;
        }

        Service service = getOrCreateService(stubRecord);
        if (service == null) {
            return;
        }
        stubRecord.mServiceIntent.setExtrasClassLoader(service.getClassLoader());

        ParallaxServiceRecord record = findRecord(stubRecord.mServiceIntent);
        record.setStartId(stubRecord.mStartId);
    }

    public void onDestroy() {
        if (mService.size() > 0) {
            for (ParallaxServiceRecord record : mService.values()) {
                try {
                    record.getService().onDestroy();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
        mService.clear();
    }

    public void onConfigurationChanged(Configuration newConfig) {
        if (mService.size() > 0) {
            for (ParallaxServiceRecord record : mService.values()) {
                try {
                    record.getService().onConfigurationChanged(newConfig);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void onLowMemory() {
        if (mService.size() > 0) {
            for (ParallaxServiceRecord record : mService.values()) {
                try {
                    record.getService().onLowMemory();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void onTrimMemory(int level) {
        if (mService.size() > 0) {
            for (ParallaxServiceRecord record : mService.values()) {
                try {
                    record.getService().onTrimMemory(level);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void onUnbind(Intent proxyIntent) {
        ParallaxProxyServiceRecord stubRecord = ParallaxProxyServiceRecord.create(proxyIntent);
        if (stubRecord.mServiceIntent == null || stubRecord.mServiceInfo == null) {
            return;
        }

        Intent intent = stubRecord.mServiceIntent;
        try {
            ParallaxUnbindRecord unbindRecord = ParallaxCore.getBActivityManager().onServiceUnbind(proxyIntent, ParallaxActivityThread.getUserId());
            if (unbindRecord == null) {
                return;
            }

            Service service = getOrCreateService(stubRecord);
            if (service == null) {
                return;
            }
            stubRecord.mServiceIntent.setExtrasClassLoader(service.getClassLoader());

            ParallaxServiceRecord record = findRecord(intent);
            boolean destroy = unbindRecord.getStartId() == 0;

            if (destroy || record.decreaseConnectionCount(intent)) {
                if (destroy) {
                    service.onDestroy();

                    ParallaxCore.getBActivityManager().onServiceDestroy(proxyIntent, ParallaxActivityThread.getUserId());
                    mService.remove(new Intent.FilterComparison(intent));
                }
                record.setRebind(true);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public IBinder peekService(Intent intent) {
        ParallaxServiceRecord record = findRecord(intent);
        if (record == null) {
            return null;
        }
        return record.getBinder(intent);
    }

    public void stopService(Intent intent) {
        if (intent == null) {
            return;
        }

        ParallaxServiceRecord record = findRecord(intent);
        if (record == null) {
            return;
        }

        if (record.getService() != null) {
            boolean destroy = record.getStartId() > 0;
            try {
                if (destroy) {
                    mHandler.post(() -> record.getService().onDestroy());
                    ParallaxCore.getBActivityManager().onServiceDestroy(intent, ParallaxActivityThread.getUserId());
                    mService.remove(new Intent.FilterComparison(intent));
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    private ParallaxServiceRecord findRecord(Intent intent) {
        return mService.get(new Intent.FilterComparison(intent));
    }

    private Service getOrCreateService(ParallaxProxyServiceRecord proxyServiceRecord) {
        Intent intent = proxyServiceRecord.mServiceIntent;
        ServiceInfo serviceInfo = proxyServiceRecord.mServiceInfo;
        IBinder token = proxyServiceRecord.mToken;

        ParallaxServiceRecord record = findRecord(intent);
        if (record != null && record.getService() != null) {
            return record.getService();
        }

        Service service = ParallaxActivityThread.currentActivityThread().createService(serviceInfo, token);
        if (service == null) {
            return null;
        }

        record = new ParallaxServiceRecord();
        record.setService(service);
        mService.put(new Intent.FilterComparison(intent), record);
        return service;
    }
}
