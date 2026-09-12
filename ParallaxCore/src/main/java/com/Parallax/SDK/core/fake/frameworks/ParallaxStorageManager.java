package com.Parallax.SDK.core.fake.frameworks;

import android.net.Uri;
import android.os.RemoteException;
import android.os.storage.StorageVolume;

import com.Parallax.SDK.core.core.system.ParallaxServiceManager;
import com.Parallax.SDK.core.core.system.os.IParallaxStorageManagerService;

/**
 * Created by @RIYAZXERO on 3/30/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxStorageManager extends ParallaxBlackManager<IParallaxStorageManagerService> {
    private static final ParallaxStorageManager sStorageManager = new ParallaxStorageManager();

    public static ParallaxStorageManager get() {
        return sStorageManager;
    }

    @Override
    protected String getServiceName() {
        return ParallaxServiceManager.STORAGE_MANAGER;
    }

    public StorageVolume[] getVolumeList(int uid, String packageName, int flags, int userId) {
        try {
            return getService().getVolumeList(uid, packageName, flags, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return new StorageVolume[]{};
    }

    public Uri getUriForFile(String file) {
        try {
            return getService().getUriForFile(file);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }
}
