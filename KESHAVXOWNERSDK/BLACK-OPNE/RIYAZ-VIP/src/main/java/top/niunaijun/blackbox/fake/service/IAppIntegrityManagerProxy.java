package top.niunaijun.blackbox.fake.service;

import java.lang.reflect.Method;
import java.util.Collections;

import black.android.content.integrity.BRIAppIntegrityManagerStub;
import black.android.os.BRServiceManager;

import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.compat.ParceledListSliceCompat;

public class IAppIntegrityManagerProxy extends BinderInvocationStub {

    private static final String SERVER_NAME = "app_integrity";

    public IAppIntegrityManagerProxy() {
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

    @ProxyMethod("updateRuleSet")
    public static class UpdateRuleSet extends MethodHook {
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

    @ProxyMethod("getCurrentRuleSetVersion")
    public static class GetCurrentRuleSetVersion extends MethodHook {
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

    @ProxyMethod("getCurrentRuleSetProvider")
    public static class GetCurrentRuleSetProvider extends MethodHook {
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

    @ProxyMethod("getCurrentRules")
    public static class GetCurrentRules extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Object real = method.invoke(who, args);
                if (real != null) return real;

                return ParceledListSliceCompat.create(Collections.emptyList());
            } catch (Throwable e) {
                try {
                    return ParceledListSliceCompat.create(Collections.emptyList());
                } catch (Throwable ex) {
                    return null;
                }
            }
        }
    }

    @ProxyMethod("getWhitelistedRuleProviders")
    public static class GetWhitelistedRuleProviders extends MethodHook {
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