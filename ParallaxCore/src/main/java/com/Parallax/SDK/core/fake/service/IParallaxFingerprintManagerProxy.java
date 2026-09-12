package com.Parallax.SDK.core.fake.service;

import android.content.Context;
import android.os.IBinder;

import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.mirror.android.view.BRIGraphicsStatsStub;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.service.base.ParallaxPkgMethodProxy;

public class IParallaxFingerprintManagerProxy extends ParallaxBinderInvocationStub {
	public IParallaxFingerprintManagerProxy() {
		super(BRServiceManager.get().getService(Context.FINGERPRINT_SERVICE));
	}

	@Override
	protected Object getWho() {
		return BRIGraphicsStatsStub.get().asInterface(BRServiceManager.get().getService(Context.FINGERPRINT_SERVICE));
	}

	@Override
	protected void inject(Object baseInvocation, Object proxyInvocation) {
		replaceSystemService(Context.FINGERPRINT_SERVICE);
	}

	@Override
	public boolean isBadEnv() {
		return false;
	}

	@Override
	protected void onBindMethod() {
		super.onBindMethod();
		addMethodHook(new ParallaxPkgMethodProxy("isHardwareDetected"));
		addMethodHook(new ParallaxPkgMethodProxy("hasEnrolledFingerprints"));
		addMethodHook(new ParallaxPkgMethodProxy("authenticate"));
		addMethodHook(new ParallaxPkgMethodProxy("cancelAuthentication"));
		addMethodHook(new ParallaxPkgMethodProxy("getEnrolledFingerprints"));
		addMethodHook(new ParallaxPkgMethodProxy("getAuthenticatorId"));
	}
}
