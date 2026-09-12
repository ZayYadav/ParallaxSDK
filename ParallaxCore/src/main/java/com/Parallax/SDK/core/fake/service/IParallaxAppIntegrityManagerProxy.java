package com.Parallax.SDK.core.fake.service;

import java.lang.reflect.Method;
import java.util.Collections;

import com.Parallax.SDK.mirror.android.content.integrity.BRIAppIntegrityManagerStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;

import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.utils.compat.ParallaxParceledListSliceCompat;

public class IParallaxAppIntegrityManagerProxy extends ParallaxBinderInvocationStub {

    private static final String SERVER_NAME = "app_integrity";

    public IParallaxAppIntegrityManagerProxy() {
        super(BRServiceManager.get().getService(SERVER_NAME));
    }

    @Override
	protected Object getWho() {
		try {
			Object service = BRServiceManager.get().getService(SERVER_NAME);
			if (service == null) return null;
			return BRIAppIntegrityManagerStub.get().asInterface((android.os.IBinder) service);
		} catch (Throwable e) {
			return null;
		}
	}

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        // Always replace → some ROM send null proxy
        replaceSystemService(SERVER_NAME);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    /* ================= Hooks ================= */

    @ParallaxProxyMethod("updateRuleSet")
    public static class UpdateRuleSet extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                // ignore update but keep stability
                return null;
            } catch (Throwable e) {
                return method.invoke(who, args);
            }
        }
    }

    @ParallaxProxyMethod("getCurrentRuleSetVersion")
    public static class GetCurrentRuleSetVersion extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Object real = method.invoke(who, args);
                return real == null ? "unknown" : real;
            } catch (Throwable e) {
                return "unknown";
            }
        }
    }

    @ParallaxProxyMethod("getCurrentRuleSetProvider")
    public static class GetCurrentRuleSetProvider extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Object real = method.invoke(who, args);
                return real == null ? "system" : real;
            } catch (Throwable e) {
                return "system";
            }
        }
    }

    @ParallaxProxyMethod("getCurrentRules")
    public static class GetCurrentRules extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Object real = method.invoke(who, args);
                if (real != null) return real;

                return ParallaxParceledListSliceCompat.create(Collections.emptyList());
            } catch (Throwable e) {
                try {
                    return ParallaxParceledListSliceCompat.create(Collections.emptyList());
                } catch (Throwable ex) {
                    return null;
                }
            }
        }
    }

    @ParallaxProxyMethod("getWhitelistedRuleProviders")
    public static class GetWhitelistedRuleProviders extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Object real = method.invoke(who, args);
                return real == null ? Collections.emptyList() : real;
            } catch (Throwable e) {
                return Collections.emptyList();
            }
        }
    }
}