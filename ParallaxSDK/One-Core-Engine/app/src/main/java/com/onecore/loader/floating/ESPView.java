package com.onecore.loader.floating;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import static com.onecore.loader.floating.Overlay.getConfig;
import android.os.Build;
import android.view.Surface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Rect;
import java.util.Random;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.onecore.loader.R;
import androidx.core.content.res.ResourcesCompat;

public class ESPView extends View implements Runnable {

    private Paint boxFillPaint,
	DistancePaint,
	mTextPaint,
	mPaintBitmap,
	mPaintBitmap1,
	mNamePaint,
	mTeamPaint,
	weaponPaint,
	linePaint,
	boxPaint,
	mItemsPaint,
	mVehiclesPaint;
    private Bitmap botBitmap, lootBitmap, airdropBitmap, vehicleBitmap, boatBitmap;
    private Thread mThread;
    static long sleepTime;
    private Date time;
    private SimpleDateFormat formatter;
    private int mFPS = 0;
    private int itemCount = 2;
    private int mFPSCounter = 0;
    private long mFPSTime = 0;
    private boolean isAr;

    private float mScaleX = 1;
    private float mScaleY = 1;
    
    
    private String[] TeamColors = {
		"#00ffff",
		"#ffa3ff",
		"#b3b9ff",
		"#ffc96b",
		"#a4ff73"
    };
    
    private String[] IMGUI_COLORS = {
		"#00ffff"
    };

    private boolean mHardwareAccelerated = false;

    public static void ChangeFps(int fps) {
        sleepTime = 1000 / (15 + fps);
    }

	public void ResetItemCount() {
		itemCount = 2;
	}

    public ESPView(Context context) {
        super(context, null, 0);
        InitializePaints();
        setFocusableInTouchMode(false);
        setBackgroundColor(Color.TRANSPARENT);
        time = new Date();
        formatter = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        sleepTime = 1000 / 60;
        mThread = new Thread(this);
        mThread.start();

        // Enable hardware acceleration if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            mHardwareAccelerated = true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
		int rotation = getDisplay().getRotation();
        if (canvas == null || rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            return;
        }
        if (!mHardwareAccelerated) {
            // Clear the canvas only if hardware acceleration is not enabled
            ClearCanvas(canvas);
        }

        time.setTime(System.currentTimeMillis());    
        Overlay.DrawOn(this, canvas);
    }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND);
        while (mThread.isAlive() && !mThread.isInterrupted()) {
            try {
                long t1 = System.currentTimeMillis();
                postInvalidate();
                long td = System.currentTimeMillis() - t1;
                Thread.sleep(Math.max(Math.min(0, sleepTime - td), sleepTime));
            } catch (InterruptedException it) {
                Log.e("OverlayThread", it.getMessage());
            }
        }
    }

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		mScaleX = getWidth() / (float) 2340;
        mScaleY = getHeight() / (float) 1080;
		botBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.bot), (int) (42 * mScaleY), (int) (30 * mScaleY), false);
        lootBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.lootx), (int) (40 * mScaleY), (int) (40 * mScaleY), false);
        airdropBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.airdrop), (int) (40 * mScaleY), (int) (40 * mScaleY), false);
        vehicleBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.vehicle), (int) (42 * mScaleY) , (int) (42 * mScaleY), false);
        boatBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.boat), (int) (42 * mScaleY), (int) (42 * mScaleY), false);
	}

    public void InitializePaints() {
        // =======================================================
        // Credit Text
        // =======================================================
        botBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.bot), (int) (42 * mScaleY), (int) (30 * mScaleY), false);
        lootBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.lootx), (int) (40 * mScaleY), (int) (40 * mScaleY), false);
        airdropBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.airdrop), (int) (40 * mScaleY), (int) (40 * mScaleY), false);
        vehicleBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.vehicle), (int) (42 * mScaleY) , (int) (42 * mScaleY), false);
        boatBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.boat), (int) (42 * mScaleY), (int) (42 * mScaleY), false);
		
        mPaintBitmap = new Paint();
        mPaintBitmap.setAlpha(180);

        mPaintBitmap1 = new Paint();
        mPaintBitmap1.setAlpha(120);

        linePaint = new Paint();
        linePaint.setAntiAlias(true);
		linePaint.setAlpha(200);
        linePaint.setStyle(Paint.Style.STROKE);

        // =======================================================
        // Enemy Box
        // =======================================================
        boxPaint = new Paint();
        boxPaint.setAntiAlias(true);
        boxPaint.setStyle(Paint.Style.STROKE);

        boxFillPaint = new Paint();
        boxFillPaint.setAntiAlias(true);
        boxFillPaint.setStyle(Paint.Style.FILL);

        // =======================================================
        mVehiclesPaint = new Paint();
        mVehiclesPaint.setAntiAlias(true);
        mVehiclesPaint.setTextAlign(Paint.Align.CENTER);
		int shadowColor5 = Color.argb(200, 0, 0, 0);
        mVehiclesPaint.setShadowLayer(7, 0, 0, shadowColor5);
        mVehiclesPaint.setColor(Color.parseColor(IMGUI_COLORS[new Random().nextInt(1)]));
        Typeface appTypeface = ResourcesCompat.getFont(getContext(), R.font.acme);
        mVehiclesPaint.setTypeface(appTypeface);
        
        // =======================================================
        mItemsPaint = new Paint();
        mItemsPaint.setAntiAlias(true);
        int shadowColor4 = Color.argb(200, 0, 0, 0);
        mItemsPaint.setShadowLayer(7, 0, 0, shadowColor4);
        mItemsPaint.setColor(Color.parseColor(IMGUI_COLORS[new Random().nextInt(1)]));
        mItemsPaint.setTextAlign(Paint.Align.CENTER);
        mItemsPaint.setTypeface(appTypeface);
        
        // =======================================================
        mTextPaint = new Paint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setColor(Color.rgb(0, 0, 0));
        mTextPaint.setTypeface(appTypeface);
        mTextPaint.setDither(true);
        
        // =======================================================
        mNamePaint = new Paint();
        mNamePaint.setStyle(Paint.Style.FILL_AND_STROKE);
	    mNamePaint.setAntiAlias(true);
        mNamePaint.setColor(Color.argb(255, 9, 255, 255));
        mNamePaint.setTypeface(appTypeface);
        mNamePaint.setTextAlign(Paint.Align.CENTER);
        mNamePaint.setTextSize(mScaleY * 18);
        
		mTeamPaint = new Paint();
        mTeamPaint.setAntiAlias(true);
		int shadowColor6 = Color.argb(200, 0, 0, 0);
        mTeamPaint.setShadowLayer(7, 0, 0, shadowColor6);
        mTeamPaint.setTextAlign(Paint.Align.CENTER);
		mTeamPaint.setTextSize(mScaleY * 18);
		mTeamPaint.setAlpha(200);
        mTeamPaint.setTypeface(Typeface.create(appTypeface, Typeface.BOLD));

        // =======================================================
        DistancePaint = new Paint();
        DistancePaint.setAntiAlias(true);
		int shadowColor = Color.argb(200, 0, 0, 0); // 128 is the alpha value (0-255)
        DistancePaint.setShadowLayer(7, 0, 0, shadowColor);
        DistancePaint.setTextAlign(Paint.Align.CENTER);
        DistancePaint.setColor(Color.rgb(255, 175, 20));
		DistancePaint.setTextSize(mScaleY * 18);
		DistancePaint.setAlpha(208);
        DistancePaint.setTypeface(Typeface.create(appTypeface, Typeface.BOLD));

        // =======================================================

        weaponPaint = new Paint();
        weaponPaint.setAntiAlias(true);
        weaponPaint.setTextAlign(Paint.Align.CENTER);
		int shadowColor1 = Color.argb(200, 0, 0, 0);
        weaponPaint.setShadowLayer(7, 0, 0, shadowColor1);
        weaponPaint.setColor(Color.argb(255, 255, 255, 255));
		weaponPaint.setAlpha(208);
		weaponPaint.setTextSize(mScaleY * 18);
        weaponPaint.setTypeface(Typeface.create(appTypeface, Typeface.BOLD));
    }
    
    public void ClearCanvas(Canvas cvs) {
        cvs.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    }

    public void DrawLine(Canvas cvs, int a, int r, int g, int b, float lineWidth, float fromX, float fromY, float toX, float toY) {
        linePaint.setStrokeWidth(lineWidth);
        linePaint.setARGB(a, r, g, b);
        cvs.drawLine(fromX, fromY, toX, toY, linePaint);
    }

    public void DrawRect(Canvas cvs, int a, int r, int g, int b, float stroke, float x, float y, float width, float height) {
        boxPaint.setStrokeWidth(stroke);
        boxPaint.setARGB(a, r, g, b);
        cvs.drawRect(x, y, width, height, boxPaint);
    }

    public void DrawFilledRect(Canvas cvs, int a, int r, int g, int b, float x, float y, float width, float height) {
        boxFillPaint.setARGB(a, r, g, b);
        cvs.drawRect(x, y, width, height, boxFillPaint);
    }

    public void DebugText(String s) {
        System.out.println(s);
    }

    public void DrawText(Canvas cvs, int a, int r, int g, int b, String txt, float posX, float posY, float size) {
        mTextPaint.setARGB(a, r, g, b);
		mTextPaint.setShadowLayer(0, 0, 0, Color.TRANSPARENT);

        if (getRight() > 1920 || getBottom() > 1920)
            mTextPaint.setTextSize(4 + size);
        else if (getRight() == 1920 || getBottom() == 1920)
            mTextPaint.setTextSize(2 + size);
        else
            mTextPaint.setTextSize(size);

        cvs.drawText(txt, posX, posY, mTextPaint);
    }

    public void DrawText1(Canvas cvs, int a, int r, int g, int b, String txt, float posX, float posY, float size) {
        mTextPaint.setARGB(a, r, g, b);
        mTextPaint.setShadowLayer(7, 0, 0, Color.BLACK);
        if (getRight() > 1920 || getBottom() > 1920)
            mTextPaint.setTextSize(4 + size);
        else if (getRight() == 1920 || getBottom() == 1920)
            mTextPaint.setTextSize(2 + size);
        else
            mTextPaint.setTextSize(size);

        cvs.drawText(txt, posX, posY, mTextPaint);
    }

    public void DrawWeapon(Canvas cvs, int a, int r, int g, int b, int id, int ammo, int maxammo, float posX, float posY, float size) {
        String wname = getWeapon(id);
        if (wname != null) {
            cvs.drawText(wname, posX, posY, weaponPaint);
        }
    }

    public void DrawTextName(Canvas cvs, int a, int r, int g, int b, float posX, float posY, float size, boolean isInGame) {
        mTextPaint.setARGB(a, r, g, b);
        mTextPaint.setTextSize(size);
		mTextPaint.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
        if (SystemClock.uptimeMillis() - mFPSTime > 1000) {
            mFPSTime = SystemClock.uptimeMillis();
            mFPS = mFPSCounter;
            mFPSCounter = 0;
        } else {
            mFPSCounter++;
        }
        cvs.drawText((isInGame ? formatter.format(time) : "...") + "    FPS: " + mFPS, posX, posY, mTextPaint);
    }

    public void DrawDistance(Canvas cvs, float distance, float posX, float posY, float size) {
        cvs.drawText(String.valueOf((int) distance + "m"), posX, posY, DistancePaint);
    }
    
    public void DrawName(Canvas cvs, String nametxt, int id, float posX, float posY) {
        String[] namesp = nametxt.split(":");
        char[] nameint = new char[namesp.length];
        for (int i = 0; i < namesp.length; i++) {
            nameint[i] = (char) Integer.parseInt(namesp[i]);
        }
        String realname = new String(nameint);
        String teamid = String.valueOf(id);
        if (getConfig("Team ID") && getConfig("Name")) {
            Rect textBounds = new Rect();
            mNamePaint.getTextBounds(realname, 0, realname.length(), textBounds);
            float nameTextWidth = textBounds.width() / 2;
            mNamePaint.getTextBounds(teamid, 0, teamid.length(), textBounds);
            float teamidTextWidth = textBounds.width() / 2;
            if (realname.equals("[ BOTS ]")) {
                teamidTextWidth = botBitmap.getWidth() / 2;
                cvs.drawBitmap(botBitmap, posX - nameTextWidth - (mScaleY * 28), (posY - (mScaleY * 32)) - (28 * mScaleY), mPaintBitmap);
            } else {
                mTeamPaint.setColor(Color.parseColor(TeamColors[new Random(id).nextInt(5)]));
                cvs.drawText(teamid, posX - nameTextWidth - 4, posY - (32 * mScaleY), mTeamPaint);
            }
            cvs.drawText(realname, posX + teamidTextWidth + 4, posY - (32 * mScaleY), mNamePaint);
        } else if (getConfig("Team ID")) {
            if (realname.equals("[ BOTS ]")) {
                cvs.drawBitmap(botBitmap, posX - 24, (posY - (mScaleY * 32)) - (28 * mScaleY), mPaintBitmap);
            } else {
                mTeamPaint.setColor(Color.parseColor(TeamColors[new Random(id).nextInt(5)]));
                cvs.drawText(teamid, posX, posY - (32 * mScaleY), mTeamPaint);
            }
        } else if (getConfig("Name")) {
            cvs.drawText(realname, posX, posY - (32 * mScaleY), mNamePaint);
        }
    }

    public void DrawEnemyCount(Canvas cvs, int a, int r, int g, int b, int x, int y, int width, int height) {
        int colors[] = { Color.TRANSPARENT, Color.rgb(r, g, b), Color.TRANSPARENT };
        GradientDrawable mDrawable = new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, colors);
        mDrawable.setShape(GradientDrawable.RECTANGLE);
        mDrawable.setGradientRadius(2.0f * 60);
        Rect mRect = new Rect(x, y, width, height);
        mDrawable.setBounds(mRect);
        cvs.save();
        mDrawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        mDrawable.draw(cvs);
        cvs.restore();
    }

    public void DrawItems(Canvas cvs, String itemName, float distance, float posX, float posY) {
        isAr = false;
        String realItemName = getItemName(itemName);
        if (realItemName != null && !realItemName.equals("")) {
            mItemsPaint.setTextSize(mScaleY * 25);
            if (realItemName.equals("Loot")) {
                if (distance < 150) {
                    cvs.drawBitmap(lootBitmap, posX - 25, posY - (54 * mScaleY), mPaintBitmap1);
                    cvs.drawText(realItemName + " (" + (int) distance + ")", posX, posY - 8, mItemsPaint);
                }
            }
            else if (realItemName.equals("DropPlane")) {
                cvs.drawText(realItemName + " (" + (int) distance + ")", posX, posY - 8, mItemsPaint);
            }
            else if (realItemName.equals("AirDrop")) {
                cvs.drawBitmap(airdropBitmap, posX - 25, posY - (54 * mScaleY), mPaintBitmap1);
                cvs.drawText(realItemName + " (" + (int) distance + ")", posX, posY - 8, mItemsPaint);
            }
            else {
                mItemsPaint.setShadowLayer(3, 0, 0, Color.TRANSPARENT);
                cvs.drawCircle(posX, posY, 7, mItemsPaint);
                mItemsPaint.setColor(Color.argb(255, 0, 248, 255));
                cvs.drawText(realItemName + " (" + (int) distance + ")", posX, posY - 8, mItemsPaint);
            }
        }
    }

    public void DrawListItem(Canvas cvs, int a, int r, int g, int b, int itemID, int count, float posX, float posY) {
        String realItemName = getItemName(itemID);
        mTextPaint.setARGB(a, r, g, b);
        mTextPaint.setTextSize(24);
        if (realItemName != null && !realItemName.equals("")) {
            itemCount = itemCount + 1;
            if (count == 1) {
                cvs.drawText(realItemName, posX, posY - itemCount * 24, mTextPaint);
            } else {
                cvs.drawText(realItemName + " • " + count, posX, posY - itemCount * 24, mTextPaint);
            }
        }
    }

    public void DrawVehicles(Canvas cvs, String itemName, float distance, float health, float fuel, float posX, float posY) {
        String realVehicleName = getVehicleName(itemName);
        mVehiclesPaint.setTextSize(mScaleY * 26);
        if (realVehicleName != null && !realVehicleName.equals("")) {
            if (realVehicleName.equals("Boat")) {
                cvs.drawBitmap(boatBitmap, posX - 25, posY - (56 * mScaleY), mPaintBitmap1);
                cvs.drawText(realVehicleName + " (" + (int) distance + ")", posX, posY - 8, mVehiclesPaint);
            }
            else if (realVehicleName.equals("AquaRail")) {
                cvs.drawBitmap(boatBitmap, posX - 25, posY - (56 * mScaleY), mPaintBitmap1);
                cvs.drawText(realVehicleName + " (" + (int) distance + ")", posX, posY - 8, mVehiclesPaint);
            }
            else {
                cvs.drawBitmap(vehicleBitmap, posX - 25, posY - (56 * mScaleY), mPaintBitmap1);
                cvs.drawText(realVehicleName + " (" + (int) distance + ")", posX, posY - 8, mVehiclesPaint);
            }
        }
    }

    public void DrawCircle(Canvas cvs, int a, int r, int g, int b, float posX, float posY, float radius, float strokeZ) {
        linePaint.setStrokeWidth(strokeZ);
        linePaint.setColor(Color.rgb(r, g, b));
        linePaint.setAlpha(a);
        cvs.drawCircle(posX, posY, radius, linePaint);
    }

    public void DrawFilledCircle(Canvas cvs, int a, int r, int g, int b, float posX, float posY, float radius) {
        boxFillPaint.setColor(Color.rgb(r, g, b));
        boxFillPaint.setAlpha(a);
        cvs.drawCircle(posX, posY, radius, boxFillPaint);
    }

    private String getItemName(String s) {
        // Scopes
        if (s.contains("MZJ_8X") && getConfig("8x")) {
            return "8x";
        }
        if (s.contains("MZJ_2X") && getConfig("2x")) {
            return "2x";
        }
        if (s.contains("MZJ_HD") && getConfig("Red Dot")) {
            return "Red Dot";
        }
        if (s.contains("MZJ_3X") && getConfig("3x")) {
            return "3X";
        }
        if (s.contains("MZJ_QX") && getConfig("Hollow")) {
            return "Hollow Sight";
        }
        if (s.contains("MZJ_6X") && getConfig("6x")) {
            return "6x";
        }
        if (s.contains("MZJ_4X") && getConfig("4x")) {
            return "4x";
        }
        if (s.contains("MZJ_SideRMR") && getConfig("Canted")) {
            return "Canted Sight";
        }

        // AR and SMG
        if (s.contains("AUG") && getConfig("AUG")) {
            return "AUG";
        }
        if (s.contains("M762") && getConfig("M762")) {
            return "M762";
        }
        if (s.contains("SCAR") && getConfig("SCAR-L")) {
            return "SCARL";
        }
        if (s.contains("M416") && getConfig("M416")) {
            return "M416";
        }
        if (s.contains("M16A4") && getConfig("M16A4")) {
            return "M16A4";
        }
        if (s.contains("Mk47") && getConfig("Mk47 Mutant")) {
            return "Mk47 Mutant";
        }
        if (s.contains("G36") && getConfig("G36C")) {
            return "G36C";
        }
        if (s.contains("QBZ") && getConfig("QBZ")) {
            return "QBZ";
        }
        if (s.contains("AKM") && getConfig("AKM")) {
            return "AKM";
        }
        if (s.contains("Groza") && getConfig("Groza")) {
            return "Groza";
        }
        if (s.contains("PP19") && getConfig("Bizon")) {
            return "Bizon";
        }
        if (s.contains("TommyGun") && getConfig("TommyGun")) {
            return "TommyGun";
        }
        if (s.contains("MP5K") && getConfig("MP5K")) {
            return "MP5K";
        }
        if (s.contains("UMP9") && getConfig("UMP")) {
            return "UMP";
        }
        if (s.contains("Vector") && getConfig("Vector")) {
            return "Vector";
        }
        if (s.contains("MachineGun_Uzi") && getConfig("Uzi")) {
            return "Uzi";
        }
        if (s.contains("DP28") && getConfig("DP28")) {
            return "DP28";
        }
        if (s.contains("M249") && getConfig("M249")) {
            return "M249";
        }

        // Snipers
        if (s.contains("AWM") && getConfig("AWM")) {
            return "AWM";
        }
        if (s.contains("QBU") && getConfig("QBU")) {
            return "QBU";
        }
        if (s.contains("SLR") && getConfig("SLR")) {
            return "SLR";
        }
        if (s.contains("SKS") && getConfig("SKS")) {
            return "SKS";
        }
        if (s.contains("Mini14") && getConfig("Mini14")) {
            return "Mini14";
        }
        if (s.contains("Sniper_M24") && getConfig("M24")) {
            return "M24";
        }
        if (s.contains("Kar98k") && getConfig("Kar98k")) {
            return "Kar98k";
        }
        if (s.contains("VSS") && getConfig("VSS")) {
            return "VSS";
        }
        if (s.contains("Win94") && getConfig("Win94")) {
            return "Win94";
        }
        if (s.contains("Mk14") && getConfig("Mk14")) {
            return "Mk14";
        }

        // Shotguns and Hand weapons
        if (s.contains("S12K") && getConfig("S12K")) {
            return "S12K";
        }
        if (s.contains("ShotGun_DP12") && getConfig("DBS")) {
            return "DBS";
        }
        if (s.contains("S686") && getConfig("S686")) {
            return "S686";
        }
        if (s.contains("S1897") && getConfig("S1897")) {
            return "S1897";
        }
        if (s.contains("Sickle") && getConfig("Sickle")) {
            return "Sickle";
        }
        if (s.contains("Machete") && getConfig("Machete")) {
            return "Machete";
        }
        if (s.contains("Cowbar") && getConfig("Crowbar")) {
            return "Crowbar";
        }
        if (s.contains("CrossBow") && getConfig("CrossBow")) {
            return "CrossBow";
        }
        if (s.contains("Pan") && getConfig("Pan")) {
            return "Pan";
        }

        // Pistols
        if (s.contains("SawedOff") && getConfig("SawedOff")) {
            return "SawedOff";
        }
        if (s.contains("R1895") && getConfig("R1895")) {
            return "R1895";
        }
        if (s.contains("Vz61") && getConfig("Vz61")) {
            return "Vz61";
        }
        if (s.contains("P92") && getConfig("P92")) {
            return "P92";
        }
        if (s.contains("P18C") && getConfig("P18C")) {
            return "P18C";
        }
        if (s.contains("R45") && getConfig("R45")) {
            return "R45";
        }
        if (s.contains("P1911") && getConfig("P1911")) {
            return "P1911";
        }
        if (s.contains("DesertEagle") && getConfig("Desert Eagle")) {
            return "DesertEagle";
        }

        // Ammo
        if (s.contains("Ammo_762mm") && getConfig("7.62")) {
            return "7.62";
        }
        if (s.contains("Ammo_45AC") && getConfig("45ACP")) {
            return "45ACP";
        }
        if (s.contains("Ammo_556mm") && getConfig("5.56")) {
            return "5.56";
        }
        if (s.contains("Ammo_9mm") && getConfig("9mm")) {
            return "9mm";
        }
        if (s.contains("Ammo_300Magnum") && getConfig("300Magnum")) {
            return "300Magnum";
        }
        if (s.contains("Ammo_12Guage") && getConfig("12 Guage")) {
            return "12 Guage";
        }
        if (s.contains("Ammo_Bolt") && getConfig("Arrow")) {
            return "Arrow";
        }

        // Bag, Helmet, Vest
        if (s.contains("Bag_Lv3") && getConfig("Bag L 3")) {
            return "Bag lvl 3";
        }
        if (s.contains("Bag_Lv1") && getConfig("Bag L 1")) {
            return "Bag lvl 1";
        }
        if (s.contains("Bag_Lv2") && getConfig("Bag L 2")) {
            return "Bag lvl 2";
        }
        if (s.contains("Armor_Lv2") && getConfig("Vest L 2")) {
            return "Vest lvl 2";
        }
        if (s.contains("Armor_Lv1") && getConfig("Vest L 1")) {
            return "Vest lvl 1";
        }
        if (s.contains("Armor_Lv3") && getConfig("Vest L 3")) {
            return "Vest lvl 3";
        }
        if (s.contains("Helmet_Lv2") && getConfig("Helmet 2")) {
            return "Helmet lvl 2";
        }
        if (s.contains("Helmet_Lv1") && getConfig("Helmet 1")) {
            return "Helmet lvl 1";
        }
        if (s.contains("Helmet_Lv3") && getConfig("Helmet 3")) {
            return "Helmet lvl 3";
        }

        // Health kits
        if (s.contains("Pills") && getConfig("PainKiller")) {
            return "Painkiller";
        }
        if (s.contains("Injection") && getConfig("Adrenaline")) {
            return "Adrenaline";
        }
        if (s.contains("Drink") && getConfig("Energy Drink")) {
            return "Energy Drink";
        }
        if (s.contains("Firstaid") && getConfig("FirstAidKit")) {
            return "FirstAidKit";
        }
        if (s.contains("Bandage") && getConfig("Bandage")) {
            return "Bandage";
        }
        if (s.contains("FirstAidbox") && getConfig("Medkit")) {
            return "Medkit";
        }

        // Throwables
        if (s.contains("Grenade_Stun") && getConfig("Stung")) {
            return "Stung";
        }
        if (s.contains("Grenade_Shoulei") && getConfig("Grenade")) {
            return "Grenade";
        }
        if (s.contains("Grenade_Smoke") && getConfig("Smoke")) {
            return "Smoke";
        }
        if (s.contains("Grenade_Burn") && getConfig("Molotov")) {
            return "Molotov";
        }

        // Others
        if (s.contains("Large_FlashHider") && getConfig("Flash Hider Ar")) {
            return "Flash Hider Ar";
        }
        if (s.contains("QK_Large_C") && getConfig("Ar Compensator")) {
            return "Ar Compensator";
        }
        if (s.contains("Mid_FlashHider") && getConfig("Flash Hider SMG")) {
            return "Flash Hider SMG";
        }
        if (s.contains("QT_A_") && getConfig("Tactical Stock")) {
            return "Tactical Stock";
        }
        if (s.contains("DuckBill") && getConfig("Duckbill")) {
            return "DuckBill";
        }
        if (s.contains("Sniper_FlashHider") && getConfig("Flash Hider Snp")) {
            return "Flash Hider Sniper";
        }
        if (s.contains("Mid_Suppressor") && getConfig("Suppressor SMG")) {
            return "Suppressor SMG";
        }
        if (s.contains("HalfGrip") && getConfig("Half Grip")) {
            return "Half Grip";
        }
        if (s.contains("Choke") && getConfig("Choke")) {
            return "Choke";
        }
        if (s.contains("QT_UZI") && getConfig("Stock Micro UZI")) {
            return "Stock Micro UZI";
        }
        if (s.contains("QK_Sniper_C") && getConfig("SniperCompensator")) {
            return "Sniper Compensator";
        }
        if (s.contains("Sniper_Suppressor") && getConfig("Sup Sniper")) {
            return "Suppressor Sniper";
        }
        if (s.contains("Large_Suppressor") && getConfig("Suppressor Ar")) {
            return "Suppressor Ar";
        }
        if (s.contains("Sniper_EQ_") && getConfig("Ex.Qd.Sniper")) {
            return "Ex.Qd.Sniper";
        }
        if (s.contains("Mid_Q_") && getConfig("Qd.SMG")) {
            return "Qd.SMG";
        }
        if (s.contains("Mid_E_") && getConfig("Ex.SMG")) {
            return "Ex.SMG";
        }
        if (s.contains("Sniper_Q_") && getConfig("Qd.Sniper")) {
            return "Qd.Sniper";
        }
        if (s.contains("Sniper_E_") && getConfig("Ex.Sniper")) {
            return "Ex.Sniper";
        }
        if (s.contains("Large_E_") && getConfig("Ex.Ar")) {
            return "Ex.Ar";
        }
        if (s.contains("Large_EQ_") && getConfig("Ex.Qd.Ar")) {
            return "Ex.Qd.Ar";
        }
        if (s.contains("Large_Q_") && getConfig("Qd.Ar")) {
            return "Qd.Ar";
        }
        if (s.contains("Mid_EQ_") && getConfig("Ex.Qd.SMG")) {
            return "Ex.Qd.SMG";
        }
        if (s.contains("Crossbow_Q") && getConfig("Quiver CrossBow")) {
            return "Quiver CrossBow";
        }
        if (s.contains("ZDD_Sniper") && getConfig("Bullet Loop")) {
            return "Bullet Loop";
        }
        if (s.contains("ThumbGrip") && getConfig("Thumb Grip")) {
            return "Thumb Grip";
        }
        if (s.contains("Lasersight") && getConfig("Laser Sight")) {
            return "Laser Sight";
        }
        if (s.contains("Angled") && getConfig("Angled Grip")) {
            return "Angled Grip";
        }
        if (s.contains("LightGrip") && getConfig("Light Grip")) {
            return "Light Grip";
        }
        if (s.contains("Vertical") && getConfig("Vertical Grip")) {
            return "Vertical Grip";
        }
        if (s.contains("GasCan") && getConfig("Gas Can")) {
            return "Gas Can";
        }
        if (s.contains("Mid_Compensator") && getConfig("Compensator SMG")) {
            return "Compensator SMG";
        }

        // Special
        if (s.contains("Flare") && getConfig("Flare Gun")) {
            return "Flare Gun";
        }
        if (s.contains("Ghillie") && getConfig("Ghillie Suit")) {
            return "Ghillie Suit";
        }
        if (s.contains("QT_Sniper") && getConfig("CheekPad")) {
            return "CheekPad";
        }
        if (s.contains("PickUpListWrapperActor") && getConfig("Loot")) {
            return "Loot";
        }
        if ((s.contains("AirDropPlane")) && getConfig("DropPlane")) {
            return "DropPlane";
        }

        if ((s.contains("BP_AirDropBox_")) && getConfig("AirDrop")) {
            return "AirDrop";
        }

        // return s;
        return null;
    }

    private String getWeapon(int id) {
        // AR and SMG
        if (id == 101006)
            return "AUG";
        if (id == 101008)
            return "M762";
        if (id == 101003)
            return "SCAR";
        if (id == 101004)
            return "M416";
        if (id == 101002)
            return "M16A4";
        if (id == 101009)
            return "Mk47";
        if (id == 101010)
            return "G36C";
        if (id == 101007)
            return "QBZ";
        if (id == 101001)
            return "AKM";
        if (id == 101005)
            return "Groza";
        if (id == 102005)
            return "Bizon";
        if (id == 102004)
            return "TommyGun";
        if (id == 102007)
            return "MP5K";
        if (id == 102002)
            return "UMP9";
        if (id == 102003)
            return "Vector";
        if (id == 102001)
            return "Uzi";
        if (id == 105002)
            return "DP28";
        if (id == 105001)
            return "M249";

        // Snipers
        if (id == 103003)
            return "AWM";
        if (id == 103010)
            return "QBU";
        if (id == 103009)
            return "SLR";
        if (id == 103004)
            return "SKS";
        if (id == 103006)
            return "Mini14";
        if (id == 103002)
            return "M24";
        if (id == 103001)
            return "Kar98";
        if (id == 103005)
            return "VSS";
        if (id == 103008)
            return "Win94";
        if (id == 103007)
            return "Mk14";

        // Shotguns and Hand weapons
        if (id == 104003)
            return "S12K";
        if (id == 104004)
            return "DBS";
        if (id == 104001)
            return "S686";
        if (id == 104002)
            return "S1897";
        if (id == 108003)
            return "Sickle";
        if (id == 108001)
            return "Machete";
        if (id == 108002)
            return "Crowbar";
        if (id == 107001)
            return "CrossBow";
        if (id == 108004)
            return "Pan";

        // Pistols
        if (id == 106006)
            return "SawedOff";
        if (id == 106003)
            return "R1895";
        if (id == 106008)
            return "Vz61";
        if (id == 106001)
            return "P92";
        if (id == 106004)
            return "P18C";
        if (id == 106005)
            return "R45";
        if (id == 106002)
            return "P1911";
        if (id == 106010)
            return "DesertEagle";

        return null;
    }

    private String getVehicleName(String s) {
        if (s.contains("Buggy") && getConfig("Buggy"))
            return "Buggy";
        if (s.contains("UAZ") && getConfig("UAZ"))
            return "UAZ";
		if (s.contains("UTV") && getConfig("UTV"))
            return "UTV";
        if (s.contains("Motorcycle") && getConfig("Bike"))
            return "MotorBike";
        if (s.contains("Dacia") && getConfig("Dacia"))
            return "Dacia";
        if (s.contains("CoupeRB") && getConfig("CoupeRB"))
            return "CoupeRB";
        if (s.contains("ATV") && getConfig("ATV"))
            return "ATV";
        if (s.contains("AquaRail") && getConfig("AquaRail"))
            return "AquaRail";
        if (s.contains("PG117") && getConfig("Boat"))
            return "Boat";
        if (s.contains("MiniBus") && getConfig("Bus"))
            return "Bus";
        if (s.contains("Mirado") && getConfig("Mirado"))
            return "Mirado";
        if (s.contains("Scooter") && getConfig("Scooter"))
            return "Scooter";
        if (s.contains("Rony") && getConfig("Rony"))
            return "Rony";
        if (s.contains("Snowbike") && getConfig("Snowbike"))
            return "Snowbike";
        if (s.contains("Snowmobile") && getConfig("Snowmobile"))
            return "Snowmobile";
        if (s.contains("Tuk") && getConfig("Tuk"))
            return "Tuk";
        if (s.contains("PickUp") && getConfig("PickUp"))
            return "PickUp";
        if (s.contains("BRDM") && getConfig("BRDM"))
            return "BRDM";
        if (s.contains("LadaNiva") && getConfig("LadaNiva"))
            return "LadaNiva";
        if (s.contains("Bigfoot") && getConfig("Bigfoot"))
            return "Bigfoot";

        return null;
    }

    public String getItemName(int id) {
        // Scopes
        if (id == 203005 && getConfig("8x")) {
            return "8x";
        }

        if (id == 203003 && getConfig("2x")) {
            return "2x";
        }

        if (id == 203001 && getConfig("Red Dot")) {
            return "Red Dot";
        }

        if (id == 203014 && getConfig("3x")) {
            return "3X";
        }

        if (id == 203002 && getConfig("Hollow")) {
            return "Hollow Sight";
        }

        if (id == 203015 && getConfig("6x")) {
            return "6x";
        }

        if (id == 203004 && getConfig("4x")) {
            return "4x";
        }

        if (id == 203018 && getConfig("Canted")) {
            return "Canted Sight";
        }

        // AR and smg
        if (id == 101006 && getConfig("AUG")) {
            return "AUG";
        }

        if (id == 101008 && getConfig("M762")) {
            return "M762";
        }

        if (id == 101003 && getConfig("SCAR-L")) {
            return "SCARL";
        }

        if (id == 101004 && getConfig("M416")) {
            return "M416";
        }

        if (id == 101002 && getConfig("M16A4")) {
            return "M16A4";
        }

        if (id == 101009 && getConfig("Mk47 Mutant")) {
            return "Mk47";
        }

        if (id == 101010 && getConfig("G36C")) {
            return "G36C";
        }

        if (id == 101007 && getConfig("QBZ")) {
            return "QBZ";
        }

        if (id == 101001 && getConfig("AKM")) {
            return "AKM";
        }

        if (id == 101005 && getConfig("Groza")) {
            return "Groza";
        }

        if (id == 102005 && getConfig("Bizon")) {
            return "Bizon";
        }

        if (id == 102004 && getConfig("TommyGun")) {
            return "TommyGun";
        }

        if (id == 102007 && getConfig("MP5K")) {
            return "MP5K";
        }

        if (id == 102002 && getConfig("UMP")) {
            return "UMP9";
        }

        if (id == 102003 && getConfig("Vector")) {
            return "Vector";
        }

        if (id == 102001 && getConfig("Uzi")) {
            return "Uzi";
        }

        if (id == 105002 && getConfig("DP28")) {
            return "DP28";
        }

        if (id == 105001 && getConfig("M249")) {
            return "M249";
        }

        // snipers

        if (id == 103003 && getConfig("AWM")) {
            return "AWM";
        }

        if (id == 103010 && getConfig("QBU")) {
            return "QBU";
        }

        if (id == 103009 && getConfig("SLR")) {
            return "SLR";
        }

        if (id == 103004 && getConfig("SKS")) {
            return "SKS";
        }

        if (id == 103006 && getConfig("Mini14")) {
            return "Mini14";
        }

        if (id == 103002 && getConfig("M24")) {
            return "M24";
        }

        if (id == 103001 && getConfig("Kar98k")) {
            return "Kar98";
        }

        if (id == 103005 && getConfig("VSS")) {
            return "VSS";
        }

        if (id == 103008 && getConfig("Win94")) {
            return "Win94";
        }

        if (id == 103007 && getConfig("Mk14")) {
            return "Mk14";
        }

        // shotguns and hand weapons
        if (id == 104003 && getConfig("S12K")) {
            return "S12K";
        }

        if (id == 104004 && getConfig("DBS")) {
            return "DBS";
        }

        if (id == 104001 && getConfig("S686")) {
            return "S686";
        }

        if (id == 104002 && getConfig("S1897")) {
            return "S1897";
        }

        if (id == 108003 && getConfig("Sickle")) {
            return "Sickle";
        }

        if (id == 108001 && getConfig("Machete")) {
            return "Machete";
        }

        if (id == 108002 && getConfig("Crowbar")) {
            return "Crowbar";
        }

        if (id == 107001 && getConfig("CrossBow")) {
            return "CrossBow";
        }

        if (id == 108004 && getConfig("Pan")) {
            return "Pan";
        }

        // pistols

        if (id == 106006 && getConfig("SawedOff")) {
            return "SawedOff";
        }

        if (id == 106003 && getConfig("R1895")) {
            return "R1895";
        }

        if (id == 106008 && getConfig("Vz61")) {
            return "Vz61";
        }

        if (id == 106001 && getConfig("P92")) {
            return "P92";
        }

        if (id == 106004 && getConfig("P18C")) {
            return "P18C";
        }

        if (id == 106005 && getConfig("R45")) {
            return "R45";
        }

        if (id == 106002 && getConfig("P1911")) {
            return "P1911";
        }

        if (id == 106010 && getConfig("Desert Eagle")) {
            return "DesertEagle";
        }

        // Ammo
        if (id == 302001 && getConfig("7.62")) {
            return "7.62";
        }

        if (id == 305001 && getConfig("45ACP")) {
            return "45ACP";
        }

        if (id == 303001 && getConfig("5.56")) {
            return "5.56";
        }

        if (id == 301001 && getConfig("9mm")) {
            return "9mm";
        }

        if (id == 306001 && getConfig("300Magnum")) {
            return "300Magnum";
        }

        if (id == 304001 && getConfig("12 Guage")) {
            return "12 Guage";
        }

        if (id == 307001 && getConfig("Arrow")) {
            return "Arrow";
        }

        // bag helmet vest
        if ((id == 501006 || id == 501003) && getConfig("Bag L 3")) {
            return "Bag lvl 3";
        }

        if ((id == 501004 || id == 501001) && getConfig("Bag L 1")) {
            return "Bag lvl 1";
        }

        if ((id == 501005 || id == 501002) && getConfig("Bag L 2")) {
            return "Bag lvl 2";
        }

        if (id == 503002 && getConfig("Vest L 2")) {
            return "Vest lvl 2";
        }

        if (id == 503001 && getConfig("Vest L 1")) {
            return "Vest lvl 1";
        }

        if (id == 503003 && getConfig("Vest L 3")) {
            return "Vest lvl 3";
        }

        if (id == 502002 && getConfig("Helmet 2")) {
            return "Helmet lvl 2";
        }

        if (id == 502001 && getConfig("Helmet 1")) {
            return "Helmet lvl 1";
        }

        if (id == 502003 && getConfig("Helmet 3")) {
            return "Helmet lvl 3";
        }

        // Healthkits
        if (id == 601003 && getConfig("PainKiller")) {
            return "Painkiller";
        }

        if (id == 601002 && getConfig("Adrenaline")) {
            return "Adrenaline";
        }

        if (id == 601001 && getConfig("Energy Drink")) {
            return "Energy Drink";
        }

        if (id == 601005 && getConfig("FirstAidKit")) {
            return "FirstAidKit";
        }

        if (id == 601004 && getConfig("Bandage")) {
            return "Bandage";
        }

        if (id == 601006 && getConfig("Medkit")) {
            return "Medkit";
        }

        // Throwables
        if (id == 602001 && getConfig("Stung")) {
            return "Stung";
        }

        if (id == 602004 && getConfig("Grenade")) {
            return "Grenade";
        }

        if (id == 602002 && getConfig("Smoke")) {
            return "Smoke";
        }

        if (id == 602003 && getConfig("Molotov")) {
            return "Molotov";
        }

        // others
        if (id == 201010 && getConfig("Flash Hider Ar")) {
            return "Flash Hider Ar";
        }

        if (id == 201009 && getConfig("Ar Compensator")) {
            return "Ar Compensator";
        }

        if (id == 201004 && getConfig("Flash Hider SMG")) {
            return "Flash Hider SMG";
        }

        if (id == 205002 && getConfig("Tactical Stock")) {
            return "Tactical Stock";
        }

        if (id == 201012 && getConfig("Duckbill")) {
            return "Duckbill";
        }

        if (id == 201005 && getConfig("Flash Hider Snp")) {
            return "Flash Hider Sniper";
        }

        if (id == 201006 && getConfig("Suppressor SMG")) {
            return "Suppressor SMG";
        }

        if (id == 205003 && getConfig("Half Grip")) {
            return "Half Grip";
        }

        if (id == 202005 && getConfig("Half Grip")) {
            return "Half Grip";
        }

        if (id == 201001 && getConfig("Choke")) {
            return "Choke";
        }

        if (id == 205001 && getConfig("Stock Micro UZI")) {
            return "Stock Micro UZI";
        }

        if (id == 201003 && getConfig("SniperCompensator")) {
            return "Sniper Compensator";
        }

        if (id == 201007 && getConfig("Sup Sniper")) {
            return "Suppressor Sniper";
        }

        if (id == 201011 && getConfig("Suppressor Ar")) {
            return "Suppressor Ar";
        }

        if (id == 204009 && getConfig("Ex.Qd.Sniper")) {
            return "Ex.Qd.Sniper";
        }

        if (id == 204005 && getConfig("Qd.SMG")) {
            return "Qd.SMG";
        }

        if (id == 204004 && getConfig("Ex.SMG")) {
            return "Ex.SMG";
        }

        if (id == 204008 && getConfig("Qd.Sniper")) {
            return "Qd.Sniper";
        }

        if (id == 204007 && getConfig("Ex.Sniper")) {
            return "Ex.Sniper";
        }

        if (id == 204011 && getConfig("Ex.Ar")) {
            return "Ex.Ar";
        }

        if (id == 204013 && getConfig("Ex.Qd.Ar")) {
            return "Ex.Qd.Ar";
        }

        if (id == 204012 && getConfig("Qd.Ar")) {
            return "Qd.Ar";
        }

        if (id == 204006 && getConfig("Ex.Qd.SMG")) {
            return "Ex.Qd.SMG";
        }

        if (id == 205004 && getConfig("Quiver CrossBow")) {
            return "Quiver CrossBow";
        }

        if (id == 204014 && getConfig("Bullet Loop")) {
            return "Bullet Loop";
        }

        if (id == 202006 && getConfig("Thumb Grip")) {
            return "Thumb Grip";
        }

        if (id == 202007 && getConfig("Laser Sight")) {
            return "Laser Sight";
        }

        if (id == 202001 && getConfig("Angled Grip")) {
            return "Angled Grip";
        }

        if (id == 202004 && getConfig("Light Grip")) {
            return "Light Grip";
        }

        if (id == 202002 && getConfig("Vertical Grip")) {
            return "Vertical Grip";
        }

        if (id == 603001 && getConfig("Gas Can")) {
            return "Gas Can";
        }

        if (id == 201002 && getConfig("Compensator SMG")) {
            return "Compensator SMG";
        }

        // special
        if ((id == 106007 || id == 308001) && getConfig("Flare Gun")) {
            return "Flare Gun";
        }

        if ((id == 403989 || id == 403045 || id == 403187 || id == 403188) && getConfig("Gullie Suit")) {
            return "Gullie Suit";
        }
        if (id == 103011 && getConfig("Mosin")) {
            return "Mosin";
        }
        if (id == 107005 && getConfig("PanzerFaust")) {
            return "PanzerFaust";
        }
        return "";
        // return String.valueOf(id);
    }
}

