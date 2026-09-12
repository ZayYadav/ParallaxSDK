package com.Parallax.SDK.core.fake.service;

import android.content.ComponentName;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.mirror.android.view.BRIAutoFillManagerStub;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.proxy.ParallaxProxyManifest;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;

/**
 * Created by @RIYAZXERO on 4/8/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class IParallaxAutofillManagerProxy extends ParallaxBinderInvocationStub {
	public static final String TAG = "AutofillManagerStub";

	public IParallaxAutofillManagerProxy() {
		super(BRServiceManager.get().getService("autofill"));
	}

	@Override
	protected Object getWho() {
		return BRIAutoFillManagerStub.get().asInterface(BRServiceManager.get().getService("autofill"));
	}

	@Override
	protected void inject(Object baseInvocation, Object proxyInvocation) {
		replaceSystemService("autofill");
	}

	@Override
	public boolean isBadEnv() {
		return false;
	}

	@ParallaxProxyMethod("startSession")
	public static class StartSession extends ParallaxMethodHook {

		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			if (args != null) {
				for (int i = 0; i < args.length; i++) {
					if (args[i] == null) continue;
					if (args[i] instanceof ComponentName) {
						args[i] = new ComponentName(ParallaxCore.getHostPkg(),ParallaxProxyManifest.getProxyActivity(ParallaxActivityThread.getAppPid()));
					}
				}
			}
			return method.invoke(who, args);
		}
	}
}
