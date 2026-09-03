package top.niunaijun.blackbox.core.system.am;

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

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.system.pm.BPackage;
import top.niunaijun.blackbox.core.system.pm.BPackageManagerService;
import top.niunaijun.blackbox.core.system.pm.BPackageSettings;
import top.niunaijun.blackbox.core.system.pm.PackageMonitor;
import top.niunaijun.blackbox.entity.am.PendingResultData;
import top.niunaijun.blackbox.proxy.ProxyBroadcastReceiver;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.BuildCompat;

/**
 * Fixed BroadcastManager
 * Android 10–15 compatible
 */
public class BroadcastManager implements PackageMonitor {

    public static final String TAG = "BroadcastManager";

    public static final int TIMEOUT = 9000;
    public static final int MSG_TIME_OUT = 1;

    private static BroadcastManager sBroadcastManager;

    private final BActivityManagerService mAms;
    private final BPackageManagerService mPms;

    private final Map<String, List<BroadcastReceiver>> mReceivers = new HashMap<>();
    private final Map<String, PendingResultData> mReceiversData = new HashMap<>();

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_TIME_OUT) {
                try {
                    PendingResultData data = (PendingResultData) msg.obj;
                    data.build().finish();
                    Slog.d(TAG, "Timeout Receiver: " + data);
                } catch (Throwable ignored) {
                }
            }
        }
    };

    public static BroadcastManager startSystem(
            BActivityManagerService ams,
            BPackageManagerService pms) {

        if (sBroadcastManager == null) {
            synchronized (BroadcastManager.class) {
                if (sBroadcastManager == null) {
                    sBroadcastManager = new BroadcastManager(ams, pms);
                }
            }
        }
        return sBroadcastManager;
    }

    private BroadcastManager(BActivityManagerService ams,BPackageManagerService pms) {
        mAms = ams;
        mPms = pms;
    }

    public void startup() {
        mPms.addPackageMonitor(this);

        List<BPackageSettings> settings = mPms.getBPackageSettings();
        for (BPackageSettings s : settings) {
            registerPackage(s.pkg);
        }
    }

    // =========================
    // REGISTER RECEIVERS (FIXED)
    // =========================
    private void registerPackage(BPackage bPackage) {
        synchronized (mReceivers) {
            Slog.d(TAG, "register: " + bPackage.packageName + ", size: " + bPackage.receivers.size());
            for (BPackage.Activity receiver : bPackage.receivers) {
                for (BPackage.ActivityIntentInfo info : receiver.intents) {
                    // Filter dangerous system-only broadcasts
                    if (shouldIgnore(info.intentFilter.getAction(0))) {
                        continue;
                    }

                    ProxyBroadcastReceiver proxy = new ProxyBroadcastReceiver();
                    Context ctx = BlackBoxCore.getContext();

                    try {
                        if (BuildCompat.isT()) {
                            // Android 13+
                            ctx.registerReceiver(proxy,info.intentFilter,Context.RECEIVER_EXPORTED);
                        } else if (BuildCompat.isS()) {
                            // Android 12
                            ctx.registerReceiver(proxy,info.intentFilter,Context.RECEIVER_NOT_EXPORTED);
                        } else {
                            // Android 10–11
                            ctx.registerReceiver(proxy, info.intentFilter);
                        }
                        addReceiver(bPackage.packageName, proxy);

                    } catch (Throwable e) {
                        Slog.w(TAG, "registerReceiver failed: " + e);
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
    public void sendBroadcast(PendingResultData data) {
        synchronized (mReceiversData) {
            mReceiversData.put(data.mBToken, data);
            Message m = Message.obtain(mHandler, MSG_TIME_OUT, data);
            mHandler.sendMessageDelayed(m, TIMEOUT);
        }
    }

    public void finishBroadcast(PendingResultData data) {
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
                Slog.d(TAG, "unregisterReceiver: " + packageName + ", size=" + list.size());
                for (BroadcastReceiver r : list) {
                    try {
                        BlackBoxCore.getContext().unregisterReceiver(r);
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
            BPackageSettings s = mPms.getBPackageSetting(packageName);
            if (s != null) {
                registerPackage(s.pkg);
            }
        }
    }
}