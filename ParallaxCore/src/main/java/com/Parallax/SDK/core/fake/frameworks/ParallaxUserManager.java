package com.Parallax.SDK.core.fake.frameworks;

import android.os.RemoteException;

import java.util.Collections;
import java.util.List;

import com.Parallax.SDK.core.core.system.ParallaxServiceManager;
import com.Parallax.SDK.core.core.system.user.ParallaxUserInfo;
import com.Parallax.SDK.core.core.system.user.IParallaxUserManagerService;

/**
 * Created by @RIYAZXERO on 4/28/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxUserManager extends ParallaxBlackManager<IParallaxUserManagerService> {
    private static final ParallaxUserManager sUserManager = new ParallaxUserManager();

    public static ParallaxUserManager get() {
        return sUserManager;
    }

    @Override
    protected String getServiceName() {
        return ParallaxServiceManager.USER_MANAGER;
    }

    public ParallaxUserInfo createUser(int userId) {
        try {
            return getService().createUser(userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void deleteUser(int userId) {
        try {
            getService().deleteUser(userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public List<ParallaxUserInfo> getUsers() {
        try {
            return getService().getUsers();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }
}
