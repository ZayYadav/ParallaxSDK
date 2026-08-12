package com.onecore.loader.floating;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;
import com.onecore.loader.R;
import com.onecore.loader.utils.FLog;
import com.onecore.loader.utils.FPrefs;

public class FloatAim extends Service {

	private static final boolean NATIVE_LIBRARY_LOADED;
	private boolean checkStatus;
	private View mainView;
	private RelativeLayout miniFloatView;
	private WindowManager windowManager;
	private LayoutParams paramsView;

    public FPrefs getPref() {
        return FPrefs.with(this);
    }
    
	public static native void AimbotFOV(boolean value);

	static {
        boolean loaded = false;
        try {
            System.loadLibrary("MCoreEsp");
            loaded = true;
        } catch(UnsatisfiedLinkError w) {
            FLog.error(w.getMessage());
        }
        NATIVE_LIBRARY_LOADED = loaded;
    }

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		if (!NATIVE_LIBRARY_LOADED) {
			stopSelf();
			return;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
			FLog.error("Overlay permission is not granted");
			stopSelf();
			return;
		}
		ShowMainView();
	}

	private void ShowMainView() {
		mainView = LayoutInflater.from(this).inflate(R.layout.floataimbot, null);
		paramsView = getParaams();
		windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
		windowManager.addView(mainView, paramsView);
		InitShowMainView();
	}

	private WindowManager.LayoutParams getParaams() {
		final WindowManager.LayoutParams params = new WindowManager.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT, getLayoutType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        
		params.gravity = Gravity.CENTER | Gravity.CENTER;
		params.x = 0;
		params.y = 0;
		return params;
	}

	private static int getLayoutType() {
		int LAYOUT_FLAG;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_TOAST;
		} else {
			LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
		}
		return LAYOUT_FLAG;
	}

	private void InitShowMainView() {
		miniFloatView = mainView.findViewById(R.id.miniFloatMenu);
		RelativeLayout layoutView = mainView.findViewById(R.id.layoutControlView);
		final ImageView myImageView = mainView.findViewById(R.id.imgAimbot);

		layoutView.setOnTouchListener(new View.OnTouchListener() {
				private int initialX;
				private int initialY;
				private float initialTouchX;
				private float initialTouchY;

				@Override
				public boolean onTouch(View v, MotionEvent event) {
					switch (event.getAction()) {
						case MotionEvent.ACTION_DOWN:
							initialX = paramsView.x;
							initialY = paramsView.y;
							initialTouchX = event.getRawX();
							initialTouchY = event.getRawY();
							return true;

						case MotionEvent.ACTION_UP:
							int Xdiff = (int) (event.getRawX() - initialTouchX);
							int Ydiff = (int) (event.getRawY() - initialTouchY);
							if (Xdiff < 5 && Ydiff < 5) {
								if (miniFloatView.getVisibility() == View.VISIBLE) {
									if (!checkStatus) {
										checkStatus = true;
										AimbotFOV(true);
										myImageView.animate().rotationBy(0).rotation(-45);
										myImageView.setColorFilter(0xFFa1e36f, PorterDuff.Mode.SRC_ATOP);
									} else {
										checkStatus = false;
										AimbotFOV(false);
										myImageView.animate().rotationBy(-45).rotation(0);
										myImageView.setColorFilter(0xFFb5b5b5, PorterDuff.Mode.SRC_ATOP);
									}
								}
							}
							return true;

						case MotionEvent.ACTION_MOVE:
							paramsView.x = initialX + (int) (event.getRawX() - initialTouchX);
							paramsView.y = initialY + (int) (event.getRawY() - initialTouchY);
							windowManager.updateViewLayout(mainView, paramsView);
							return true;
					}
					return false;
				}
			});
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		checkStatus = false;
		AimbotFOV(false);
		if (mainView != null)
			windowManager.removeView(mainView);
	}
}
