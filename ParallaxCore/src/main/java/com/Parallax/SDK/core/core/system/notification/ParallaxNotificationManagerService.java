package com.Parallax.SDK.core.core.system.notification;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Binder;
import android.os.Build;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.Parallax.SDK.mirror.android.app.BRNotificationChannel;
import com.Parallax.SDK.mirror.android.app.BRNotificationChannelGroup;
import com.Parallax.SDK.mirror.android.app.BRNotificationO;
import com.Parallax.SDK.mirror.android.app.NotificationChannelContext;
import com.Parallax.SDK.mirror.android.app.NotificationChannelGroupContext;
import com.Parallax.SDK.mirror.android.app.NotificationOContext;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.core.system.ParallaxProcessManagerService;
import com.Parallax.SDK.core.core.system.IParallaxSystemService;
import com.Parallax.SDK.core.core.system.ParallaxProcessRecord;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;

/**
 * Created by BlackBox on 2022/3/15.
 */
public class ParallaxNotificationManagerService extends IParallaxNotificationManagerService.Stub implements IParallaxSystemService {
    private final static ParallaxNotificationManagerService sService = new ParallaxNotificationManagerService();
    public static final String CHANNEL_BLACK = "@com.Parallax.SDK.mirror-";
    public static final String GROUP_BLACK = "@com.Parallax.SDK.mirror-group-";

    private ParallaxNotificationChannelManager mNotificationChannelManager;
    private final Map<String, ParallaxNotificationRecord> mNotificationRecords = new HashMap<>();

    private final NotificationManager mRealNotificationManager =
            (NotificationManager) ParallaxCore.getContext().getSystemService(Context.NOTIFICATION_SERVICE);

    public static ParallaxNotificationManagerService get() {
        return sService;
    }

    @Override
    public void systemReady() {
        mNotificationChannelManager = ParallaxNotificationChannelManager.get();
    }


    private ParallaxNotificationRecord getNotificationRecord(String packageName, int userId) {
        String key = packageName + "-" + userId;
        synchronized (mNotificationRecords) {
            ParallaxNotificationRecord notificationRecord = mNotificationRecords.get(key);
            if (notificationRecord == null) {
                notificationRecord = new ParallaxNotificationRecord();
                mNotificationRecords.put(key, notificationRecord);
            }
            return notificationRecord;
        }
    }

    private void removeNotificationRecord(String packageName, int userId) {
        String key = packageName + "-" + userId;
        synchronized (mNotificationRecords) {
            mNotificationRecords.remove(key);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public NotificationChannel getNotificationChannel(String channelId, int userId) throws RemoteException {
        int callingPid = getCallingPid();
        ParallaxProcessRecord processByPid = ParallaxProcessManagerService.get().findProcessByPid(callingPid);
        if (processByPid == null)
            return null;
        ParallaxNotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mNotificationChannels) {
            return notificationRecord.mNotificationChannels.get(channelId);
        }
    }

    @Override
    public List<NotificationChannel> getNotificationChannels(String packageName, int userId) throws RemoteException {
        ParallaxNotificationRecord notificationRecord = getNotificationRecord(packageName, userId);
        synchronized (notificationRecord.mNotificationChannels) {
            return new ArrayList<>(notificationRecord.mNotificationChannels.values());
        }
    }

    @Override
    public List<NotificationChannelGroup> getNotificationChannelGroups(String packageName, int userId) throws RemoteException {
        ParallaxNotificationRecord notificationRecord = getNotificationRecord(packageName, userId);
        synchronized (notificationRecord.mNotificationChannelGroups) {
            return new ArrayList<>(notificationRecord.mNotificationChannelGroups.values());
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public void createNotificationChannel(NotificationChannel notificationChannel, int userId) {
        int callingPid = getCallingPid();
        ParallaxProcessRecord processByPid = ParallaxProcessManagerService.get().findProcessByPid(callingPid);
        if (processByPid == null)
            return;
        handleNotificationChannel(notificationChannel, userId);
        mRealNotificationManager.createNotificationChannel(notificationChannel);

        resetNotificationChannel(notificationChannel);
        ParallaxNotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mNotificationChannels) {
            notificationRecord.mNotificationChannels.put(notificationChannel.getId(), notificationChannel);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public void deleteNotificationChannel(String channelId, int userId) {
        int callingPid = getCallingPid();
        ParallaxProcessRecord processByPid = ParallaxProcessManagerService.get().findProcessByPid(callingPid);
        if (processByPid == null)
            return;
        ParallaxNotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mNotificationChannels) {
            NotificationChannel remove = notificationRecord.mNotificationChannels.remove(channelId);
            if (remove != null) {
                String blackChannelId = getBlackChannelId(remove.getId(), userId);
                mRealNotificationManager.deleteNotificationChannel(blackChannelId);
            }
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public void createNotificationChannelGroup(NotificationChannelGroup notificationChannelGroup, int userId) {
        int callingPid = getCallingPid();
        ParallaxProcessRecord processByPid = ParallaxProcessManagerService.get().findProcessByPid(callingPid);
        if (processByPid == null)
            return;
        handleNotificationGroup(notificationChannelGroup, userId);
        mRealNotificationManager.createNotificationChannelGroup(notificationChannelGroup);

        resetNotificationGroup(notificationChannelGroup);
        ParallaxNotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mNotificationChannelGroups) {
            notificationRecord.mNotificationChannelGroups.put(notificationChannelGroup.getId(), notificationChannelGroup);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public void deleteNotificationChannelGroup(String groupId, int userId) {
        int callingPid = getCallingPid();
        ParallaxProcessRecord processByPid = ParallaxProcessManagerService.get().findProcessByPid(callingPid);
        if (processByPid == null)
            return;
        ParallaxNotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mNotificationChannelGroups) {
            NotificationChannelGroup remove = notificationRecord.mNotificationChannelGroups.remove(groupId);
            if (remove != null) {
                String blackGroupId = getBlackGroupId(remove.getId(), userId);
                mRealNotificationManager.deleteNotificationChannelGroup(blackGroupId);
            }
        }
    }

    @Override
    public void enqueueNotificationWithTag(int id, String tag, Notification notification, int userId) {
        ParallaxProcessRecord processByPid = ParallaxProcessManagerService.get().findProcessByPid(Binder.getCallingPid());
        if (processByPid == null)
            return;
        int notificationId = getNotificationId(userId, id, processByPid.getPackageName());

        if (ParallaxBuildCompat.isOreo()) {
            NotificationOContext notificationOContext = BRNotificationO.get(notification);
            // channel
            if (notificationOContext._check_mChannelId() != null) {
                String blackChannelId = getBlackChannelId(notificationOContext.mChannelId(), userId);
                notificationOContext._set_mChannelId(blackChannelId);
            }
            // group
            if (notificationOContext._check_mGroupKey() != null) {
                String blackGroupId = getBlackGroupId(notificationOContext.mGroupKey(), userId);
                notificationOContext._set_mGroupKey(blackGroupId);
            }
        }
        ParallaxNotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mIds) {
            notificationRecord.mIds.add(notificationId);
        }
        mRealNotificationManager.notify(notificationId, notification);
    }

    @Override
    public void cancelNotificationWithTag(int id, String tag, int userId) throws RemoteException {
        ParallaxProcessRecord processByPid = ParallaxProcessManagerService.get().findProcessByPid(Binder.getCallingPid());
        if (processByPid == null)
            return;
        int notificationId = getNotificationId(userId, id, processByPid.getPackageName());
        mRealNotificationManager.cancel(notificationId);

        ParallaxNotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mIds) {
            notificationRecord.mIds.remove(notificationId);
        }
    }

    private void handleNotificationChannel(NotificationChannel notificationChannel, int userId) {
        NotificationChannelContext channelContext = BRNotificationChannel.get(notificationChannel);
        String channelId = channelContext.mId();
        String blackChannelId = getBlackChannelId(channelId, userId);
        channelContext._set_mId(blackChannelId);
        notificationChannel.setGroup(getBlackGroupId(notificationChannel.getGroup(), userId));
    }

    private void resetNotificationChannel(NotificationChannel notificationChannel) {
        NotificationChannelContext channelContext = BRNotificationChannel.get(notificationChannel);
        String channelId = channelContext.mId();
        String realChannelId = getRealChannelId(channelId);
        channelContext._set_mId(realChannelId);
    }

    private void handleNotificationGroup(NotificationChannelGroup notificationChannelGroup, int userId) {
        NotificationChannelGroupContext groupContext = BRNotificationChannelGroup.get(notificationChannelGroup);
        String groupId = groupContext.mId();
        String blackGroupId = getBlackGroupId(groupId, userId);
        groupContext._set_mId(blackGroupId);

        List<NotificationChannel> notificationChannels = groupContext.mChannels();
        if (notificationChannels != null) {
            for (NotificationChannel notificationChannel : notificationChannels) {
                createNotificationChannel(notificationChannel, userId);
            }
        }
    }

    private void resetNotificationGroup(NotificationChannelGroup notificationChannelGroup) {
        NotificationChannelGroupContext groupContext = BRNotificationChannelGroup.get(notificationChannelGroup);
        String groupId = groupContext.mId();
        String realGroupId = getRealGroupId(groupId);
        groupContext._set_mId(realGroupId);

        List<NotificationChannel> notificationChannels = groupContext.mChannels();
        if (notificationChannels != null) {
            for (NotificationChannel notificationChannel : notificationChannels) {
                resetNotificationChannel(notificationChannel);
            }
        }
    }

    @SuppressLint("NewApi")
    public void deletePackageNotification(String packageName, int userId) {
        ParallaxNotificationRecord notificationRecord = getNotificationRecord(packageName, userId);
        if (ParallaxBuildCompat.isOreo()) {
            for (NotificationChannelGroup value : notificationRecord.mNotificationChannelGroups.values()) {
                String blackGroupId = getBlackGroupId(value.getId(), userId);
                mRealNotificationManager.deleteNotificationChannelGroup(blackGroupId);
            }
            for (NotificationChannel value : notificationRecord.mNotificationChannels.values()) {
                String blackChannelId = getBlackChannelId(value.getId(), userId);
                mRealNotificationManager.deleteNotificationChannel(blackChannelId);
            }
        }
        for (Integer id : notificationRecord.mIds) {
            mRealNotificationManager.cancel(id);
        }
        removeNotificationRecord(packageName, userId);
    }

    private String getBlackChannelId(String channelId, int userId) {
        if (channelId == null || channelId.contains(CHANNEL_BLACK)) {
            return channelId;
        }
        return channelId + CHANNEL_BLACK + userId;
    }

    private String getRealChannelId(String channelId) {
        if (channelId == null || !channelId.contains(CHANNEL_BLACK)) {
            return channelId;
        }
        return channelId.split(CHANNEL_BLACK)[0];
    }

    private String getBlackGroupId(String groupId, int userId) {
        if (groupId == null || groupId.contains(GROUP_BLACK))
            return groupId;
        return groupId + GROUP_BLACK + userId;
    }

    private String getRealGroupId(String groupId) {
        if (groupId == null || !groupId.contains(GROUP_BLACK))
            return groupId;
        return groupId.split(GROUP_BLACK)[0];
    }

    public static int getNotificationId(int userId, int notificationId, String packageName) {
        return (packageName + userId + notificationId).hashCode();
    }
}
