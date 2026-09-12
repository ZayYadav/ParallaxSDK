package com.Parallax.SDK.core.core.system.user;

import android.os.Parcel;
import android.os.RemoteException;

import androidx.core.util.AtomicFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.Parallax.SDK.core.core.env.ParallaxEnvironment;
import com.Parallax.SDK.core.core.system.IParallaxSystemService;
import com.Parallax.SDK.core.core.system.pm.ParallaxPackageManagerService;
import com.Parallax.SDK.core.utils.ParallaxCloseUtils;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;

public class ParallaxUserManagerService extends IParallaxUserManagerService.Stub implements IParallaxSystemService {
    private static ParallaxUserManagerService sService = new ParallaxUserManagerService();
    public final HashMap<Integer, ParallaxUserInfo> mUsers = new HashMap<>();
    public final Object mUserLock = new Object();

    public static ParallaxUserManagerService get() {
        return sService;
    }

    @Override
    public void systemReady() {
        scanUserL();
    }

    @Override
    public ParallaxUserInfo getUserInfo(int userId) {
        synchronized (mUserLock) {
            return mUsers.get(userId);
        }
    }

    @Override
    public boolean exists(int userId) {
        synchronized (mUsers) {
            return mUsers.get(userId) != null;
        }
    }

    @Override
    public ParallaxUserInfo createUser(int userId) throws RemoteException {
        synchronized (mUserLock) {
            if (exists(userId)) {
                return getUserInfo(userId);
            }
            return createUserLocked(userId);
        }
    }

    @Override
    public List<ParallaxUserInfo> getUsers() {
        synchronized (mUsers) {
            ArrayList<ParallaxUserInfo> bUsers = new ArrayList<>();
            for (ParallaxUserInfo value : mUsers.values()) {
                if (value.id >= 0) {
                    bUsers.add(value);
                }
            }
            return bUsers;
        }
    }

    public List<ParallaxUserInfo> getAllUsers() {
        synchronized (mUsers) {
            return new ArrayList<>(mUsers.values());
        }
    }

    @Override
    public void deleteUser(int userId) throws RemoteException {
        synchronized (mUserLock) {
            synchronized (mUsers) {
                ParallaxPackageManagerService.get().deleteUser(userId);
                mUsers.remove(userId);
                saveUserInfoLocked();
                ParallaxFileUtils.deleteDir(ParallaxEnvironment.getUserDir(userId));
                //ParallaxFileUtils.deleteDir(ParallaxEnvironment.getExternalStorageDirectory());
            }
        }
    }

    private ParallaxUserInfo createUserLocked(int userId) {
        ParallaxUserInfo bUserInfo = new ParallaxUserInfo();
        bUserInfo.id = userId;
        bUserInfo.status = ParallaxUserStatus.ENABLE;
        mUsers.put(userId, bUserInfo);
        synchronized (mUsers) {
            saveUserInfoLocked();
        }
        return bUserInfo;
    }

    private void saveUserInfoLocked() {
        Parcel parcel = Parcel.obtain();
        AtomicFile atomicFile = new AtomicFile(ParallaxEnvironment.getUserInfoConf());
        FileOutputStream fileOutputStream = null;
        try {
            ArrayList<ParallaxUserInfo> bUsers = new ArrayList<>(mUsers.values());
            parcel.writeTypedList(bUsers);
            try {
                fileOutputStream = atomicFile.startWrite();
                ParallaxFileUtils.writeParcelToOutput(parcel, fileOutputStream);
                atomicFile.finishWrite(fileOutputStream);
            } catch (IOException e) {
                e.printStackTrace();
                atomicFile.failWrite(fileOutputStream);
            } finally {
                ParallaxCloseUtils.close(fileOutputStream);
            }
        } finally {
            parcel.recycle();
        }
    }

    private void scanUserL() {
        synchronized (mUserLock) {
            Parcel parcel = Parcel.obtain();
            InputStream is = null;
            try {
                File userInfoConf = ParallaxEnvironment.getUserInfoConf();
                if (!userInfoConf.exists()) {
                    return;
                }
                is = new FileInputStream(ParallaxEnvironment.getUserInfoConf());
                byte[] bytes = ParallaxFileUtils.toByteArray(is);
                parcel.unmarshall(bytes, 0, bytes.length);
                parcel.setDataPosition(0);

                ArrayList<ParallaxUserInfo> loadUsers = parcel.createTypedArrayList(ParallaxUserInfo.CREATOR);
                if (loadUsers == null)
                    return;
                synchronized (mUsers) {
                    mUsers.clear();
                    for (ParallaxUserInfo loadUser : loadUsers) {
                        mUsers.put(loadUser.id, loadUser);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                parcel.recycle();
                ParallaxCloseUtils.close(is);
            }
        }
    }
}