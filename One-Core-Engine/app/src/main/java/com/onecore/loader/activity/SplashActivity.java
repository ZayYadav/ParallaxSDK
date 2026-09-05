package com.onecore.loader.activity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.onecore.loader.R;
import com.onecore.loader.security.HostedLicenseClient;
import com.onecore.loader.security.SecurityThreatDetector;
import com.onecore.loader.ui.ThemeManager;
import com.onecore.loader.utils.CrashHandler;

import org.lsposed.lsparanoid.Obfuscate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

@Obfuscate
public class SplashActivity extends Activity {

    private final List<Animator> runningAnimators = new ArrayList<>();
    private SharedPreferences prefs;
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView percentageText;
    private View brandCard;
    private View ambientGlow;
    private View statusDot;
    private ImageView logoRing;
    private int progressPhase = -1;
    private boolean transitioned;

    /** Lightweight native particles keep the splash smooth without a WebView or network asset. */
    public static final class BrandParticleView extends View {
        private static final int MAX_PARTICLES = 30;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Particle> particles = new ArrayList<>();
        private final Random random = new Random();
        private ValueAnimator animator;

        private static final class Particle {
            float x;
            float y;
            float radius;
            float speed;
            float drift;
            float alpha;
            int color;
        }

        public BrandParticleView(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        private Particle createParticle(boolean randomY) {
            Particle particle = new Particle();
            particle.x = random.nextFloat() * Math.max(1, getWidth());
            particle.y = randomY
                    ? random.nextFloat() * Math.max(1, getHeight())
                    : getHeight() + random.nextFloat() * 40f;
            particle.radius = 0.8f + random.nextFloat() * 2.2f;
            particle.speed = 0.25f + random.nextFloat() * 0.75f;
            particle.drift = (random.nextFloat() - 0.5f) * 0.28f;
            particle.alpha = 0.18f + random.nextFloat() * 0.52f;
            particle.color = random.nextBoolean()
                    ? Color.rgb(213, 169, 79)
                    : Color.rgb(225, 229, 236);
            return particle;
        }

        private void seedParticles() {
            if (getWidth() <= 0 || getHeight() <= 0 || !particles.isEmpty()) {
                return;
            }
            for (int i = 0; i < MAX_PARTICLES; i++) {
                particles.add(createParticle(true));
            }
        }

        private void updateParticles() {
            seedParticles();
            Iterator<Particle> iterator = particles.iterator();
            while (iterator.hasNext()) {
                Particle particle = iterator.next();
                particle.y -= particle.speed;
                particle.x += particle.drift;
                if (particle.y < -12f || particle.x < -12f || particle.x > getWidth() + 12f) {
                    iterator.remove();
                }
            }
            while (particles.size() < MAX_PARTICLES) {
                particles.add(createParticle(false));
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            for (Particle particle : particles) {
                paint.setColor(particle.color);
                paint.setAlpha(Math.round(particle.alpha * 255f));
                canvas.drawCircle(particle.x, particle.y, particle.radius, paint);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(16L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.addUpdateListener(value -> {
                updateParticles();
                invalidate();
            });
            animator.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (animator != null) {
                animator.cancel();
            }
            super.onDetachedFromWindow();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // A fresh non-repeating theme is chosen every time the launcher entry opens,
        // even when Android kept the Loader process alive in the background.
        ThemeManager.randomizeForLaunch(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        hideSystemUi();
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));
        setContentView(R.layout.activity_splash);

        SecurityThreatDetector.Threat threat = SecurityThreatDetector.detect(this);
        if (threat != SecurityThreatDetector.Threat.NONE) {
            new Thread(() -> new HostedLicenseClient(this).reportSecurityEvent(
                    threat.name(),
                    threat == SecurityThreatDetector.Threat.INVALID_SIGNATURE
                            ? "critical" : "warning")).start();
            showSecurityWarning(threat);
            return;
        }

        bindViews();
        startBrandAnimations();

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean firstLaunch = prefs.getBoolean("first_launch", true);
        startProgress(firstLaunch ? 3400L : 1900L, firstLaunch);
    }

    private void bindViews() {
        progressBar = findViewById(R.id.progressBar);
        progressText = findViewById(R.id.progressText);
        percentageText = findViewById(R.id.percentageText);
        brandCard = findViewById(R.id.brandCard);
        ambientGlow = findViewById(R.id.ambientGlow);
        statusDot = findViewById(R.id.statusDot);
        logoRing = findViewById(R.id.logoRing);

        FrameLayout particleContainer = findViewById(R.id.particleContainer);
        particleContainer.addView(new BrandParticleView(this),
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void startBrandAnimations() {
        brandCard.setAlpha(0f);
        brandCard.setScaleX(0.78f);
        brandCard.setScaleY(0.78f);
        AnimatorSet entrance = new AnimatorSet();
        entrance.playTogether(
                ObjectAnimator.ofFloat(brandCard, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(brandCard, View.SCALE_X, 0.78f, 1f),
                ObjectAnimator.ofFloat(brandCard, View.SCALE_Y, 0.78f, 1f));
        entrance.setDuration(720L);
        entrance.setInterpolator(new DecelerateInterpolator(1.6f));
        trackAndStart(entrance);

        ObjectAnimator orbit = ObjectAnimator.ofFloat(logoRing, View.ROTATION, 0f, 360f);
        orbit.setDuration(9000L);
        orbit.setRepeatCount(ValueAnimator.INFINITE);
        orbit.setInterpolator(new LinearInterpolator());
        trackAndStart(orbit);

        AnimatorSet breathingGlow = new AnimatorSet();
        ObjectAnimator glowX = ObjectAnimator.ofFloat(ambientGlow, View.SCALE_X, 0.92f, 1.08f);
        ObjectAnimator glowY = ObjectAnimator.ofFloat(ambientGlow, View.SCALE_Y, 0.92f, 1.08f);
        ObjectAnimator glowAlpha = ObjectAnimator.ofFloat(ambientGlow, View.ALPHA, 0.45f, 0.9f);
        for (ObjectAnimator animator : new ObjectAnimator[]{glowX, glowY, glowAlpha}) {
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.REVERSE);
        }
        breathingGlow.playTogether(glowX, glowY, glowAlpha);
        breathingGlow.setDuration(1700L);
        trackAndStart(breathingGlow);

        ObjectAnimator dotPulse = ObjectAnimator.ofFloat(statusDot, View.ALPHA, 0.25f, 1f);
        dotPulse.setDuration(620L);
        dotPulse.setRepeatCount(ValueAnimator.INFINITE);
        dotPulse.setRepeatMode(ValueAnimator.REVERSE);
        trackAndStart(dotPulse);
    }

    private void startProgress(long duration, boolean firstLaunch) {
        ValueAnimator animator = ValueAnimator.ofInt(0, 100);
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator(1.25f));
        animator.addUpdateListener(valueAnimator -> {
            int value = (int) valueAnimator.getAnimatedValue();
            progressBar.setProgress(value);
            percentageText.setText(value + "%");
            updateProgressStatus(value);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (firstLaunch) {
                    prefs.edit().putBoolean("first_launch", false).apply();
                }
                goToLogin();
            }
        });
        trackAndStart(animator);
    }

    private void updateProgressStatus(int value) {
        int phase;
        String label;
        if (value < 28) {
            phase = 0;
            label = "STARTING ONECORE ENGINE";
        } else if (value < 62) {
            phase = 1;
            label = "VERIFYING SECURE RUNTIME";
        } else if (value < 90) {
            phase = 2;
            label = "PREPARING PARALLAX CORE";
        } else {
            phase = 3;
            label = "SECURE WORKSPACE READY";
        }
        if (phase == progressPhase) {
            return;
        }
        progressPhase = phase;
        progressText.animate().cancel();
        progressText.animate()
                .alpha(0f)
                .translationY(5f)
                .setDuration(110L)
                .withEndAction(() -> {
                    progressText.setText(label);
                    progressText.setTranslationY(-5f);
                    progressText.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(190L)
                            .start();
                })
                .start();
    }

    private void trackAndStart(Animator animator) {
        runningAnimators.add(animator);
        animator.start();
    }

    private void goToLogin() {
        if (transitioned || isFinishing()) {
            return;
        }
        transitioned = true;
        startActivity(new Intent(this, LoginActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void showSecurityWarning(SecurityThreatDetector.Threat threat) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.security_warning_title)
                .setMessage(threat.messageResource())
                .setCancelable(false)
                .setPositiveButton(R.string.close_app, (dialog, which) -> {
                    dialog.dismiss();
                    finishAffinity();
                })
                .show();
    }

    private void hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getDecorView().getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    @Override
    protected void onDestroy() {
        for (Animator animator : runningAnimators) {
            animator.cancel();
        }
        runningAnimators.clear();
        super.onDestroy();
    }
}
