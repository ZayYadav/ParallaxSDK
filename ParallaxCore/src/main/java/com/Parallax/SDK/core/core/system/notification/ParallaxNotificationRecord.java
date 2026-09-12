package com.Parallax.SDK.core.core.system.notification;

import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by BlackBox on 2022/3/18.
 */
public class ParallaxNotificationRecord {
    public final Map<String, NotificationChannel> mNotificationChannels = new HashMap<>();
    public final Map<String, NotificationChannelGroup> mNotificationChannelGroups = new HashMap<>();
    public final Set<Integer> mIds = new HashSet<>();
}
