package com.dbf.aqhi.geomet.realtime;

public class RealtimeData {
    public String type;
    public Properties properties;
    public String id;

    public static class Properties {
        public Double aqhi;
        public Boolean latest;
        public String observation_datetime_text_en;
    }
}
