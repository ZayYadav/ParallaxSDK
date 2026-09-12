package com.Parallax.SDK.core.fake.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.lang.reflect.Method;
import java.util.List;

import com.Parallax.SDK.mirror.android.app.BRNotificationManager;
import com.Parallax.SDK.mirror.android.content.pm.BRParceledListSlice;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.fake.frameworks.ParallaxNotificationManager;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;
import com.Parallax.SDK.core.utils.compat.ParallaxParceledListSliceCompat;

/**
 * Created by @RIYAZXERO on 4/2/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class IParallaxNotificationManagerProxy extends ParallaxBinderInvocationStub {
    public static final String TAG = "IParallaxNotificationManagerProxy";

    public IParallaxNotificationManagerProxy() {
        super(BRNotificationManager.get().getService().asBinder());
    }

    @Override
    protected Object getWho() {
        return BRNotificationManager.get().getService();
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        BRNotificationManager.get()._set_sService(getProxyInvocation());
        replaceSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
//        Slog.d(TAG, "call: " + method.getName());
        ParallaxMethodParameterUtils.replaceAllAppPkg(args);
        return super.invoke(proxy, method, args);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ParallaxProxyMethod("getNotificationChannel")
    public static class GetNotificationChannel extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            NotificationChannel notificationChannel = ParallaxNotificationManager.get().getNotificationChannel((String) args[args.length - 1]);
            return notificationChannel;
        }
    }

    @ParallaxProxyMethod("getNotificationChannels")
    public static class GetNotificationChannels extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            List<NotificationChannel> notificationChannels = ParallaxNotificationManager.get().getNotificationChannels(ParallaxActivityThread.getAppPackageName());
            return ParallaxParceledListSliceCompat.create(notificationChannels);
        }
    }

    @ParallaxProxyMethod("cancelNotificationWithTag")
    public static class CancelNotificationWithTag extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String tag = (String) args[getTagIndex()];
            int id = (int) args[getIdIndex()];
            ParallaxNotificationManager.get().cancelNotificationWithTag(id, tag);
            return 0;
        }

        public int getTagIndex() {
            if (ParallaxBuildCompat.isR()) {
                return 2;
            }
            return 1;
        }

        public int getIdIndex() {
            return getTagIndex() + 1;
        }
    }

    @ParallaxProxyMethod("enqueueNotificationWithTag")
    public static class EnqueueNotificationWithTag extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String tag = (String) args[getTagIndex()];
            int id = (int) args[getIdIndex()];
            Notification notification = ParallaxMethodParameterUtils.getFirstParam(args, Notification.class);
            ParallaxNotificationManager.get().enqueueNotificationWithTag(id, tag, notification);
            return 0;
        }

        public int getTagIndex() {
            return 2;
        }

        public int getIdIndex() {
            return getTagIndex() + 1;
        }
    }

    @ParallaxProxyMethod("createNotificationChannels")
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static class CreateNotificationChannels extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            List<?> list = BRParceledListSlice.get(args[1]).getList();
            for (Object o : list) {
                ParallaxNotificationManager.get().createNotificationChannel((NotificationChannel) o);
            }
            return 0;
        }
    }

    @ParallaxProxyMethod("deleteNotificationChannel")
    public static class DeleteNotificationChannel extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxNotificationManager.get().deleteNotificationChannel((String) args[1]);
            return 0;
        }
    }

    @ParallaxProxyMethod("createNotificationChannelGroups")
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static class CreateNotificationChannelGroups extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            List<?> list = BRParceledListSlice.get(args[1]).getList();
            for (Object o : list) {
                ParallaxNotificationManager.get().createNotificationChannelGroup((NotificationChannelGroup) o);
            }
            return 0;
        }
    }

    @ParallaxProxyMethod("deleteNotificationChannelGroup")
    public static class DeleteNotificationChannelGroup extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxNotificationManager.get().deleteNotificationChannelGroup((String) args[1]);
            return 0;
        }
    }

    @ParallaxProxyMethod("getNotificationChannelGroups")
    public static class GetNotificationChannelGroups extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            List<NotificationChannelGroup> notificationChannelGroups = ParallaxNotificationManager.get().getNotificationChannelGroups(ParallaxActivityThread.getAppPackageName());
            return ParallaxParceledListSliceCompat.create(notificationChannelGroups);
        }
    }
}
