// IParallaxJobManagerService.aidl
package com.Parallax.SDK.core.core.system.am;

import android.content.Intent;
import android.content.ComponentName;
import android.os.IBinder;
import java.lang.String;
import android.app.job.JobInfo;
import com.Parallax.SDK.core.entity.ParallaxJobRecord;

// Declare any non-default types here with import statements

interface IParallaxJobManagerService {
    JobInfo schedule(in JobInfo info, int userId);
    ParallaxJobRecord queryJobRecord(String processName, int jobId, int userId);
    void cancelAll(String processName, int userId);
    int cancel(String processName, int jobId, int userId);

}
