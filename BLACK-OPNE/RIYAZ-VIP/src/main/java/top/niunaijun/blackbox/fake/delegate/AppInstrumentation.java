package top.niunaijun.blackbox.fake.delegate;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.util.Log;
import black.com.cosmos.apm.framework.page.BRActivityLifeCycleHelper$ApplicationInstrumentation;
import black.dalvik.system.BRBaseDexClassLoader;
import java.lang.reflect.Field;

import black.android.app.BRActivity;
import black.android.app.BRActivityThread;
import java.lang.reflect.Method;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.HookManager;
import top.niunaijun.blackbox.fake.hook.IInjectHook;
import top.niunaijun.blackbox.fake.service.HCallbackStub;
import top.niunaijun.blackbox.fake.service.IActivityClientProxy;
import top.niunaijun.blackbox.utils.HackAppUtils;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.ActivityCompat;
import top.niunaijun.blackbox.utils.compat.ActivityManagerCompat;
import top.niunaijun.blackbox.utils.compat.ContextCompat;

public final class AppInstrumentation extends BaseInstrumentationDelegate implements IInjectHook {
    
	private static final String TAG = AppInstrumentation.class.getSimpleName();
    private static final String RIFLE_APP_CLASS = "com.cosmos.apm.framework.page.ActivityLifeCycleHelper$ApplicationInstrumentation";
	private static AppInstrumentation sAppInstrumentation;
	private ClassLoader delegateAppClassLoader;

	private AppInstrumentation() {}

	public static AppInstrumentation get() {
		if (sAppInstrumentation == null) {
			synchronized (AppInstrumentation.class) {
				if (sAppInstrumentation == null) {
					sAppInstrumentation = new AppInstrumentation();
				}
			}
		}
		return sAppInstrumentation;
	}

	public ClassLoader getDelegateAppClassLoader() {
		return delegateAppClassLoader;
	}

	@Override
	public void injectHook() {
		try {
			Instrumentation mInstrumentation = getCurrInstrumentation();
			if (mInstrumentation == this || checkInstrumentation(mInstrumentation)) return;
			mBaseInstrumentation = (Instrumentation) mInstrumentation;
			BRActivityThread.get(BlackBoxCore.mainThread())._set_mInstrumentation(this);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void fixRifleHook() {
		Instrumentation mInstrumentation = getCurrInstrumentation();
		if (mInstrumentation instanceof AppInstrumentation) {
			return;
		}
		if (RIFLE_APP_CLASS.equals(mInstrumentation.getClass().getName())) {
			AppInstrumentation appInstrumentation = (AppInstrumentation) BRActivityLifeCycleHelper$ApplicationInstrumentation.get(mInstrumentation).mBase();
			if (appInstrumentation != null) mBaseInstrumentation = appInstrumentation.mBaseInstrumentation;
			BRActivityThread.get(BlackBoxCore.mainThread())._set_mInstrumentation(this);
		}
	}

	public void fixInstrumentationAfterApplicationOnCreate() {
		Instrumentation mInstrumentation = getCurrInstrumentation();
		if (mInstrumentation instanceof AppInstrumentation) {
			return;
		}
		Class clazz = mInstrumentation.getClass();
		Field[] fields = clazz.getDeclaredFields();
		for (Field field : fields) {
			if (Instrumentation.class.isAssignableFrom(field.getType())) {
				field.setAccessible(true);
				try {
					Object obj = field.get(mInstrumentation);
					if ((obj instanceof AppInstrumentation)) {
						Instrumentation mBaseInstrumentation = ((AppInstrumentation) obj).mBaseInstrumentation;
						field.set(mInstrumentation, mBaseInstrumentation);
						((AppInstrumentation) obj).mBaseInstrumentation = mInstrumentation;
						BRActivityThread.get(BlackBoxCore.mainThread())._set_mInstrumentation(obj);
					}
				} catch (Exception ignored) {
				}
				break;
			}
		}
	}

	public Instrumentation getCurrInstrumentation() {
		Object currentActivityThread = BlackBoxCore.mainThread();
		return BRActivityThread.get(currentActivityThread).mInstrumentation();
	}

	@Override
	public boolean isBadEnv() {
		return !checkInstrumentation(getCurrInstrumentation());
	}

	private boolean checkInstrumentation(Instrumentation instrumentation) {
		if (instrumentation instanceof AppInstrumentation) {
			return true;
		}
		Class<?> clazz = instrumentation.getClass();
		if (Instrumentation.class.equals(clazz)) {
			return false;
		}
		do {
			assert clazz != null;
			Field[] fields = clazz.getDeclaredFields();
			for (Field field : fields) {
				if (Instrumentation.class.isAssignableFrom(field.getType())) {
					field.setAccessible(true);
					try {
						Object obj = field.get(instrumentation);
						if ((obj instanceof AppInstrumentation)) {
							return true;
						}
					} catch (Exception e) {
						return false;
					}
				}
			}
			clazz = clazz.getSuperclass();
		} while (!Instrumentation.class.equals(clazz));
		return false;
	}

	private void checkHCallback() {
		HookManager.get().checkEnv(HCallbackStub.class);
	}

	private void checkActivity(Activity activity) {
		HackAppUtils.enableQQLogOutput(activity.getPackageName(), activity.getClassLoader());
		checkHCallback();
		HookManager.get().checkEnv(IActivityClientProxy.class);
		ActivityInfo info = BRActivity.get(activity).mActivityInfo();
		ContextCompat.fix(activity);
		ActivityCompat.fix(activity);
		if (info.theme != 0) {
			activity.getTheme().applyStyle(info.theme, true);
		}
		ActivityManagerCompat.setActivityOrientation(activity, info.screenOrientation);
	}

	@Override
	public Application newApplication(ClassLoader cl, String className, Context context)
	throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		ContextCompat.fix(context);
		//fixSharedLibraryLoaders(cl);
		BActivityThread.currentActivityThread().loadXposed(context);
		delegateAppClassLoader = context.getClassLoader();
		return super.newApplication(cl, className, context);
	}

	private void fixSharedLibraryLoaders(ClassLoader cl) {
		try {
			Object classLoaders = BRBaseDexClassLoader.get(ContextCompat.class.getClassLoader()).sharedLibraryLoaders();
			BRBaseDexClassLoader.get(cl)._set_sharedLibraryLoaders(classLoaders);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void callActivityOnCreate(Activity activity, Bundle icicle, PersistableBundle persistentState) {
		checkActivity(activity);
		super.callActivityOnCreate(activity, icicle, persistentState);
	}

	@Override
	public void callActivityOnCreate(Activity activity, Bundle icicle) {
		checkActivity(activity);
		super.callActivityOnCreate(activity, icicle);
	}

	@Override
	public void callApplicationOnCreate(Application app) {
		checkHCallback();
		super.callApplicationOnCreate(app);
	}
    
    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        try {
            return super.newActivity(cl, className, intent);
        } catch (ClassNotFoundException e) {
            return mBaseInstrumentation.newActivity(cl, className, intent);
        }
    }
}
