package com.onecore.loader.server;

/**
 * All user-visible text for the OneCore server downloader lives here.
 *
 * Edit only these values if you want to rename downloader buttons/messages
 * without touching the downloader logic.
 */
public final class ServerInstallStrings {

    private ServerInstallStrings() {
    }

    public static final String INSTALL_BUTTON = "INSTALL";
    public static final String UNINSTALL_BUTTON = "UNINSTALL";
    public static final String DOWNLOAD_BUTTON_PREFIX = "DOWNLOAD";

    public static final String CHOOSER_TITLE = "INSTALL BGMI";
    public static final String CHOOSER_SUBTITLE =
            "Choose where OneCore should get the game files";

    public static final String INSTALL_FROM_DEVICE =
            "INSTALL FROM YOUR INSTALLED GAME";
    public static final String INSTALL_FROM_DEVICE_SUBTITLE =
            "Copy the BGMI APK + OBB already available on this device";

    public static final String INSTALL_FROM_SERVER =
            "INSTALL BGMI FROM ONECORE SERVER";
    public static final String INSTALL_FROM_SERVER_SUBTITLE =
            "Fast resumable CDN download • runs in background with notification progress";

    public static final String MANAGER_TITLE = "BGMI SERVER DOWNLOAD";
    public static final String CANCEL_DOWNLOAD = "CANCEL DOWNLOAD";
    public static final String KEEP_DOWNLOADING = "KEEP DOWNLOADING";
    public static final String CANCEL_CONFIRM_TITLE = "Cancel BGMI download?";
    public static final String CANCEL_CONFIRM_MESSAGE =
            "Downloaded partial files will be kept so a later install can resume.";
    public static final String CANCELLED = "Download cancelled";
    public static final String TAP_OUTSIDE_TO_CANCEL = "Tap outside to close";

    public static final String NOTIFICATION_CHANNEL = "BGMI server downloads";
    public static final String NOTIFICATION_CHANNEL_DESCRIPTION =
            "Background progress for OneCore BGMI server installation";
    public static final String NOTIFICATION_TITLE = "OneCore BGMI Server Install";
    public static final String NOTIFICATION_COMPLETE_TITLE = "BGMI install complete";
    public static final String NOTIFICATION_FAILED_TITLE = "BGMI server install failed";
    public static final String NOTIFICATION_CANCELLED_TITLE = "BGMI download cancelled";

    public static final String STARTED_TOAST =
            "BGMI download started • you can keep using the phone while it downloads.";
    public static final String ALREADY_RUNNING_TOAST =
            "BGMI server download is already running in background.";
    public static final String START_FAILED_TOAST =
            "Unable to start background download.";
    public static final String NOTIFICATION_PERMISSION_TOAST =
            "Notification permission is needed to show background download progress.";
    public static final String STORAGE_PERMISSION_TOAST =
            "Allow file access once. Download will start when you return.";
}
