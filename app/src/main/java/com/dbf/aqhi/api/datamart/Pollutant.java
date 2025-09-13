package com.dbf.aqhi.api.datamart;

public enum Pollutant {
    PM25("_PM2.5_Sfc","_PM2.5_Sfc","PM 2.5"),
    PM25_SMOKE("_PM2.5-WildfireSmokePlume_Sfc","-FW_PM2.5_Sfc","PM 2.5 Smoke"),
    PM10("_PM10_Sfc","_PM10_Sfc","PM 10"),
    PM10_SMOKE("_PM10-WildfireSmokePlume_Sfc","-FW_PM10_Sfc","PM 10 Smoke"),
    NO2("_NO2_Sfc","_NO2_Sfc","NO2"),
    NO("_NO_Sfc","_NO_Sfc","NO"),
    O3("_O3_Sfc","_O3_Sfc","O3"),
    SO2("_SO2_Sfc","_SO2_Sfc","SO2");

    private final String datamartForecastName;
    private final String datamartObservationName;
    private final String displayName;

    Pollutant(String datamartForecastName, String datamartObservationName, String displayName) {
        this.datamartForecastName = datamartForecastName;
        this.datamartObservationName = datamartObservationName;
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDatamartForecastName() {
        return datamartForecastName;
    }

    public String getDatamartObservationName() {
        return datamartObservationName;
    }

    public static Pollutant fromDisplayName(String name) {
        for (Pollutant mode : values()) {
            if (mode.displayName.equalsIgnoreCase(name)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown pollutant name: " + name);
    }

}
