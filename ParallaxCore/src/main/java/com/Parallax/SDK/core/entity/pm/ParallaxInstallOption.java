package com.Parallax.SDK.core.entity.pm;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Milk on 4/21/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxInstallOption implements Parcelable {
    public static final int FLAG_SYSTEM = 1;
    public static final int FLAG_STORAGE = 1 << 1;
    public static final int FLAG_XPOSED = 1 << 2;
    public static final int FLAG_URI_FILE = 1 << 3;

    public int flags = 0;

    public static ParallaxInstallOption installBySystem() {
        ParallaxInstallOption installOption = new ParallaxInstallOption();
        installOption.flags = installOption.flags | FLAG_SYSTEM;
        return installOption;
    }

    public static ParallaxInstallOption installByStorage() {
        ParallaxInstallOption installOption = new ParallaxInstallOption();
        installOption.flags = installOption.flags | FLAG_STORAGE;
        return installOption;
    }

    public ParallaxInstallOption makeXposed() {
        this.flags |= FLAG_XPOSED;
        return this;
    }

    public ParallaxInstallOption makeUriFile() {
        this.flags |= FLAG_URI_FILE;
        return this;
    }

    public boolean isFlag(int flag) {
        return (flags & flag) != 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.flags);
    }

    public ParallaxInstallOption() {
    }

    protected ParallaxInstallOption(Parcel in) {
        this.flags = in.readInt();
    }

    public static final Parcelable.Creator<ParallaxInstallOption> CREATOR = new Parcelable.Creator<ParallaxInstallOption>() {
        @Override
        public ParallaxInstallOption createFromParcel(Parcel source) {
            return new ParallaxInstallOption(source);
        }

        @Override
        public ParallaxInstallOption[] newArray(int size) {
            return new ParallaxInstallOption[size];
        }
    };
}
