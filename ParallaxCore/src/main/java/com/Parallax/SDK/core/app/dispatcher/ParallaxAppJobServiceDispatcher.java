package com.Parallax.SDK.core.app.dispatcher;

import android.app.Service;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.IBinder;

import com.Parallax.SDK.core.entity.ParallaxJobRecord;
import java.util.HashMap;
import java.util.Map;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.entity.ParallaxServiceRecord;
import com.Parallax.SDK.core.entity.ParallaxUnbindRecord;
import com.Parallax.SDK.core.proxy.record.ParallaxProxyServiceRecord;
/**
 * Created by @RIYAZXERO on 4/1/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxAppJobServiceDispatcher {
    private static final ParallaxAppJobServiceDispatcher sServiceDispatcher = new ParallaxAppJobServiceDispatcher();
    private final Map<Integer, ParallaxJobRecord> mJobRecords = new HashMap<>();

    public static ParallaxAppJobServiceDispatcher get() {
        return sServiceDispatcher;
    }

    public boolean onStartJob(JobParameters params) {
        try {
            JobService jobService = getJobService(params.getJobId());
            if (jobService == null) {
                return false;
            }
            return jobService.onStartJob(params);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean onStopJob(JobParameters params) {
        JobService jobService = getJobService(params.getJobId());
        if (jobService == null) {
            return false;
        }

        boolean isStopJob = jobService.onStopJob(params);
        jobService.onDestroy();

        synchronized (mJobRecords) {
            mJobRecords.remove(params.getJobId());
        }
        return isStopJob;
    }

    public void onConfigurationChanged(Configuration newConfig) {
        for (ParallaxJobRecord jobRecord : mJobRecords.values()) {
            if (jobRecord.mJobService != null) {
                jobRecord.mJobService.onConfigurationChanged(newConfig);
            }
        }
    }

    public void onDestroy() {
        for (ParallaxJobRecord jobRecord : mJobRecords.values()) {
            if (jobRecord.mJobService != null) {
                jobRecord.mJobService.onDestroy();
            }
        }
    }

    public void onLowMemory() {
        for (ParallaxJobRecord jobRecord : mJobRecords.values()) {
            if (jobRecord.mJobService != null) {
                jobRecord.mJobService.onLowMemory();
            }
        }
    }

    public void onTrimMemory(int level) {
        for (ParallaxJobRecord jobRecord : mJobRecords.values()) {
            if (jobRecord.mJobService != null) {
                jobRecord.mJobService.onTrimMemory(level);
            }
        }
    }

    JobService getJobService(int jobId) {
        synchronized (mJobRecords) {
            ParallaxJobRecord jobRecord = mJobRecords.get(jobId);
            if (jobRecord != null && jobRecord.mJobService != null) {
                return jobRecord.mJobService;
            }

            try {
                ParallaxJobRecord record = ParallaxCore.getBJobManager().queryJobRecord(ParallaxActivityThread.getAppProcessName(), jobId);
                if (record == null) {
                    return null;
                }

                record.mJobService = ParallaxActivityThread.currentActivityThread().createJobService(record.mServiceInfo);
                if (record.mJobService == null) {
                    return null;
                }

                mJobRecords.put(jobId, record);
                return record.mJobService;
            } catch (Throwable t) {
                t.printStackTrace();
            }
            return null;
        }
    }
}
