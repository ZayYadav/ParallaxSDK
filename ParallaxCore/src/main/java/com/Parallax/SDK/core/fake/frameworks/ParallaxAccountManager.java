package com.Parallax.SDK.core.fake.frameworks;

import android.accounts.Account;
import android.accounts.AuthenticatorDescription;
import android.accounts.IAccountManagerResponse;
import android.os.Bundle;
import android.os.RemoteException;

import java.util.Map;

import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.core.system.ParallaxServiceManager;
import com.Parallax.SDK.core.core.system.accounts.IParallaxAccountManagerService;

/**
 * Created by BlackBox on 2022/3/3.
 */
public class ParallaxAccountManager extends ParallaxBlackManager<IParallaxAccountManagerService> {
    private static final ParallaxAccountManager sBAccountManager = new ParallaxAccountManager();

    public static ParallaxAccountManager get() {
        return sBAccountManager;
    }

    @Override
    protected String getServiceName() {
        return ParallaxServiceManager.ACCOUNT_MANAGER;
    }

    public String getPassword(Account account) {
        try {
            return getService().getPassword(account, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }


    public String getUserData(Account account, String key) {
        try {
            return getService().getUserData(account, key, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public AuthenticatorDescription[] getAuthenticatorTypes() {
        try {
            return getService().getAuthenticatorTypes(ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Account[] getAccountsForPackage(String packageName, int uid) {
        try {
            return getService().getAccountsForPackage(packageName, uid, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Account[] getAccountsByTypeForPackage(String type, String packageName) {
        try {
            return getService().getAccountsByTypeForPackage(type, packageName, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Account[] getAccountsAsUser(String type) {
        try {
            return getService().getAccountsAsUser(type, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void getAccountByTypeAndFeatures(IAccountManagerResponse response, String accountType,
                                            String[] features) {
        try {
            getService().getAccountByTypeAndFeatures(response, accountType, features, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
    
    public void getAccountsByFeatures(IAccountManagerResponse response, String accountType,
                               String[] features) {
        try {
            getService().getAccountsByFeatures(response, accountType, features, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public boolean addAccountExplicitly(Account account, String password, Bundle extras) {
        try {
            return getService().addAccountExplicitly(account, password, extras, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void removeAccountAsUser(IAccountManagerResponse response, Account account,
                             boolean expectActivityLaunch) {
        try {
            getService().removeAccountAsUser(response, account, expectActivityLaunch, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public boolean removeAccountExplicitly(Account account) {
        try {
            return getService().removeAccountExplicitly(account, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void copyAccountToUser(IAccountManagerResponse response, Account account,
                           int userFrom, int userTo) {
        try {
            getService().copyAccountToUser(response, account, userFrom, userTo);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void invalidateAuthToken(String accountType, String authToken) {
        try {
            getService().invalidateAuthToken(accountType, authToken, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public String peekAuthToken(Account account, String authTokenType) {
        try {
            return getService().peekAuthToken(account, authTokenType, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setAuthToken(Account account, String authTokenType, String authToken) {
        try {
            getService().setAuthToken(account, authTokenType, authToken, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    public void setPassword(Account account, String password) {
        try {
            getService().setPassword(account, password, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void clearPassword(Account account) {
        try {
            getService().clearPassword(account, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void setUserData(Account account, String key, String value) {
        try {
            getService().setUserData(account, key, value, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void updateAppPermission(Account account, String authTokenType, int uid, boolean value) {
        try {
            getService().updateAppPermission(account, authTokenType, uid, value);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void getAuthToken(IAccountManagerResponse response, Account account,
                      String authTokenType, boolean notifyOnAuthFailure, boolean expectActivityLaunch,
                      Bundle options) {
        try {
            getService().getAuthToken(response, account, authTokenType, notifyOnAuthFailure, expectActivityLaunch, options, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void addAccount(IAccountManagerResponse response, String accountType,
                    String authTokenType, String[] requiredFeatures, boolean expectActivityLaunch,
                    Bundle options) {
        try {
            getService().addAccount(response, accountType, authTokenType, requiredFeatures, expectActivityLaunch, options, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void addAccountAsUser(IAccountManagerResponse response, String accountType,
                          String authTokenType, String[] requiredFeatures, boolean expectActivityLaunch,
                          Bundle options) {
        try {
            getService().addAccountAsUser(response, accountType, authTokenType, requiredFeatures, expectActivityLaunch, options, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void updateCredentials(IAccountManagerResponse response, Account account,
                           String authTokenType, boolean expectActivityLaunch, Bundle options) {
        try {
            getService().updateCredentials(response, account, authTokenType, expectActivityLaunch, options, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void editProperties(IAccountManagerResponse response, String accountType,
                        boolean expectActivityLaunch) {
        try {
            getService().editProperties(response, accountType, expectActivityLaunch, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void confirmCredentialsAsUser(IAccountManagerResponse response, Account account,
                                  Bundle options, boolean expectActivityLaunch) {
        try {
            getService().confirmCredentialsAsUser(response, account, options, expectActivityLaunch, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public boolean accountAuthenticated(Account account) {
        try {
            return getService().accountAuthenticated(account, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void getAuthTokenLabel(IAccountManagerResponse response, String accountType,
                           String authTokenType) {
        try {
            getService().getAuthTokenLabel(response, accountType, authTokenType, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /* Returns Map<String, Integer> from package name to visibility with all values stored for given account */
    public Map getPackagesAndVisibilityForAccount(Account account) {
        try {
            return getService().getPackagesAndVisibilityForAccount(account, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean addAccountExplicitlyWithVisibility(Account account, String password, Bundle extras,
                                               Map visibility) {
        try {
            return getService().addAccountExplicitlyWithVisibility(account, password, extras, visibility, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean setAccountVisibility(Account account, String packageName, int newVisibility) {
        try {
            return getService().setAccountVisibility(account, packageName, newVisibility, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    public int getAccountVisibility(Account account, String packageName) {
        try {
            return getService().getAccountVisibility(account, packageName, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        // AccountManager.VISIBILITY_NOT_VISIBLE
        return 3;
    }

    /* Type may be null returns Map <Account, Integer>*/
    public Map getAccountsAndVisibilityForPackage(String packageName, String accountType) {
        try {
            return getService().getAccountsAndVisibilityForPackage(packageName, accountType, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void registerAccountListener(String[] accountTypes, String opPackageName) {
        try {
            getService().registerAccountListener(accountTypes, opPackageName, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void unregisterAccountListener(String[] accountTypes, String opPackageName) {
        try {
            getService().unregisterAccountListener(accountTypes, opPackageName, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}
