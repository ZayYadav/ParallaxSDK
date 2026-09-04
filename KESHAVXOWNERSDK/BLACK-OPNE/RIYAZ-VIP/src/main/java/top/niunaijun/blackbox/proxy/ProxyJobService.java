package top.niunaijun.blackbox.proxy;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.content.res.Configuration;

import top.niunaijun.blackbox.app.dispatcher.AppJobServiceDispatcher;

/**
 * Created by Milk on 4/2/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ProxyJobService extends JobService {
    public static final String TAG = "StubJobService";

    @Override
    public boolean onStartJob(JobParameters params) {
        return AppJobServiceDispatcher.get().onStartJob(params);
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return AppJobServiceDispatcher.get().onStopJob(params);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppJobServiceDispatcher.get().onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        AppJobServiceDispatcher.get().onConfigurationChanged(newConfig);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        AppJobServiceDispatcher.get().onLowMemory();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        AppJobServiceDispatcher.get().onTrimMemory(level);
    }

    public static class P0 extends ProxyJobService { }

    public static class P1 extends ProxyJobService { }

    public static class P2 extends ProxyJobService { }

    public static class P3 extends ProxyJobService { }

    public static class P4 extends ProxyJobService { }

    public static class P5 extends ProxyJobService { }

    public static class P6 extends ProxyJobService { }

    public static class P7 extends ProxyJobService { }

    public static class P8 extends ProxyJobService { }

    public static class P9 extends ProxyJobService { }

    public static class P10 extends ProxyJobService { }

    public static class P11 extends ProxyJobService { }

    public static class P12 extends ProxyJobService { }

    public static class P13 extends ProxyJobService { }

    public static class P14 extends ProxyJobService { }

    public static class P15 extends ProxyJobService { }

    public static class P16 extends ProxyJobService { }

    public static class P17 extends ProxyJobService { }

    public static class P18 extends ProxyJobService { }

    public static class P19 extends ProxyJobService { }

    public static class P20 extends ProxyJobService { }

    public static class P21 extends ProxyJobService { }

    public static class P22 extends ProxyJobService { }

    public static class P23 extends ProxyJobService { }

    public static class P24 extends ProxyJobService { }

    
}
