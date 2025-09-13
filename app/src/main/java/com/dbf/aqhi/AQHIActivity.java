package com.dbf.aqhi;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.dbf.aqhi.widgets.AQHIWidgetUpdateWorker;
import com.dbf.utils.stacktrace.StackTraceCompactor;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import ovh.plrapps.mapview.MapViewConfiguration;
import ovh.plrapps.mapview.core.TileStreamProvider;

public abstract class AQHIActivity extends Activity implements AQHIFeature {

    private static final String LOG_TAG = "AQHIActivity";

    public void showLegalNotices(View view) {
        showDialog("Legal Notices", R.raw.legal_notices);
    }

    public void showAbout(View view) {
        showDialog("About AQHI", R.raw.about_aqhi);
    }

    public void showPrivacy(View view) { showDialog("Privacy Statement", R.raw.privacy); }

    protected void showDialog(String title, Integer resourceID) {
        showDialog(title, resourceID, null, null, null);
    }

    protected void showDialog(String title, Integer resourceID, String additionalHTML, Html.ImageGetter imageGetter, Html.TagHandler tagHandler){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        // Create a TextView to show the message
        TextView messageTextView = new TextView(this);
        String contents = resourceID==null ? "" : loadDialogContent(resourceID);
        if(null != additionalHTML && !additionalHTML.isEmpty()) {
            contents += additionalHTML;
        }
        messageTextView.setText(Html.fromHtml(contents, Html.FROM_HTML_MODE_LEGACY, imageGetter, tagHandler));
        messageTextView.setMovementMethod(LinkMovementMethod.getInstance());
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        messageTextView.setPadding(padding, padding, padding, padding);
        builder.setView(messageTextView);
        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.show();

        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            window.setAttributes(params);
        }
    }

    protected String loadDialogContent(int resourceID) {
        try {
            return IOUtils.toString(getResources().openRawResource(resourceID), Charset.defaultCharset());
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to load content.\n" + StackTraceCompactor.getCompactStackTrace(e));
            return "<p>Failed to content.</p>";
        }
    }

    protected void forceWidgetUpdate() {
        //Forcefully update the widgets, this will handle any change in location
        //and also make sure that they get the same data the user is seeing on the
        //main activity.
        WorkManager workManager = WorkManager.getInstance(this);
        workManager.enqueueUniqueWork("widget_update_now", ExistingWorkPolicy.APPEND,
                new OneTimeWorkRequest.Builder(AQHIWidgetUpdateWorker.class)
                        .setInitialDelay(0, TimeUnit.MINUTES)
                        .build());
    }

    protected MapViewConfiguration getMapConfiguration() {
        return getMapConfiguration(getMapTileProvider());
    }

    protected MapViewConfiguration getMapConfiguration(TileStreamProvider tileStreamProvider) {
        return new MapViewConfiguration(MAP_LEVEL_COUNT, MAP_WIDTH, MAP_HEIGHT, MAP_TILE_SIZE, tileStreamProvider);
    }

    protected TileStreamProvider getMapTileProvider()
    {
        return (row, col, zoomLvl) -> {
            try {
                return getAssets().open("map_tiles/" + zoomLvl + "/" + row + "/" + col + ".webp");
            } catch (IOException e) {
                Log.e(LOG_TAG, "Failed to load map tile row: "+ row + ", col: " + col + ", lvl:"
                        + zoomLvl + ".\n" + StackTraceCompactor.getCompactStackTrace(e));
            }
            return null;
        };
    }

    protected abstract void initUI();
    protected Color getColour(String colourId){
        return Utils.getColor(this, colourId);
    }
}