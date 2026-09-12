package com.Parallax.SDK.core.utils.compat;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import java.util.Locale;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.utils.ParallaxDrawableUtils;

public class ParallaxTaskDescriptionCompat {
    public static ActivityManager.TaskDescription fix(ActivityManager.TaskDescription td) {
        String label = td.getLabel();
        Bitmap icon = td.getIcon();

        if (label != null && icon != null)
            return td;

        label = getTaskDescriptionLabel(ParallaxActivityThread.getUserId(), getApplicationLabel());
        Drawable drawable = getApplicationIcon();
        if (drawable == null)
            return td;

        ActivityManager am = (ActivityManager) ParallaxCore.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        int iconSize = am.getLauncherLargeIconSize();
        icon = ParallaxDrawableUtils.drawableToBitmap(drawable, iconSize, iconSize);
        td = new ActivityManager.TaskDescription(label, icon, td.getPrimaryColor());
        return td;
    }

    public static String getTaskDescriptionLabel(int userId, CharSequence label) {
        return String.format(Locale.CHINA, "%s", label);
    }

    private static CharSequence getApplicationLabel() {
        try {
            PackageManager pm = ParallaxCore.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(ParallaxActivityThread.getAppPackageName(), 0));
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static Drawable getApplicationIcon() {
		try {
			PackageManager pm = ParallaxCore.getContext().getPackageManager();
			ApplicationInfo appInfo = pm.getApplicationInfo(ParallaxActivityThread.getAppPackageName(), 0);
			return pm.getApplicationIcon(appInfo);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
