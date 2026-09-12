package com.Parallax.SDK.core.proxy;

import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.dispatcher.ParallaxAppServiceDispatcher;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;

/**
 * Created by Milk on 3/30/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxProxyService extends Service {
    public static final String TAG = "StubService";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return ParallaxAppServiceDispatcher.get().onBind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ParallaxAppServiceDispatcher.get().onStartCommand(intent);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ParallaxAppServiceDispatcher.get().onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ParallaxAppServiceDispatcher.get().onConfigurationChanged(newConfig);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        ParallaxAppServiceDispatcher.get().onLowMemory();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        ParallaxAppServiceDispatcher.get().onTrimMemory(level);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        ParallaxAppServiceDispatcher.get().onUnbind(intent);
        return false;
    }

    private void showNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), getPackageName() + ".parallaxcore_core_proxy").setPriority(NotificationCompat.PRIORITY_MAX);
        if (ParallaxBuildCompat.isOreo()) {
            startForeground(ParallaxCore.getHostPkg().hashCode(), builder.build());
        }
    }

    public static class P0 extends ParallaxProxyService { }

    public static class P1 extends ParallaxProxyService { }

    public static class P2 extends ParallaxProxyService { }

    public static class P3 extends ParallaxProxyService { }

    public static class P4 extends ParallaxProxyService { }

    public static class P5 extends ParallaxProxyService { }

    public static class P6 extends ParallaxProxyService { }

    public static class P7 extends ParallaxProxyService { }

    public static class P8 extends ParallaxProxyService { }

    public static class P9 extends ParallaxProxyService { }

    public static class P10 extends ParallaxProxyService { }

    public static class P11 extends ParallaxProxyService { }

    public static class P12 extends ParallaxProxyService { }

    public static class P13 extends ParallaxProxyService { }

    public static class P14 extends ParallaxProxyService { }

    public static class P15 extends ParallaxProxyService { }

    public static class P16 extends ParallaxProxyService { }

    public static class P17 extends ParallaxProxyService { }

    public static class P18 extends ParallaxProxyService { }

    public static class P19 extends ParallaxProxyService { }

    public static class P20 extends ParallaxProxyService { }

    public static class P21 extends ParallaxProxyService { }

    public static class P22 extends ParallaxProxyService { }

    public static class P23 extends ParallaxProxyService { }

    public static class P24 extends ParallaxProxyService { }

}
