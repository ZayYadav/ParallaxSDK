package com.Parallax.SDK.core.app;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.core.system.am.IParallaxRequestPermissionsResult;
import com.Parallax.SDK.core.utils.compat.ParallaxBundleCompat;

// 20240801 add request permission add start 0
@TargetApi(Build.VERSION_CODES.M)
public class ParallaxRequestPermissionsActivity extends Activity {
    private static final int REQUEST_PERMISSION_CODE = 996;

    public static void request(Context context, String[] permissions, IParallaxRequestPermissionsResult callback) {
        Intent intent = new Intent();
        intent.setClassName(ParallaxCore.getContext(), ParallaxRequestPermissionsActivity.class.getName());

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("permissions", permissions);
        ParallaxBundleCompat.putBinder(intent, "callback", callback.asBinder());
        context.startActivity(intent);
    }

    private IParallaxRequestPermissionsResult mCallBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent == null) {
            //finish();
            return;
        }
        final String[] permissions = intent.getStringArrayExtra("permissions");
        IBinder binder = ParallaxBundleCompat.getBinder(intent, "callback");
        if (binder == null || permissions == null) {
           // finish();
            return;
        }
        mCallBack = IParallaxRequestPermissionsResult.Stub.asInterface(binder);
        ParallaxRequestPermissionsActivity.this.requestPermissions(permissions, REQUEST_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, final String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (mCallBack != null) {
            try {
                boolean success = mCallBack.onResult(requestCode, permissions, grantResults);
                if (!success) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ParallaxRequestPermissionsActivity.this, "Request permission failed.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        //finish();
    }
}
// 20240801 add request permission add end 0