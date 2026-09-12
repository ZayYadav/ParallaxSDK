package com.Parallax.SDK.core.fake.service.base;

import java.lang.reflect.Method;

import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;

public class ParallaxPkgMethodProxy extends ParallaxMethodHook {

	String mName;

	public ParallaxPkgMethodProxy(String name) {
		mName = name;
	}

	@Override
	protected String getMethodName() {
		return mName;
	}

	@Override
	protected Object hook(Object who, Method method, Object[] args) throws Throwable {
		ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
		return method.invoke(who, args);
	}
}
