package com.Parallax.SDK.core.core.system.location;

/**
 * Created by BlackBox on 2022/3/19.
 */
public class ParallaxLocationRecord {
    public String packageName;
    public int userId;

    public ParallaxLocationRecord(String packageName, int userId) {
        this.packageName = packageName;
        this.userId = userId;
    }
}
