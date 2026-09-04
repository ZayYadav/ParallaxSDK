package com.onecore.loader.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.app.Activity;
import android.app.Service;

import java.util.Locale;

public class FPrefs {
    private SharedPreferences sp;
    private Context context;

    public FPrefs(Context context){
        this.context = context.getApplicationContext();
        sp = this.context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public static FPrefs with(Context context){
        return new FPrefs(context);
    }

    public String read(String key, String value){
        return sp.getString(key, value);
    }

    public void write(String key, String value){
        SharedPreferences.Editor ed = sp.edit();
        ed.putString(key, value);
        ed.apply();
    }

    public boolean readBoolean(String key, boolean value){
        return sp.getBoolean(key, value);
    }

    public void writeBoolean(String key, boolean value){
        SharedPreferences.Editor ed = sp.edit();
        ed.putBoolean(key, value);
        ed.apply();
    }

    public int readInt(String key, int defaultValue){
        return sp.getInt(key, defaultValue);
    }

    public void writeInt(String key, int value){
        SharedPreferences.Editor ed = sp.edit();
        ed.putInt(key, value);
        ed.apply();
    }

    // Overloaded methods for custom preference files
    public void writeBoolean(String file, String key, boolean value) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(file, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putBoolean(key, value);
        ed.apply();
    }

    public void write(String file, String key, String value) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(file, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putString(key, value);
        ed.apply();
    }

    public String read(String file, String key, String defaultValue){
        SharedPreferences sharedPreferences = context.getSharedPreferences(file, Context.MODE_PRIVATE);
        return sharedPreferences.getString(key, defaultValue);
    }

    public void writeInt(String file, String key, int value) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(file, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putInt(key, value);
        ed.apply();
    }

    public int readInt(String file, String key, int defaultValue){
        SharedPreferences sharedPreferences = context.getSharedPreferences(file, Context.MODE_PRIVATE);
        return sharedPreferences.getInt(key, defaultValue);
    }

    // Locale methods
    public void setLocale(Activity act, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        Resources resources = act.getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }

    public void setLocale(Service svc, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        Resources resources = svc.getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }
}
