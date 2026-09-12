package com.Parallax.SDK.core.fake.delegate;

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
import com.Parallax.SDK.mirror.com.cosmos.apm.framework.page.BRActivityLifeCycleHelper$ApplicationInstrumentation;
import com.Parallax.SDK.mirror.dalvik.system.BRBaseDexClassLoader;
import java.lang.reflect.Field;

import com.Parallax.SDK.mirror.android.app.BRActivity;
import com.Parallax.SDK.mirror.android.app.BRActivityThread;
import java.lang.reflect.Method;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.fake.hook.ParallaxHookManager;
import com.Parallax.SDK.core.fake.hook.IParallaxInjectHook;
import com.Parallax.SDK.core.fake.service.ParallaxHCallbackStub;
import com.Parallax.SDK.core.fake.service.IParallaxActivityClientProxy;
import com.Parallax.SDK.core.utils.ParallaxHackAppUtils;
import com.Parallax.SDK.core.utils.ParallaxSlog;
import com.Parallax.SDK.core.utils.compat.ParallaxActivityCompat;
import com.Parallax.SDK.core.utils.compat.ParallaxActivityManagerCompat;
import com.Parallax.SDK.core.utils.compat.ParallaxContextCompat;

public final class ParallaxAppInstrumentation extends ParallaxBaseInstrumentationDelegate implements IParallaxInjectHook {
    
	private static final String TAG = ParallaxAppInstrumentation.class.getSimpleName();
    private static final String RIFLE_APP_CLASS = "com.cosmos.apm.framework.page.ActivityLifeCycleHelper$ApplicationInstrumentation";
	private static ParallaxAppInstrumentation sAppInstrumentation;
	private ClassLoader delegateAppClassLoader;

	private ParallaxAppInstrumentation() {}

	public static ParallaxAppInstrumentation get() {
		if (sAppInstrumentation == null) {
			synchronized (ParallaxAppInstrumentation.class) {
				if (sAppInstrumentation == null) {
					sAppInstrumentation = new ParallaxAppInstrumentation();
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
			BRActivityThread.get(ParallaxCore.mainThread())._set_mInstrumentation(this);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void fixRifleHook() {
		Instrumentation mInstrumentation = getCurrInstrumentation();
		if (mInstrumentation instanceof ParallaxAppInstrumentation) {
			return;
		}
		if (RIFLE_APP_CLASS.equals(mInstrumentation.getClass().getName())) {
			ParallaxAppInstrumentation appInstrumentation = (ParallaxAppInstrumentation) BRActivityLifeCycleHelper$ApplicationInstrumentation.get(mInstrumentation).mBase();
			if (appInstrumentation != null) mBaseInstrumentation = appInstrumentation.mBaseInstrumentation;
			BRActivityThread.get(ParallaxCore.mainThread())._set_mInstrumentation(this);
		}
	}

	public void fixInstrumentationAfterApplicationOnCreate() {
		Instrumentation mInstrumentation = getCurrInstrumentation();
		if (mInstrumentation instanceof ParallaxAppInstrumentation) {
			return;
		}
		Class clazz = mInstrumentation.getClass();
		Field[] fields = clazz.getDeclaredFields();
		for (Field field : fields) {
			if (Instrumentation.class.isAssignableFrom(field.getType())) {
				field.setAccessible(true);
				try {
					Object obj = field.get(mInstrumentation);
					if ((obj instanceof ParallaxAppInstrumentation)) {
						Instrumentation mBaseInstrumentation = ((ParallaxAppInstrumentation) obj).mBaseInstrumentation;
						field.set(mInstrumentation, mBaseInstrumentation);
						((ParallaxAppInstrumentation) obj).mBaseInstrumentation = mInstrumentation;
						BRActivityThread.get(ParallaxCore.mainThread())._set_mInstrumentation(obj);
					}
				} catch (Exception ignored) {
				}
				break;
			}
		}
	}

	public Instrumentation getCurrInstrumentation() {
		Object currentActivityThread = ParallaxCore.mainThread();
		return BRActivityThread.get(currentActivityThread).mInstrumentation();
	}

	@Override
	public boolean isBadEnv() {
		return !checkInstrumentation(getCurrInstrumentation());
	}

	private boolean checkInstrumentation(Instrumentation instrumentation) {
		if (instrumentation instanceof ParallaxAppInstrumentation) {
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
						if ((obj instanceof ParallaxAppInstrumentation)) {
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
		ParallaxHookManager.get().checkEnv(ParallaxHCallbackStub.class);
	}

	private void checkActivity(Activity activity) {
		ParallaxHackAppUtils.enableQQLogOutput(activity.getPackageName(), activity.getClassLoader());
		checkHCallback();
		ParallaxHookManager.get().checkEnv(IParallaxActivityClientProxy.class);
		ActivityInfo info = BRActivity.get(activity).mActivityInfo();
		ParallaxContextCompat.fix(activity);
		ParallaxActivityCompat.fix(activity);
		if (info.theme != 0) {
			activity.getTheme().applyStyle(info.theme, true);
		}
		ParallaxActivityManagerCompat.setActivityOrientation(activity, info.screenOrientation);
	}

	@Override
	public Application newApplication(ClassLoader cl, String className, Context context)
	throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		ParallaxContextCompat.fix(context);
		//fixSharedLibraryLoaders(cl);
		ParallaxActivityThread.currentActivityThread().loadXposed(context);
		delegateAppClassLoader = context.getClassLoader();
		return super.newApplication(cl, className, context);
	}

	private void fixSharedLibraryLoaders(ClassLoader cl) {
		try {
			Object classLoaders = BRBaseDexClassLoader.get(ParallaxContextCompat.class.getClassLoader()).sharedLibraryLoaders();
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
