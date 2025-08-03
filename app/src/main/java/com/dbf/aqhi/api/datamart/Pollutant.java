package com.dbf.aqhi.api.datamart;

public enum Pollutant {
    NO2("NO2_Sfc"),
    NO("NO_Sfc"),
    O3("O3_Sfc"),
    SO2("SO2_Sfc"),
    PM25("PM2.5_Sfc"),
    PM10("PM10_Sfc"),
    PM25_SMOKE("PM2.5-WildfireSmokePlume_Sfc"),
    PM10_SMOKE("PM10-WildfireSmokePlume_Sfc");

    private final String value;

    Pollutant(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
