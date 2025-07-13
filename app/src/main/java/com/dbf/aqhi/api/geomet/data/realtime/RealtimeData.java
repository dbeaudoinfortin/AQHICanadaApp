package com.dbf.aqhi.api.geomet.data.realtime;

import com.dbf.aqhi.api.geomet.data.Data;
import com.dbf.aqhi.api.geomet.data.DataProperties;

public class RealtimeData extends Data {

    public RealtimeProperties properties;

    @Override
    public RealtimeProperties getProperties() {
        return properties;
    }

    public static class RealtimeProperties extends DataProperties {
        public Boolean latest;
        public String observation_datetime_text_en;
        public String observation_datetime;

        @Override
        public String getDateTimeString() {
            return observation_datetime;
        }
    }
}
