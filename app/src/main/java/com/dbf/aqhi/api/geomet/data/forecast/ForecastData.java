package com.dbf.aqhi.api.geomet.data.forecast;

import com.dbf.aqhi.api.geomet.data.Data;
import com.dbf.aqhi.api.geomet.data.DataProperties;

public class ForecastData extends Data {
    public ForecastProperties properties;

    @Override
    public ForecastProperties getProperties() {
        return properties;
    }

    public static class ForecastProperties extends DataProperties {
        public Boolean latest;
        public String forecast_datetime_text_en;
        public String forecast_datetime;

        @Override
        public String getDateTimeString() {
            return forecast_datetime;
        }
    }
}
