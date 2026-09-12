package com.Parallax.SDK.core.entity.am;

import android.app.ActivityManager;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by BlackBox on 2022/2/25.
 */
public class ParallaxRunningServiceInfo implements Parcelable {
    public List<ActivityManager.RunningServiceInfo> mRunningServiceInfoList;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(this.mRunningServiceInfoList);
    }

    public void readFromParcel(Parcel source) {
        this.mRunningServiceInfoList = source.createTypedArrayList(ActivityManager.RunningServiceInfo.CREATOR);
    }

    public ParallaxRunningServiceInfo() {
        mRunningServiceInfoList = new ArrayList<>();
    }

    protected ParallaxRunningServiceInfo(Parcel in) {
        this.mRunningServiceInfoList = in.createTypedArrayList(ActivityManager.RunningServiceInfo.CREATOR);
    }

    public static final Parcelable.Creator<ParallaxRunningServiceInfo> CREATOR = new Parcelable.Creator<ParallaxRunningServiceInfo>() {
        @Override
        public ParallaxRunningServiceInfo createFromParcel(Parcel source) {
            return new ParallaxRunningServiceInfo(source);
        }

        @Override
        public ParallaxRunningServiceInfo[] newArray(int size) {
            return new ParallaxRunningServiceInfo[size];
        }
    };
}
