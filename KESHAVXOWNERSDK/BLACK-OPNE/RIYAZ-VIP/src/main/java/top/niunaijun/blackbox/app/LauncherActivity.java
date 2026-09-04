package top.niunaijun.blackbox.app;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;


import java.util.ArrayList;
import java.util.List;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.R;
import top.niunaijun.blackbox.utils.Slog;

public class LauncherActivity extends Activity {

    public static final String TAG = "KESHAVXOWNERLaunch";
    public static final String KEY_INTENT = "launch_intent";
    public static final String KEY_PKG = "launch_pkg";
    public static final String KEY_USER_ID = "launch_user_id";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Animator> runningAnimators = new ArrayList<>();
    private boolean isRunning;

    public static void launch(Intent intent, int userId) {
        Intent splash = new Intent();
        splash.setClass(BlackBoxCore.getContext(), LauncherActivity.class);
        splash.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        splash.putExtra(KEY_INTENT, intent);
        splash.putExtra(KEY_PKG, intent.getPackage());
        splash.putExtra(KEY_USER_ID, userId);
        BlackBoxCore.getContext().startActivity(splash);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        Intent sourceIntent = getIntent();
        if (sourceIntent == null) {
            finish();
            return;
        }

        Intent launchIntent = sourceIntent.getParcelableExtra(KEY_INTENT);
        String packageName = sourceIntent.getStringExtra(KEY_PKG);
        int userId = sourceIntent.getIntExtra(KEY_USER_ID, 0);
        if (launchIntent == null) {
            Slog.e(TAG, "Launch intent is missing");
            finish();
            return;
        }

        if (packageName == null) {
            packageName = launchIntent.getPackage();
        }
        if (packageName == null) {
            Slog.e(TAG, "Package name is missing");
            finish();
            return;
        }

        PackageInfo packageInfo = BlackBoxCore.getBPackageManager()
                .getPackageInfo(packageName, 0, userId);
        if (packageInfo == null) {
            Slog.e(TAG, packageName + " is not installed");
            finish();
            return;
        }

        setContentView(R.layout.activity_launcher);
        bindAppIdentity(packageInfo);
        startBrandAnimations();
        scheduleLaunch(launchIntent, userId);
    }

    private void bindAppIdentity(PackageInfo packageInfo) {
        Drawable icon = packageInfo.applicationInfo
                .loadIcon(BlackBoxCore.getPackageManager());
        CharSequence label = packageInfo.applicationInfo
                .loadLabel(BlackBoxCore.getPackageManager());

        ImageView iconView = findViewById(R.id.iv_icon);
        TextView nameView = findViewById(R.id.tv_app_name);
        iconView.setImageDrawable(icon);
        nameView.setText(label);
    }

    private void startBrandAnimations() {
        View brandPanel = findViewById(R.id.brand_panel);
        View appCard = findViewById(R.id.app_identity_card);
        View ring = findViewById(R.id.brand_ring);
        View glow = findViewById(R.id.runtime_glow);
        View statusDot = findViewById(R.id.runtime_status_dot);

        brandPanel.setAlpha(0f);
        brandPanel.setScaleX(0.8f);
        brandPanel.setScaleY(0.8f);
        AnimatorSet entrance = new AnimatorSet();
        entrance.playTogether(
                ObjectAnimator.ofFloat(brandPanel, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(brandPanel, View.SCALE_X, 0.8f, 1f),
                ObjectAnimator.ofFloat(brandPanel, View.SCALE_Y, 0.8f, 1f));
        entrance.setDuration(620L);
        entrance.setInterpolator(new DecelerateInterpolator(1.5f));
        trackAndStart(entrance);

        appCard.setAlpha(0f);
        appCard.setTranslationY(24f);
        AnimatorSet appEntrance = new AnimatorSet();
        appEntrance.playTogether(
                ObjectAnimator.ofFloat(appCard, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(appCard, View.TRANSLATION_Y, 24f, 0f));
        appEntrance.setStartDelay(170L);
        appEntrance.setDuration(480L);
        appEntrance.setInterpolator(new DecelerateInterpolator());
        trackAndStart(appEntrance);

        ObjectAnimator orbit = ObjectAnimator.ofFloat(ring, View.ROTATION, 0f, 360f);
        orbit.setDuration(7600L);
        orbit.setRepeatCount(ValueAnimator.INFINITE);
        orbit.setInterpolator(new LinearInterpolator());
        trackAndStart(orbit);

        AnimatorSet breathingGlow = new AnimatorSet();
        ObjectAnimator glowX = ObjectAnimator.ofFloat(glow, View.SCALE_X, 0.93f, 1.07f);
        ObjectAnimator glowY = ObjectAnimator.ofFloat(glow, View.SCALE_Y, 0.93f, 1.07f);
        ObjectAnimator glowAlpha = ObjectAnimator.ofFloat(glow, View.ALPHA, 0.4f, 0.85f);
        for (ObjectAnimator animator : new ObjectAnimator[]{glowX, glowY, glowAlpha}) {
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.REVERSE);
        }
        breathingGlow.playTogether(glowX, glowY, glowAlpha);
        breathingGlow.setDuration(1450L);
        trackAndStart(breathingGlow);

        ObjectAnimator pulse = ObjectAnimator.ofFloat(statusDot, View.ALPHA, 0.25f, 1f);
        pulse.setDuration(520L);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        trackAndStart(pulse);
    }

    private void scheduleLaunch(Intent launchIntent, int userId) {
        TextView status = findViewById(R.id.launch_status);
        mainHandler.postDelayed(() -> animateStatus(status, "VERIFYING APP PROFILE"), 220L);
        mainHandler.postDelayed(() -> animateStatus(status, "OPENING ISOLATED WORKSPACE"), 520L);
        mainHandler.postDelayed(() -> new Thread(() -> {
            try {
                BlackBoxCore.getBActivityManager().startActivity(launchIntent, userId);
            } catch (Throwable throwable) {
                Slog.e(TAG, "Unable to start isolated activity: " + throwable.getMessage());
                mainHandler.post(this::finish);
            }
        }, "KESHAVXOWNERLaunch").start(), 680L);
    }

    private void animateStatus(TextView view, String value) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        view.animate().cancel();
        view.animate().alpha(0f).translationY(4f).setDuration(90L).withEndAction(() -> {
            view.setText(value);
            view.setTranslationY(-4f);
            view.animate().alpha(1f).translationY(0f).setDuration(160L).start();
        }).start();
    }

    private void trackAndStart(Animator animator) {
        runningAnimators.add(animator);
        animator.start();
    }

    @Override
    protected void onPause() {
        isRunning = true;
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isRunning) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        for (Animator animator : runningAnimators) {
            animator.cancel();
        }
        runningAnimators.clear();
        super.onDestroy();
    }
}
