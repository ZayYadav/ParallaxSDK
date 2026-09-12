package com.Parallax.SDK.core.entity.pm;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Parcel;
import android.os.Parcelable;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.core.system.user.ParallaxUserHandle;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;

/**
 * Created by Milk on 5/2/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxInstalledModule implements Parcelable {
    public String packageName;
    public String name;
    public String desc;
    public String main;
    public boolean enable;

    public ParallaxInstalledModule() {
    }


    public ApplicationInfo getApplication() {
        return ParallaxCore.getBPackageManager().getApplicationInfo(packageName, ParallaxFileUtils.FileMode.MODE_IWUSR, ParallaxUserHandle.USER_XPOSED);
    }

    public PackageInfo getPackageInfo() {
        return ParallaxCore.getBPackageManager().getPackageInfo(packageName, ParallaxFileUtils.FileMode.MODE_IWUSR, ParallaxUserHandle.USER_XPOSED);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.packageName);
        dest.writeString(this.name);
        dest.writeString(this.desc);
        dest.writeString(this.main);
        dest.writeByte(this.enable ? (byte) 1 : (byte) 0);
    }

    protected ParallaxInstalledModule(Parcel in) {
        this.packageName = in.readString();
        this.name = in.readString();
        this.desc = in.readString();
        this.main = in.readString();
        this.enable = in.readByte() != 0;
    }

    public static final Creator<ParallaxInstalledModule> CREATOR = new Creator<ParallaxInstalledModule>() {
        @Override
        public ParallaxInstalledModule createFromParcel(Parcel source) {
            return new ParallaxInstalledModule(source);
        }

        @Override
        public ParallaxInstalledModule[] newArray(int size) {
            return new ParallaxInstalledModule[size];
        }
    };
}
