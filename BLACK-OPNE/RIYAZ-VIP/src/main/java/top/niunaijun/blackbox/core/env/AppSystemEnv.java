package top.niunaijun.blackbox.core.env;

import android.content.ComponentName;
import android.content.Intent;
import android.MetaCore.RemoteManager;

import java.util.ArrayList;
import java.util.List;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.GmsCore;

public class AppSystemEnv {
    private static final List<String> sSystemPackages = new ArrayList<>();
    private static final List<String> sSuPackages = new ArrayList<>();
    private static final List<String> sXposedPackages = new ArrayList<>();
    private static final List<String> sPreInstallPackages = new ArrayList<>();

    static {
        // Core / AOSP
        sSystemPackages.add("android");
        sSystemPackages.add("com.google.android.webview");
        sSystemPackages.add("com.google.android.webview.dev");
        sSystemPackages.add("com.google.android.webview.beta");
        sSystemPackages.add("com.google.android.webview.canary");
        sSystemPackages.add("com.android.webview");

        // Real Google authentication stack. These packages are never cloned or
        // rewritten: virtual apps may discover and call the copies installed on
        // the phone, so provider-side package/signature verification stays intact.
        sSystemPackages.add("com.google.android.gms");
        sSystemPackages.add("com.google.android.gsf");
        sSystemPackages.add("com.google.android.gsf.login");
        sSystemPackages.add("com.android.vending");
        sSystemPackages.add("com.google.android.play.games");

        // Extra WebView variants
        sSystemPackages.add("com.le.android.webview");
        sSystemPackages.add("com.android.camera");
        sSystemPackages.add("com.android.talkback");
        sSystemPackages.add("com.miui.gallery");

        // MIUI / Xiaomi
        sSystemPackages.add("com.lbe.security.miui");
        sSystemPackages.add("com.miui.contentcatcher");
        sSystemPackages.add("com.miui.catcherpatch");

        // Permission Controllers
        sSystemPackages.add("com.android.permissioncontroller");
        sSystemPackages.add("com.google.android.permissioncontroller");

        // Google Gboard
        sSystemPackages.add("com.google.android.inputmethod.latin");

        // Huawei
        sSystemPackages.add("com.huawei.webview");

        // Oppo / ColorOS & OEM IDs
        sSystemPackages.add("com.heytap.openid");
        sSystemPackages.add("com.coloros.safecenter");

        // Samsung / Asus / Lenovo / ZUI / MSA
        sSystemPackages.add("com.samsung.android.deviceidservice");
        sSystemPackages.add("com.asus.msa.SupplementaryDID");
        sSystemPackages.add("com.zui.deviceidservice");
        sSystemPackages.add("com.mdid.msa");

        // ---- SU / Root apps ----
        sSuPackages.add("com.noshufou.android.su");
        sSuPackages.add("com.noshufou.android.su.elite");
        sSuPackages.add("eu.chainfire.supersu");
        sSuPackages.add("com.koushikdutta.superuser");
        sSuPackages.add("com.thirdparty.superuser");
        sSuPackages.add("com.yellowes.su");
        sSuPackages.add("com.topjohnwu.magisk");

        // ---- Xposed ----
        sXposedPackages.add("de.robv.android.xposed.installer");

        // Real Twitter / X apps. Do not clone them merely for authentication.
        sSystemPackages.add("com.twitter.android");
        sSystemPackages.add("com.twitter.android.lite");
        sSystemPackages.add("com.x.android");

        // Real Facebook apps/services.
        sSystemPackages.add("com.facebook.katana");
        sSystemPackages.add("com.facebook.orca");
        sSystemPackages.add("com.facebook.lite");
        sSystemPackages.add("com.facebook.mlite");
        sSystemPackages.add("com.facebook.services");
    }

    public static boolean isOpenPackage(String packageName) {
        return packageName != null && sSystemPackages.contains(packageName);
    }

    public static boolean isOpenPackage(ComponentName componentName) {
        return componentName != null && isOpenPackage(componentName.getPackageName());
    }

    public static boolean isOpenPackage(Intent intent) {
        if (intent == null) {
            return false;
        }
        // Android 16: real GMS remains open for authentication, but measurement
        // must not receive a virtual package identity over a real binder. Returning
        // false here makes the existing virtual service path reject measurement
        // cleanly while every other GMS/Facebook/X auth intent remains open.
        if (GmsCore.isMeasurementIntent(intent)) {
            return false;
        }
        if (isOpenPackage(intent.getComponent())) {
            return true;
        }
        return isOpenPackage(intent.getPackage());
    }

    public static boolean isBlackPackage(String packageName) {
        if (BlackBoxCore.get().setHideRoot() && sSuPackages.contains(packageName)) {
            return true;
        }
        return RemoteManager.sHideXposed && sXposedPackages.contains(packageName);
    }

    public static List<String> getPreInstallPackages() {
        return sPreInstallPackages;
    }
}
