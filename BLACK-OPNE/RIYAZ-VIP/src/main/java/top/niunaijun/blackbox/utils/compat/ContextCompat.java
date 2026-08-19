package top.niunaijun.blackbox.utils.compat;

import android.content.Context;
import android.content.ContextWrapper;

import black.android.app.BRContextImpl;
import black.android.app.BRContextImplKitkat;
import black.android.content.AttributionSourceStateContext;
import black.android.content.BRAttributionSource;
import black.android.content.BRAttributionSourceState;
import black.android.content.BRContentResolver;
import top.niunaijun.blackbox.BlackBoxCore;

/**
 * Created by @RIYAZXERO on 3/31/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ContextCompat {
    public static final String TAG = "ContextCompat";

    public static void fixAttributionSourceState(Object obj, int uid) {
        fixAttributionSourceState(obj, uid, 0);
    }

    public static void fixAttributionSourceState(Object obj, int uid, int depth) {
        if (depth >= 10) return;
        if (obj != null && BRAttributionSource.get(obj)._check_mAttributionSourceState() != null) {
            Object mAttributionSourceState = BRAttributionSource.get(obj).mAttributionSourceState();
            AttributionSourceStateContext attributionSourceStateContext =
                    BRAttributionSourceState.get(mAttributionSourceState);
            attributionSourceStateContext._set_packageName(BlackBoxCore.getHostPkg());
            attributionSourceStateContext._set_uid(uid);
            fixAttributionSourceState(BRAttributionSource.get(obj).getNext(), uid, depth + 1);
        }
    }

    public static void fix(Context context) {
        if (context == null) return;
        try {
            int deep = 0;
            while (context instanceof ContextWrapper) {
                context = ((ContextWrapper) context).getBaseContext();
                deep++;
                if (deep >= 10) {
                    return;
                }
            }

            BRContextImpl.get(context)._set_mPackageManager(null);
            try {
                context.getPackageManager();
            } catch (Throwable ignored) {
                // A missing package manager should not make context repair fatal.
            }

            // Calls that leave the virtual process run under the real host UID.
            // Keep package and UID paired consistently; Android 16 validates this
            // pair more strictly in AppOps/AttributionSource and Google services.
            final String hostPackage = BlackBoxCore.getHostPkg();
            final int hostUid = BlackBoxCore.getHostUid();
            BRContextImpl.get(context)._set_mBasePackageName(hostPackage);
            BRContextImplKitkat.get(context)._set_mOpPackageName(hostPackage);
            BRContentResolver.get(context.getContentResolver())._set_mPackageName(hostPackage);

            if (BuildCompat.isS()) {
                fixAttributionSourceState(BRContextImpl.get(context).getAttributionSource(), hostUid);
            }
        } catch (Exception ignored) {
            // Context repair is compatibility best-effort; callers keep their
            // original context if a hidden field differs on an OEM build.
        }
    }
}
