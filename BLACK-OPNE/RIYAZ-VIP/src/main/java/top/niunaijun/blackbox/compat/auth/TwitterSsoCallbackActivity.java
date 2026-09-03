package top.niunaijun.blackbox.compat.auth;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/** Receives only the legacy twittersdk callback and hands it to the active bridge. */
public final class TwitterSsoCallbackActivity extends Activity {
    private static final String TAG = "TwitterSSOCompat";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dispatch(getIntent());
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        dispatch(intent);
        finish();
    }

    private void dispatch(Intent intent) {
        Uri uri = intent == null ? null : intent.getData();
        boolean expected = uri != null
                && "twittersdk".equalsIgnoreCase(uri.getScheme())
                && "callback".equalsIgnoreCase(uri.getHost());
        boolean delivered = expected && TwitterSsoCompatActivity.dispatchCallback(uri);
        Log.i(TAG, "compat stage=callback_receiver expected=" + expected
                + " delivered=" + delivered);
    }
}
