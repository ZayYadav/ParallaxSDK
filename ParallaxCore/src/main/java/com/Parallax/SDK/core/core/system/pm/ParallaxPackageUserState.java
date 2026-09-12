package com.Parallax.SDK.core.core.system.pm;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by @RIYAZXERO on 4/27/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxPackageUserState implements Parcelable {
    public boolean installed;
    public boolean stopped;
    public boolean hidden;

    public ParallaxPackageUserState() {
        this.installed = false;
        this.stopped = true;
        this.hidden = false;
    }

    public static ParallaxPackageUserState create() {
        ParallaxPackageUserState state = new ParallaxPackageUserState();
        state.installed = true;
        return state;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte(this.installed ? (byte) 1 : (byte) 0);
        dest.writeByte(this.stopped ? (byte) 1 : (byte) 0);
        dest.writeByte(this.hidden ? (byte) 1 : (byte) 0);
    }

    protected ParallaxPackageUserState(Parcel in) {
        this.installed = in.readByte() != 0;
        this.stopped = in.readByte() != 0;
        this.hidden = in.readByte() != 0;
    }

    public ParallaxPackageUserState(ParallaxPackageUserState state) {
        this.installed = state.installed;
        this.stopped = state.stopped;
        this.hidden = state.hidden;
    }

    public static final Parcelable.Creator<ParallaxPackageUserState> CREATOR = new Parcelable.Creator<ParallaxPackageUserState>() {
        @Override
        public ParallaxPackageUserState createFromParcel(Parcel source) {
            return new ParallaxPackageUserState(source);
        }

        @Override
        public ParallaxPackageUserState[] newArray(int size) {
            return new ParallaxPackageUserState[size];
        }
    };
}
