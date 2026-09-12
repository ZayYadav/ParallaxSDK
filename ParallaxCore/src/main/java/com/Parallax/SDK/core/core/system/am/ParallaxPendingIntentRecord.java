package com.Parallax.SDK.core.core.system.am;

import java.util.Objects;

/**
 * Created by BlackBox on 2022/3/8.
 */
public class ParallaxPendingIntentRecord {
    public int uid;
    public String packageName;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParallaxPendingIntentRecord)) return false;
        ParallaxPendingIntentRecord that = (ParallaxPendingIntentRecord) o;
        return uid == that.uid &&
                Objects.equals(packageName, that.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid, packageName);
    }
}
