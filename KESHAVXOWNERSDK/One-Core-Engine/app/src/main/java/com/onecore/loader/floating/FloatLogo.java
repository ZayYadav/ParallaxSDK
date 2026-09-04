package com.onecore.loader.floating;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Vibrator;
import android.os.IBinder;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.animation.ObjectAnimator;
import com.onecore.loader.R;
import com.onecore.loader.utils.FLog;
import com.onecore.loader.utils.FPrefs;
import android.widget.Toast;
import com.topjohnwu.superuser.Shell;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FloatLogo extends Service {

    private WindowManager mWindowManager;
    private View mFloatingView;

    public FloatLogo() {
    }

    static {
        try {
            System.loadLibrary("ParallaxLoader");
        } catch(UnsatisfiedLinkError w) {
            FLog.error(w.getMessage());
        }
    }

	public native void SettingMemory(int code, boolean value);
    public native void SettingValue(int code, boolean value);
    public native void SettingValueI(int code, int value);
    public native void recoil(int recoil);
    public native void distances(int distances);
    
    
    public FPrefs getPref() {
        return FPrefs.with(this);
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    View espView, logoView;

    @SuppressLint("CutPasteId")
    @Override
    public void onCreate() {
        super.onCreate();
        createOver();
        logoView = mFloatingView.findViewById(R.id.relativeLayoutParent);
        espView = mFloatingView.findViewById(R.id.espView);
		espView.setAlpha(1f);
        Init();
    }

    @SuppressLint("InflateParams")
    void createOver() {
        mFloatingView = LayoutInflater.from(this).inflate(R.layout.float_logo, null);
        int LAYOUT_FLAG;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
        }
		
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            LAYOUT_FLAG, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.RGBA_8888
		);

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mFloatingView, params);
        
		TextView closeBtn = mFloatingView.findViewById(R.id.closeBtn);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                espView.setVisibility(View.GONE);
                logoView.setVisibility(View.VISIBLE);
            }
        });
		
        final LinearLayout player = mFloatingView.findViewById(R.id.players);
        final LinearLayout items = mFloatingView.findViewById(R.id.items);
        final LinearLayout vehicle = mFloatingView.findViewById(R.id.vehicles);
        final LinearLayout selectorItems = mFloatingView.findViewById(R.id.selectorItems);
        final LinearLayout modeitems2 = mFloatingView.findViewById(R.id.modeitems2);
		final LinearLayout memory = mFloatingView.findViewById(R.id.memoryMenu);
        final LinearLayout track = mFloatingView.findViewById(R.id.selectorTrack);
		
		final LinearLayout bg_playerBtn = mFloatingView.findViewById(R.id.bg_playerBtn);
        final LinearLayout bg_pickupBtn = mFloatingView.findViewById(R.id.bg_pickupBtn);
		final LinearLayout bg_trackBtn = mFloatingView.findViewById(R.id.bg_trackBtn);
        final LinearLayout bg_setting = mFloatingView.findViewById(R.id.bg_setting);
        
		final ImageView playerBtn = mFloatingView.findViewById(R.id.playerBtn);
        final ImageView pickupBtn = mFloatingView.findViewById(R.id.pickupBtn);
		final ImageView trackBtn = mFloatingView.findViewById(R.id.trackBtn);
		final ImageView settingBtn = mFloatingView.findViewById(R.id.setBtn);
		
		final TextView vehicleBtn = mFloatingView.findViewById(R.id.vehicleBtn);
        final TextView itemBtn = mFloatingView.findViewById(R.id.itemBtn);
        final TextView textview_subx = mFloatingView.findViewById(R.id.textview_subx);


        playerBtn.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {
                bg_setting.setBackgroundColor(Color.TRANSPARENT);
                bg_pickupBtn.setBackgroundColor(Color.TRANSPARENT);
                bg_playerBtn.setBackgroundColor(Color.parseColor("#3C8C92AC"));
				bg_trackBtn.setBackgroundColor(Color.TRANSPARENT);
				playerBtn.setColorFilter(Color.parseColor("#ffffff"));
				pickupBtn.setColorFilter(Color.TRANSPARENT);
				trackBtn.setColorFilter(Color.TRANSPARENT);
				settingBtn.setColorFilter(Color.TRANSPARENT);
				
                selectorItems.setVisibility(View.GONE);
                player.setVisibility(View.VISIBLE);
                modeitems2.setVisibility(View.GONE);
				memory.setVisibility(View.GONE);
				track.setVisibility(View.GONE);
                textview_subx.setText("Display");
            }
        });
		
        pickupBtn.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {
                bg_pickupBtn.setBackgroundColor(Color.parseColor("#3C8C92AC"));
                bg_playerBtn.setBackgroundColor(Color.TRANSPARENT);
                bg_setting.setBackgroundColor(Color.TRANSPARENT);
				bg_trackBtn.setBackgroundColor(Color.TRANSPARENT);
				playerBtn.setColorFilter(Color.TRANSPARENT);
				pickupBtn.setColorFilter(Color.parseColor("#ffffff"));
				trackBtn.setColorFilter(Color.TRANSPARENT);
				settingBtn.setColorFilter(Color.TRANSPARENT);
				
                modeitems2.setVisibility(View.VISIBLE);
                selectorItems.setVisibility(View.VISIBLE);
                player.setVisibility(View.GONE);
				memory.setVisibility(View.GONE);
				track.setVisibility(View.GONE);
                textview_subx.setText("Items");
            }
        });
		
		trackBtn.setOnClickListener(new View.OnClickListener() {
				@SuppressLint("SetTextI18n")
				@Override
				public void onClick(View v) {
					bg_pickupBtn.setBackgroundColor(Color.TRANSPARENT);
					bg_playerBtn.setBackgroundColor(Color.TRANSPARENT);
					bg_setting.setBackgroundColor(Color.TRANSPARENT);
					bg_trackBtn.setBackgroundColor(Color.parseColor("#3C8C92AC"));
					playerBtn.setColorFilter(Color.TRANSPARENT);
					pickupBtn.setColorFilter(Color.TRANSPARENT);
					trackBtn.setColorFilter(Color.parseColor("#ffffff"));
					settingBtn.setColorFilter(Color.TRANSPARENT);
					
					modeitems2.setVisibility(View.GONE);
					selectorItems.setVisibility(View.GONE);
					player.setVisibility(View.GONE);
					memory.setVisibility(View.GONE);
					track.setVisibility(View.VISIBLE);
					textview_subx.setText("Controls");
				}
			});
		
        settingBtn.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {
                bg_pickupBtn.setBackgroundColor(Color.TRANSPARENT);
                bg_playerBtn.setBackgroundColor(Color.TRANSPARENT);
                bg_setting.setBackgroundColor(Color.parseColor("#3C8C92AC"));
				bg_trackBtn.setBackgroundColor(Color.TRANSPARENT);
				playerBtn.setColorFilter(Color.TRANSPARENT);
				pickupBtn.setColorFilter(Color.TRANSPARENT);
				trackBtn.setColorFilter(Color.TRANSPARENT);
				settingBtn.setColorFilter(Color.parseColor("#ffffff"));
				
                modeitems2.setVisibility(View.GONE);
                selectorItems.setVisibility(View.GONE);
                player.setVisibility(View.GONE);
				memory.setVisibility(View.VISIBLE);
				track.setVisibility(View.GONE);
                textview_subx.setText("Settings");
            }
        });

        vehicleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                items.setVisibility(View.GONE);
                vehicle.setVisibility(View.VISIBLE);
                player.setVisibility(View.GONE);
				memory.setVisibility(View.GONE);
				track.setVisibility(View.GONE);
            }
        });

        itemBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                items.setVisibility(View.VISIBLE);
                vehicle.setVisibility(View.GONE);
                player.setVisibility(View.GONE);
				memory.setVisibility(View.GONE);
				track.setVisibility(View.GONE);
            }
        });

        //Floating window setting
		Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		
		final GestureDetector gestureDetector = new GestureDetector(this, new SingleTapConfirm());
		mFloatingView.findViewById(R.id.relativeLayoutParent).setOnTouchListener(new View.OnTouchListener() {
				private int initialX;
				private int initialY;
				private float initialTouchX;
				private float initialTouchY;
				private ObjectAnimator alphaAnimator;

				@SuppressLint("ClickableViewAccessibility")
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					final Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

					if (gestureDetector.onTouchEvent(event)) {
						espView.setVisibility(View.VISIBLE);
						logoView.setVisibility(View.GONE);
						return true;
					} else {
						switch (event.getAction()) {
							case MotionEvent.ACTION_DOWN:
								initialX = params.x;
								initialY = params.y;
								initialTouchX = event.getRawX();
								initialTouchY = event.getRawY();
								// Vibrate for 200 milliseconds
								vibrator.vibrate(100);
								// Set the alpha value to 0.5 (0.0 - fully transparent, 1.0 - fully opaque)
								v.setAlpha(1f);
								return true;
							case MotionEvent.ACTION_MOVE:
								params.x = initialX + (int) (event.getRawX() - initialTouchX);
								params.y = initialY + (int) (event.getRawY() - initialTouchY);
								mWindowManager.updateViewLayout(mFloatingView, params);
								return true;
							case MotionEvent.ACTION_UP:
								// Gradually restore transparency to normal
								alphaAnimator = ObjectAnimator.ofFloat(v, "alpha", 0.5f);
								alphaAnimator.setDuration(500); // Adjust the duration as desired
								alphaAnimator.start();
								return true;
						}
						return false;
					}
				}
			});
		
		
		
        mFloatingView.findViewById(R.id.espView).setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        mWindowManager.updateViewLayout(mFloatingView, params);
                        return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFloatingView != null) {
			mWindowManager.removeView(mFloatingView);
		}
    }
    
    private boolean isDisplay;
    private String daemonPath;
    public static String socket;
    
    private void DrawESP() {
        loadAssets();
        if (!isDisplay) {
           socket = daemonPath;
           isDisplay = true;
        }
        startService(new Intent(this, Overlay.class));
    }
    
    private void StopESP() {
        isDisplay = false;
        stopService(new Intent(this, Overlay.class));
    }
    
    private void loadAssets() {
        InputStream in = getResources().openRawResource(R.raw.sock);
        FileOutputStream out ;
        try {
            out = new FileOutputStream(getFilesDir() + "/sock");
            byte[] buff = new byte[1024];
            int read ;

            try {
                while ((read = in.read(buff)) > 0) {
                    out.write(buff, 0, read);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                in.close();
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        daemonPath = getFilesDir() + "/sock";
		try {
			Runtime.getRuntime().exec("chmod 777 " + daemonPath);
		} catch (IOException e) {}
    }
    
    private void setValue(String key, boolean value) {
        SharedPreferences sp = getSharedPreferences("espValue", Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putBoolean(key, value);
        ed.apply();
    }

    private boolean getConfig(String key) {
        SharedPreferences sp = getSharedPreferences("espValue", Context.MODE_PRIVATE);
        return sp.getBoolean(key, false);
    }
    
    int getEspValue(String a, int b) {
        SharedPreferences sp = this.getSharedPreferences("espValue", Context.MODE_PRIVATE);
        return sp.getInt(a, b);
    }
    
    void setEspValue(String a, int b) {
        SharedPreferences sp = this.getSharedPreferences("espValue", Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putInt(a, b);
        ed.apply();
    }
    
    

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    void Init() {
        final CheckBox Buggy = mFloatingView.findViewById(R.id.Buggy);
        Buggy.setChecked(getConfig((String) Buggy.getText()));
        Buggy.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Buggy.getText()), Buggy.isChecked());
            }
        });
        final CheckBox UAZ = mFloatingView.findViewById(R.id.UAZ);
        UAZ.setChecked(getConfig((String) UAZ.getText()));
        UAZ.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(UAZ.getText()), UAZ.isChecked());
            }
        });
		final CheckBox UTV = mFloatingView.findViewById(R.id.UTV);
        UTV.setChecked(getConfig((String) UTV.getText()));
        UTV.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					setValue(String.valueOf(UTV.getText()), UTV.isChecked());
				}
			});
        final CheckBox Bike = mFloatingView.findViewById(R.id.Bike);
        Bike.setChecked(getConfig((String) Bike.getText()));
        Bike.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Bike.getText()), Bike.isChecked());
            }
        });
        final CheckBox Dacia = mFloatingView.findViewById(R.id.Dacia);
        Dacia.setChecked(getConfig((String) Dacia.getText()));
        Dacia.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Dacia.getText()), Dacia.isChecked());
            }
        });
		final CheckBox CoupeRB = mFloatingView.findViewById(R.id.CoupeRB);
        CoupeRB.setChecked(getConfig((String) CoupeRB.getText()));
        CoupeRB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				setValue(String.valueOf(CoupeRB.getText()), CoupeRB.isChecked());
			}
		});
		final CheckBox ATV = mFloatingView.findViewById(R.id.ATV);
        ATV.setChecked(getConfig((String) ATV.getText()));
        ATV.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				setValue(String.valueOf(ATV.getText()), ATV.isChecked());
			}
		});
        final CheckBox AquaRail = mFloatingView.findViewById(R.id.AquaRail);
        AquaRail.setChecked(getConfig((String) AquaRail.getText()));
        AquaRail.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(AquaRail.getText()), AquaRail.isChecked());
            }
        });
        final CheckBox Boat = mFloatingView.findViewById(R.id.Boat);
        Boat.setChecked(getConfig((String) Boat.getText()));
        Boat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Boat.getText()), Boat.isChecked());
            }
        });
        final CheckBox Scooter = mFloatingView.findViewById(R.id.Scooter);
        Scooter.setChecked(getConfig((String) Scooter.getText()));
        Scooter.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Scooter.getText()), Scooter.isChecked());
            }
        });
        final CheckBox Bus = mFloatingView.findViewById(R.id.Bus);
        Bus.setChecked(getConfig((String) Bus.getText()));
        Bus.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Bus.getText()), Bus.isChecked());
            }
        });
        final CheckBox Mirado = mFloatingView.findViewById(R.id.Mirado);
        Mirado.setChecked(getConfig((String) Mirado.getText()));
        Mirado.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Mirado.getText()), Mirado.isChecked());
            }
        });
        final CheckBox Rony = mFloatingView.findViewById(R.id.Rony);
        Rony.setChecked(getConfig((String) Rony.getText()));
        Rony.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Rony.getText()), Rony.isChecked());
            }
        });
        final CheckBox Snowbike = mFloatingView.findViewById(R.id.Snowbike);
        Snowbike.setChecked(getConfig((String) Snowbike.getText()));
        Snowbike.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Snowbike.getText()), Snowbike.isChecked());
            }
        });
        final CheckBox Snowmobile = mFloatingView.findViewById(R.id.Snowmobile);
        Snowmobile.setChecked(getConfig((String) Snowmobile.getText()));
        Snowmobile.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Snowmobile.getText()), Snowmobile.isChecked());
            }
        });
        final CheckBox Tuk = mFloatingView.findViewById(R.id.Tuk);
        Tuk.setChecked(getConfig((String) Tuk.getText()));
        Tuk.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Tuk.getText()), Tuk.isChecked());
            }
        });
        final CheckBox PickUp = mFloatingView.findViewById(R.id.PickUp);
        PickUp.setChecked(getConfig((String) PickUp.getText()));
        PickUp.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(PickUp.getText()), PickUp.isChecked());
            }
        });
        final CheckBox Bigfoot = mFloatingView.findViewById(R.id.Bigfoot);
        Bigfoot.setChecked(getConfig((String) Bigfoot.getText()));
        Bigfoot.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Bigfoot.getText()), Bigfoot.isChecked());
            }
        });
        final CheckBox BRDM = mFloatingView.findViewById(R.id.BRDM);
        BRDM.setChecked(getConfig((String) BRDM.getText()));
        BRDM.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(BRDM.getText()), BRDM.isChecked());
            }
        });
        final CheckBox LadaNiva = mFloatingView.findViewById(R.id.LadaNiva);
        LadaNiva.setChecked(getConfig((String) LadaNiva.getText()));
        LadaNiva.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(LadaNiva.getText()), LadaNiva.isChecked());
            }
        });
        final CheckBox CheekPad = mFloatingView.findViewById(R.id.CheekPad);
        CheekPad.setChecked(getConfig((String) CheekPad.getText()));
        CheekPad.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(CheekPad.getText()), CheekPad.isChecked());
            }
        });
        final CheckBox AirDrop = mFloatingView.findViewById(R.id.AirDrop);
        AirDrop.setChecked(getConfig((String) AirDrop.getText()));
        AirDrop.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(AirDrop.getText()), AirDrop.isChecked());
            }
        });
        final CheckBox Loot = mFloatingView.findViewById(R.id.Loot);
        Loot.setChecked(getConfig((String) Loot.getText()));
        Loot.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Loot.getText()), Loot.isChecked());
            }
        });
        final CheckBox DropPlane = mFloatingView.findViewById(R.id.DropPlane);
        DropPlane.setChecked(getConfig((String) DropPlane.getText()));
        DropPlane.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(DropPlane.getText()), DropPlane.isChecked());
            }
        });
        final CheckBox isEnemyWeapon = mFloatingView.findViewById(R.id.isEnemyWeapon);
        isEnemyWeapon.setChecked(getConfig((String) isEnemyWeapon.getText()));
        SettingValue(10, getConfig((String) isEnemyWeapon.getText()));
        isEnemyWeapon.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(isEnemyWeapon.getText()), isEnemyWeapon.isChecked());
                SettingValue(10, isEnemyWeapon.isChecked());
            }
        });
        final CheckBox isGrenadeWarning = mFloatingView.findViewById(R.id.isGrenadeWarning);
        isGrenadeWarning.setChecked(getConfig((String) isGrenadeWarning.getText()));
        SettingValue(9, getConfig((String) isGrenadeWarning.getText()));
        isGrenadeWarning.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(isGrenadeWarning.getText()), isGrenadeWarning.isChecked());
                SettingValue(9, isGrenadeWarning.isChecked());
            }
        });
        final CheckBox isSkelton = mFloatingView.findViewById(R.id.isSkelton);
        isSkelton.setChecked(getConfig((String) isSkelton.getText()));
        SettingValue(8, getConfig((String) isSkelton.getText()));
        isSkelton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(isSkelton.getText()), isSkelton.isChecked());
                SettingValue(8, isSkelton.isChecked());
            }
        });
        final CheckBox isHead = mFloatingView.findViewById(R.id.isHead);
        isHead.setChecked(getConfig((String) isHead.getText()));
        SettingValue(6, getConfig((String) isHead.getText()));
        isHead.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(isHead.getText()), isHead.isChecked());
                SettingValue(6, isHead.isChecked());
            }
        });
        final CheckBox isBack = mFloatingView.findViewById(R.id.isBack);
        isBack.setChecked(getConfig((String) isBack.getText()));
        SettingValue(7, getConfig((String) isBack.getText()));
        isBack.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(isBack.getText()), isBack.isChecked());
                SettingValue(7, isBack.isChecked());
            }
        });
        final CheckBox isHealth = mFloatingView.findViewById(R.id.isHealth);
        isHealth.setChecked(getConfig((String) isHealth.getText()));
        SettingValue(4, getConfig((String) isHealth.getText()));
        isHealth.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(isHealth.getText()), isHealth.isChecked());
                SettingValue(4, isHealth.isChecked());
            }
        });
        final CheckBox isName = mFloatingView.findViewById(R.id.isName);
        isName.setChecked(getConfig((String) isName.getText()));
        SettingValue(5, getConfig((String) isName.getText()));
        isName.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(isName.getText()), isName.isChecked());
                SettingValue(5, isName.isChecked());
            }
        });
		final CheckBox isTeamID = mFloatingView.findViewById(R.id.isTeamID);
        isTeamID.setChecked(getConfig((String) isTeamID.getText()));
		SettingValue(2, getConfig((String) isTeamID.getText()));
        isTeamID.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				setValue(String.valueOf(isTeamID.getText()), isTeamID.isChecked());
				SettingValue(2, getConfig((String) isTeamID.getText()));
			}
		});
        final CheckBox isDist = mFloatingView.findViewById(R.id.isDist);
        isDist.setChecked(getConfig((String) isDist.getText()));
        SettingValue(3, getConfig((String) isDist.getText()));
        isDist.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(isDist.getText()), isDist.isChecked());
                SettingValue(3, isDist.isChecked());
            }
        });
        final CheckBox canted = mFloatingView.findViewById(R.id.canted);
        canted.setChecked(getConfig((String) canted.getText()));
        canted.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(canted.getText()), canted.isChecked());
            }
        });
        final CheckBox reddot = mFloatingView.findViewById(R.id.reddot);
        reddot.setChecked(getConfig((String) reddot.getText()));
        reddot.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(reddot.getText()), reddot.isChecked());
            }
        });
        final CheckBox hollow = mFloatingView.findViewById(R.id.hollow);
        hollow.setChecked(getConfig((String) hollow.getText()));
        hollow.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(hollow.getText()), hollow.isChecked());
            }
        });
        final CheckBox twox = mFloatingView.findViewById(R.id.twox);
        twox.setChecked(getConfig((String) twox.getText()));
        twox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(twox.getText()), twox.isChecked());
            }
        });
        final CheckBox threex = mFloatingView.findViewById(R.id.threex);
        threex.setChecked(getConfig((String) threex.getText()));
        threex.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(threex.getText()), threex.isChecked());
            }
        });
        final CheckBox fourx = mFloatingView.findViewById(R.id.fourx);
        fourx.setChecked(getConfig((String) fourx.getText()));
        fourx.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(fourx.getText()), fourx.isChecked());
            }
        });
        final CheckBox sixx = mFloatingView.findViewById(R.id.sixx);
        sixx.setChecked(getConfig((String) sixx.getText()));
        sixx.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(sixx.getText()), sixx.isChecked());
            }
        });
        final CheckBox eightx = mFloatingView.findViewById(R.id.eightx);
        eightx.setChecked(getConfig((String) eightx.getText()));
        eightx.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(eightx.getText()), eightx.isChecked());
            }
        });
        final CheckBox AWM = mFloatingView.findViewById(R.id.AWM);
        AWM.setChecked(getConfig((String) AWM.getText()));
        AWM.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(AWM.getText()), AWM.isChecked());
            }
        });
        final CheckBox QBU = mFloatingView.findViewById(R.id.QBU);
        QBU.setChecked(getConfig((String) QBU.getText()));
        QBU.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(QBU.getText()), QBU.isChecked());
            }
        });
        final CheckBox SLR = mFloatingView.findViewById(R.id.SLR);
        SLR.setChecked(getConfig((String) SLR.getText()));
        SLR.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(SLR.getText()), SLR.isChecked());
            }
        });
        final CheckBox SKS = mFloatingView.findViewById(R.id.SKS);
        SKS.setChecked(getConfig((String) SKS.getText()));
        SKS.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(SKS.getText()), SKS.isChecked());
            }
        });
        final CheckBox Mini14 = mFloatingView.findViewById(R.id.Mini14);
        Mini14.setChecked(getConfig((String) Mini14.getText()));
        Mini14.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Mini14.getText()), Mini14.isChecked());
            }
        });
        final CheckBox M24 = mFloatingView.findViewById(R.id.M24);
        M24.setChecked(getConfig((String) M24.getText()));
        M24.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(M24.getText()), M24.isChecked());
            }
        });
        final CheckBox Kar98k = mFloatingView.findViewById(R.id.Kar98k);
        Kar98k.setChecked(getConfig((String) Kar98k.getText()));
        Kar98k.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Kar98k.getText()), Kar98k.isChecked());
            }
        });
        final CheckBox VSS = mFloatingView.findViewById(R.id.VSS);
        VSS.setChecked(getConfig((String) VSS.getText()));
        VSS.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(VSS.getText()), VSS.isChecked());
            }
        });
        final CheckBox Win94 = mFloatingView.findViewById(R.id.Win94);
        Win94.setChecked(getConfig((String) Win94.getText()));
        Win94.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Win94.getText()), Win94.isChecked());
            }
        });
        final CheckBox AUG = mFloatingView.findViewById(R.id.AUG);
        AUG.setChecked(getConfig((String) AUG.getText()));
        AUG.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(AUG.getText()), AUG.isChecked());
            }
        });
        final CheckBox M762 = mFloatingView.findViewById(R.id.M762);
        M762.setChecked(getConfig((String) M762.getText()));
        M762.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(M762.getText()), M762.isChecked());
            }
        });
        final CheckBox SCARL = mFloatingView.findViewById(R.id.SCARL);
        SCARL.setChecked(getConfig((String) SCARL.getText()));
        SCARL.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(SCARL.getText()), SCARL.isChecked());
            }
        });
        final CheckBox M416 = mFloatingView.findViewById(R.id.M416);
        M416.setChecked(getConfig((String) M416.getText()));
        M416.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(M416.getText()), M416.isChecked());
            }
        });
        final CheckBox M16A4 = mFloatingView.findViewById(R.id.M16A4);
        M16A4.setChecked(getConfig((String) M16A4.getText()));
        M16A4.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(M16A4.getText()), M16A4.isChecked());
            }
        });
        final CheckBox Mk47Mutant = mFloatingView.findViewById(R.id.Mk47Mutant);
        Mk47Mutant.setChecked(getConfig((String) Mk47Mutant.getText()));
        Mk47Mutant.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Mk47Mutant.getText()), Mk47Mutant.isChecked());
            }
        });
        final CheckBox G36C = mFloatingView.findViewById(R.id.G36C);
        G36C.setChecked(getConfig((String) G36C.getText()));
        G36C.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(G36C.getText()), G36C.isChecked());
            }
        });
        final CheckBox QBZ = mFloatingView.findViewById(R.id.QBZ);
        QBZ.setChecked(getConfig((String) QBZ.getText()));
        QBZ.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(QBZ.getText()), QBZ.isChecked());
            }
        });
        final CheckBox AKM = mFloatingView.findViewById(R.id.AKM);
        AKM.setChecked(getConfig((String) AKM.getText()));
        AKM.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(AKM.getText()), AKM.isChecked());
            }
        });
        final CheckBox Groza = mFloatingView.findViewById(R.id.Groza);
        Groza.setChecked(getConfig((String) Groza.getText()));
        Groza.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Groza.getText()), Groza.isChecked());
            }
        });
        final CheckBox S12K = mFloatingView.findViewById(R.id.S12K);
        S12K.setChecked(getConfig((String) S12K.getText()));
        S12K.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(S12K.getText()), S12K.isChecked());
            }
        });
        final CheckBox DBS = mFloatingView.findViewById(R.id.DBS);
        DBS.setChecked(getConfig((String) DBS.getText()));
        DBS.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(DBS.getText()), DBS.isChecked());
            }
        });
        final CheckBox S686 = mFloatingView.findViewById(R.id.S686);
        S686.setChecked(getConfig((String) S686.getText()));
        S686.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(S686.getText()), S686.isChecked());
            }
        });
        final CheckBox S1897 = mFloatingView.findViewById(R.id.S1897);
        S1897.setChecked(getConfig((String) S1897.getText()));
        S1897.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(S1897.getText()), S1897.isChecked());
            }
        });
        final CheckBox SawedOff = mFloatingView.findViewById(R.id.SawedOff);
        SawedOff.setChecked(getConfig((String) SawedOff.getText()));
        SawedOff.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(SawedOff.getText()), SawedOff.isChecked());
            }
        });
        final CheckBox TommyGun = mFloatingView.findViewById(R.id.TommyGun);
        TommyGun.setChecked(getConfig((String) TommyGun.getText()));
        TommyGun.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(TommyGun.getText()), TommyGun.isChecked());
            }
        });
        final CheckBox MP5K = mFloatingView.findViewById(R.id.MP5K);
        MP5K.setChecked(getConfig((String) MP5K.getText()));
        MP5K.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(MP5K.getText()), MP5K.isChecked());
            }
        });
        final CheckBox Vector = mFloatingView.findViewById(R.id.Vector);
        Vector.setChecked(getConfig((String) Vector.getText()));
        Vector.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Vector.getText()), Vector.isChecked());
            }
        });
        final CheckBox Uzi = mFloatingView.findViewById(R.id.Uzi);
        Uzi.setChecked(getConfig((String) Uzi.getText()));
        Uzi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Uzi.getText()), Uzi.isChecked());
            }
        });
        final CheckBox R1895 = mFloatingView.findViewById(R.id.R1895);
        R1895.setChecked(getConfig((String) R1895.getText()));
        R1895.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(R1895.getText()), R1895.isChecked());
            }
        });
        final CheckBox Vz61 = mFloatingView.findViewById(R.id.Vz61);
        Vz61.setChecked(getConfig((String) Vz61.getText()));
        Vz61.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Vz61.getText()), Vz61.isChecked());
            }
        });
        final CheckBox P92 = mFloatingView.findViewById(R.id.P92);
        P92.setChecked(getConfig((String) P92.getText()));
        P92.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(P92.getText()), P92.isChecked());
            }
        });
        final CheckBox P18C = mFloatingView.findViewById(R.id.P18C);
        P18C.setChecked(getConfig((String) P18C.getText()));
        P18C.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(P18C.getText()), P18C.isChecked());
            }
        });
        final CheckBox R45 = mFloatingView.findViewById(R.id.R45);
        R45.setChecked(getConfig((String) R45.getText()));
        R45.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(R45.getText()), R45.isChecked());
            }
        });
        final CheckBox P1911 = mFloatingView.findViewById(R.id.P1911);
        P1911.setChecked(getConfig((String) P1911.getText()));
        P1911.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(P1911.getText()), P1911.isChecked());
            }
        });
        final CheckBox DesertEagle = mFloatingView.findViewById(R.id.DesertEagle);
        DesertEagle.setChecked(getConfig((String) DesertEagle.getText()));
        DesertEagle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(DesertEagle.getText()), DesertEagle.isChecked());
            }
        });
        final CheckBox Sickle = mFloatingView.findViewById(R.id.Sickle);
        Sickle.setChecked(getConfig((String) Sickle.getText()));
        Sickle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Sickle.getText()), Sickle.isChecked());
            }
        });
        final CheckBox Machete = mFloatingView.findViewById(R.id.Machete);
        Machete.setChecked(getConfig((String) Machete.getText()));
        Machete.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Machete.getText()), Machete.isChecked());
            }
        });
        final CheckBox Pan = mFloatingView.findViewById(R.id.Pan);
        Pan.setChecked(getConfig((String) Pan.getText()));
        Pan.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Pan.getText()), Pan.isChecked());
            }
        });
        final CheckBox Mk14 = mFloatingView.findViewById(R.id.Mk14);
        Mk14.setChecked(getConfig((String) Mk14.getText()));
        Mk14.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Mk14.getText()), Mk14.isChecked());
            }
        });
        final CheckBox sst = mFloatingView.findViewById(R.id.sst);
        sst.setChecked(getConfig((String) sst.getText()));
        sst.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(sst.getText()), sst.isChecked());
            }
        });
        final CheckBox ffACP = mFloatingView.findViewById(R.id.ffACP);
        ffACP.setChecked(getConfig((String) ffACP.getText()));
        ffACP.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(ffACP.getText()), ffACP.isChecked());
            }
        });
        final CheckBox ffs = mFloatingView.findViewById(R.id.ffs);
        ffs.setChecked(getConfig((String) ffs.getText()));
        ffs.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(ffs.getText()), ffs.isChecked());
            }
        });
        final CheckBox nmm = mFloatingView.findViewById(R.id.nmm);
        nmm.setChecked(getConfig((String) nmm.getText()));
        nmm.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(nmm.getText()), nmm.isChecked());
            }
        });
        final CheckBox tzzMagnum = mFloatingView.findViewById(R.id.tzzMagnum);
        tzzMagnum.setChecked(getConfig((String) tzzMagnum.getText()));
        tzzMagnum.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(tzzMagnum.getText()), tzzMagnum.isChecked());
            }
        });
        final CheckBox otGuage = mFloatingView.findViewById(R.id.otGuage);
        otGuage.setChecked(getConfig((String) otGuage.getText()));
        otGuage.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(otGuage.getText()), otGuage.isChecked());
            }
        });
        final CheckBox Choke = mFloatingView.findViewById(R.id.Choke);
        Choke.setChecked(getConfig((String) Choke.getText()));
        Choke.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Choke.getText()), Choke.isChecked());
            }
        });
        final CheckBox SniperCompensator = mFloatingView.findViewById(R.id.SniperCompensator);
        SniperCompensator.setChecked(getConfig((String) SniperCompensator.getText()));
        SniperCompensator.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(SniperCompensator.getText()), SniperCompensator.isChecked());
            }
        });
        final CheckBox DP28 = mFloatingView.findViewById(R.id.DP28);
        DP28.setChecked(getConfig((String) DP28.getText()));
        DP28.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(DP28.getText()), DP28.isChecked());
            }
        });
        final CheckBox M249 = mFloatingView.findViewById(R.id.M249);
        M249.setChecked(getConfig((String) M249.getText()));
        M249.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(M249.getText()), M249.isChecked());
            }
        });
        final CheckBox Grenade = mFloatingView.findViewById(R.id.Grenade);
        Grenade.setChecked(getConfig((String) Grenade.getText()));
        Grenade.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Grenade.getText()), Grenade.isChecked());
            }
        });
        final CheckBox Smoke = mFloatingView.findViewById(R.id.Smoke);
        Smoke.setChecked(getConfig((String) Smoke.getText()));
        Smoke.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Smoke.getText()), Smoke.isChecked());
            }
        });
        final CheckBox Molotov = mFloatingView.findViewById(R.id.Molotov);
        Molotov.setChecked(getConfig((String) Molotov.getText()));
        Molotov.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Molotov.getText()), Molotov.isChecked());
            }
        });
        final CheckBox Painkiller = mFloatingView.findViewById(R.id.Painkiller);
        Painkiller.setChecked(getConfig((String) Painkiller.getText()));
        Painkiller.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Painkiller.getText()), Painkiller.isChecked());
            }
        });
        final CheckBox Adrenaline = mFloatingView.findViewById(R.id.Adrenaline);
        Adrenaline.setChecked(getConfig((String) Adrenaline.getText()));
        Adrenaline.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Adrenaline.getText()), Adrenaline.isChecked());
            }
        });
        final CheckBox EnergyDrink = mFloatingView.findViewById(R.id.EnergyDrink);
        EnergyDrink.setChecked(getConfig((String) EnergyDrink.getText()));
        EnergyDrink.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(EnergyDrink.getText()), EnergyDrink.isChecked());
            }
        });
        final CheckBox FirstAidKit = mFloatingView.findViewById(R.id.FirstAidKit);
        FirstAidKit.setChecked(getConfig((String) FirstAidKit.getText()));
        FirstAidKit.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(FirstAidKit.getText()), FirstAidKit.isChecked());
            }
        });
        final CheckBox Bandage = mFloatingView.findViewById(R.id.Bandage);
        Bandage.setChecked(getConfig((String) Bandage.getText()));
        Bandage.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Bandage.getText()), Bandage.isChecked());
            }
        });
        final CheckBox Medkit = mFloatingView.findViewById(R.id.Medkit);
        Medkit.setChecked(getConfig((String) Medkit.getText()));
        Medkit.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Medkit.getText()), Medkit.isChecked());
            }
        });
        final CheckBox FlareGun = mFloatingView.findViewById(R.id.FlareGun);
        FlareGun.setChecked(getConfig((String) FlareGun.getText()));
        FlareGun.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(FlareGun.getText()), FlareGun.isChecked());
            }
        });
        final CheckBox GhillieSuit = mFloatingView.findViewById(R.id.GhillieSuit);
        GhillieSuit.setChecked(getConfig((String) GhillieSuit.getText()));
        GhillieSuit.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(GhillieSuit.getText()), GhillieSuit.isChecked());
            }
        });
        final CheckBox UMP = mFloatingView.findViewById(R.id.UMP);
        UMP.setChecked(getConfig((String) UMP.getText()));
        UMP.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(UMP.getText()), UMP.isChecked());
            }
        });
        final CheckBox Bizon = mFloatingView.findViewById(R.id.Bizon);
        Bizon.setChecked(getConfig((String) Bizon.getText()));
        Bizon.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Bizon.getText()), Bizon.isChecked());
            }
        });
        final CheckBox CompensatorSMG = mFloatingView.findViewById(R.id.CompensatorSMG);
        CompensatorSMG.setChecked(getConfig((String) CompensatorSMG.getText()));
        CompensatorSMG.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(CompensatorSMG.getText()), CompensatorSMG.isChecked());
            }
        });
        final CheckBox FlashHiderSMG = mFloatingView.findViewById(R.id.FlashHiderSMG);
        FlashHiderSMG.setChecked(getConfig((String) FlashHiderSMG.getText()));
        FlashHiderSMG.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(FlashHiderSMG.getText()), FlashHiderSMG.isChecked());
            }
        });
        final CheckBox FlashHiderAr = mFloatingView.findViewById(R.id.FlashHiderAr);
        FlashHiderAr.setChecked(getConfig((String) FlashHiderAr.getText()));
        FlashHiderAr.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(FlashHiderAr.getText()), FlashHiderAr.isChecked());
            }
        });
        final CheckBox ArCompensator = mFloatingView.findViewById(R.id.ArCompensator);
        ArCompensator.setChecked(getConfig((String) ArCompensator.getText()));
        ArCompensator.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(ArCompensator.getText()), ArCompensator.isChecked());
            }
        });
        final CheckBox TacticalStock = mFloatingView.findViewById(R.id.TacticalStock);
        TacticalStock.setChecked(getConfig((String) TacticalStock.getText()));
        TacticalStock.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(TacticalStock.getText()), TacticalStock.isChecked());
            }
        });
        final CheckBox Duckbill = mFloatingView.findViewById(R.id.Duckbill);
        Duckbill.setChecked(getConfig((String) Duckbill.getText()));
        Duckbill.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Duckbill.getText()), Duckbill.isChecked());
            }
        });
        final CheckBox FlashHiderSniper = mFloatingView.findViewById(R.id.FlashHiderSniper);
        FlashHiderSniper.setChecked(getConfig((String) FlashHiderSniper.getText()));
        FlashHiderSniper.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(FlashHiderSniper.getText()), FlashHiderSniper.isChecked());
            }
        });
        final CheckBox SuppressorSMG = mFloatingView.findViewById(R.id.SuppressorSMG);
        SuppressorSMG.setChecked(getConfig((String) SuppressorSMG.getText()));
        SuppressorSMG.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(SuppressorSMG.getText()), SuppressorSMG.isChecked());
            }
        });
        final CheckBox HalfGrip = mFloatingView.findViewById(R.id.HalfGrip);
        HalfGrip.setChecked(getConfig((String) HalfGrip.getText()));
        HalfGrip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(HalfGrip.getText()), HalfGrip.isChecked());
            }
        });
        final CheckBox StockMicroUZI = mFloatingView.findViewById(R.id.StockMicroUZI);
        StockMicroUZI.setChecked(getConfig((String) StockMicroUZI.getText()));
        StockMicroUZI.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(StockMicroUZI.getText()), StockMicroUZI.isChecked());
            }
        });
        final CheckBox SuppressorSniper = mFloatingView.findViewById(R.id.SuppressorSniper);
        SuppressorSniper.setChecked(getConfig((String) SuppressorSniper.getText()));
        SuppressorSniper.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(SuppressorSniper.getText()), SuppressorSniper.isChecked());
            }
        });
        final CheckBox SuppressorAr = mFloatingView.findViewById(R.id.SuppressorAr);
        SuppressorAr.setChecked(getConfig((String) SuppressorAr.getText()));
        SuppressorAr.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(SuppressorAr.getText()), SuppressorAr.isChecked());
            }
        });
        final CheckBox ExQdSniper = mFloatingView.findViewById(R.id.ExQdSniper);
        ExQdSniper.setChecked(getConfig((String) ExQdSniper.getText()));
        ExQdSniper.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(ExQdSniper.getText()), ExQdSniper.isChecked());
            }
        });
        final CheckBox QdSMG = mFloatingView.findViewById(R.id.QdSMG);
        QdSMG.setChecked(getConfig((String) QdSMG.getText()));
        QdSMG.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(QdSMG.getText()), QdSMG.isChecked());
            }
        });
        final CheckBox ExSMG = mFloatingView.findViewById(R.id.ExSMG);
        ExSMG.setChecked(getConfig((String) ExSMG.getText()));
        ExSMG.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(ExSMG.getText()), ExSMG.isChecked());
            }
        });
        final CheckBox QdSniper = mFloatingView.findViewById(R.id.QdSniper);
        QdSniper.setChecked(getConfig((String) QdSniper.getText()));
        QdSniper.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(QdSniper.getText()), QdSniper.isChecked());
            }
        });
        final CheckBox ExSniper = mFloatingView.findViewById(R.id.ExSniper);
        ExSniper.setChecked(getConfig((String) ExSniper.getText()));
        ExSniper.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(ExSniper.getText()), ExSniper.isChecked());
            }
        });
        final CheckBox ExAr = mFloatingView.findViewById(R.id.ExAr);
        ExAr.setChecked(getConfig((String) ExAr.getText()));
        ExAr.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(ExAr.getText()), ExAr.isChecked());
            }
        });
        final CheckBox ExQdAr = mFloatingView.findViewById(R.id.ExQdAr);
        ExQdAr.setChecked(getConfig((String) ExQdAr.getText()));
        ExQdAr.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(ExQdAr.getText()), ExQdAr.isChecked());
            }
        });
        final CheckBox QdAr = mFloatingView.findViewById(R.id.QdAr);
        QdAr.setChecked(getConfig((String) QdAr.getText()));
        QdAr.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(QdAr.getText()), QdAr.isChecked());
            }
        });
        final CheckBox ExQdSMG = mFloatingView.findViewById(R.id.ExQdSMG);
        ExQdSMG.setChecked(getConfig((String) ExQdSMG.getText()));
        ExQdSMG.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(ExQdSMG.getText()), ExQdSMG.isChecked());
            }
        });
        final CheckBox QuiverCrossBow = mFloatingView.findViewById(R.id.QuiverCrossBow);
        QuiverCrossBow.setChecked(getConfig((String) QuiverCrossBow.getText()));
        QuiverCrossBow.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(QuiverCrossBow.getText()), QuiverCrossBow.isChecked());
            }
        });
        final CheckBox BulletLoop = mFloatingView.findViewById(R.id.BulletLoop);
        BulletLoop.setChecked(getConfig((String) BulletLoop.getText()));
        BulletLoop.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(BulletLoop.getText()), BulletLoop.isChecked());
            }
        });
        final CheckBox ThumbGrip = mFloatingView.findViewById(R.id.ThumbGrip);
        ThumbGrip.setChecked(getConfig((String) ThumbGrip.getText()));
        ThumbGrip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(ThumbGrip.getText()), ThumbGrip.isChecked());
            }
        });
        final CheckBox LaserSight = mFloatingView.findViewById(R.id.LaserSight);
        LaserSight.setChecked(getConfig((String) LaserSight.getText()));
        LaserSight.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(LaserSight.getText()), LaserSight.isChecked());
            }
        });
        final CheckBox AngledGrip = mFloatingView.findViewById(R.id.AngledGrip);
        AngledGrip.setChecked(getConfig((String) AngledGrip.getText()));
        AngledGrip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(AngledGrip.getText()), AngledGrip.isChecked());
            }
        });
        final CheckBox LightGrip = mFloatingView.findViewById(R.id.LightGrip);
        LightGrip.setChecked(getConfig((String) LightGrip.getText()));
        LightGrip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(LightGrip.getText()), LightGrip.isChecked());
            }
        });
        final CheckBox VerticalGrip = mFloatingView.findViewById(R.id.VerticalGrip);
        VerticalGrip.setChecked(getConfig((String) VerticalGrip.getText()));
        VerticalGrip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(VerticalGrip.getText()), VerticalGrip.isChecked());
            }
        });
        final CheckBox GasCan = mFloatingView.findViewById(R.id.GasCan);
        GasCan.setChecked(getConfig((String) GasCan.getText()));
        GasCan.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(GasCan.getText()), GasCan.isChecked());
            }
        });
        final CheckBox Arrow = mFloatingView.findViewById(R.id.Arrow);
        Arrow.setChecked(getConfig((String) Arrow.getText()));
        Arrow.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Arrow.getText()), Arrow.isChecked());
            }
        });
        final CheckBox CrossBow = mFloatingView.findViewById(R.id.CrossBow);
        CrossBow.setChecked(getConfig((String) CrossBow.getText()));
        CrossBow.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(CrossBow.getText()), CrossBow.isChecked());
            }
        });
        final CheckBox Baglvl1 = mFloatingView.findViewById(R.id.Baglvl1);
        Baglvl1.setChecked(getConfig((String) Baglvl1.getText()));
        Baglvl1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Baglvl1.getText()), Baglvl1.isChecked());
            }
        });
        final CheckBox Baglvl2 = mFloatingView.findViewById(R.id.Baglvl2);
        Baglvl2.setChecked(getConfig((String) Baglvl2.getText()));
        Baglvl2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Baglvl2.getText()), Baglvl2.isChecked());
            }
        });
        final CheckBox Baglvl3 = mFloatingView.findViewById(R.id.Baglvl3);
        Baglvl3.setChecked(getConfig((String) Baglvl3.getText()));
        Baglvl3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Baglvl3.getText()), Baglvl3.isChecked());
            }
        });
        final CheckBox Helmetlvl1 = mFloatingView.findViewById(R.id.Helmetlvl1);
        Helmetlvl1.setChecked(getConfig((String) Helmetlvl1.getText()));
        Helmetlvl1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Helmetlvl1.getText()), Helmetlvl1.isChecked());
            }
        });
        final CheckBox Helmetlvl2 = mFloatingView.findViewById(R.id.Helmetlvl2);
        Helmetlvl2.setChecked(getConfig((String) Helmetlvl2.getText()));
        Helmetlvl2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Helmetlvl2.getText()), Helmetlvl2.isChecked());
            }
        });
        final CheckBox Helmetlvl3 = mFloatingView.findViewById(R.id.Helmetlvl3);
        Helmetlvl3.setChecked(getConfig((String) Helmetlvl3.getText()));
        Helmetlvl3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Helmetlvl3.getText()), Helmetlvl3.isChecked());
            }
        });
        final CheckBox Vestlvl1 = mFloatingView.findViewById(R.id.Vestlvl1);
        Vestlvl1.setChecked(getConfig((String) Vestlvl1.getText()));
        Vestlvl1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Vestlvl1.getText()), Vestlvl1.isChecked());
            }
        });
        final CheckBox Vestlvl2 = mFloatingView.findViewById(R.id.Vestlvl2);
        Vestlvl2.setChecked(getConfig((String) Vestlvl2.getText()));
        Vestlvl2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Vestlvl2.getText()), Vestlvl2.isChecked());
            }
        });
        final CheckBox Vestlvl3 = mFloatingView.findViewById(R.id.Vestlvl3);
        Vestlvl3.setChecked(getConfig((String) Vestlvl3.getText()));
        Vestlvl3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Vestlvl3.getText()), Vestlvl3.isChecked());
            }
        });
        final CheckBox Stung = mFloatingView.findViewById(R.id.Stung);
        Stung.setChecked(getConfig((String) Stung.getText()));
        Stung.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Stung.getText()), Stung.isChecked());
            }
        });
        final CheckBox Crowbar = mFloatingView.findViewById(R.id.Crowbar);
        Crowbar.setChecked(getConfig((String) Crowbar.getText()));
        Crowbar.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setValue(String.valueOf(Crowbar.getText()), Crowbar.isChecked());
            }
        });

        final RadioGroup radioFPS = mFloatingView.findViewById(R.id.radioFPS);
        radioFPS.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @SuppressLint("NonConstantResourceId")
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.fps30:
                        ESPView.ChangeFps(15);
                        break;
                    case R.id.fps45:
                        ESPView.ChangeFps(30);
                        break;
                    case R.id.fps60:
                        ESPView.ChangeFps(45);
                        break;
                    case R.id.fps90:
                        ESPView.ChangeFps(60);
                        break;
					case R.id.fps120:
                        ESPView.ChangeFps(90);
                }
            }
        });

        final RadioGroup radiooBox = mFloatingView.findViewById(R.id.radiooBox);
        radiooBox.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                int chkdId = radiooBox.getCheckedRadioButtonId();
                RadioButton btn = mFloatingView.findViewById(chkdId);
                SettingValueI(1, Integer.parseInt(btn.getTag().toString()));
            }
        });

        final RadioGroup radiooLine = mFloatingView.findViewById(R.id.radiooLine);
        radiooLine.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                int chkdId = radiooLine.getCheckedRadioButtonId();
                RadioButton btn = mFloatingView.findViewById(chkdId);
                SettingValueI(2, Integer.parseInt(btn.getTag().toString()));
            }
        });
        
        
        
        final SeekBar wideView = mFloatingView.findViewById(R.id.wideView);
        wideView.setProgress(getEspValue("wideView",0));
        final TextView wide_text = mFloatingView.findViewById(R.id.int_wideView);
        wide_text.setText(String.valueOf(getEspValue("wideView",0)));
        WideView(wideView.getProgress());
        wideView.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    WideView(wideView.getProgress());
                    String a = String.valueOf(wideView.getProgress());
                    wide_text.setText(a);
                    setEspValue("wideView",i);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });
        
        
		
		/*final CheckBox lessRecoil = mFloatingView.findViewById(R.id.lessRecoil);
        lessRecoil.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				SettingMemory(1, isChecked);
			}
		});
		final CheckBox zeroRecoil = mFloatingView.findViewById(R.id.zeroRecoil);
        zeroRecoil.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				SettingMemory(5, isChecked);
			}
		});
		final CheckBox noShake = mFloatingView.findViewById(R.id.noShake);
        noShake.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				SettingMemory(7, isChecked);
			}
		});
		final CheckBox instantHit = mFloatingView.findViewById(R.id.instantHit);
        instantHit.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				SettingMemory(2, isChecked);
			}
		});
		final CheckBox smallCross = mFloatingView.findViewById(R.id.smallCross);
        smallCross.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				SettingMemory(4, isChecked);
			}
		});
		final CheckBox hitEffect = mFloatingView.findViewById(R.id.hitEffect);
        hitEffect.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				SettingMemory(8, isChecked);
			}
		});
		final CheckBox fastParachute = mFloatingView.findViewById(R.id.fastParachute);
        fastParachute.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				SettingMemory(6, isChecked);
			}
		});*/
        
        
        final Switch MagicBullet = mFloatingView.findViewById(R.id.MagicBullet);
        MagicBullet.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked){
                    Toast.makeText(FloatLogo.this,"ACTIVATE MAGICBULLET √",Toast.LENGTH_LONG).show();
                    Execute("/MagicBypass 203");
                    Execute("/MagicBullet 1990");
                    Execute("/MagicBullet.sh");
                   }else{
                    Toast.makeText(FloatLogo.this,"DEACTIVATE MAGICBULLET √",Toast.LENGTH_LONG).show();
                    Execute("/MagicBypass 204");
                    Execute("/MagicBullet 1980");
					Execute("/MagicBullet.sh");
                }
			}
		});
		
        
        
        final SeekBar range = mFloatingView.findViewById(R.id.radiusaim);
        range.setProgress(getEspValue("RangeAim",0));
        final TextView rangetext = mFloatingView.findViewById(R.id.intradius);
        rangetext.setText(String.valueOf(getEspValue("RangeAim",0)));
        Range(range.getProgress());
        range.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    Range(range.getProgress());
                    String a = String.valueOf(range.getProgress());
                    rangetext.setText(a);
                    setEspValue("RangeAim",i);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });
        
        final SeekBar Recoil = mFloatingView.findViewById(R.id.recoil);
        Recoil.setProgress(getEspValue("RecoilAim",0));
        final TextView setrecoil = mFloatingView.findViewById(R.id.intrecoil);
        setrecoil.setText(String.valueOf(getEspValue("RecoilAim",0)));
        recoil(Recoil.getProgress());
        Recoil.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    recoil(Recoil.getProgress());
                    String a = String.valueOf(Recoil.getProgress());
                    setrecoil.setText(a);
                    setEspValue("RecoilAim",i);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });
        
        final SeekBar distances = mFloatingView.findViewById(R.id.distanceaim);
        distances.setProgress(getEspValue("DistAim",0));
        final TextView distancetext = mFloatingView.findViewById(R.id.intdistance);
        distancetext.setText(String.valueOf(getEspValue("DistAim",0)));
        distances(distances.getProgress());
        distances.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    distances(distances.getProgress());
                    String a = String.valueOf(distances.getProgress());
                    distancetext.setText(a);
                    setEspValue("DistAim",i);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });
        
        

		final RadioGroup aimby=mFloatingView.findViewById(R.id.aimby);
        aimby.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup radioGroup, int i) {
				int chkdId = aimby.getCheckedRadioButtonId();
				RadioButton btn = mFloatingView.findViewById(chkdId);
				AimBy(Integer.parseInt(btn.getTag().toString()));
			}
		});
			
        final RadioGroup aimbotmode=mFloatingView.findViewById(R.id.aimbotmode);
        aimbotmode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup radioGroup, int i) {
			int chkdId = aimbotmode.getCheckedRadioButtonId();
				RadioButton btn = mFloatingView.findViewById(chkdId);
				Target(Integer.parseInt(btn.getTag().toString()));
			}
		});
        
        
        final Switch ESP_ENABLE = mFloatingView.findViewById(R.id.ESP_ENABLE);
		ESP_ENABLE.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if (isChecked) {
						DrawESP();
					} else {
						StopESP();
						stopService(new Intent(getApplicationContext(), FloatAim.class));
					}
				}
			});
        
        
        // Antiban Non-Root
        final Switch isLogobypass = mFloatingView.findViewById(R.id.isLogobypass);
		isLogobypass.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if (isChecked) {
                        Toast.makeText(FloatLogo.this,"ACTIVATE LOGO BYPASS √",Toast.LENGTH_LONG).show();
						Execute("/LogoBypass");
						Execute("/LogoBypass1");
						Execute("/LogoBypass2");
						Execute("/LogoBypass3 11207");
						Execute("/Bypass10 1672");
                        Execute("/Bypass12 1672");
					} else {
                        Toast.makeText(FloatLogo.this,"DEACTIVATE LOGO BYPASS √",Toast.LENGTH_LONG).show();
						Execute("/LogoBypass");
						Execute("/LogoBypass1");
						Execute("/LogoBypass2");
						Execute("/LogoBypass3 11207");
						Execute("/Bypass10 1672");
                        Execute("/Bypass12 1672");
					}
				}
			});
        
        final Switch Islandbypass = mFloatingView.findViewById(R.id.Islandbypass);
		Islandbypass.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if (isChecked){
						ExecuteElf("/Spwn_on");
						ExecuteElf("/Island_on.sh");
                        Toast.makeText(FloatLogo.this,"ACTIVATE ISLAND BYPASS √",Toast.LENGTH_LONG).show();
						Execute("/HIGHLIGHT 11111");
					}else{
						ExecuteElf("/Spwn_off");
						ExecuteElf("/Island_off.sh");
                        Toast.makeText(FloatLogo.this,"DEACTIVATE ISLAND BYPASS √",Toast.LENGTH_LONG).show();
						Execute("/HIGHLIGHT 11111");
					}
				}
			});
        
        
		final Switch aimbot = mFloatingView.findViewById(R.id.aimbot);
		aimbot.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if (isChecked) {
						startService(new Intent(getApplicationContext(), FloatAim.class));
					} else {
						SettingValue(11, false);
						stopService(new Intent(getApplicationContext(), FloatAim.class));
					}
				}
			});

        final Switch aimknocked = mFloatingView.findViewById(R.id.aimknocked);
        aimknocked.setChecked(getConfig((String) aimknocked.getText()));
        SettingValue(13, getConfig((String) aimknocked.getText()));
        aimknocked.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				setValue(String.valueOf(aimknocked.getText()), aimknocked.isChecked());
				SettingValue(13, aimknocked.isChecked());
			}
		});
    }
    
    public void Execute(String path) {
        try {
            ExecuteElf("chmod 777 " + getFilesDir() + path);//VIRTUAL
            ExecuteElf(getFilesDir() + path);
            ExecuteElf("su -c chmod 777 " + getFilesDir() + path);//ROOT
            ExecuteElf("su -c " + getFilesDir() + path);
        } catch (Exception e) {
        }
    }

    private void ExecuteElf(String shell) {
        try {
            Runtime.getRuntime().exec(shell, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    public Context context;
	public native void WideView(int view);
	public native void Range(int range);
    public native void Target(int target);
    public native void AimBy(int aimby);
}

class SingleTapConfirm extends GestureDetector.SimpleOnGestureListener {
    @Override
    public boolean onSingleTapUp(MotionEvent event) {
        return true;
    }
}
