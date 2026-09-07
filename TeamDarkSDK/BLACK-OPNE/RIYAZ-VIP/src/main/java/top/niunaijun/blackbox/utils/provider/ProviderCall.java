package top.niunaijun.blackbox.utils.provider;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import java.io.Serializable;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.utils.compat.ContentProviderCompat;

public class ProviderCall {
    private static final String TAG = "ProviderCall";

    public static Bundle call(String authority, Context context, String method, String arg, Bundle bundle, int retryCount) {
        try {
            Uri uri = Uri.parse("content://" + authority);
            Bundle result = ContentProviderCompat.call(context, uri, method, arg, bundle, retryCount);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Provider call failed: " + authority + ", method: " + method);
            return null;
        }
    }

    public static Bundle callSafely(String authority, String methodName, String arg, Bundle bundle) {
        try {
            Bundle result = call(authority, BlackBoxCore.getContext(), methodName, arg, bundle, 5);
            return result != null ? result : new Bundle();
        } catch (Exception e) {
            Log.e(TAG, "Safe call failed: " + authority);
            return new Bundle();
        }
    }

    public static final class Builder {
        private Context context;
        private Bundle bundle = new Bundle();
        private String method;
        private String auth;
        private String arg;
        private int retryCount = 5;

        public Builder(Context context, String auth) {
            this.context = context;
            this.auth = auth;
        }

        public Builder methodName(String name) {
            this.method = name;
            return this;
        }

        public Builder arg(String arg) {
            this.arg = arg;
            return this;
        }

        public Builder addArg(String key, Object value) {
            if (value != null) {
                if (value instanceof Boolean) {
                    bundle.putBoolean(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    bundle.putInt(key, (Integer) value);
                } else if (value instanceof String) {
                    bundle.putString(key, (String) value);
                } else if (value instanceof Serializable) {
                    bundle.putSerializable(key, (Serializable) value);
                } else if (value instanceof Bundle) {
                    bundle.putBundle(key, (Bundle) value);
                } else if (value instanceof Parcelable) {
                    bundle.putParcelable(key, (Parcelable) value);
                } else if (value instanceof int[]) {
                    bundle.putIntArray(key, (int[]) value);
                } else {
                    throw new IllegalArgumentException("Unknown type " + value.getClass() + " in Bundle.");
                }
            }
            return this;
        }

        public Builder retry(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public Bundle call() {
            try {
                return ProviderCall.call(auth, context, method, arg, bundle, retryCount);
            } catch (Exception e) {
                Log.e(TAG, "Builder call failed: " + auth);
                return null;
            }
        }

        public Bundle callSafely() {
            Bundle result = call();
            return result != null ? result : new Bundle();
        }
    }
}