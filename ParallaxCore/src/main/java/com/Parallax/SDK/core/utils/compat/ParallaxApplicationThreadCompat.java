package com.Parallax.SDK.core.utils.compat;

import android.os.IBinder;
import android.os.IInterface;

import com.Parallax.SDK.mirror.android.app.BRApplicationThreadNative;
import com.Parallax.SDK.mirror.android.app.BRIApplicationThreadOreoStub;

public class ParallaxApplicationThreadCompat {

    public static IInterface asInterface(IBinder binder) {
        if (ParallaxBuildCompat.isOreo()) {
            return BRIApplicationThreadOreoStub.get().asInterface(binder);
        }
        return BRApplicationThreadNative.get().asInterface(binder);
    }
}
