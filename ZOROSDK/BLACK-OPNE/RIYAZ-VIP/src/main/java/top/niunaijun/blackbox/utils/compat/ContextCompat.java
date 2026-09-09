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
        try {
            if (obj != null && BRAttributionSource.get(obj)._check_mAttributionSourceState() != null) {
                Object mAttributionSourceState = BRAttributionSource.get(obj).mAttributionSourceState();
                AttributionSourceStateContext attributionSourceStateContext =
                        BRAttributionSourceState.get(mAttributionSourceState);
                attributionSourceStateContext._set_packageName(BlackBoxCore.getHostPkg());
                attributionSourceStateContext._set_uid(uid);
                fixAttributionSourceState(BRAttributionSource.get(obj).getNext(), uid, depth + 1);
            }
        } catch (Throwable ignored) {
            // Android/OEM releases may reshape hidden attribution fields. A failed
            // node must not prevent the remaining outbound identity repairs.
        }
    }

    /**
     * Repairs the identity Android system services observe for a virtual Context.
     * Calls crossing into the real framework execute under the Loader host UID,
     * so package and UID attribution have to remain paired.
     *
     * Each hidden-field update is isolated: one Android 16/OEM reflection mismatch
     * must not skip every later repair and leave a stale virtual package attached
     * to the host UID.
     */
    public static void fix(Context context) {
        Context baseContext = unwrap(context);
        if (baseContext == null) return;

        final String hostPackage = BlackBoxCore.getHostPkg();
        final int hostUid = BlackBoxCore.getHostUid();

        try {
            BRContextImpl.get(baseContext)._set_mPackageManager(null);
            baseContext.getPackageManager();
        } catch (Throwable ignored) {
            // PackageManager refresh is best effort.
        }

        try {
            BRContextImpl.get(baseContext)._set_mBasePackageName(hostPackage);
        } catch (Throwable ignored) {
        }

        try {
            BRContextImplKitkat.get(baseContext)._set_mOpPackageName(hostPackage);
        } catch (Throwable ignored) {
        }

        try {
            if (baseContext.getContentResolver() != null) {
                BRContentResolver.get(baseContext.getContentResolver())._set_mPackageName(hostPackage);
            }
        } catch (Throwable ignored) {
        }

        // Android 16: keep non-essential Google measurement from sending a
        // virtual package name to real Play Services. The wrapper delegates every
        // normal PM operation and only augments returned metadata.
        try {
            VirtualPackageMetadataCompat.install(baseContext);
        } catch (Throwable ignored) {
        }

        if (BuildCompat.isS()) {
            try {
                fixAttributionSourceState(
                        BRContextImpl.get(baseContext).getAttributionSource(), hostUid);
            } catch (Throwable ignored) {
            }
        }
    }

    private static Context unwrap(Context context) {
        if (context == null) return null;
        Context current = context;
        int depth = 0;
        try {
            while (current instanceof ContextWrapper && depth < 10) {
                Context next = ((ContextWrapper) current).getBaseContext();
                if (next == null || next == current) break;
                current = next;
                depth++;
            }
        } catch (Throwable ignored) {
            // Keep the deepest Context reached so far.
        }
        return current;
    }
}
