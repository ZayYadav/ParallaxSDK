package com.onecore.loader.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.lsposed.lsparanoid.Obfuscate;

/** Handles the notification Cancel action without opening an Activity. */
@Obfuscate
public final class ServerInstallCancelReceiver extends BroadcastReceiver {

    public static final String ACTION_CANCEL =
            "com.onecore.loader.action.CANCEL_SERVER_INSTALL";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null
                || !ACTION_CANCEL.equals(intent.getAction())) {
            return;
        }
        ServerInstallWorker.cancel(context);
    }
}
