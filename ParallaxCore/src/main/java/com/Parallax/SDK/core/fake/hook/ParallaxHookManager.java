package com.Parallax.SDK.core.fake.hook;

import android.util.Log;
import java.util.HashMap;
import java.util.Map;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.fake.delegate.ParallaxAppInstrumentation;
import com.Parallax.SDK.core.fake.service.*;
import com.Parallax.SDK.core.fake.service.context.ParallaxContentServiceStub;
import com.Parallax.SDK.core.fake.service.context.ParallaxRestrictionsManagerStub;
import com.Parallax.SDK.core.fake.service.libcore.ParallaxOsStub;
import com.Parallax.SDK.core.fake.service.vivo.IParallaxVivoPermissionServiceProxy;
import com.Parallax.SDK.core.utils.ParallaxSlog;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;
/**
 * Created by @RIYAZXERO on 3/30/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxHookManager {
    public static final String TAG = "ParallaxHookManager";

    private static final ParallaxHookManager sHookManager = new ParallaxHookManager();

    private final Map<Class<?>, IParallaxInjectHook> mInjectors = new HashMap<>();

    public static ParallaxHookManager get() {
        return sHookManager;
    }

    public void init() {
        if (ParallaxCore.get().isBlackProcess() || ParallaxCore.get().isServerProcess()) {
            addInjector(new ParallaxOsStub());
            addInjector(new IParallaxDisplayManagerProxy());
            addInjector(new IParallaxJobServiceProxy());
            addInjector(new IParallaxActivityManagerProxy());
            addInjector(new IParallaxFacebookWebPackageManagerProxy());
            addInjector(new IParallaxTelephonyManagerProxy());
            addInjector(new ParallaxHCallbackStub());
            addInjector(new IParallaxWifiManagerProxy());
            addInjector(new IParallaxWifiScannerProxy());
           // addInjector(new ISubProxy());
            addInjector(new IParallaxAppOpsManagerProxy());
            addInjector(new IParallaxNotificationManagerProxy());
            addInjector(new IParallaxAlarmManagerProxy());
            addInjector(new IParallaxAppWidgetManagerProxy());
            addInjector(new IParallaxAudioManagerProxy());
            addInjector(new IParallaxBackupManagerProxy());
            addInjector(new IParallaxBluetoothManagerProxy());
            addInjector(new ParallaxContentServiceStub());
            addInjector(new IParallaxWindowManagerProxy());
            addInjector(new IParallaxUserManagerProxy());
            addInjector(new IParallaxMediaSessionManagerProxy());
            addInjector(new IParallaxLocationManagerProxy());
           // addInjector(new ISmsProxy());
            addInjector(new IParallaxStorageManagerProxy());
            addInjector(new IParallaxLauncherAppsProxy());
            addInjector(new IParallaxAccessibilityManagerProxy());
            addInjector(new IParallaxTelephonyRegistryProxy());
            addInjector(new IParallaxDevicePolicyManagerProxy());
            addInjector(new IParallaxTwitterAwareAccountManagerProxy());
            addInjector(new IParallaxConnectivityManagerProxy());
            addInjector(new IParallaxClipboardManagerProxy());
            addInjector(new IParallaxPhoneSubInfoProxy());
            addInjector(new IParallaxMediaRouterServiceProxy());
            addInjector(new IParallaxNetworkManagementServiceProxy());
            addInjector(new IParallaxPowerManagerProxy());
            addInjector(new IParallaxVibratorServiceProxy());
            addInjector(ParallaxAppInstrumentation.get());
            
            if (ParallaxBuildCompat.isVivo()) {
                addInjector(new IParallaxVivoPermissionServiceProxy());
            }
            if (ParallaxBuildCompat.isBaklava()) {
                addInjector(new IParallaxPersistentDataBlockServiceProxy());
            }
            if (ParallaxBuildCompat.isUpsideDownCake()) {
                //addInjector(new IParallaxAppIntegrityManagerProxy());
                addInjector(new IParallaxLocaleManagerProxy());
            }
            
            if (ParallaxBuildCompat.isS()) {
                addInjector(new IParallaxActivityClientProxy((Object) null));
                addInjector(new IParallaxVpnManagerProxy());
            }
            if (ParallaxBuildCompat.isR()) {
                addInjector(new IParallaxActivityTaskManagerProxy());
                addInjector(new IParallaxPermissionManagerProxy());
            }
            if (ParallaxBuildCompat.isQ()) {
                addInjector(new IParallaxDeviceIdentifiersPolicyProxy());
            }
            if (ParallaxBuildCompat.isPie()) {
                addInjector(new IParallaxSystemUpdateProxy());
            }
            
            if (ParallaxBuildCompat.isOreo_MR1()) {
                addInjector(new IParallaxAutofillManagerProxy());
                addInjector(new IParallaxContextHubServiceProxy());
                addInjector(new IParallaxStorageStatsManagerProxy());
                addInjector(new IParallaxSystemUpdateProxy());
            }
            
            if (ParallaxBuildCompat.isOreo()) {
                addInjector(new IParallaxShortcutManagerProxy());
            }
            
            if (ParallaxBuildCompat.isN()) {
                addInjector(new IParallaxFingerprintManagerProxy());
                addInjector(new IParallaxGraphicsStatsProxy());
            }
        }
        injectAll();
    }

    public void checkEnv(Class<?> clazz) {
        IParallaxInjectHook iInjectHook = mInjectors.get(clazz);
        if (iInjectHook != null && iInjectHook.isBadEnv()) {
            Log.d(TAG, "checkEnv: " + clazz.getSimpleName() + " is bad env");
            iInjectHook.injectHook();
        }
    }

    public void checkAll() {
        for (Class<?> aClass : mInjectors.keySet()) {
            IParallaxInjectHook iInjectHook = mInjectors.get(aClass);
            if (iInjectHook != null && iInjectHook.isBadEnv()) {
                Log.d(TAG, "checkEnv: " + aClass.getSimpleName() + " is bad env");
                iInjectHook.injectHook();
            }
        }
    }

    void addInjector(IParallaxInjectHook injectHook) {
        mInjectors.put(injectHook.getClass(), injectHook);

    }

    void injectAll() {
        for (IParallaxInjectHook value : mInjectors.values()) {
            try {
                ParallaxSlog.d(TAG, "hook: " + value);
                value.injectHook();
            } catch (Exception e) {
                ParallaxSlog.d(TAG, "hook error: " + value);
            }
        }
    }
}