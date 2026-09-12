package com.Parallax.SDK.core.fake.frameworks;

import android.app.job.JobInfo;
import android.os.RemoteException;

import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.core.system.ParallaxServiceManager;
import com.Parallax.SDK.core.core.system.am.IParallaxJobManagerService;
import com.Parallax.SDK.core.entity.ParallaxJobRecord;

/**
 * Created by @RIYAZXERO on 3/30/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxJobManager extends ParallaxBlackManager<IParallaxJobManagerService> {
    private static final ParallaxJobManager sJobManager = new ParallaxJobManager();

    public static ParallaxJobManager get() {
        return sJobManager;
    }

    @Override
    protected String getServiceName() {
        return ParallaxServiceManager.JOB_MANAGER;
    }

    public JobInfo schedule(JobInfo info) {
        try {
            return getService().schedule(info, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public ParallaxJobRecord queryJobRecord(String processName, int jobId) {
        try {
            return getService().queryJobRecord(processName, jobId, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void cancelAll(String processName) {
        try {
            getService().cancelAll(processName, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public int cancel(String processName, int jobId) {
        try {
            return getService().cancel(processName, jobId, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return -1;
    }
}
