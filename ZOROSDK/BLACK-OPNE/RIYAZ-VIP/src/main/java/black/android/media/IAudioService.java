package black.android.media;

import android.os.IBinder;
import android.os.IInterface;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BStaticMethod;

@BClassName("android.media.IAudioService")
public interface IAudioService {

    // ✅ Stub for binder compatibility
    @BClassName("android.media.IAudioService$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder binder);
    }

    // ✅ Android 9-16: Standard IAudioService interface
    @BStaticMethod
    void setMicrophoneMute(boolean mute, String callingPackage, int userId);

    @BStaticMethod
    boolean isMicrophoneMute();

    // ✅ Android 10+: Bluetooth SCO methods
    @BStaticMethod
    void startBluetoothSco(String callingPackage);

    @BStaticMethod
    void stopBluetoothSco(String callingPackage);

    
}
