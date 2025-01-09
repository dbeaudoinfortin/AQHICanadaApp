package com.dbf.aqhi.geomet.station;

import java.util.List;

public class Station {
    public String type;
    public Geometry geometry;
    public Properties properties;
    public String id;

    public static class Geometry {
        public String type;
        public List<Double> coordinates;
    }

    public static class Properties {
        public String location_name_en;
        public String location_id;
    }
}
