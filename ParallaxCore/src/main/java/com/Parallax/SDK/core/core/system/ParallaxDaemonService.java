package com.Parallax.SDK.core.core.system;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;


/**
 * Created by Milk on 3/2/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxDaemonService extends Service {
    public static final String TAG = "ParallaxDaemonService";
    private static final int NOTIFY_ID = ParallaxCore.getHostPkg().hashCode();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent innerIntent = new Intent(this, DaemonInnerService.class);
        startService(innerIntent);
        showNotification();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
    
    private void showNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), getPackageName() + ".vbox_core").setPriority(NotificationCompat.PRIORITY_MAX);
        startForeground(NOTIFY_ID, builder.build());
    }

/*
    private void showNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), getPackageName() + ".blackbox_core").setPriority(NotificationCompat.PRIORITY_MAX);
        if (ParallaxBuildCompat.isVanillaIceCream()) {
            startForeground(NOTIFY_ID, builder.build(), 1073741824);
        } else {
            startForeground(NOTIFY_ID, builder.build());
        }
    }
*/

    public static class DaemonInnerService extends Service {
        @Override
        public void onCreate() {
            super.onCreate();
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(NOTIFY_ID);
            stopSelf();
            return super.onStartCommand(intent, flags, startId);
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
        }
    }
}
