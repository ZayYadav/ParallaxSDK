package com.Parallax.SDK;

import java.io.File;

import net_62v.external.MetaActivationManager;
import net_62v.external.MetaStorageManager;

/**
 * Stable public facade for the ParallaxCore SDK.
 *
 * <p>Consumer apps should use this class instead of importing internal SDK
 * implementation packages directly. The existing internal APIs are kept intact
 * for backwards compatibility.</p>
 */
public final class ParallaxSDK {

    private ParallaxSDK() {
        // Utility class.
    }

    /**
     * Activates the SDK with the supplied user key.
     */
    public static void activate(final String userKey) {
        MetaActivationManager.activateSdk(userKey);
    }

    /**
     * Returns whether the SDK is currently activated.
     */
    public static boolean isActivated() {
        return MetaActivationManager.getActivatedStatus();
    }

    /**
     * Backwards-friendly status alias.
     */
    public static boolean getActivatedStatus() {
        return isActivated();
    }

    /**
     * Returns the latest activation/server message exposed by the SDK.
     */
    public static String getServerMessage() {
        return MetaActivationManager.getServerMessage();
    }

    /**
     * Returns the SDK external storage root used by the existing storage manager.
     */
    public static File getExternalStorageDir() {
        return MetaStorageManager.obtainAppExternalStorageDir();
    }

    /**
     * Returns the OBB container path for a package.
     */
    public static File getObbContainerPath(final String packageName) {
        return MetaStorageManager.getObbContainerPath(packageName);
    }

    /**
     * Returns the data container path for a package.
     */
    public static File getDataContainerPath(final String packageName) {
        return MetaStorageManager.getDataContainerPath(packageName);
    }
}
