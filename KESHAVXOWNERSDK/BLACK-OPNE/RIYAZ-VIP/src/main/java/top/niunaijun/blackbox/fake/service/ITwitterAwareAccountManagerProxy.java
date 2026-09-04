package top.niunaijun.blackbox.fake.service;

import android.accounts.Account;
import android.accounts.AuthenticatorDescription;
import android.os.Bundle;

import org.lsposed.lsparanoid.Obfuscate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.ScanClass;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;

/**
 * Twitter/X-aware AccountManager bridge.
 *
 * The normal BlackBox AccountManager remains fully virtualized. Only the
 * interactive Twitter/X authentication surface is allowed to reach Android's
 * real AccountManager so a virtual app can use the official Twitter/X
 * authenticator installed on the phone.
 *
 * Security properties:
 *  - package/signature identity is never spoofed; Android sees the real host UID;
 *  - host package/user values are supplied where AccountManager requires them;
 *  - raw password, peekAuthToken, setAuthToken, setPassword and user-data calls
 *    remain virtual and can never read the phone account store through this class;
 *  - getAuthToken is passed through only when the caller requested an interactive
 *    activity-capable flow (expectActivityLaunch=true).
 *
 * Android's own account visibility and authenticator authorization checks remain
 * authoritative. If the official provider does not grant access, this bridge
 * cannot manufacture access.
 */
@Obfuscate
@ScanClass(IAccountManagerProxy.class)
public final class ITwitterAwareAccountManagerProxy extends IAccountManagerProxy {
    private static final String TAG = "TwitterAccountBridge";

    public static final String TWITTER_ACCOUNT_TYPE = "com.twitter.android.auth.login";
    public static final String X_ACCOUNT_TYPE = "com.x.android.auth.login";

    private static final Set<String> TWITTER_ACCOUNT_TYPES = new HashSet<>();
    private static final Set<String> TWITTER_PROVIDER_PACKAGES = new HashSet<>();

    static {
        TWITTER_ACCOUNT_TYPES.add(TWITTER_ACCOUNT_TYPE);
        TWITTER_ACCOUNT_TYPES.add(X_ACCOUNT_TYPE);

        TWITTER_PROVIDER_PACKAGES.add("com.twitter.android");
        TWITTER_PROVIDER_PACKAGES.add("com.twitter.android.lite");
        TWITTER_PROVIDER_PACKAGES.add("com.x.android");
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        final String name = method.getName();

        // Keep the virtual authenticator list, but append only the real Twitter/X
        // authenticator. Other phone account types remain private to the host OS.
        if ("getAuthenticatorTypes".equals(name)) {
            Object virtualResult = super.invoke(proxy, method, args);
            try {
                Object realResult = invokeSystem(method, args, true);
                return mergeTwitterAuthenticatorTypes(virtualResult, realResult);
            } catch (Throwable e) {
                Slog.d(TAG, "real authenticator lookup unavailable: " + e.getClass().getSimpleName());
                return virtualResult;
            }
        }

        if (shouldUseRealTwitterAuthenticator(name, args)) {
            try {
                boolean replaceUserId = hasExplicitUserId(name);
                return invokeSystem(method, args, replaceUserId);
            } catch (Throwable e) {
                // Do not silently turn an authorization failure into a fake token.
                // Falling back to the virtual AccountManager is safe and preserves
                // the pre-existing behavior for devices/provider versions that do
                // not expose the Android authenticator API.
                Slog.d(TAG, "native Twitter account call failed for " + name
                        + ": " + e.getClass().getSimpleName());
            }
        }

        return super.invoke(proxy, method, args);
    }

    private Object invokeSystem(Method method, Object[] originalArgs, boolean replaceUserId)
            throws Throwable {
        Object[] hostArgs = originalArgs == null ? null : originalArgs.clone();
        if (hostArgs != null) {
            // Android validates package names against the real Linux caller UID.
            // A virtual package name therefore must not be presented as the
            // AccountManager caller package.
            MethodParameterUtils.replaceAllAppPkg(hostArgs);
            sanitizeOptionBundles(hostArgs);
            if (replaceUserId) {
                MethodParameterUtils.replaceLastUserId(hostArgs);
            }
        }

        try {
            return method.invoke(getBase(), hostArgs);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw cause != null ? cause : e;
        }
    }

    private static void sanitizeOptionBundles(Object[] args) {
        if (args == null) return;
        for (int i = 0; i < args.length; i++) {
            if (!(args[i] instanceof Bundle)) continue;
            Bundle source = (Bundle) args[i];
            Bundle copy = new Bundle(source);
            // AccountManager.KEY_ANDROID_PACKAGE_NAME is hidden on some SDK
            // levels; use its stable platform key to avoid hidden-API coupling.
            copy.putString("androidPackageName", BlackBoxCore.getHostPkg());
            args[i] = copy;
        }
    }

    private static boolean shouldUseRealTwitterAuthenticator(String name, Object[] args) {
        switch (name) {
            case "getAccountsAsUser":
            case "getAccountsByTypeForPackage":
                return isTwitterAccountType(arg(args, 0));

            case "getAccountByTypeAndFeatures":
            case "getAccountsByFeatures":
            case "getAuthTokenLabel":
                return isTwitterAccountType(arg(args, 1));

            case "getAuthToken":
                return isTwitterAccount(arg(args, 1))
                        && booleanArg(args, 4, false);

            case "addAccount":
            case "addAccountAsUser":
                return isTwitterAccountType(arg(args, 1))
                        && booleanArg(args, 4, false);

            case "updateCredentials":
                return isTwitterAccount(arg(args, 1))
                        && booleanArg(args, 3, false);

            case "confirmCredentialsAsUser":
                return isTwitterAccount(arg(args, 1))
                        && booleanArg(args, 3, false);

            case "accountAuthenticated":
            case "getAccountVisibility":
                return isTwitterAccount(arg(args, 0));

            case "getAccountsAndVisibilityForPackage":
                return isTwitterAccountType(arg(args, 1));

            default:
                return false;
        }
    }

    private static boolean hasExplicitUserId(String methodName) {
        return "getAuthenticatorTypes".equals(methodName)
                || "getAccountsAsUser".equals(methodName)
                || "addAccountAsUser".equals(methodName)
                || "confirmCredentialsAsUser".equals(methodName);
    }

    private static Object arg(Object[] args, int index) {
        if (args == null || index < 0 || index >= args.length) return null;
        return args[index];
    }

    private static boolean booleanArg(Object[] args, int index, boolean fallback) {
        Object value = arg(args, index);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static boolean isTwitterAccount(Object value) {
        return value instanceof Account && isTwitterAccountType(((Account) value).type);
    }

    private static boolean isTwitterAccountType(Object value) {
        return value instanceof String && TWITTER_ACCOUNT_TYPES.contains(value);
    }

    private static Object mergeTwitterAuthenticatorTypes(Object virtualResult, Object realResult) {
        AuthenticatorDescription[] virtualTypes = virtualResult instanceof AuthenticatorDescription[]
                ? (AuthenticatorDescription[]) virtualResult
                : new AuthenticatorDescription[0];
        AuthenticatorDescription[] realTypes = realResult instanceof AuthenticatorDescription[]
                ? (AuthenticatorDescription[]) realResult
                : new AuthenticatorDescription[0];

        List<AuthenticatorDescription> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (AuthenticatorDescription description : virtualTypes) {
            if (description == null) continue;
            merged.add(description);
            if (description.type != null) seen.add(description.type);
        }

        for (AuthenticatorDescription description : realTypes) {
            if (!isTrustedTwitterAuthenticator(description)) continue;
            if (seen.add(description.type)) {
                merged.add(description);
            }
        }

        return merged.toArray(new AuthenticatorDescription[0]);
    }

    private static boolean isTrustedTwitterAuthenticator(AuthenticatorDescription description) {
        return description != null
                && TWITTER_ACCOUNT_TYPES.contains(description.type)
                && TWITTER_PROVIDER_PACKAGES.contains(description.packageName);
    }
}
