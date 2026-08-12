package com.onecore.loader.activity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ProgressBar;
import androidx.cardview.widget.CardView;
import java.util.Random;
import android.graphics.PixelFormat;
import android.widget.LinearLayout;
import android.view.ViewGroup;

import com.onecore.loader.R;
import com.onecore.loader.utils.CrashHandler;
import com.onecore.loader.utils.FLog;
import com.onecore.loader.security.SecurePreferences;
import com.onecore.loader.security.HostedLicenseClient;
import com.onecore.loader.security.SecurityThreatDetector;
import com.Jagdish.tastytoast.TastyToast;

public class LoginActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "com.onecore.loader.prefs";
    private static final String PREF_PERMISSIONS_GRANTED = "permissions_granted";
    private static final int REQUEST_MANAGE_STORAGE_PERMISSION = 100;
    private static final int REQUEST_MANAGE_UNKNOWN_APP_SOURCES = 200;
    private static final String USER = "USER";

    private TextView btnSignIn;
    private FrameLayout logo;
    private FrameLayout particlesContainer;
    private TextView textVip1;
    private TextView textVip2;
    private Handler handler;
    private AnimatorSet logoAnimator;
    private Runnable particleRunnable;
    private LinearLayout loadingOverlay;
    private TextView loadingText;
    private boolean isShowingDenied = false;
    private LinearLayout deniedOverlay = null;
    private ProgressBar loadingSpinner;
    
    public static class PremiumBackgroundDrawable extends Drawable {
        private int angle = 0;
        private final Handler handler = new Handler();
        private final Paint paint = new Paint();
        private final int[] colors = {Color.parseColor("#000000"), Color.parseColor("#1A1A1A"), Color.parseColor("#FFD700")};
        private final float[] positions = {0.0f, 0.5f, 1.0f};
        private final Runnable animator;
        private final LinearGradient gradient;

        public PremiumBackgroundDrawable() {
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
            gradient = new LinearGradient(0, 0, 0, 0, colors, positions, Shader.TileMode.CLAMP);
            paint.setShader(gradient);

            animator = new Runnable() {
                @Override
                public void run() {
                    angle = (angle + 2) % 360;
                    invalidateSelf();
                    handler.postDelayed(this, 16);
                }
            };

            handler.post(animator);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            Matrix matrix = new Matrix();
            matrix.setRotate(angle, bounds.centerX(), bounds.centerY());
            gradient.setLocalMatrix(matrix);
            canvas.drawRect(bounds, paint);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter colorFilter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

        public void stop() {
            handler.removeCallbacks(animator);
        }
    }

    public static class PremiumProgressDrawable extends Drawable {
        private final Handler handler = new Handler();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private boolean increasing = true;
        private int sweepAngle = 0;

        private final Runnable animator = new Runnable() {
            @Override
            public void run() {
                if (increasing) {
                    sweepAngle += 10;
                    if (sweepAngle >= 300) increasing = false;
                } else {
                    sweepAngle -= 10;
                    if (sweepAngle <= 20) increasing = true;
                }
                invalidateSelf();
                handler.postDelayed(this, 20);
            }
        };

        public PremiumProgressDrawable() {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(8f);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            oval.set(bounds);
            paint.setShader(new SweepGradient(
                    bounds.centerX(), bounds.centerY(),
                    new int[]{0, Color.parseColor("#FFD700"), Color.parseColor("#FFA500"), 0},
                    new float[]{0f, 0.25f, 0.75f, 1f}
            ));
            canvas.drawArc(oval, -90f, sweepAngle, false, paint);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter colorFilter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

        public void start() {
            handler.post(animator);
        }

        public void stop() {
            handler.removeCallbacks(animator);
        }
    }

    static {
        try {
            System.loadLibrary("ParallaxLoader");
        } catch (UnsatisfiedLinkError e) {
            FLog.error("Native library not loaded: " + e.getMessage());
        }
    }

    private void showLoadingAnimation(String message) {
        runOnUiThread(() -> {
            // Don't show loading if denied is showing
            if (isShowingDenied) return;
            
            // Remove existing overlay if any
            if (loadingOverlay != null && loadingOverlay.getParent() != null) {
                ((ViewGroup) loadingOverlay.getParent()).removeView(loadingOverlay);
            }
            
            // Create new overlay
            loadingOverlay = new LinearLayout(LoginActivity.this);
            loadingOverlay.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            loadingOverlay.setGravity(android.view.Gravity.CENTER);
            loadingOverlay.setBackgroundColor(Color.parseColor("#CC000000"));
            loadingOverlay.setOrientation(LinearLayout.VERTICAL);
            loadingOverlay.setClickable(true);
            loadingOverlay.setFocusable(true);
            
            // Create loading text
            loadingText = new TextView(LoginActivity.this);
            loadingText.setText(message);
            loadingText.setTextColor(Color.parseColor("#FFD700"));
            loadingText.setTextSize(20);
            loadingText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            loadingText.setGravity(android.view.Gravity.CENTER);
            loadingText.setPadding(0, 30, 0, 30);
            
            // Create loading spinner
            loadingSpinner = new ProgressBar(LoginActivity.this);
            loadingSpinner.setIndeterminate(true);
            loadingSpinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FFD700")));
            
            loadingOverlay.addView(loadingSpinner);
            loadingOverlay.addView(loadingText);
            
            // Add to window
            addContentView(loadingOverlay, loadingOverlay.getLayoutParams());
            
            // Add fade in animation
            loadingOverlay.setAlpha(0f);
            loadingOverlay.animate()
                .alpha(1f)
                .setDuration(300)
                .start();
            
            // Add scale animation to text
            ScaleAnimation scaleAnim = new ScaleAnimation(
                1f, 1.2f, 1f, 1.2f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            );
            scaleAnim.setDuration(500);
            scaleAnim.setRepeatCount(Animation.INFINITE);
            scaleAnim.setRepeatMode(Animation.REVERSE);
            loadingText.startAnimation(scaleAnim);
        });
    }
    
    private void hideLoadingAnimation() {
        runOnUiThread(() -> {
            if (loadingOverlay != null) {
                loadingOverlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        if (loadingOverlay.getParent() != null) {
                            ((ViewGroup) loadingOverlay.getParent()).removeView(loadingOverlay);
                        }
                        if (loadingText != null) {
                            loadingText.clearAnimation();
                        }
                        loadingOverlay = null;
                        loadingText = null;
                        loadingSpinner = null;
                    })
                    .start();
            }
        });
    }
    
    private void showAccessDeniedAnimation(String errorMessage) {
        runOnUiThread(() -> {
            // Prevent multiple denied overlays
            if (isShowingDenied) return;
            isShowingDenied = true;
            
            // Remove any existing denied overlay
            if (deniedOverlay != null && deniedOverlay.getParent() != null) {
                ((ViewGroup) deniedOverlay.getParent()).removeView(deniedOverlay);
            }
            
            deniedOverlay = new LinearLayout(LoginActivity.this);
            deniedOverlay.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            deniedOverlay.setGravity(android.view.Gravity.CENTER);
            deniedOverlay.setBackgroundColor(Color.parseColor("#CC000000"));
            deniedOverlay.setOrientation(LinearLayout.VERTICAL);
            deniedOverlay.setClickable(true);
            deniedOverlay.setFocusable(true);
            
            // Create denied text
            TextView deniedText = new TextView(LoginActivity.this);
            deniedText.setText("✗ ACCESS DENIED ✗");
            deniedText.setTextColor(Color.parseColor("#FF4444"));
            deniedText.setTextSize(28);
            deniedText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            deniedText.setGravity(android.view.Gravity.CENTER);
            deniedText.setPadding(20, 20, 20, 20);
            
            // Create message text
            TextView messageText = new TextView(LoginActivity.this);
            messageText.setText(errorMessage != null && !errorMessage.isEmpty() ? errorMessage : "USER OR GAME NOT REGISTERED");
            messageText.setTextColor(Color.parseColor("#FFD700"));
            messageText.setTextSize(16);
            messageText.setGravity(android.view.Gravity.CENTER);
            messageText.setPadding(20, 10, 20, 20);
            
            // Create buttons layout
            LinearLayout buttonLayout = new LinearLayout(LoginActivity.this);
            buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
            buttonLayout.setGravity(android.view.Gravity.CENTER);
            buttonLayout.setPadding(20, 20, 20, 20);
            
            // Get Key Button
            TextView getKeyBtn = new TextView(LoginActivity.this);
            getKeyBtn.setText("GET KEY");
            getKeyBtn.setTextColor(Color.parseColor("#FFD700"));
            getKeyBtn.setTextSize(14);
            getKeyBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            getKeyBtn.setPadding(40, 15, 40, 15);
            getKeyBtn.setBackgroundResource(R.drawable.premium_button_border);
            getKeyBtn.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(getString(R.string.key_portal_url)));
                startActivity(intent);
            });
            
            // Try Again Button
            TextView tryAgainBtn = new TextView(LoginActivity.this);
            tryAgainBtn.setText("TRY AGAIN");
            tryAgainBtn.setTextColor(Color.parseColor("#FFD700"));
            tryAgainBtn.setTextSize(14);
            tryAgainBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            tryAgainBtn.setPadding(40, 15, 40, 15);
            tryAgainBtn.setBackgroundResource(R.drawable.premium_button_border);
            tryAgainBtn.setOnClickListener(v -> {
                hideAccessDeniedAnimation();
            });
            
            buttonLayout.addView(getKeyBtn);
            buttonLayout.addView(tryAgainBtn);
            
            deniedOverlay.addView(deniedText);
            deniedOverlay.addView(messageText);
            deniedOverlay.addView(buttonLayout);
            
            // Add to window
            addContentView(deniedOverlay, deniedOverlay.getLayoutParams());
            
            // Add fade in animation
            deniedOverlay.setAlpha(0f);
            deniedOverlay.animate()
                .alpha(1f)
                .setDuration(300)
                .withEndAction(() -> {
                    // Shake animation
                    ObjectAnimator shakeX = ObjectAnimator.ofFloat(deniedOverlay, "translationX", 0f, 20f, -20f, 10f, -10f, 0f);
                    shakeX.setDuration(500);
                    shakeX.start();
                })
                .start();
        });
    }
    
    private void hideAccessDeniedAnimation() {
        runOnUiThread(() -> {
            if (deniedOverlay != null && isShowingDenied) {
                deniedOverlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        if (deniedOverlay.getParent() != null) {
                            ((ViewGroup) deniedOverlay.getParent()).removeView(deniedOverlay);
                        }
                        isShowingDenied = false;
                        deniedOverlay = null;
                    })
                    .start();
            } else {
                isShowingDenied = false;
            }
        });
    }

    private void InitView() {
        SecurePreferences securePreferences = new SecurePreferences(this);
        final EditText inputKey = findViewById(R.id.textUsername);
        inputKey.setFilterTouchesWhenObscured(true);
        String savedKey = securePreferences.getString(USER, "");
        SharedPreferences legacyPreferences = getSharedPreferences("settings", MODE_PRIVATE);
        String legacyKey = legacyPreferences.getString(USER, "");
        if ((savedKey == null || savedKey.isEmpty()) && legacyKey != null && !legacyKey.isEmpty()) {
            try {
                securePreferences.putString(USER, legacyKey);
                savedKey = legacyKey;
                legacyPreferences.edit().remove(USER).apply();
            } catch (IllegalStateException ignored) {
                // Leave the legacy value intact until secure storage becomes available.
            }
        }
        if (savedKey != null) inputKey.setText(savedKey);

        btnSignIn = findViewById(R.id.btnSignIn);
        btnSignIn.setOnClickListener(v -> {
            String key = inputKey.getText().toString().trim();
            if (key.isEmpty()) {
                inputKey.setError("Please enter Licence Key");
                inputKey.requestFocus();
                return;
            }
            try {
                securePreferences.putString(USER, key);
            } catch (IllegalStateException exception) {
                inputKey.setError("Secure storage is unavailable");
                return;
            }
            // Hide denied overlay if showing
            if (isShowingDenied) {
                hideAccessDeniedAnimation();
            }
            
            // Show loading animation EVERY TIME
            showLoadingAnimation("✦ VERIFYING LICENSE ✦");
            
            // Start login verification
            Login(this, key);
        });

        ImageView showPwd = findViewById(R.id.show_pwd);
        ImageView hidePwd = findViewById(R.id.vis_pwd);
        
        findViewById(R.id.paste).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clipData = cm != null && cm.hasPrimaryClip() ? cm.getPrimaryClip() : null;
            if (clipData != null && clipData.getItemCount() > 0) {
                CharSequence text = clipData.getItemAt(0).coerceToText(this);
                if (text != null) {
                    inputKey.setText(text.toString().trim());
                    inputKey.setSelection(inputKey.getText().length());
                    FLog.info("Clipboard text pasted into license field");
                    TastyToast.makeText(this, "Key Pasted!", TastyToast.LENGTH_SHORT, TastyToast.SUCCESS);
                }
            }
        });

        showPwd.setOnClickListener(v -> {
            showPwd.setVisibility(View.GONE);
            hidePwd.setVisibility(View.VISIBLE);
            inputKey.setTransformationMethod(PasswordTransformationMethod.getInstance());
            inputKey.setSelection(inputKey.getText().length());
        });

        hidePwd.setOnClickListener(v -> {
            hidePwd.setVisibility(View.GONE);
            showPwd.setVisibility(View.VISIBLE);
            inputKey.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            inputKey.setSelection(inputKey.getText().length());
        });
        
        TextView timg = findViewById(R.id.telegram);
        timg.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(getString(R.string.key_portal_url)));
            startActivity(intent);
        });
    }

    private static void Login(LoginActivity activity, String key) {
        Handler responseHandler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(Message msg) {
                // Hide loading animation first
                activity.hideLoadingAnimation();
                
                if (msg.what == 0) {
                    // Success - show success message and go to main
                    TastyToast.makeText(activity, "✓ ACCESS GRANTED ✓", TastyToast.LENGTH_SHORT, TastyToast.SUCCESS);
                    activity.startActivity(new Intent(activity, MainActivity.class));
                    activity.finish();
                } else {
                    // Failed - show access denied animation
                    String error = msg.obj.toString();
                    activity.showAccessDeniedAnimation(error);
                }
            }
        };

        new Thread(() -> {
            Message msg = new Message();
            try {
                String result = new HostedLicenseClient(activity).activate(key);
                if ("OK".equals(result)) {
                    msg.what = 0;
                } else {
                    msg.what = 1;
                    msg.obj = result != null && !result.isEmpty() ? result : "USER OR GAME NOT REGISTERED";
                }
            } catch (Throwable t) {
                FLog.error("License verification failed", t);
                msg.what = 1;
                msg.obj = "Verification is temporarily unavailable";
            }
            responseHandler.sendMessage(msg);
        }).start();
    }

    private void setupLogoAnimation() {
        ObjectAnimator ofFloat = ObjectAnimator.ofFloat(this.logo, "translationY", 0.0f, -15.0f);
        ObjectAnimator ofFloat2 = ObjectAnimator.ofFloat(this.logo, "translationY", -15.0f, 0.0f);
        ObjectAnimator ofFloat3 = ObjectAnimator.ofFloat(this.logo, "rotation", 0.0f, 360.0f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(this.logo, "scaleX", 1.0f, 1.05f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(this.logo, "scaleY", 1.0f, 1.05f);
        
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleX.setRepeatMode(ValueAnimator.REVERSE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatMode(ValueAnimator.REVERSE);
        
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playSequentially(ofFloat, ofFloat2);
        animatorSet.setDuration(2000);
        
        AnimatorSet scaleSet = new AnimatorSet();
        scaleSet.playTogether(scaleX, scaleY);
        scaleSet.setDuration(2000);
        
        AnimatorSet animatorSet2 = new AnimatorSet();
        this.logoAnimator = animatorSet2;
        animatorSet2.playTogether(animatorSet, ofFloat3, scaleSet);
        this.logoAnimator.setDuration(4000);
        this.logoAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        this.logoAnimator.addListener(new Animator.AnimatorListener() {
            public void onAnimationCancel(Animator animator) {}
            public void onAnimationEnd(Animator animator) {
                LoginActivity.this.logoAnimator.start();
            }
            public void onAnimationRepeat(Animator animator) {}
            public void onAnimationStart(Animator animator) {}
        });
        this.logoAnimator.start();
    }
    
    private void startParticlesAnimation() {
        final Random random = new Random();
        this.particleRunnable = new Runnable() {
            @Override
            public void run() {
                int width = particlesContainer.getWidth();
                int height = particlesContainer.getHeight();
                if (width <= 0 || height <= 0) {
                    handler.postDelayed(this, 100);
                    return;
                }

                View particle = new View(LoginActivity.this);
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.OVAL);
                
                int[] goldColors = {
                    Color.parseColor("#FFD700"),
                    Color.parseColor("#FFC125"),
                    Color.parseColor("#FFB347"),
                    Color.parseColor("#FFA500")
                };
                drawable.setColor(goldColors[random.nextInt(goldColors.length)]);
                drawable.setAlpha(180);
                particle.setBackground(drawable);

                int size = random.nextInt(8) + 3;
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
                params.leftMargin = random.nextInt(width);
                params.topMargin = height;
                particle.setLayoutParams(params);

                particlesContainer.addView(particle);

                particle.animate()
                        .translationY(-height - 150)
                        .translationX(random.nextInt(120) - 60)
                        .rotation(random.nextInt(360))
                        .setDuration(random.nextInt(4000) + 3000)
                        .withEndAction(() -> particlesContainer.removeView(particle))
                        .start();

                handler.postDelayed(this, 150);
            }
        };

        particlesContainer.post(() -> handler.post(particleRunnable));
    }

    private void checkAndRequestPermissions() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!isStoragePermissionGranted()) {
            requestStoragePermissionDirect();
        } else if (!canRequestPackageInstalls()) {
            requestUnknownAppPermissionsDirect();
        } else {
            prefs.edit().putBoolean(PREF_PERMISSIONS_GRANTED, true).apply();
            FLog.info("Required permissions are available");
        }
    }
    
    private static Drawable createCardBackground(LoginActivity loginActivity) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(GradientDrawable.RECTANGLE);
        gradientDrawable.setCornerRadius((float) dpToPx(loginActivity, 16));
        gradientDrawable.setColor(Color.parseColor("#1A1A1A"));
        gradientDrawable.setStroke(dpToPx(loginActivity, 2), Color.parseColor("#FFD700"));
        return gradientDrawable;
    }
    
    private static int dpToPx(LoginActivity loginActivity, int i5) {
        return (int) (((float) i5) * loginActivity.getResources().getDisplayMetrics().density);
    }

    private boolean canRequestPackageInstalls() {
        return Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
    }

    private boolean isStoragePermissionGranted() {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
    }

    private void requestStoragePermissionDirect() {
        if (Build.VERSION.SDK_INT >= 30) {
            Intent intent = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
            intent.setData(Uri.fromParts("package", getPackageName(), null));
            startActivityForResult(intent, REQUEST_MANAGE_STORAGE_PERMISSION);
        } else {
            requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, REQUEST_MANAGE_STORAGE_PERMISSION);
        }
    }

    private void requestUnknownAppPermissionsDirect() {
        if (Build.VERSION.SDK_INT >= 26) {
            Intent intent = new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_MANAGE_UNKNOWN_APP_SOURCES);
        }
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_login);

        SecurityThreatDetector.Threat threat = SecurityThreatDetector.detect(this);
        if (threat != SecurityThreatDetector.Threat.NONE) {
            new Thread(() -> new HostedLicenseClient(this).reportSecurityEvent(
                    threat.name(),
                    threat == SecurityThreatDetector.Threat.INVALID_SIGNATURE
                            ? "critical" : "warning")).start();
            showSecurityWarning(threat);
            return;
        }

        this.logo = findViewById(R.id.logoAnimator);
        this.particlesContainer = findViewById(R.id.particles_container);
        this.textVip1 = findViewById(R.id.textVip1);
        this.textVip2 = findViewById(R.id.textVip2);

        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));
        checkAndRequestPermissions();
        setupLogoAnimation();
        startParticlesAnimation();
        this.handler = new Handler();
        InitView();
        hideSystemUI();

    }
    
    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().getDecorView().getWindowInsetsController()
                    .hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && particleRunnable != null)
            handler.removeCallbacks(particleRunnable);
        if (logoAnimator != null) logoAnimator.cancel();
        hideAccessDeniedAnimation();
        hideLoadingAnimation();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MANAGE_STORAGE_PERMISSION) {
            checkAndRequestPermissions();
        } else if (requestCode == REQUEST_MANAGE_UNKNOWN_APP_SOURCES && canRequestPackageInstalls()) {
            Process.killProcess(Process.myPid());
        }
    }
}
