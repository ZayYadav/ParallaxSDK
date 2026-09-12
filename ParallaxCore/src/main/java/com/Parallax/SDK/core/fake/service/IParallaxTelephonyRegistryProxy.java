package com.Parallax.SDK.core.fake.service;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.mirror.com.android.internal.telephony.BRITelephonyRegistryStub;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;

/**
 * Created by @RIYAZXERO on 2021/5/17.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class IParallaxTelephonyRegistryProxy extends ParallaxBinderInvocationStub {
	public IParallaxTelephonyRegistryProxy() {
		super(BRServiceManager.get().getService("telephony.registry"));
	}

	@Override
	protected Object getWho() {
		return BRITelephonyRegistryStub.get().asInterface(BRServiceManager.get().getService("telephony.registry"));
	}

	@Override
	protected void inject(Object baseInvocation, Object proxyInvocation) {
		replaceSystemService("telephony.registry");
	}

	@Override
	public boolean isBadEnv() {
		return false;
	}

	@ParallaxProxyMethod("listenForSubscriber")
	public static class ListenForSubscriber extends ParallaxMethodHook {

		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
			return method.invoke(who, args);
		}
	}

	@ParallaxProxyMethod("listen")
	public static class Listen extends ParallaxMethodHook {

		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Throwable {
			ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
			return method.invoke(who, args);
		}
	}
}
