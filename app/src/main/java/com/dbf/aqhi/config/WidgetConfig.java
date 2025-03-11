package com.dbf.aqhi.config;

import android.content.Context;
import android.content.SharedPreferences;

public class WidgetConfig {

    private static final String AQHI_PREF_KEY = "com.dbf.aqhi.widgets.";

    private final SharedPreferences prefs;
    private final int widgetId;
    private final Context context;

    public WidgetConfig(int widgetId, Context context) {
        this.widgetId = widgetId;
        this.prefs = context.getSharedPreferences(AQHI_PREF_KEY + widgetId, Context.MODE_PRIVATE);
        this.context  = context;
    }

    public void clearConfigs() {
        prefs.edit().clear().apply();
    }
}
