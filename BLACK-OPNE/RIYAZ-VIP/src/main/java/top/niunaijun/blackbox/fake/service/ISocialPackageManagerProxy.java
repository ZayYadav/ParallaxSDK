package top.niunaijun.blackbox.fake.service;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.env.AppSystemEnv;
import top.niunaijun.blackbox.fake.frameworks.BPackageManager;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.ParceledListSliceCompat;

/**
 * Package visibility compatibility for real social/auth providers.
 *
 * The virtual package manager remains authoritative for guest components. Real
 * Android results are merged only for packages already present in AppSystemEnv's
 * open/trusted allowlist. No package identity, signature or UID is fabricated.
 */
public final class ISocialPackageManagerProxy extends IPackageManagerProxy {
    private static final String TAG = "SocialPackageManager";

    @Override
    public void injectHook() {
        // Build the same package-manager proxy as IPackageManagerProxy first.
        super.injectHook();

        // When the runtime class is this subclass, ClassInvocationStub scans only
        // this class' declared inner classes. Restore all annotated hooks declared
        // by the parent, then install the social-specific overrides last.
        for (Class<?> declaredClass : IPackageManagerProxy.class.getDeclaredClasses()) {
            initAnnotation(declaredClass);
        }

        addMethodHook("queryIntentActivities", new QueryIntentActivities());
        addMethodHook("queryIntentServices", new QueryIntentServices());
        addMethodHook("queryIntentReceivers", new QueryIntentReceivers());
        addMethodHook("getInstalledApplications", new GetInstalledApplications());
        addMethodHook("getInstalledPackages", new GetInstalledPackages());
        addMethodHook("getPackagesForUid", new GetPackagesForUid());
        addMethodHook("checkUidSignatures", new CheckUidSignatures());
    }

    private static final class QueryIntentActivities extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = MethodParameterUtils.getFirstParam(args, Intent.class);
            String resolvedType = firstStringAfterIntent(args);
            int flags = firstFlags(args);

            List<ResolveInfo> merged = new ArrayList<>();
            if (intent != null) {
                List<ResolveInfo> virtual = BPackageManager.get().queryIntentActivities(
                        intent, flags, resolvedType, BActivityThread.getUserId());
                if (virtual != null) merged.addAll(virtual);
            }
            mergeTrustedResolveInfos(merged, unwrapList(method.invoke(who, args)), true);
            Slog.d(TAG, "queryIntentActivities trustedMerged=" + merged.size());
            return adaptListReturn(method, merged);
        }
    }

    private static final class QueryIntentServices extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = MethodParameterUtils.getFirstParam(args, Intent.class);
            int flags = firstFlags(args);

            List<ResolveInfo> merged = new ArrayList<>();
            try {
                if (intent != null && BPackageManager.get().getService() != null) {
                    List<ResolveInfo> virtual = BPackageManager.get().getService()
                            .queryIntentServices(intent, flags, BActivityThread.getUserId());
                    if (virtual != null) merged.addAll(virtual);
                }
            } catch (Throwable ignored) {
            }
            mergeTrustedResolveInfos(merged, unwrapList(method.invoke(who, args)), false);
            Slog.d(TAG, "queryIntentServices trustedMerged=" + merged.size());
            return adaptListReturn(method, merged);
        }
    }

    private static final class QueryIntentReceivers extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = MethodParameterUtils.getFirstParam(args, Intent.class);
            String resolvedType = firstStringAfterIntent(args);
            int flags = firstFlags(args);

            List<ResolveInfo> merged = new ArrayList<>();
            if (intent != null) {
                List<ResolveInfo> virtual = BPackageManager.get().queryBroadcastReceivers(
                        intent, flags, resolvedType, BActivityThread.getUserId());
                if (virtual != null) merged.addAll(virtual);
            }
            mergeTrustedResolveInfos(merged, unwrapList(method.invoke(who, args)), true);
            Slog.d(TAG, "queryIntentReceivers trustedMerged=" + merged.size());
            return adaptListReturn(method, merged);
        }
    }

    private static final class GetInstalledApplications extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int flags = firstFlags(args);
            List<ApplicationInfo> merged = new ArrayList<>(
                    BPackageManager.get().getInstalledApplications(flags, BActivityThread.getUserId()));
            Set<String> seen = new HashSet<>();
            for (ApplicationInfo info : merged) {
                if (info != null && info.packageName != null) seen.add(info.packageName);
            }
            for (Object item : unwrapList(method.invoke(who, args))) {
                if (!(item instanceof ApplicationInfo)) continue;
                ApplicationInfo info = (ApplicationInfo) item;
                if (info.packageName != null
                        && AppSystemEnv.isOpenPackage(info.packageName)
                        && seen.add(info.packageName)) {
                    merged.add(info);
                }
            }
            return adaptListReturn(method, merged);
        }
    }

    private static final class GetInstalledPackages extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int flags = firstFlags(args);
            List<PackageInfo> merged = new ArrayList<>(
                    BPackageManager.get().getInstalledPackages(flags, BActivityThread.getUserId()));
            Set<String> seen = new HashSet<>();
            for (PackageInfo info : merged) {
                if (info != null && info.packageName != null) seen.add(info.packageName);
            }
            for (Object item : unwrapList(method.invoke(who, args))) {
                if (!(item instanceof PackageInfo)) continue;
                PackageInfo info = (PackageInfo) item;
                if (info.packageName != null
                        && AppSystemEnv.isOpenPackage(info.packageName)
                        && seen.add(info.packageName)) {
                    merged.add(info);
                }
            }
            return adaptListReturn(method, merged);
        }
    }

    private static final class GetPackagesForUid extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int uid = firstInt(args, -1);
            if (uid == BlackBoxCore.getHostUid() || uid == BActivityThread.getBUid()) {
                String[] virtual = BPackageManager.get().getPackagesForUid(BActivityThread.getBUid());
                return virtual == null ? new String[0] : virtual;
            }

            Object raw = method.invoke(who, args);
            if (!(raw instanceof String[])) return new String[0];
            List<String> trusted = new ArrayList<>();
            for (String pkg : (String[]) raw) {
                if (AppSystemEnv.isOpenPackage(pkg)) trusted.add(pkg);
            }
            return trusted.toArray(new String[0]);
        }
    }

    /**
     * Never claim unrelated UIDs have matching signatures. Provider verification
     * remains owned by the real Android PackageManager.
     */
    private static final class CheckUidSignatures extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }

    private static void mergeTrustedResolveInfos(
            List<ResolveInfo> out, List<?> realValues, boolean activityLike) {
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : out) {
            String key = resolveKey(info, activityLike);
            if (key != null) seen.add(key);
        }
        for (Object item : realValues) {
            if (!(item instanceof ResolveInfo)) continue;
            ResolveInfo info = (ResolveInfo) item;
            String pkg = resolvePackage(info, activityLike);
            String key = resolveKey(info, activityLike);
            if (pkg != null && key != null
                    && AppSystemEnv.isOpenPackage(pkg)
                    && seen.add(key)) {
                out.add(info);
            }
        }
    }

    private static String resolvePackage(ResolveInfo info, boolean activityLike) {
        if (info == null) return null;
        if (activityLike) {
            ActivityInfo ai = info.activityInfo;
            return ai == null ? null : ai.packageName;
        }
        ServiceInfo si = info.serviceInfo;
        return si == null ? null : si.packageName;
    }

    private static String resolveKey(ResolveInfo info, boolean activityLike) {
        if (info == null) return null;
        if (activityLike && info.activityInfo != null) {
            return info.activityInfo.packageName + "/" + info.activityInfo.name;
        }
        if (!activityLike && info.serviceInfo != null) {
            return info.serviceInfo.packageName + "/" + info.serviceInfo.name;
        }
        return null;
    }

    private static List<?> unwrapList(Object raw) {
        if (raw == null) return new ArrayList<>();
        if (raw instanceof List) return (List<?>) raw;
        try {
            Method getList = raw.getClass().getMethod("getList");
            getList.setAccessible(true);
            Object value = getList.invoke(raw);
            return value instanceof List ? (List<?>) value : new ArrayList<>();
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
    }

    private static Object adaptListReturn(Method method, List<?> values) {
        if (ParceledListSliceCompat.isReturnParceledListSlice(method)) {
            return ParceledListSliceCompat.create(values);
        }
        return values;
    }

    private static String firstStringAfterIntent(Object[] args) {
        if (args == null) return null;
        boolean sawIntent = false;
        for (Object arg : args) {
            if (arg instanceof Intent) {
                sawIntent = true;
                continue;
            }
            if (sawIntent && arg instanceof String) return (String) arg;
        }
        return null;
    }

    private static int firstFlags(Object[] args) {
        if (args == null) return 0;
        boolean sawIntent = false;
        for (Object arg : args) {
            if (arg instanceof Intent) {
                sawIntent = true;
                continue;
            }
            if ((sawIntent || !containsIntent(args)) && arg instanceof Number) {
                return ((Number) arg).intValue();
            }
        }
        return 0;
    }

    private static int firstInt(Object[] args, int fallback) {
        if (args == null) return fallback;
        for (Object arg : args) {
            if (arg instanceof Integer) return (Integer) arg;
        }
        return fallback;
    }

    private static boolean containsIntent(Object[] args) {
        if (args == null) return false;
        for (Object arg : args) if (arg instanceof Intent) return true;
        return false;
    }
}
