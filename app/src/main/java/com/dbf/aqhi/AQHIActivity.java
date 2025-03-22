package com.dbf.aqhi;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.dbf.utils.stacktrace.StackTraceCompactor;

import org.apache.commons.io.IOUtils;

import java.nio.charset.Charset;

public abstract class AQHIActivity extends Activity implements AQHIFeature {

    private static final String LOG_TAG = "AQHIActivity";

    public void showLegalNotices(View view) {
        showDialog("Legal Notices", R.raw.legal_notices);
    }

    public void showAbout(View view) {
        showDialog("About AQHI", R.raw.about_aqhi);
    }

    public void showPrivacy(View view) { showDialog("Privacy Statement", R.raw.privacy); }

    private void showDialog(String title, int resourceID){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        // Create a TextView to show the message
        TextView messageTextView = new TextView(this);
        messageTextView.setText(Html.fromHtml(loadDialogContent(resourceID),Html.FROM_HTML_MODE_LEGACY));
        messageTextView.setMovementMethod(LinkMovementMethod.getInstance());
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        messageTextView.setPadding(padding, padding, padding, padding);
        builder.setView(messageTextView);
        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    protected String loadDialogContent(int resourceID) {
        try {
            return IOUtils.toString(getResources().openRawResource(resourceID), Charset.defaultCharset());
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to load content.\n" + StackTraceCompactor.getCompactStackTrace(e));
            return "<p>Failed to content.</p>";
        }
    }

    protected abstract void initUI();
    protected Color getColour(String colourId){
        return Utils.getColor(this, colourId);
    }
}