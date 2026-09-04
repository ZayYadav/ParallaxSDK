package top.niunaijun.blackbox.entity.am;

import android.content.BroadcastReceiver;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.UUID;

import black.android.content.BRBroadcastReceiverPendingResult;
import black.android.content.BRBroadcastReceiverPendingResultM;
import black.android.content.BroadcastReceiverPendingResultContext;
import black.android.content.BroadcastReceiverPendingResultMContext;
import top.niunaijun.blackbox.utils.compat.BuildCompat;

/**
 * Created by BlackBox on 2022/2/28.
 */
public class PendingResultData implements Parcelable {
    
    public boolean mAbortBroadcast;
    public String mBToken;
    public boolean mFinished;
    public int mFlags;
    public boolean mInitialStickyHint;
    public boolean mOrderedHint;
    public int mResultCode;
    public String mResultData;
    public Bundle mResultExtras;
    public int mSendingUser;
    public IBinder mToken;
    public int mType;

    public PendingResultData(BroadcastReceiver.PendingResult pendingResult) {
        mBToken = UUID.randomUUID().toString();
        if (BuildCompat.isM()) {
            BroadcastReceiverPendingResultMContext resultMContext = BRBroadcastReceiverPendingResultM.get(pendingResult);
            mType = resultMContext.mType();
            mOrderedHint = resultMContext.mOrderedHint();
            mInitialStickyHint = resultMContext.mInitialStickyHint();
            mToken = resultMContext.mToken();
            mSendingUser = resultMContext.mSendingUser();
            mFlags = resultMContext.mFlags();
            mResultData = resultMContext.mResultData();
            mResultExtras = resultMContext.mResultExtras();
            mAbortBroadcast = resultMContext.mAbortBroadcast();
            mFinished = resultMContext.mFinished();
        } else {
            BroadcastReceiverPendingResultContext resultContext = BRBroadcastReceiverPendingResult.get(pendingResult);
            mType = resultContext.mType();
            mOrderedHint = resultContext.mOrderedHint();
            mInitialStickyHint = resultContext.mInitialStickyHint();
            mToken = resultContext.mToken();
            mSendingUser = resultContext.mSendingUser();
            mResultData = resultContext.mResultData();
            mResultExtras = resultContext.mResultExtras();
            mAbortBroadcast = resultContext.mAbortBroadcast();
            mFinished = resultContext.mFinished();
        }
    }

    public BroadcastReceiver.PendingResult build() {
        if (BuildCompat.isM()) {
            return BRBroadcastReceiverPendingResultM.get()._new(mResultCode, mResultData, mResultExtras, mType, mOrderedHint, mInitialStickyHint, mToken, mSendingUser, mFlags);
        }
        return BRBroadcastReceiverPendingResult.get()._new(mResultCode, mResultData, mResultExtras, mType, mOrderedHint, mInitialStickyHint, mToken, mSendingUser);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.mType);
        dest.writeByte(this.mOrderedHint ? (byte) 1 : (byte) 0);
        dest.writeByte(this.mInitialStickyHint ? (byte) 1 : (byte) 0);
        dest.writeStrongBinder(this.mToken);
        dest.writeInt(this.mSendingUser);
        dest.writeInt(this.mFlags);
        dest.writeInt(this.mResultCode);
        dest.writeString(this.mResultData);
        dest.writeBundle(this.mResultExtras);
        dest.writeByte(this.mAbortBroadcast ? (byte) 1 : (byte) 0);
        dest.writeByte(this.mFinished ? (byte) 1 : (byte) 0);
        dest.writeString(this.mBToken);
    }

    public void readFromParcel(Parcel source) {
        this.mType = source.readInt();
        this.mOrderedHint = source.readByte() != 0;
        this.mInitialStickyHint = source.readByte() != 0;
        this.mToken = source.readStrongBinder();
        this.mSendingUser = source.readInt();
        this.mFlags = source.readInt();
        this.mResultCode = source.readInt();
        this.mResultData = source.readString();
        this.mResultExtras = source.readBundle();
        this.mAbortBroadcast = source.readByte() != 0;
        this.mFinished = source.readByte() != 0;
        this.mBToken = source.readString();
    }

    protected PendingResultData(Parcel in) {
        this.mType = in.readInt();
        this.mOrderedHint = in.readByte() != 0;
        this.mInitialStickyHint = in.readByte() != 0;
        this.mToken = in.readStrongBinder();
        this.mSendingUser = in.readInt();
        this.mFlags = in.readInt();
        this.mResultCode = in.readInt();
        this.mResultData = in.readString();
        this.mResultExtras = in.readBundle();
        this.mAbortBroadcast = in.readByte() != 0;
        this.mFinished = in.readByte() != 0;
        this.mBToken = in.readString();
    }
    
    public static final Parcelable.Creator<PendingResultData> CREATOR = new Parcelable.Creator<PendingResultData>() {
        @Override
        public PendingResultData createFromParcel(Parcel source) {
            return new PendingResultData(source);
        }

        @Override
        public PendingResultData[] newArray(int size) {
            return new PendingResultData[size];
        }
    };
    

    public String toString() {
        return "PendingResultData{mType=" + this.mType + ", mOrderedHint=" + this.mOrderedHint + ", mInitialStickyHint=" + this.mInitialStickyHint + ", mToken=" + this.mToken + ", mSendingUser=" + this.mSendingUser + ", mFlags=" + this.mFlags + ", mResultCode=" + this.mResultCode + ", mResultData='" + this.mResultData + '\'' + ", mResultExtras=" + this.mResultExtras + ", mAbortBroadcast=" + this.mAbortBroadcast + ", mFinished=" + this.mFinished + '}';
    }
}
