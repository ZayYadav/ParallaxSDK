package top.niunaijun.blackbox.core.system.api;

import android.MetaCore.social.SocialAuthManager;
import android.app.Activity;
import android.content.Context;

/** Java-friendly public facade for social sign-in shipped by the SDK. */
public final class MetaSocialAuthManager {
    private MetaSocialAuthManager() {
    }

    public interface Callback {
        void onSuccess(String provider, String userId, String email,
                       String name, String avatarUrl, long sessionExpiresAtEpochSeconds);

        void onError(String code, String message);

        void onCancelled();
    }

    public static void configure(Context context, String socialAuthBaseUrl) {
        SocialAuthManager.configure(context, socialAuthBaseUrl);
    }

    public static void showLogin(Activity activity, Callback callback) {
        SocialAuthManager.showLogin(activity, adapt(callback));
    }

    public static void loginGoogle(Activity activity, Callback callback) {
        SocialAuthManager.login(activity, SocialAuthManager.Provider.GOOGLE, adapt(callback));
    }

    public static void loginFacebook(Activity activity, Callback callback) {
        SocialAuthManager.login(activity, SocialAuthManager.Provider.FACEBOOK, adapt(callback));
    }

    public static void loginX(Activity activity, Callback callback) {
        SocialAuthManager.login(activity, SocialAuthManager.Provider.X, adapt(callback));
    }

    public static boolean isLoggedIn(Context context) {
        return SocialAuthManager.isLoggedIn(context);
    }

    public static String currentUserJson(Context context) {
        SocialAuthManager.SocialUser user = SocialAuthManager.currentUser(context);
        return user == null ? "{}" : user.toJson().toString();
    }

    public static void validateSession(Context context, Callback callback) {
        SocialAuthManager.validateSession(context, adapt(callback));
    }

    public static void logout(Context context) {
        SocialAuthManager.logout(context);
    }

    private static SocialAuthManager.Listener adapt(Callback callback) {
        if (callback == null) {
            return null;
        }
        return new SocialAuthManager.Listener() {
            @Override
            public void onSuccess(SocialAuthManager.SocialUser user) {
                callback.onSuccess(
                        user.provider,
                        user.id,
                        user.email,
                        user.name,
                        user.avatarUrl,
                        user.sessionExpiresAtEpochSeconds);
            }

            @Override
            public void onError(String code, String message) {
                callback.onError(code, message);
            }

            @Override
            public void onCancelled() {
                callback.onCancelled();
            }
        };
    }
}
