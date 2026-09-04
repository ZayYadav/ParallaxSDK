package top.niunaijun.blackbox.utils.compat;

import android.content.ContentProviderClient;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

public class ContentProviderCompat {
    private static final String TAG = "ContentProviderCompat";

    public static Bundle call(Context context, String authority, String method, String arg, Bundle extras) {
        try {
            Uri uri = Uri.parse("content://" + authority);
            return context.getContentResolver().call(uri, method, arg, extras);
        } catch (Exception e) {
            Log.e(TAG, "Provider call failed: " + authority + ", error: " + e.getMessage());
            return null;
        }
    }

    public static Bundle call(Context context, Uri uri, String method, String arg, Bundle extras, int retryCount) {
        ContentProviderClient client = acquireContentProviderClientRetry(context, uri, retryCount);
        try {
            if (client == null) {
                Log.e(TAG, "Client is null for URI: " + uri);
                return null;
            }
            return client.call(method, arg, extras);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException: " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage());
            return null;
        } finally {
            releaseQuietly(client);
        }
    }

    private static ContentProviderClient acquireContentProviderClient(Context context, Uri uri) {
        try {
            return context.getContentResolver().acquireUnstableContentProviderClient(uri);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException for URI: " + uri);
            return null;
        }
    }

    public static ContentProviderClient acquireContentProviderClientRetry(Context context, Uri uri, int retryCount) {
        ContentProviderClient client = acquireContentProviderClient(context, uri);
        if (client == null && retryCount > 0) {
            int retry = 0;
            while (retry < retryCount && client == null) {
                SystemClock.sleep(400L);
                retry++;
                client = acquireContentProviderClient(context, uri);
            }
        }
        return client;
    }

    public static ContentProviderClient acquireContentProviderClientRetry(Context context, String name, int retryCount) {
        ContentProviderClient client = acquireContentProviderClient(context, name);
        if (client == null && retryCount > 0) {
            int retry = 0;
            while (retry < retryCount && client == null) {
                SystemClock.sleep(400L);
                retry++;
                client = acquireContentProviderClient(context, name);
            }
        }
        return client;
    }
    
    private static ContentProviderClient acquireContentProviderClient(Context context, String name) {
        try {
            return context.getContentResolver().acquireUnstableContentProviderClient(name);
        } catch (Exception e) {
            Log.e(TAG, "Acquire failed for: " + name);
            return null;
        }
    }
    
    private static void releaseQuietly(ContentProviderClient client) {
        if (client != null) {
            try {
                if (VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    client.close();
                } else {
                    client.release();
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}