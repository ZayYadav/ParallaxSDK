package com.Parallax.SDK.core.entity.am;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by BlackBox on 2022/2/28.
 */
public class ParallaxReceiverData implements Parcelable {
    public Intent intent;
    public ActivityInfo activityInfo;
    public ParallaxPendingResultData data;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(this.intent, flags);
        dest.writeParcelable(this.activityInfo, flags);
        dest.writeParcelable(this.data, flags);
    }

    public void readFromParcel(Parcel source) {
        this.intent = source.readParcelable(Intent.class.getClassLoader());
        this.activityInfo = source.readParcelable(ActivityInfo.class.getClassLoader());
        this.data = source.readParcelable(ParallaxPendingResultData.class.getClassLoader());
    }

    public ParallaxReceiverData() {
    }

    protected ParallaxReceiverData(Parcel in) {
        this.intent = in.readParcelable(Intent.class.getClassLoader());
        this.activityInfo = in.readParcelable(ActivityInfo.class.getClassLoader());
        this.data = in.readParcelable(ParallaxPendingResultData.class.getClassLoader());
    }

    public static final Parcelable.Creator<ParallaxReceiverData> CREATOR = new Parcelable.Creator<ParallaxReceiverData>() {
        @Override
        public ParallaxReceiverData createFromParcel(Parcel source) {
            return new ParallaxReceiverData(source);
        }

        @Override
        public ParallaxReceiverData[] newArray(int size) {
            return new ParallaxReceiverData[size];
        }
    };
}
