package com.Parallax.SDK.core.fake.frameworks;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.List;

import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.core.system.ParallaxServiceManager;
import com.Parallax.SDK.core.core.system.notification.IParallaxNotificationManagerService;

/**
 * Created by BlackBox on 2022/3/18.
 */
public class ParallaxNotificationManager extends ParallaxBlackManager<IParallaxNotificationManagerService> {
    private static final ParallaxNotificationManager sNotificationManager = new ParallaxNotificationManager();

    public static ParallaxNotificationManager get() {
        return sNotificationManager;
    }

    @Override
    protected String getServiceName() {
        return ParallaxServiceManager.NOTIFICATION_MANAGER;
    }

    public NotificationChannel getNotificationChannel(String channelId) {
        try {
            return getService().getNotificationChannel(channelId, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<NotificationChannelGroup> getNotificationChannelGroups(String packageName) {
        try {
            return getService().getNotificationChannelGroups(packageName, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void createNotificationChannel(NotificationChannel notificationChannel) {
        try {
            getService().createNotificationChannel(notificationChannel, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void deleteNotificationChannel(String channelId) {
        try {
            getService().deleteNotificationChannel(channelId, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void createNotificationChannelGroup(NotificationChannelGroup notificationChannelGroup) {
        try {
            getService().createNotificationChannelGroup(notificationChannelGroup, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void deleteNotificationChannelGroup(String groupId) {
        try {
            getService().deleteNotificationChannelGroup(groupId, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void enqueueNotificationWithTag(int id, String tag, Notification notification) {
        try {
            getService().enqueueNotificationWithTag(id, tag, notification, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void cancelNotificationWithTag(int id, String tag) {
        try {
            getService().cancelNotificationWithTag(id, tag, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public List<NotificationChannel> getNotificationChannels(String packageName) {
        try {
            return getService().getNotificationChannels(packageName, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
}
