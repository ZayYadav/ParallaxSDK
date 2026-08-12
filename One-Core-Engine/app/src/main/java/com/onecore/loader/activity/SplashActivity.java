package com.onecore.loader.activity;

import android.animation.Animator;
import android.app.Activity;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.onecore.loader.R;
import com.onecore.loader.security.SecurityThreatDetector;
import com.onecore.loader.security.HostedLicenseClient;
import com.onecore.loader.utils.CrashHandler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class SplashActivity extends Activity {
    
    // Midnight Black to Electric Yellow gradient - Dark Mode Premium
    private final int COLOR_START = Color.parseColor("#000000");     // Pure Black
    private final int COLOR_CENTER = Color.parseColor("#1A1A1A");    // Dark Gray
    private final int COLOR_END = Color.parseColor("#FFD700");       // Electric Yellow
    
    private FrameLayout background;
    private ImageView logo;
    private SharedPreferences prefs;
    private int progress = 0;
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView percentageText;
    
    // Premium Fonts using System Fonts
    private Typeface boldFont;
    private Typeface mediumFont;
    private Typeface regularFont;

    // Enhanced Particle View with Rotation and Better Effects
    public static class EnhancedParticleView extends View {
        private ValueAnimator animator;
        private final Paint paint;
        private final List<Particle> particles = new ArrayList<>();
        private final Random random;

        public static class Particle {
            int color;
            float life;
            float size;
            float vx, vy;
            float x, y;
            float rotation;
            float rotationSpeed;
        }

        public EnhancedParticleView(Context context) {
            super(context);
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.FILL);
            random = new Random();
        }

        private Particle createParticle() {
            Particle p = new Particle();
            p.size = random.nextFloat() * 12f + 4f;
            p.x = random.nextFloat() * getWidth();
            p.y = getHeight();
            p.vy = -(random.nextFloat() * 5f + 2f);
            p.vx = (random.nextFloat() - 0.5f) * 2f;
            p.rotation = random.nextFloat() * 360f;
            p.rotationSpeed = (random.nextFloat() - 0.5f) * 15f;
            
            // Yellow to orange gradient particles
            int red = 255;
            int green = random.nextInt(100) + 155;
            int blue = random.nextInt(50);
            p.color = Color.argb(220, red, green, blue);
            p.life = random.nextFloat() * 0.8f + 0.5f;
            
            return p;
        }

        private void updateParticles() {
            // Create new particles
            if (particles.size() < 80 && random.nextFloat() < 0.4f) {
                particles.add(createParticle());
            }

            // Update existing particles
            Iterator<Particle> iterator = particles.iterator();
            while (iterator.hasNext()) {
                Particle p = iterator.next();
                p.x += p.vx;
                p.y += p.vy;
                p.rotation += p.rotationSpeed;
                p.life -= 0.008f;
                
                // Add some wind effect
                p.vx += (random.nextFloat() - 0.5f) * 0.1f;

                if (p.life <= 0 || p.y < -p.size || p.x > getWidth() + p.size || p.x < -p.size) {
                    iterator.remove();
                }
            }
        }

        private void startAnimation() {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(16);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.addUpdateListener(anim -> {
                updateParticles();
                invalidate();
            });
            animator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            for (Particle p : particles) {
                paint.setColor(p.color);
                paint.setAlpha((int) (p.life * 255));
                canvas.save();
                canvas.rotate(p.rotation, p.x, p.y);
                canvas.drawCircle(p.x, p.y, p.size, paint);
                canvas.restore();
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            startAnimation();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (animator != null) animator.cancel();
        }
    }

    private void initFonts() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Premium system fonts - Modern and Classy
                boldFont = Typeface.create("sans-serif-condensed", Typeface.BOLD);
                mediumFont = Typeface.create("sans-serif-medium", Typeface.NORMAL);
                regularFont = Typeface.create("sans-serif", Typeface.NORMAL);
                
                // Alternative premium fonts (uncomment to try)
                // boldFont = Typeface.create("sans-serif-black", Typeface.NORMAL);
                // mediumFont = Typeface.create("sans-serif", Typeface.BOLD);
                // regularFont = Typeface.create("sans-serif-light", Typeface.NORMAL);
            } else {
                // Fallback for older devices
                boldFont = Typeface.defaultFromStyle(Typeface.BOLD);
                mediumFont = Typeface.defaultFromStyle(Typeface.NORMAL);
                regularFont = Typeface.defaultFromStyle(Typeface.NORMAL);
            }
            
            // Apply fonts to text views
            progressText.setTypeface(boldFont);
            if (percentageText != null) {
                percentageText.setTypeface(mediumFont);
            }
            
            // Add text shadow for premium look
            progressText.setShadowLayer(4, 2, 2, Color.parseColor("#33000000"));
            
            // Add letter spacing for premium look (Android 5.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                progressText.setLetterSpacing(0.08f);
                if (percentageText != null) {
                    percentageText.setLetterSpacing(0.05f);
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback to default
            progressText.setTypeface(Typeface.DEFAULT_BOLD);
        }
    }

    private void animateProgressColor(int progress) {
        int color = (Integer) new ArgbEvaluator().evaluate(progress / 100f, COLOR_START, COLOR_END);
        if (progressBar.getProgressDrawable() != null) {
            progressBar.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        }
    }

    private void updateProgressText(int value) {
        if (value < 30) {
            progressText.setText("✦ INITIALIZING ✦");
        } else if (value < 70) {
            progressText.setText("⚡ LOADING RESOURCES ⚡");
        } else {
            progressText.setText("✨ FINALIZING SETUP ✨");
        }
        
        // Update percentage text
        if (percentageText != null) {
            percentageText.setText(value + "%");
            percentageText.setVisibility(View.VISIBLE);
        }

        // Scale animation for text with bounce effect
        progressText.setScaleX(0.8f);
        progressText.setScaleY(0.8f);
        progressText.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(300)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .start();
    }

    private void goToLogin() {
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

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().getDecorView().getWindowInsetsController().hide(
                    WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            getWindow().setFlags(1024, 1024);
        }
    }

    private void startBackgroundAnimation() {
        ObjectAnimator bgAnim = ObjectAnimator.ofInt(background, "backgroundColor",
                COLOR_START, COLOR_CENTER, COLOR_END, COLOR_START);
        bgAnim.setEvaluator(new ArgbEvaluator());
        bgAnim.setDuration(8000);
        bgAnim.setRepeatCount(ValueAnimator.INFINITE);
        bgAnim.setRepeatMode(ValueAnimator.REVERSE);
        bgAnim.start();
    }

    private void animateLogo() {
        // Logo pulse animation using ScaleAnimation
        ScaleAnimation scaleAnimation = new ScaleAnimation(
                1.0f, 1.1f,
                1.0f, 1.1f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scaleAnimation.setDuration(1000);
        scaleAnimation.setRepeatCount(Animation.INFINITE);
        scaleAnimation.setRepeatMode(Animation.REVERSE);
        scaleAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
        logo.startAnimation(scaleAnimation);
        
        // Logo rotation animation
        ObjectAnimator rotationAnim = ObjectAnimator.ofFloat(logo, "rotation", -5f, 5f);
        rotationAnim.setDuration(2000);
        rotationAnim.setRepeatCount(ValueAnimator.INFINITE);
        rotationAnim.setRepeatMode(ValueAnimator.REVERSE);
        rotationAnim.start();
    }

    private void startTextShimmer() {
        // Text alpha animation with premium effect
        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(progressText, "alpha", 0.7f, 1f);
        alphaAnim.setDuration(800);
        alphaAnim.setRepeatCount(ValueAnimator.INFINITE);
        alphaAnim.setRepeatMode(ValueAnimator.REVERSE);
        alphaAnim.start();
    }

    private void startProgressBarGlow() {
        // Progress bar glow effect
        ValueAnimator glowAnim = ValueAnimator.ofFloat(0.5f, 1f, 0.5f);
        glowAnim.setDuration(1500);
        glowAnim.setRepeatCount(ValueAnimator.INFINITE);
        glowAnim.addUpdateListener(animation -> {
            float alpha = (float) animation.getAnimatedValue();
            if (progressBar.getProgressDrawable() != null) {
                progressBar.getProgressDrawable().setAlpha((int)(alpha * 255));
            }
        });
        glowAnim.start();
    }

    private void startProgress(int duration, boolean isFirstTime) {
        ValueAnimator animator = ValueAnimator.ofInt(0, 100);
        animator.setDuration(duration);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());

        animator.addUpdateListener(valueAnimator -> {
            progress = (int) valueAnimator.getAnimatedValue();
            progressBar.setProgress(progress);
            updateProgressText(progress);
            animateProgressColor(progress);
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (isFirstTime) {
                    prefs.edit().putBoolean("first_launch", false).apply();
                }
                goToLogin();
            }
        });

        animator.start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        hideSystemUI();
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

        // Initialize views
        progressBar = findViewById(R.id.progressBar);
        progressText = findViewById(R.id.progressText);
        logo = findViewById(R.id.logo);
        background = findViewById(R.id.background);
        percentageText = findViewById(R.id.percentageText);

        // Initialize premium fonts
        initFonts();

        // Add enhanced particle animation
        FrameLayout particleContainer = findViewById(R.id.particleContainer);
        particleContainer.addView(new EnhancedParticleView(this));

        // Start all animations
        animateLogo();              // Logo pulse and rotation
        startBackgroundAnimation(); // Background color transition
        startTextShimmer();        // Text fade animation
        startProgressBarGlow();    // Progress bar glow effect

        // Check if first time launch
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean isFirstTime = prefs.getBoolean("first_launch", true);

        // Start progress
        if (isFirstTime) {
            progressText.setText("✦ INITIALIZING FOR FIRST TIME ✦");
            startProgress(5000, true);
        } else {
            progressText.setText("⚡ WELCOME BACK! ⚡");
            startProgress(2000, false);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }
}
