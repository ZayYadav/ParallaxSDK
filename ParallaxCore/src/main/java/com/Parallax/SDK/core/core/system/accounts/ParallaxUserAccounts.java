package com.Parallax.SDK.core.core.system.accounts;

import android.accounts.Account;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by BlackBox on 2022/3/3.
 */
public class ParallaxUserAccounts implements Parcelable {
    public final Object lock = new Object();

    public int userId;
    public List<ParallaxAccount> accounts = new ArrayList<>();

    public Account[] toAccounts() {
        List<Account> local = new ArrayList<>();
        for (ParallaxAccount account : accounts) {
            local.add(account.account);
        }
        return local.toArray(new Account[]{});
    }

    public ParallaxAccount addAccount(Account account) {
        ParallaxAccount bAccount = new ParallaxAccount();
        bAccount.account = account;
        accounts.add(bAccount);
        return bAccount;
    }

    public ParallaxAccount getAccount(Account account) {
        for (ParallaxAccount bAccount : accounts) {
            if (bAccount.isMatch(account))
                return bAccount;
        }
        return null;
    }

    public boolean delAccount(Account account) {
        ParallaxAccount bAccount = getAccount(account);
        return accounts.remove(bAccount);
    }


    public Map<String, Integer> getVisibility(Account account) {
        ParallaxAccount bAccount = getAccount(account);
        if (bAccount == null)
            return new HashMap<>();
        return bAccount.visibility;
    }

    public Map<String, String> getAccountUserData(Account account) {
        ParallaxAccount bAccount = getAccount(account);
        if (bAccount == null)
            return new HashMap<>();
        return bAccount.accountUserData;
    }

    public Map<String, String> getAuthToken(Account account) {
        ParallaxAccount bAccount = getAccount(account);
        if (bAccount == null)
            return new HashMap<>();
        return bAccount.authTokens;
    }

    public Account[] getAccountsByType(String type) {
        List<Account> local = new ArrayList<>();
        for (ParallaxAccount account : accounts) {
            if (account.account.type.equals(type)) {
                local.add(account.account);
            }
        }
        return local.toArray(new Account[]{});
    }

    public void updateLastAuthenticatedTime(Account account) {
        ParallaxAccount bAccount = getAccount(account);
        if (bAccount != null) {
            bAccount.updateLastAuthenticatedTime = System.currentTimeMillis();
        }
    }

    public long findAccountLastAuthenticatedTime(Account account) {
        ParallaxAccount bAccount = getAccount(account);
        if (bAccount != null) {
            return bAccount.updateLastAuthenticatedTime;
        }
        return -1;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.userId);
        dest.writeTypedList(this.accounts);
    }

    public void readFromParcel(Parcel source) {
        this.userId = source.readInt();
        this.accounts = source.createTypedArrayList(ParallaxAccount.CREATOR);
    }

    public ParallaxUserAccounts() {
    }

    protected ParallaxUserAccounts(Parcel in) {
        this.userId = in.readInt();
        this.accounts = in.createTypedArrayList(ParallaxAccount.CREATOR);
    }

    public static final Creator<ParallaxUserAccounts> CREATOR = new Creator<ParallaxUserAccounts>() {
        @Override
        public ParallaxUserAccounts createFromParcel(Parcel source) {
            return new ParallaxUserAccounts(source);
        }

        @Override
        public ParallaxUserAccounts[] newArray(int size) {
            return new ParallaxUserAccounts[size];
        }
    };
}
