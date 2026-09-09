package com.onecore.loader.utils;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;
import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class Prefs {
    public Context context;
    SharedPreferences sp;
    public Prefs(Context context){
        this.context = context;
        sp = context.getSharedPreferences("settings",Context.MODE_PRIVATE);
    }

    public String getSt(String  map,String ori){
        return sp.getString(map,ori);
    }

    public void setSt(String  map,String write){
        SharedPreferences.Editor ed = sp.edit();
        ed.putString(map,write);
        ed.apply();
        ed.commit();
    }

    public boolean getBool(String  map,boolean ori){
        return sp.getBoolean(map,ori);
    }
    public void setBool(String  map,boolean write){
        SharedPreferences.Editor ed = sp.edit();
        ed.putBoolean(map,write);
        ed.apply();
        ed.commit();
    }

    public int getInt(String  map,int ori){
        return sp.getInt(map,ori);
    }

    public void setInt(String  map,int write){
        SharedPreferences.Editor ed = sp.edit();
        ed.putInt(map,write);
        ed.apply();
        ed.commit();
    }

    public void setBool(String file,String map, boolean write) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(file,Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putBoolean(map, write);
        ed.apply();
        ed.commit();
    }

    public void setSt(String file,String map, String write) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(file,Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putString(map, write);
        ed.apply();
        ed.commit();
    }
    public String getSt(String file,String  map, String ori){
        SharedPreferences sharedPreferences = context.getSharedPreferences(file,Context.MODE_PRIVATE);
        return sharedPreferences.getString(map,ori);
    }

    public void setInt(String file,String map, int write) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(file,Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putInt(map, write);
        ed.apply();
        ed.commit();
    }

    public int getInt(String file,String  map, int ori){
        SharedPreferences sharedPreferences = context.getSharedPreferences(file,Context.MODE_PRIVATE);
        return sharedPreferences.getInt(map,ori);
    }


    public void setLocale(Activity act, String cd) {
        Locale loc = new Locale(cd);
        loc.setDefault(loc);
        Resources ress = act.getResources();
        Configuration cfg = ress.getConfiguration();
        cfg.setLocale(loc);
        ress.updateConfiguration(cfg, ress.getDisplayMetrics());
    }
    public void setLocale(Service act, String cd) {
        Locale loc = new Locale(cd);
        loc.setDefault(loc);
        Resources ress = act.getResources();
        Configuration cfg = ress.getConfiguration();
        cfg.setLocale(loc);
        ress.updateConfiguration(cfg, ress.getDisplayMetrics());
    }

}
