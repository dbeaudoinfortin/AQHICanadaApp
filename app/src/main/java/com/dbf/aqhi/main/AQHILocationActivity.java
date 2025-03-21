package com.dbf.aqhi.main;

import android.os.Bundle;

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


    }

    @Override
    public AQHIService getAQHIService() {
        return null;
    }
}