package com.Parallax.SDK.core.core.system.am;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.core.system.pm.ParallaxPackage;
import com.Parallax.SDK.core.core.system.pm.ParallaxPackageManagerService;
import com.Parallax.SDK.core.core.system.pm.ParallaxPackageSettings;
import com.Parallax.SDK.core.core.system.pm.ParallaxPackageMonitor;
import com.Parallax.SDK.core.entity.am.ParallaxPendingResultData;
import com.Parallax.SDK.core.proxy.ParallaxProxyBroadcastReceiver;
import com.Parallax.SDK.core.utils.ParallaxSlog;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;

/**
 * Fixed ParallaxBroadcastManager
 * Android 10–15 compatible
 */
public class ParallaxBroadcastManager implements ParallaxPackageMonitor {

    public static final String TAG = "ParallaxBroadcastManager";

    public static final int TIMEOUT = 9000;
    public static final int MSG_TIME_OUT = 1;

    private static ParallaxBroadcastManager sBroadcastManager;

    private final ParallaxActivityManagerService mAms;
    private final ParallaxPackageManagerService mPms;

    private final Map<String, List<BroadcastReceiver>> mReceivers = new HashMap<>();
    private final Map<String, ParallaxPendingResultData> mReceiversData = new HashMap<>();

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_TIME_OUT) {
                try {
                    ParallaxPendingResultData data = (ParallaxPendingResultData) msg.obj;
                    data.build().finish();
                    ParallaxSlog.d(TAG, "Timeout Receiver: " + data);
                } catch (Throwable ignored) {
                }
            }
        }
    };

    public static ParallaxBroadcastManager startSystem(
            ParallaxActivityManagerService ams,
            ParallaxPackageManagerService pms) {

        if (sBroadcastManager == null) {
            synchronized (ParallaxBroadcastManager.class) {
                if (sBroadcastManager == null) {
                    sBroadcastManager = new ParallaxBroadcastManager(ams, pms);
                }
            }
        }
        return sBroadcastManager;
    }

    private ParallaxBroadcastManager(ParallaxActivityManagerService ams,ParallaxPackageManagerService pms) {
        mAms = ams;
        mPms = pms;
    }

    public void startup() {
        mPms.addPackageMonitor(this);

        List<ParallaxPackageSettings> settings = mPms.getBPackageSettings();
        for (ParallaxPackageSettings s : settings) {
            registerPackage(s.pkg);
        }
    }

    // =========================
    // REGISTER RECEIVERS (FIXED)
    // =========================
    private void registerPackage(ParallaxPackage bPackage) {
        synchronized (mReceivers) {
            ParallaxSlog.d(TAG, "register: " + bPackage.packageName + ", size: " + bPackage.receivers.size());
            for (ParallaxPackage.Activity receiver : bPackage.receivers) {
                for (ParallaxPackage.ActivityIntentInfo info : receiver.intents) {
                    // Filter dangerous system-only broadcasts
                    if (shouldIgnore(info.intentFilter.getAction(0))) {
                        continue;
                    }

                    ParallaxProxyBroadcastReceiver proxy = new ParallaxProxyBroadcastReceiver();
                    Context ctx = ParallaxCore.getContext();

                    try {
                        if (ParallaxBuildCompat.isT()) {
                            // Android 13+
                            ctx.registerReceiver(proxy,info.intentFilter,Context.RECEIVER_EXPORTED);
                        } else if (ParallaxBuildCompat.isS()) {
                            // Android 12
                            ctx.registerReceiver(proxy,info.intentFilter,Context.RECEIVER_NOT_EXPORTED);
                        } else {
                            // Android 10–11
                            ctx.registerReceiver(proxy, info.intentFilter);
                        }
                        addReceiver(bPackage.packageName, proxy);

                    } catch (Throwable e) {
                        ParallaxSlog.w(TAG, "registerReceiver failed: " + e);
                    }
                }
            }
        }
    }

    private boolean shouldIgnore(String action) {
        if (action == null) return false;
        return Intent.ACTION_BATTERY_CHANGED.equals(action) || Intent.ACTION_HEADSET_PLUG.equals(action) || Intent.ACTION_POWER_CONNECTED.equals(action) || Intent.ACTION_POWER_DISCONNECTED.equals(action);
    }

    private void addReceiver(String pkg, BroadcastReceiver r) {
        List<BroadcastReceiver> list = mReceivers.get(pkg);
        if (list == null) {
            list = new ArrayList<>();
            mReceivers.put(pkg, list);
        }
        list.add(r);
    }

    // =========================
    // BROADCAST LIFECYCLE
    // =========================
    public void sendBroadcast(ParallaxPendingResultData data) {
        synchronized (mReceiversData) {
            mReceiversData.put(data.mBToken, data);
            Message m = Message.obtain(mHandler, MSG_TIME_OUT, data);
            mHandler.sendMessageDelayed(m, TIMEOUT);
        }
    }

    public void finishBroadcast(ParallaxPendingResultData data) {
        synchronized (mReceiversData) {
            mHandler.removeMessages(MSG_TIME_OUT,mReceiversData.remove(data.mBToken));
        }
    }

    // =========================
    // PACKAGE MONITOR
    // =========================
    @Override
    public void onPackageUninstalled(String packageName,boolean removeApp,int userId) {
        if (!removeApp) return;
        synchronized (mReceivers) {
            List<BroadcastReceiver> list = mReceivers.remove(packageName);
            if (list != null) {
                ParallaxSlog.d(TAG, "unregisterReceiver: " + packageName + ", size=" + list.size());
                for (BroadcastReceiver r : list) {
                    try {
                        ParallaxCore.getContext().unregisterReceiver(r);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    @Override
    public void onPackageInstalled(String packageName, int userId) {
        synchronized (mReceivers) {
            mReceivers.remove(packageName);
            ParallaxPackageSettings s = mPms.getBPackageSetting(packageName);
            if (s != null) {
                registerPackage(s.pkg);
            }
        }
    }
}