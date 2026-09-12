package com.Parallax.SDK.core.fake.service.base;

import java.lang.reflect.Method;

import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;

public class ParallaxValueMethodProxy extends ParallaxMethodHook {

	Object mValue;
	String mName;

	public ParallaxValueMethodProxy(String name, Object value) {
		mValue = value;
		mName = name;
	}

	@Override
	protected String getMethodName() {
		return mName;
	}

	@Override
	protected Object hook(Object who, Method method, Object[] args) throws Throwable {
		return mValue;
	}
}
