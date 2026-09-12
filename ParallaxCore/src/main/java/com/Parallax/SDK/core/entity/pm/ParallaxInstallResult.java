package com.Parallax.SDK.core.entity.pm;

import android.os.Parcel;
import android.os.Parcelable;

import com.Parallax.SDK.core.utils.ParallaxSlog;

/**
 * Created by Milk on 4/20/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxInstallResult implements Parcelable {
    public static final String TAG = "ParallaxInstallResult";

    public boolean success = true;
    public String packageName;
    public String msg;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte(this.success ? (byte) 1 : (byte) 0);
        dest.writeString(this.packageName);
        dest.writeString(this.msg);
    }

    public ParallaxInstallResult() {}

    protected ParallaxInstallResult(Parcel in) {
        this.success = in.readByte() != 0;
        this.packageName = in.readString();
        this.msg = in.readString();
    }
    
    public ParallaxInstallResult(boolean success, String msg) {
        this.success = success;
        this.msg = msg;
    }

    public ParallaxInstallResult installError(String packageName, String msg) {
        this.msg = msg;
        this.success = false;
        this.packageName = packageName;
        ParallaxSlog.d(TAG, msg);
        return this;
    }

    public ParallaxInstallResult installError(String msg) {
        this.msg = msg;
        this.success = false;
        ParallaxSlog.d(TAG, msg);
        return this;
    }

    public static final Parcelable.Creator<ParallaxInstallResult> CREATOR = new Parcelable.Creator<ParallaxInstallResult>() {
        @Override
        public ParallaxInstallResult createFromParcel(Parcel source) {
            return new ParallaxInstallResult(source);
        }

        @Override
        public ParallaxInstallResult[] newArray(int size) {
            return new ParallaxInstallResult[size];
        }
    };
}
