package com.Parallax.SDK.core.proxy;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.content.res.Configuration;

import com.Parallax.SDK.core.app.dispatcher.ParallaxAppJobServiceDispatcher;

/**
 * Created by Milk on 4/2/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxProxyJobService extends JobService {
    public static final String TAG = "StubJobService";

    @Override
    public boolean onStartJob(JobParameters params) {
        return ParallaxAppJobServiceDispatcher.get().onStartJob(params);
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return ParallaxAppJobServiceDispatcher.get().onStopJob(params);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ParallaxAppJobServiceDispatcher.get().onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ParallaxAppJobServiceDispatcher.get().onConfigurationChanged(newConfig);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        ParallaxAppJobServiceDispatcher.get().onLowMemory();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        ParallaxAppJobServiceDispatcher.get().onTrimMemory(level);
    }

    public static class P0 extends ParallaxProxyJobService { }

    public static class P1 extends ParallaxProxyJobService { }

    public static class P2 extends ParallaxProxyJobService { }

    public static class P3 extends ParallaxProxyJobService { }

    public static class P4 extends ParallaxProxyJobService { }

    public static class P5 extends ParallaxProxyJobService { }

    public static class P6 extends ParallaxProxyJobService { }

    public static class P7 extends ParallaxProxyJobService { }

    public static class P8 extends ParallaxProxyJobService { }

    public static class P9 extends ParallaxProxyJobService { }

    public static class P10 extends ParallaxProxyJobService { }

    public static class P11 extends ParallaxProxyJobService { }

    public static class P12 extends ParallaxProxyJobService { }

    public static class P13 extends ParallaxProxyJobService { }

    public static class P14 extends ParallaxProxyJobService { }

    public static class P15 extends ParallaxProxyJobService { }

    public static class P16 extends ParallaxProxyJobService { }

    public static class P17 extends ParallaxProxyJobService { }

    public static class P18 extends ParallaxProxyJobService { }

    public static class P19 extends ParallaxProxyJobService { }

    public static class P20 extends ParallaxProxyJobService { }

    public static class P21 extends ParallaxProxyJobService { }

    public static class P22 extends ParallaxProxyJobService { }

    public static class P23 extends ParallaxProxyJobService { }

    public static class P24 extends ParallaxProxyJobService { }

    
}
