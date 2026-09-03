package black.android.media.session;

import android.content.Context;
import android.os.IBinder;
import android.os.IInterface;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BStaticMethod;

@BClassName("android.media.session.ISessionManager")
public interface ISessionManager {
    @BClassName("android.media.session.ISessionManager$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }

    // Add a method to get the ISessionManager instance dynamically for Android 10+
    @BStaticMethod
    IInterface getService(Context context);
}
