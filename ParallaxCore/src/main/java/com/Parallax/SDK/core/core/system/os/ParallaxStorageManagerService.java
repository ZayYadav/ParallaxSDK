package com.Parallax.SDK.core.core.system.os;

import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;
import android.os.storage.StorageVolume;

import java.io.File;

import com.Parallax.SDK.mirror.android.os.storage.BRStorageManager;
import com.Parallax.SDK.mirror.android.os.storage.BRStorageVolume;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.core.env.ParallaxEnvironment;
import com.Parallax.SDK.core.core.system.IParallaxSystemService;
import com.Parallax.SDK.core.core.system.user.ParallaxUserHandle;
import com.Parallax.SDK.core.fake.provider.ParallaxFileProvider;
import com.Parallax.SDK.core.proxy.ParallaxProxyManifest;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;


public class ParallaxStorageManagerService extends IParallaxStorageManagerService.Stub implements IParallaxSystemService {
    private static final ParallaxStorageManagerService sService = new ParallaxStorageManagerService();

    public static ParallaxStorageManagerService get() {
        return sService;
    }

    public ParallaxStorageManagerService() {
    }

    @Override
    public StorageVolume[] getVolumeList(int uid, String packageName, int flags, int userId) throws RemoteException {
        if (BRStorageManager.get().getVolumeList(0, 0) == null) {
            return null;
        }
        try {
            StorageVolume[] storageVolumes = BRStorageManager.get().getVolumeList(ParallaxUserHandle.getUserId(Process.myUid()), 0);
            if (storageVolumes == null)
                return null;
            for (StorageVolume storageVolume : storageVolumes) {
                BRStorageVolume.get(storageVolume)._set_mPath(ParallaxEnvironment.getExternalStorageDirectory());
                if (ParallaxBuildCompat.isPie()) {
                    BRStorageVolume.get(storageVolume)._set_mInternalPath(ParallaxEnvironment.getExternalStorageDirectory());
                }
            }
            return storageVolumes;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Uri getUriForFile(String file) throws RemoteException {
        return ParallaxFileProvider.getUriForFile(ParallaxCore.getContext(), ParallaxProxyManifest.getProxyFileProvider(), new File(file));
    }

    @Override
    public void systemReady() {

    }
}
