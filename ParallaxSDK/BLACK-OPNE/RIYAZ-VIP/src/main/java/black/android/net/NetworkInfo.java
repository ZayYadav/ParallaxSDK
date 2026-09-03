package black.android.net;

import android.net.NetworkInfo.DetailedState;
import android.net.NetworkInfo.State;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BConstructor;
import top.niunaijun.blackreflection.annotation.BField;

@BClassName("android.net.NetworkInfo")
public interface NetworkInfo {
    @BConstructor
    NetworkInfo _new(int type);

    @BConstructor
    NetworkInfo _new(int type, int subType, String typeName, String subTypeName);

    @BField
    DetailedState mDetailedState();

    @BField
    State mState();

    @BField
    boolean mIsAvailable();

    @BField
    String mTypeName();

    @BField
    int mNetworkType();
}
