package com.Parallax.SDK.core.utils.compat;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.WindowManager;

import com.Parallax.SDK.mirror.android.app.BRActivity;
import com.Parallax.SDK.mirror.com.android.internal.BRRstyleable;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.utils.ParallaxDrawableUtils;

public class ParallaxActivityCompat {

    public static void fix(Activity activity) {
        try {
            // Custom BlackBox proxy call
            BRActivity.get(activity).mActivityInfo();
        } catch (Throwable ignore) {
        }

        Context baseContext = activity.getBaseContext();

        // Apply style flags: wallpaper, fullscreen
        try {
            TypedArray typedArray = activity.obtainStyledAttributes(BRRstyleable.get().Window());
            if (typedArray != null) {
                if (typedArray.getBoolean(BRRstyleable.get().Window_windowShowWallpaper(), false)) {
                    try {
                        Drawable wallpaper = WallpaperManager.getInstance(activity).getDrawable();
                        if (wallpaper != null) {
                            activity.getWindow().setBackgroundDrawable(wallpaper);
                        }
                    } catch (Throwable ignore) {
                    }
                }

                if (typedArray.getBoolean(BRRstyleable.get().Window_windowFullscreen(), false)) {
                    activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                }

                typedArray.recycle();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        // Task description (label + icon)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                Intent intent = activity.getIntent();
                ApplicationInfo appInfo = baseContext.getApplicationInfo();
                PackageManager pm = activity.getPackageManager();

                if (intent != null && activity.isTaskRoot()) {
                    String label = ParallaxTaskDescriptionCompat.getTaskDescriptionLabel(ParallaxActivityThread.getUserId(), appInfo.loadLabel(pm));

                    Bitmap icon = null;
                    Drawable drawable = getActivityIcon(activity);
                    if (drawable != null) {
                        ActivityManager am = (ActivityManager) baseContext.getSystemService(Context.ACTIVITY_SERVICE);
                        int iconSize = am.getLauncherLargeIconSize();
                        icon = ParallaxDrawableUtils.drawableToBitmap(drawable, iconSize, iconSize);
                    }

                    activity.setTaskDescription(new ActivityManager.TaskDescription(label, icon));
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    private static Drawable getActivityIcon(Activity activity) {
        try {
            PackageManager pm = activity.getPackageManager();
            Drawable icon = pm.getActivityIcon(activity.getComponentName());
            if (icon != null) return icon;
        } catch (PackageManager.NameNotFoundException ignore) {
        }

        try {
            ApplicationInfo appInfo = activity.getApplicationInfo();
            return appInfo.loadIcon(activity.getPackageManager());
        } catch (Throwable ignore) {
        }

        return null;
    }
}