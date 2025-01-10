package com.dbf.aqhi.geomet.realtime;

import java.time.Instant;
import java.util.Date;

public class RealtimeData {
    public String type;
    public Properties properties;
    public String id;

    public static class Properties {
        public Double aqhi;
        public Boolean latest;
        public String observation_datetime_text_en;

        public Date getDate() {
            //ISO 8601 by default
            return Date.from(Instant.parse(observation_datetime_text_en));
        }
    }
}
