package com.Parallax.SDK.core.entity.location;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

/**
 * Created by BlackBoxing on 3/8/22.
 **/
public class ParallaxLocationConfig implements Parcelable {

    public int pattern;
    public ParallaxCell cell;
    public List<ParallaxCell> allCell;
    public List<ParallaxCell> neighboringCellInfo;
    public ParallaxLocation location;

    @Override
    public int describeContents() {
        return 0;
    }

    public ParallaxLocationConfig() {
    }

    public ParallaxLocationConfig(Parcel in) {
        refresh(in);
    }

    public void refresh(Parcel in) {
        this.pattern = in.readInt();
        this.cell = in.readParcelable(ParallaxCell.class.getClassLoader());
        this.allCell = in.createTypedArrayList(ParallaxCell.CREATOR);
        this.neighboringCellInfo = in.createTypedArrayList(ParallaxCell.CREATOR);
        this.location = in.readParcelable(ParallaxLocation.class.getClassLoader());
    }


    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.pattern);
        dest.writeParcelable(this.cell, flags);
        dest.writeTypedList(this.allCell);
        dest.writeTypedList(this.neighboringCellInfo);
        dest.writeParcelable(this.location, flags);
    }

    public static final Creator<ParallaxLocationConfig> CREATOR = new Creator<ParallaxLocationConfig>() {
        @Override
        public ParallaxLocationConfig createFromParcel(Parcel source) {
            return new ParallaxLocationConfig(source);
        }

        @Override
        public ParallaxLocationConfig[] newArray(int size) {
            return new ParallaxLocationConfig[size];
        }
    };
}
