package com.dbf.aqhi.main;

import android.os.Bundle;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.dbf.aqhi.AQHIActivity;
import com.dbf.aqhi.R;
import com.dbf.aqhi.service.AQHIService;

public class AQHILocationActivity extends AQHIActivity {
    private static final String LOG_TAG = "AQHILocationActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.change_location_activity);

        initUI ();

    }

    protected void initUI () {
        SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)findViewById(R.id.mapView);

        imageView.setImage(
                ImageSource.resource(R.drawable.canada_map).dimensions(17122,14513),
                ImageSource.resource(R.drawable.canada_map).dimensions(1712,1451)
        );

    }

    @Override
    public AQHIService getAQHIService() {
        return null;
    }
}