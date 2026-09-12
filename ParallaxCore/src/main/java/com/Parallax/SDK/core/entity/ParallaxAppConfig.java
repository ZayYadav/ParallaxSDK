package com.Parallax.SDK.core.entity;

import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;


/**
 * Created by @RIYAZXERO on 4/1/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxAppConfig implements Parcelable {
    public static final String KEY = "parallaxcore_core_client_config";

    public String packageName;
    public String processName;
    public int bpid;
    public int buid;
    public int uid;
    public int userId;
    public int callingBUid;
    public IBinder token;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.packageName);
        dest.writeString(this.processName);
        dest.writeInt(this.bpid);
        dest.writeInt(this.buid);
        dest.writeInt(this.uid);
        dest.writeInt(this.userId);
        dest.writeInt(this.callingBUid);
        dest.writeStrongBinder(token);
    }

    public ParallaxAppConfig() {
    }

    protected ParallaxAppConfig(Parcel in) {
        this.packageName = in.readString();
        this.processName = in.readString();
        this.bpid = in.readInt();
        this.buid = in.readInt();
        this.uid = in.readInt();
        this.userId = in.readInt();
        this.callingBUid = in.readInt();
        this.token = in.readStrongBinder();
    }

    public static final Parcelable.Creator<ParallaxAppConfig> CREATOR = new Parcelable.Creator<ParallaxAppConfig>() {
        @Override
        public ParallaxAppConfig createFromParcel(Parcel source) {
            return new ParallaxAppConfig(source);
        }

        @Override
        public ParallaxAppConfig[] newArray(int size) {
            return new ParallaxAppConfig[size];
        }
    };
}
