package com.Parallax.SDK.core.fake.service;

import android.accounts.Account;
import android.accounts.IAccountManagerResponse;
import android.content.Context;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.util.Map;

import com.Parallax.SDK.mirror.android.accounts.BRIAccountManagerStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.fake.frameworks.ParallaxAccountManager;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.utils.ParallaxSlog;

/**
 * Created by @RIYAZXERO on 2022/2/14.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class IParallaxAccountManagerProxy extends ParallaxBinderInvocationStub {
    public static final String TAG = "IParallaxAccountManagerProxy";

    public IParallaxAccountManagerProxy() {
        super(BRServiceManager.get().getService(Context.ACCOUNT_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIAccountManagerStub.get().asInterface(BRServiceManager.get().getService(Context.ACCOUNT_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.ACCOUNT_SERVICE);
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        ParallaxSlog.d(TAG, "call " + method.getName());
        return super.invoke(proxy, method, args);
    }

    @ParallaxProxyMethod("getPassword")
    public static class getPassword extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParallaxAccountManager.get().getPassword((Account) args[0]);
        }
    }

    @ParallaxProxyMethod("getUserData")
    public static class getUserData extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParallaxAccountManager.get().getUserData((Account) args[0], (String) args[1]);
        }
    }

    @ParallaxProxyMethod("getAuthenticatorTypes")
    public static class getAuthenticatorTypes extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParallaxAccountManager.get().getAuthenticatorTypes();
        }
    }

    @ParallaxProxyMethod("getAccountsForPackage")
    public static class getAccountsForPackage extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParallaxAccountManager.get().getAccountsForPackage((String) args[0], (int) args[1]);
        }
    }

    @ParallaxProxyMethod("getAccountsByTypeForPackage")
    public static class getAccountsByTypeForPackage extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParallaxAccountManager.get().getAccountsByTypeForPackage((String) args[0], (String) args[1]);
        }
    }

    @ParallaxProxyMethod("getAccountByTypeAndFeatures")
    public static class getAccountByTypeAndFeatures extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxAccountManager.get().getAccountByTypeAndFeatures((IAccountManagerResponse) args[0], (String) args[1], (String[]) args[2]);
            return 0;
        }
    }

    @ParallaxProxyMethod("getAccountsByFeatures")
    public static class getAccountsByFeatures extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxAccountManager.get().getAccountsByFeatures((IAccountManagerResponse) args[0], (String) args[1], (String[]) args[2]);
            return 0;
        }
    }

    @ParallaxProxyMethod("getAccountsAsUser")
    public static class getAccountsAsUser extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParallaxAccountManager.get().getAccountsAsUser((String) args[0]);
        }
    }

    @ParallaxProxyMethod("addAccountExplicitly")
    public static class addAccountExplicitly extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParallaxAccountManager.get().addAccountExplicitly((Account) args[0], (String) args[1], (Bundle) args[2]);
        }
    }

    @ParallaxProxyMethod("removeAccountAsUser")
    public static class removeAccountAsUser extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxAccountManager.get().removeAccountAsUser((IAccountManagerResponse) args[0], (Account) args[1], (boolean) args[2]);
            return 0;
        }
    }

    @ParallaxProxyMethod("removeAccountExplicitly")
    public static class removeAccountExplicitly extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParallaxAccountManager.get().removeAccountExplicitly((Account) args[0]);
        }
    }

    @ParallaxProxyMethod("copyAccountToUser")
    public static class copyAccountToUser extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxAccountManager.get().copyAccountToUser((IAccountManagerResponse) args[0], (Account) args[1], (int) args[2], (int) args[3]);
            return 0;
        }
    }

    @ParallaxProxyMethod("invalidateAuthToken")
    public static class invalidateAuthToken extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxAccountManager.get().invalidateAuthToken((String) args[0], (String) args[1]);
            return 0;
        }
    }

    @ParallaxProxyMethod("peekAuthToken")
    public static class peekAuthToken extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParallaxAccountManager.get().peekAuthToken((Account) args[0], (String) args[1]);
        }
    }

    @ParallaxProxyMethod("setAuthToken")
    public static class setAuthToken extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxAccountManager.get().setAuthToken((Account) args[0], (String) args[1], (String) args[2]);
            return 0;
        }
    }

    @ParallaxProxyMethod("setPassword")
    public static class setPassword extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxAccountManager.get().setPassword((Account) args[0], (String) args[1]);
            return 0;
        }
    }

    @ParallaxProxyMethod("clearPassword")
    public static class clearPassword extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxAccountManager.get().clearPassword((Account) args[0]);
            return 0;
        }
    }

    @ParallaxProxyMethod("setUserData")
    public static class setUserData extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxAccountManager.get().setUserData((Account) args[0], (String) args[1], (String) args[2]);
            return 0;
        }
    }

    @ParallaxProxyMethod("updateAppPermission")
    public static class updateAppPermission extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxAccountManager.get().updateAppPermission((Account) args[0], (String) args[1], (int) args[2], (boolean) args[3]);
            return 0;
        }
    }

    @ParallaxProxyMethod("getAuthToken")
    public static class getAuthToken extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxAccountManager.get().getAuthToken((IAccountManagerResponse) args[0],(Account) args[1],(String) args[2],(boolean) args[3],(boolean) args[4],(Bundle) args[5]);
            return 0;
        }
    }

    @ParallaxProxyMethod("addAccount")
    public static class addAccount extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxAccountManager.get().addAccount((IAccountManagerResponse) args[0],
                    (String) args[1],
                    (String) args[2],
                    (String[]) args[3],
                    (boolean) args[4],
                    (Bundle) args[5]);
            return 0;
        }
    }

    @ParallaxProxyMethod("addAccountAsUser")
    public static class addAccountAsUser extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxAccountManager.get().addAccountAsUser((IAccountManagerResponse) args[0],
                    (String) args[1],
                    (String) args[2],
                    (String[]) args[3],
                    (boolean) args[4],
                    (Bundle) args[5]);
            return 0;
        }
    }

    @ParallaxProxyMethod("updateCredentials")
    public static class updateCredentials extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxAccountManager.get().updateCredentials((IAccountManagerResponse) args[0],
                    (Account) args[1],
                    (String) args[2],
                    (boolean) args[3],
                    (Bundle) args[4]);
            return 0;
        }
    }

    @ParallaxProxyMethod("editProperties")
    public static class editProperties extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxAccountManager.get().editProperties((IAccountManagerResponse) args[0],
                    (String) args[1],
                    (boolean) args[2]);
            return 0;
        }
    }

    @ParallaxProxyMethod("confirmCredentialsAsUser")
    public static class confirmCredentialsAsUser extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxAccountManager.get().confirmCredentialsAsUser((IAccountManagerResponse) args[0],
                    (Account) args[1],
                    (Bundle) args[2],
                    (boolean) args[3]);
            return 0;
        }
    }

    @ParallaxProxyMethod("accountAuthenticated")
    public static class accountAuthenticated extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxAccountManager.get().accountAuthenticated((Account) args[0]);
            return 0;
        }
    }

    @ParallaxProxyMethod("getAuthTokenLabel")
    public static class getAuthTokenLabel extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxAccountManager.get().getAuthTokenLabel((IAccountManagerResponse) args[0],
                    (String) args[1],
                    (String) args[2]);
            return 0;
        }
    }

    @ParallaxProxyMethod("getPackagesAndVisibilityForAccount")
    public static class getPackagesAndVisibilityForAccount extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParallaxAccountManager.get().getPackagesAndVisibilityForAccount((Account) args[0]);
        }
    }

    @ParallaxProxyMethod("addAccountExplicitlyWithVisibility")
    public static class addAccountExplicitlyWithVisibility extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParallaxAccountManager.get().addAccountExplicitlyWithVisibility((Account) args[0],
                    (String) args[1],
                    (Bundle) args[2],
                    (Map) args[3]
            );
        }
    }

    @ParallaxProxyMethod("setAccountVisibility")
    public static class setAccountVisibility extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParallaxAccountManager.get().setAccountVisibility((Account) args[0],
                    (String) args[1],
                    (int) args[2]
            );
        }
    }

    @ParallaxProxyMethod("getAccountVisibility")
    public static class getAccountVisibility extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParallaxAccountManager.get().getAccountVisibility((Account) args[0],
                    (String) args[1]
            );
        }
    }

    @ParallaxProxyMethod("getAccountsAndVisibilityForPackage")
    public static class getAccountsAndVisibilityForPackage extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParallaxAccountManager.get().getAccountsAndVisibilityForPackage((String) args[0],
                    (String) args[1]
            );
        }
    }

    @ParallaxProxyMethod("registerAccountListener")
    public static class registerAccountListener extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxAccountManager.get().registerAccountListener((String[]) args[0],
                    (String) args[1]
            );
            return 0;
        }
    }

    @ParallaxProxyMethod("unregisterAccountListener")
    public static class unregisterAccountListener extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxAccountManager.get().unregisterAccountListener((String[]) args[0],
                    (String) args[1]
            );
            return 0;
        }
    }
}
