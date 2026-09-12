package com.Parallax.SDK.core.fake.service.context.providers;

import android.os.IInterface;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.content.BRAttributionSource;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.fake.hook.ParallaxClassInvocationStub;
import com.Parallax.SDK.core.utils.compat.ParallaxContextCompat;

/**
 * Created by @RIYAZXERO on 4/8/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxSystemProviderStub extends ParallaxClassInvocationStub implements ParallaxContentProvider {
	private IInterface mBase;

	@Override
	public IInterface wrapper(IInterface contentProviderProxy, String appPkg) {
		mBase = contentProviderProxy;
		injectHook();
		return (IInterface) getProxyInvocation();
	}

	@Override
	protected Object getWho() {
		return mBase;
	}

	@Override
	protected void inject(Object baseInvocation, Object proxyInvocation) {}

	@Override
	protected void onBindMethod() {}

	@Override
	public boolean isBadEnv() {
		return false;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		if ("asBinder".equals(method.getName())) {
			return method.invoke(mBase, args);
		}
		if (args != null && args.length > 0) {
			Object arg = args[0];
			if (arg instanceof String) {
				args[0] = ParallaxCore.getHostPkg();
			} else if (arg.getClass().getName().equals(BRAttributionSource.getRealClass().getName())) {
				ParallaxContextCompat.fixAttributionSourceState(arg, ParallaxCore.getHostUid());
			}
		}
		return method.invoke(mBase, args);
	}
}
