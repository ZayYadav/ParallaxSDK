package com.Parallax.SDK.core.entity.pm;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;

/**
 * Created by Milk on 4/20/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxInstalledPackage implements Parcelable {
    public int userId;
    public String packageName;

    public ApplicationInfo getApplication() {
        return ParallaxCore.getBPackageManager().getApplicationInfo(packageName, ParallaxFileUtils.FileMode.MODE_IWUSR, userId);
    }

    public PackageInfo getPackageInfo() {
        return ParallaxCore.getBPackageManager().getPackageInfo(packageName, ParallaxFileUtils.FileMode.MODE_IWUSR, userId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.userId);
        dest.writeString(this.packageName);
    }

    public ParallaxInstalledPackage() {
    }

    public ParallaxInstalledPackage(String packageName) {
        this.packageName = packageName;
    }

    protected ParallaxInstalledPackage(Parcel in) {
        this.userId = in.readInt();
        this.packageName = in.readString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParallaxInstalledPackage that = (ParallaxInstalledPackage) o;
        return Objects.equals(packageName, that.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName);
    }

    public static final Parcelable.Creator<ParallaxInstalledPackage> CREATOR = new Parcelable.Creator<ParallaxInstalledPackage>() {
        @Override
        public ParallaxInstalledPackage createFromParcel(Parcel source) {
            return new ParallaxInstalledPackage(source);
        }

        @Override
        public ParallaxInstalledPackage[] newArray(int size) {
            return new ParallaxInstalledPackage[size];
        }
    };
}
