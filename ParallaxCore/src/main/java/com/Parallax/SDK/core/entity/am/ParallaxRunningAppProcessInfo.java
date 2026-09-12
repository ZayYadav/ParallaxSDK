package com.Parallax.SDK.core.entity.am;

import android.app.ActivityManager;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by BlackBox on 2022/2/25.
 */
public class ParallaxRunningAppProcessInfo implements Parcelable {
    public List<ActivityManager.RunningAppProcessInfo> mAppProcessInfoList;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(this.mAppProcessInfoList);
    }

    public void readFromParcel(Parcel source) {
        this.mAppProcessInfoList = source.createTypedArrayList(ActivityManager.RunningAppProcessInfo.CREATOR);
    }

    public ParallaxRunningAppProcessInfo() {
        mAppProcessInfoList = new ArrayList<>();
    }

    protected ParallaxRunningAppProcessInfo(Parcel in) {
        this.mAppProcessInfoList = in.createTypedArrayList(ActivityManager.RunningAppProcessInfo.CREATOR);
    }

    public static final Parcelable.Creator<ParallaxRunningAppProcessInfo> CREATOR = new Parcelable.Creator<ParallaxRunningAppProcessInfo>() {
        @Override
        public ParallaxRunningAppProcessInfo createFromParcel(Parcel source) {
            return new ParallaxRunningAppProcessInfo(source);
        }

        @Override
        public ParallaxRunningAppProcessInfo[] newArray(int size) {
            return new ParallaxRunningAppProcessInfo[size];
        }
    };
}
