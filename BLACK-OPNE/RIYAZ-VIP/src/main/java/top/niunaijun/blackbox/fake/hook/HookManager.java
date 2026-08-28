package top.niunaijun.blackbox.fake.hook;

import android.util.Log;
import java.util.HashMap;
import java.util.Map;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.delegate.AppInstrumentation;
import top.niunaijun.blackbox.fake.service.*;
import top.niunaijun.blackbox.fake.service.context.ContentServiceStub;
import top.niunaijun.blackbox.fake.service.context.RestrictionsManagerStub;
import top.niunaijun.blackbox.fake.service.libcore.OsStub;
import top.niunaijun.blackbox.fake.service.vivo.IVivoPermissionServiceProxy;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.BuildCompat;
/**
 * Created by @RIYAZXERO on 3/30/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class HookManager {
    public static final String TAG = "HookManager";

    private static final HookManager sHookManager = new HookManager();

    private final Map<Class<?>, IInjectHook> mInjectors = new HashMap<>();

    public static HookManager get() {
        return sHookManager;
    }

    public void init() {
        if (BlackBoxCore.get().isBlackProcess() || BlackBoxCore.get().isServerProcess()) {
            addInjector(new OsStub());
            addInjector(new IDisplayManagerProxy());
            addInjector(new IJobServiceProxy());
            addInjector(new IActivityManagerProxy());
            addInjector(new IPackageManagerProxy());
            addInjector(new ITelephonyManagerProxy());
            addInjector(new HCallbackStub());
            addInjector(new IWifiManagerProxy());
            addInjector(new IWifiScannerProxy());
           // addInjector(new ISubProxy());
            addInjector(new IAppOpsManagerProxy());
            addInjector(new INotificationManagerProxy());
            addInjector(new IAlarmManagerProxy());
            addInjector(new IAppWidgetManagerProxy());
            addInjector(new IAudioManagerProxy());
            addInjector(new IBackupManagerProxy());
            addInjector(new IBluetoothManagerProxy());
            addInjector(new ContentServiceStub());
            addInjector(new IWindowManagerProxy());
            addInjector(new IUserManagerProxy());
            addInjector(new IMediaSessionManagerProxy());
            addInjector(new ILocationManagerProxy());
           // addInjector(new ISmsProxy());
            addInjector(new IStorageManagerProxy());
            addInjector(new ILauncherAppsProxy());
            addInjector(new IAccessibilityManagerProxy());
            addInjector(new ITelephonyRegistryProxy());
            addInjector(new IDevicePolicyManagerProxy());
            addInjector(new IAccountManagerProxy());
            addInjector(new IConnectivityManagerProxy());
            addInjector(new IClipboardManagerProxy());
            addInjector(new IPhoneSubInfoProxy());
            addInjector(new IMediaRouterServiceProxy());
            addInjector(new INetworkManagementServiceProxy());
            addInjector(new IPowerManagerProxy());
            addInjector(new IVibratorServiceProxy());
            addInjector(AppInstrumentation.get());
            
            if (BuildCompat.isVivo()) {
                addInjector(new IVivoPermissionServiceProxy());
            }
            if (BuildCompat.isBaklava()) {
                addInjector(new IPersistentDataBlockServiceProxy());
            }
            if (BuildCompat.isUpsideDownCake()) {
                //addInjector(new IAppIntegrityManagerProxy());
                addInjector(new ILocaleManagerProxy());
            }
            
            if (BuildCompat.isS()) {
                addInjector(new IActivityClientProxy((Object) null));
                addInjector(new IVpnManagerProxy());
            }
            if (BuildCompat.isR()) {
                addInjector(new IActivityTaskManagerProxy());
                addInjector(new IPermissionManagerProxy());
            }
            if (BuildCompat.isQ()) {
                addInjector(new IDeviceIdentifiersPolicyProxy());
            }
            if (BuildCompat.isPie()) {
                addInjector(new ISystemUpdateProxy());
            }
            
            if (BuildCompat.isOreo_MR1()) {
                addInjector(new IAutofillManagerProxy());
                addInjector(new IContextHubServiceProxy());
                addInjector(new IStorageStatsManagerProxy());
                addInjector(new ISystemUpdateProxy());
            }
            
            if (BuildCompat.isOreo()) {
                addInjector(new IShortcutManagerProxy());
            }
            
            if (BuildCompat.isN()) {
                addInjector(new IFingerprintManagerProxy());
                addInjector(new IGraphicsStatsProxy());
            }
        }
        injectAll();
    }

    public void checkEnv(Class<?> clazz) {
        IInjectHook iInjectHook = mInjectors.get(clazz);
        if (iInjectHook != null && iInjectHook.isBadEnv()) {
            Log.d(TAG, "checkEnv: " + clazz.getSimpleName() + " is bad env");
            iInjectHook.injectHook();
        }
    }

    public void checkAll() {
        for (Class<?> aClass : mInjectors.keySet()) {
            IInjectHook iInjectHook = mInjectors.get(aClass);
            if (iInjectHook != null && iInjectHook.isBadEnv()) {
                Log.d(TAG, "checkEnv: " + aClass.getSimpleName() + " is bad env");
                iInjectHook.injectHook();
            }
        }
    }

    void addInjector(IInjectHook injectHook) {
        mInjectors.put(injectHook.getClass(), injectHook);

    }

    void injectAll() {
        for (IInjectHook value : mInjectors.values()) {
            try {
                Slog.d(TAG, "hook: " + value);
                value.injectHook();
            } catch (Exception e) {
                Slog.d(TAG, "hook error: " + value);
            }
        }
    }
}
