package black.android.media;

import android.content.Context;
import android.os.IInterface;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BStaticField;
import top.niunaijun.blackreflection.annotation.BStaticMethod;

@BClassName("android.media.AudioManager")
public interface AudioManager {
    // ✅ Android 9-10: Direct service access
    @BStaticField
    IInterface sService();

    // ✅ Android 11+: Context-based service binding
    @BStaticMethod
    Object getService(Context context);

    // ✅ Android 12+ compatibility (e.g., for audio focus changes)
    @BStaticMethod
    boolean isStreamMute(int streamType);
}
