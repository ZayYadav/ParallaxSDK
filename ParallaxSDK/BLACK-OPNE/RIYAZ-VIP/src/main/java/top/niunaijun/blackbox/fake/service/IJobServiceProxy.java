package top.niunaijun.blackbox.fake.service;

import android.app.job.JobInfo;
import android.content.Context;
import android.os.IBinder;

import java.lang.reflect.Method;

import black.android.app.job.BRIJobSchedulerStub;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.UIDSpoofingHelper;

/**
 * IJobService Proxy to handle job scheduling in sandboxed environments
 * This prevents UID mismatch crashes when scheduling background jobs
 */
public class IJobServiceProxy extends BinderInvocationStub {
    private static final String TAG = "JobServiceStub";
    private static final String SERVICE_NAME = "jobscheduler";
    private static final int RESULT_FAILURE = 0;

    public IJobServiceProxy() {
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
    
    private static abstract class BaseJobHandler extends MethodHook {
        protected Object processJobOperation(String operation, Object who, Method method, Object[] args) throws Throwable {
            if (!validateJobArgs(args)) {
                Slog.w(TAG, operation + ": Invalid arguments, returning failure");
                return RESULT_FAILURE;
            }
            JobInfo jobInfo = (JobInfo) args[0];
            String packageName = jobInfo.getService().getPackageName();
            Slog.d(TAG, operation + ": Processing JobInfo for package: " + packageName);
            try {
                JobInfo proxyJobInfo = BlackBoxCore.getBJobManager().schedule(jobInfo);
                if (proxyJobInfo != null) {
                    args[0] = proxyJobInfo;
                    Slog.d(TAG, operation + ": Successfully created proxy JobInfo");
                    return method.invoke(who, args);
                }
            } catch (Exception e) {
                Slog.w(TAG, operation + ": BlackBox job manager failed, trying fallback", e);
            }
            return handleWithUIDSpoofing(operation, who, method, args, jobInfo);
        }

        protected boolean validateJobArgs(Object[] args) {
            if (args == null || args.length == 0 || args[0] == null) {
                return false;
            }
            
            if (!(args[0] instanceof JobInfo)) {
                Slog.w(TAG, "Argument is not JobInfo: " + args[0].getClass().getSimpleName());
                return false;
            }
            
            JobInfo jobInfo = (JobInfo) args[0];
            return jobInfo.getService() != null;
        }

        protected Object handleWithUIDSpoofing(String operation, Object who, Method method, Object[] args, JobInfo jobInfo) throws Throwable {
            String targetPackage = jobInfo.getService().getPackageName();
            Slog.d(TAG, operation + ": Attempting UID spoofing for package: " + targetPackage);
            UIDSpoofingHelper.logUIDInfo("job_" + operation.toLowerCase(), targetPackage);
            if (UIDSpoofingHelper.needsUIDSpoofing("job_" + operation.toLowerCase(), targetPackage)) {
                Slog.d(TAG, operation + ": UID spoofing needed");
                return RESULT_FAILURE;
            }
            Slog.d(TAG, operation + ": No UID spoofing needed, proceeding normally");
            return method.invoke(who, args);
        }

        protected boolean isUIDValidationError(Exception e) {
            if (e.getCause() == null) return false;
            String message = e.getCause().getMessage();
            return message != null && message.contains("cannot schedule job");
        }
    }

    @ProxyMethod("schedule")
    public static class Schedule extends BaseJobHandler {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                if (!validateJobArgs(args)) {
                    return handleInvalidJobInfo("schedule", who, method, args);
                }
                return processJobOperation("Schedule", who, method, args);
            } catch (Exception e) {
                Slog.e(TAG, "Schedule: Error processing job", e);
                if (isUIDValidationError(e)) {
                    Slog.w(TAG, "UID validation failed for job scheduling");
                    return RESULT_FAILURE;
                }
                return executeFallback(who, method, args, "Schedule");
            }
        }

        private Object handleInvalidJobInfo(String operation, Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof String) {
                String workId = (String) args[0];
                Slog.d(TAG, operation + ": Handling WorkManager string ID: " + workId);
            }
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, operation + ": Failed to handle invalid JobInfo", e);
                return RESULT_FAILURE;
            }
        }
    }

    @ProxyMethod("cancel")
    public static class Cancel extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                if (args == null || args.length == 0 || !(args[0] instanceof Integer)) {
                    Slog.w(TAG, "Cancel: Invalid arguments");
                    return method.invoke(who, args);
                }
                int jobId = (Integer) args[0];
                String processName = BActivityThread.getAppConfig().processName;
                int cancelledJobId = BlackBoxCore.getBJobManager().cancel(processName, jobId);
                args[0] = cancelledJobId;
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.e(TAG, "Cancel: Error canceling job", e);
                return method.invoke(who, args);
            }
        }
    }

    @ProxyMethod("cancelAll")
    public static class CancelAll extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                String processName = BActivityThread.getAppConfig().processName;
                BlackBoxCore.getBJobManager().cancelAll(processName);
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.e(TAG, "CancelAll: Error canceling all jobs", e);
                return method.invoke(who, args);
            }
        }
    }

    @ProxyMethod("enqueue")
    public static class Enqueue extends BaseJobHandler {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                if (!validateJobArgs(args)) {
                    return handleInvalidJobInfo("enqueue", who, method, args);
                }
                return processJobOperation("Enqueue", who, method, args);
            } catch (Exception e) {
                Slog.e(TAG, "Enqueue: Error processing job", e);
                if (isUIDValidationError(e)) {
                    Slog.w(TAG, "UID validation failed for job enqueuing");
                    return RESULT_FAILURE;
                }
                return executeFallback(who, method, args, "Enqueue");
            }
        }

        private Object handleInvalidJobInfo(String operation, Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof String) {
                String workId = (String) args[0];
                Slog.d(TAG, operation + ": Handling WorkManager string ID: " + workId);
            }
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, operation + ": Failed to handle invalid JobInfo", e);
                return RESULT_FAILURE;
            }
        }
    }
    
    private static Object executeFallback(Object who, Method method, Object[] args, String operation) {
        try {
            return method.invoke(who, args);
        } catch (Exception fallbackException) {
            Slog.e(TAG, operation + ": Fallback also failed", fallbackException);
            return RESULT_FAILURE;
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}