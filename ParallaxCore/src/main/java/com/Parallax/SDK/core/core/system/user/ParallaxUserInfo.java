package com.Parallax.SDK.core.core.system.user;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by @RIYAZXERO on 4/22/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxUserInfo implements Parcelable {
    public int id;
    public ParallaxUserStatus status;
    public String name;
    public long createTime;

    ParallaxUserInfo() {
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.id);
        dest.writeInt(this.status == null ? -1 : this.status.ordinal());
        dest.writeString(this.name);
        dest.writeLong(this.createTime);
    }

    protected ParallaxUserInfo(Parcel in) {
        this.id = in.readInt();
        int tmpStatus = in.readInt();
        this.status = tmpStatus == -1 ? null : ParallaxUserStatus.values()[tmpStatus];
        this.name = in.readString();
        this.createTime = in.readLong();
    }

    public static final Creator<ParallaxUserInfo> CREATOR = new Creator<ParallaxUserInfo>() {
        @Override
        public ParallaxUserInfo createFromParcel(Parcel source) {
            return new ParallaxUserInfo(source);
        }

        @Override
        public ParallaxUserInfo[] newArray(int size) {
            return new ParallaxUserInfo[size];
        }
    };

    @Override
    public String toString() {
        return "ParallaxUserInfo{" +
                "id=" + id +
                ", status=" + status +
                ", name='" + name + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}
