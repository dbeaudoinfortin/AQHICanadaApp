package com.dbf.aqhi.widgets;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.widget.RemoteViews;

import com.dbf.aqhi.R;
import com.dbf.aqhi.aqhiservice.AQHIService;

public class AQHIWidgetProviderFace extends AQHIWidgetProviderSmall {

    public AQHIWidgetProviderFace() {
        super();
    }

    public AQHIWidgetProviderFace(AQHIService aqhiService) {
        super(aqhiService);
    }

    @Override
    public void updateWidgetUI(Context context, int lightDarkMode, RemoteViews views, AppWidgetManager appWidgetManager, int appWidgetId) {
        Double recentAQHI = this.getLatestAQHI();
        if(null == recentAQHI || recentAQHI < 0) {
            views.setImageViewResource(R.id.imgAQHIFace,R.drawable.emoji_0);
        } else if (recentAQHI >= 10.5) { // 11+
            views.setImageViewResource(R.id.imgAQHIFace,R.drawable.emoji_6);
        } else if (recentAQHI >= 9.5) { // 10
            views.setImageViewResource(R.id.imgAQHIFace,R.drawable.emoji_5);
        } else if (recentAQHI >= 7.5) { // 8 & 9
            views.setImageViewResource(R.id.imgAQHIFace,R.drawable.emoji_4);
        } else if (recentAQHI >= 5.5) { // 6 & 7
            views.setImageViewResource(R.id.imgAQHIFace,R.drawable.emoji_3);
        } else if (recentAQHI >= 3.5) { // 4 & 5
            views.setImageViewResource(R.id.imgAQHIFace,R.drawable.emoji_2);
        } else { // 1, 2 & 3
            views.setImageViewResource(R.id.imgAQHIFace,R.drawable.emoji_1);
        }
        super.updateWidgetUI(context, lightDarkMode, views, appWidgetManager, appWidgetId);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.widget_layout_face;
    }
}