package com.Parallax.SDK.core.core.system.am;

import android.app.job.JobInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.RemoteException;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.Map;

import com.Parallax.SDK.mirror.android.app.job.BRJobInfo;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.core.system.ParallaxProcessManagerService;
import com.Parallax.SDK.core.core.system.IParallaxSystemService;
import com.Parallax.SDK.core.core.system.ParallaxProcessRecord;
import com.Parallax.SDK.core.core.system.pm.ParallaxPackageManagerService;
import com.Parallax.SDK.core.entity.ParallaxJobRecord;
import com.Parallax.SDK.core.proxy.ParallaxProxyManifest;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;

/**
 * Created by @RIYAZXERO on 4/2/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxJobManagerService extends IParallaxJobManagerService.Stub implements IParallaxSystemService {
    private static final ParallaxJobManagerService sService = new ParallaxJobManagerService();

    // process_jobId
    private final Map<String, ParallaxJobRecord> mJobRecords = new HashMap<>();

    public static ParallaxJobManagerService get() {
        return sService;
    }

    @Override
    public JobInfo schedule(JobInfo info, int userId) throws RemoteException {
        ComponentName componentName = info.getService();
        Intent intent = new Intent();
        intent.setComponent(componentName);
        ResolveInfo resolveInfo = ParallaxPackageManagerService.get().resolveService(intent, ParallaxFileUtils.FileMode.MODE_IWUSR, null, userId);
        if (resolveInfo == null) {
            return info;
        }
        ServiceInfo serviceInfo = resolveInfo.serviceInfo;
        ParallaxProcessRecord processRecord = ParallaxProcessManagerService.get().findProcessRecord(serviceInfo.packageName, serviceInfo.processName, userId);
        if (processRecord == null) {
            processRecord = ParallaxProcessManagerService.get().
                    startProcessLocked(serviceInfo.packageName, serviceInfo.processName, userId, -1, Binder.getCallingPid());
            if (processRecord == null) {
                throw new RuntimeException(
                        "Unable to create Process " + serviceInfo.processName);
            }
        }
        return scheduleJob(processRecord, info, serviceInfo);
    }

    @Override
    public ParallaxJobRecord queryJobRecord(String processName, int jobId, int userId) throws RemoteException {
        return mJobRecords.get(formatKey(processName, jobId));
    }

    public JobInfo scheduleJob(ParallaxProcessRecord processRecord, JobInfo info, ServiceInfo serviceInfo) {
        ParallaxJobRecord jobRecord = new ParallaxJobRecord();
        jobRecord.mJobInfo = info;
        jobRecord.mServiceInfo = serviceInfo;

        mJobRecords.put(formatKey(processRecord.processName, info.getId()), jobRecord);
        BRJobInfo.get(info)._set_service(new ComponentName(ParallaxCore.getHostPkg(), ParallaxProxyManifest.getProxyJobService(processRecord.bpid)));
        return info;
    }

    @Override
    public void cancelAll(String processName, int userId) throws RemoteException {
        if (TextUtils.isEmpty(processName)) return;
        for (String key : mJobRecords.keySet()) {
            if (key.startsWith(processName + "_")) {
                ParallaxJobRecord jobRecord = mJobRecords.get(key);
                // todo
            }
        }
    }

    @Override
    public int cancel(String processName, int jobId, int userId) throws RemoteException {
        return jobId;
    }

    private String formatKey(String processName, int jobId) {
        return processName + "_" + jobId;
    }

    @Override
    public void systemReady() {

    }
}
