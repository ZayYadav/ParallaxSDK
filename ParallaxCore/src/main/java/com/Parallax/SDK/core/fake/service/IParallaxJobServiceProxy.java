package com.Parallax.SDK.core.fake.service;

import android.app.job.JobInfo;
import android.content.Context;
import android.os.IBinder;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.app.job.BRIJobSchedulerStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.utils.ParallaxSlog;
import com.Parallax.SDK.core.utils.ParallaxUIDSpoofingHelper;

/**
 * IJobService Proxy to handle job scheduling in sandboxed environments
 * This prevents UID mismatch crashes when scheduling background jobs
 */
public class IParallaxJobServiceProxy extends ParallaxBinderInvocationStub {
    private static final String TAG = "JobServiceStub";
    private static final String SERVICE_NAME = "jobscheduler";
    private static final int RESULT_FAILURE = 0;

    public IParallaxJobServiceProxy() {
        super(BRServiceManager.get().getService(Context.JOB_SCHEDULER_SERVICE));
    }

    @Override
    protected Object getWho() {
        IBinder jobScheduler = BRServiceManager.get().getService(SERVICE_NAME);
        return BRIJobSchedulerStub.get().asInterface(jobScheduler);
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.JOB_SCHEDULER_SERVICE);
    }
    
    private static abstract class BaseJobHandler extends ParallaxMethodHook {
        protected Object processJobOperation(String operation, Object who, Method method, Object[] args) throws Throwable {
            if (!validateJobArgs(args)) {
                ParallaxSlog.w(TAG, operation + ": Invalid arguments, returning failure");
                return RESULT_FAILURE;
            }
            JobInfo jobInfo = (JobInfo) args[0];
            String packageName = jobInfo.getService().getPackageName();
            ParallaxSlog.d(TAG, operation + ": Processing JobInfo for package: " + packageName);
            try {
                JobInfo proxyJobInfo = ParallaxCore.getBJobManager().schedule(jobInfo);
                if (proxyJobInfo != null) {
                    args[0] = proxyJobInfo;
                    ParallaxSlog.d(TAG, operation + ": Successfully created proxy JobInfo");
                    return method.invoke(who, args);
                }
            } catch (Exception e) {
                ParallaxSlog.w(TAG, operation + ": BlackBox job manager failed, trying fallback", e);
            }
            return handleWithUIDSpoofing(operation, who, method, args, jobInfo);
        }

        protected boolean validateJobArgs(Object[] args) {
            if (args == null || args.length == 0 || args[0] == null) {
                return false;
            }
            
            if (!(args[0] instanceof JobInfo)) {
                ParallaxSlog.w(TAG, "Argument is not JobInfo: " + args[0].getClass().getSimpleName());
                return false;
            }
            
            JobInfo jobInfo = (JobInfo) args[0];
            return jobInfo.getService() != null;
        }

        protected Object handleWithUIDSpoofing(String operation, Object who, Method method, Object[] args, JobInfo jobInfo) throws Throwable {
            String targetPackage = jobInfo.getService().getPackageName();
            ParallaxSlog.d(TAG, operation + ": Attempting UID spoofing for package: " + targetPackage);
            ParallaxUIDSpoofingHelper.logUIDInfo("job_" + operation.toLowerCase(), targetPackage);
            if (ParallaxUIDSpoofingHelper.needsUIDSpoofing("job_" + operation.toLowerCase(), targetPackage)) {
                ParallaxSlog.d(TAG, operation + ": UID spoofing needed");
                return RESULT_FAILURE;
            }
            ParallaxSlog.d(TAG, operation + ": No UID spoofing needed, proceeding normally");
            return method.invoke(who, args);
        }

        protected boolean isUIDValidationError(Exception e) {
            if (e.getCause() == null) return false;
            String message = e.getCause().getMessage();
            return message != null && message.contains("cannot schedule job");
        }
    }

    @ParallaxProxyMethod("schedule")
    public static class Schedule extends BaseJobHandler {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                if (!validateJobArgs(args)) {
                    return handleInvalidJobInfo("schedule", who, method, args);
                }
                return processJobOperation("Schedule", who, method, args);
            } catch (Exception e) {
                ParallaxSlog.e(TAG, "Schedule: Error processing job", e);
                if (isUIDValidationError(e)) {
                    ParallaxSlog.w(TAG, "UID validation failed for job scheduling");
                    return RESULT_FAILURE;
                }
                return executeFallback(who, method, args, "Schedule");
            }
        }

        private Object handleInvalidJobInfo(String operation, Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof String) {
                String workId = (String) args[0];
                ParallaxSlog.d(TAG, operation + ": Handling WorkManager string ID: " + workId);
            }
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                ParallaxSlog.w(TAG, operation + ": Failed to handle invalid JobInfo", e);
                return RESULT_FAILURE;
            }
        }
    }

    @ParallaxProxyMethod("cancel")
    public static class Cancel extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                if (args == null || args.length == 0 || !(args[0] instanceof Integer)) {
                    ParallaxSlog.w(TAG, "Cancel: Invalid arguments");
                    return method.invoke(who, args);
                }
                int jobId = (Integer) args[0];
                String processName = ParallaxActivityThread.getAppConfig().processName;
                int cancelledJobId = ParallaxCore.getBJobManager().cancel(processName, jobId);
                args[0] = cancelledJobId;
                return method.invoke(who, args);
            } catch (Exception e) {
                ParallaxSlog.e(TAG, "Cancel: Error canceling job", e);
                return method.invoke(who, args);
            }
        }
    }

    @ParallaxProxyMethod("cancelAll")
    public static class CancelAll extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                String processName = ParallaxActivityThread.getAppConfig().processName;
                ParallaxCore.getBJobManager().cancelAll(processName);
                return method.invoke(who, args);
            } catch (Exception e) {
                ParallaxSlog.e(TAG, "CancelAll: Error canceling all jobs", e);
                return method.invoke(who, args);
            }
        }
    }

    @ParallaxProxyMethod("enqueue")
    public static class Enqueue extends BaseJobHandler {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                if (!validateJobArgs(args)) {
                    return handleInvalidJobInfo("enqueue", who, method, args);
                }
                return processJobOperation("Enqueue", who, method, args);
            } catch (Exception e) {
                ParallaxSlog.e(TAG, "Enqueue: Error processing job", e);
                if (isUIDValidationError(e)) {
                    ParallaxSlog.w(TAG, "UID validation failed for job enqueuing");
                    return RESULT_FAILURE;
                }
                return executeFallback(who, method, args, "Enqueue");
            }
        }

        private Object handleInvalidJobInfo(String operation, Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof String) {
                String workId = (String) args[0];
                ParallaxSlog.d(TAG, operation + ": Handling WorkManager string ID: " + workId);
            }
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                ParallaxSlog.w(TAG, operation + ": Failed to handle invalid JobInfo", e);
                return RESULT_FAILURE;
            }
        }
    }
    
    private static Object executeFallback(Object who, Method method, Object[] args, String operation) {
        try {
            return method.invoke(who, args);
        } catch (Exception fallbackException) {
            ParallaxSlog.e(TAG, operation + ": Fallback also failed", fallbackException);
            return RESULT_FAILURE;
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}