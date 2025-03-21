package com.dbf.aqhi.widgets.config;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public class WidgetConfig {

    private static final String AQHI_PREF_KEY = "com.dbf.aqhi.widgets.";
    private static final String ALPHA_KEY  = "BACKGROUND_ALPHA";
    private static final String NIGHT_MODE_KEY = "NIGHT_MODE";

    private static final int DEFAULT_ALPHA = 50; //50%
    private static final int DEFAULT_NIGHT_MODE = AppCompatDelegate.MODE_NIGHT_NO;

    private final SharedPreferences prefs;

    public WidgetConfig(Context context, int widgetId) {
        this.prefs = context.getSharedPreferences(AQHI_PREF_KEY + widgetId, Context.MODE_PRIVATE);
    }

    public void clearConfigs() {
        prefs.edit().clear().apply();
    }

    public void setAlpha(int alpha) {
        prefs.edit().putInt(ALPHA_KEY, alpha).apply();
    }

    public void setNightMode(int mode) {
        prefs.edit().putInt(NIGHT_MODE_KEY, mode).apply();
    }

    public int getAlpha(){
        return prefs.getInt(ALPHA_KEY, DEFAULT_ALPHA);
    }

    public int getNightMode(){
        return prefs.getInt(NIGHT_MODE_KEY, DEFAULT_NIGHT_MODE);
    }
}
