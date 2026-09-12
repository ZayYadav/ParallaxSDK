// IParallaxStorageManagerService.aidl
package com.Parallax.SDK.core.core.system.os;

import android.os.storage.StorageVolume;
import java.lang.String;
import android.net.Uri;

// Declare any non-default types here with import statements

interface IParallaxStorageManagerService {
      StorageVolume[] getVolumeList(int uid, String packageName, int flags, int userId);
      Uri getUriForFile(String file);
}
