package com.dbf.aqhi.api.datamart;


public enum Pollutant {
    //Raw data is in kg/m^3. We want the max scale to be 50 ug/m^3
    PM25(false, "_PM2.5_Sfc","_PM2.5_Sfc","PM 2.5", "µg/m³", 1000000000f, 0f, 50f),

    PM25_SMOKE(true, "_PM2.5-WildfireSmokePlume_Sfc","-FW_PM2.5_Sfc","PM 2.5/Smoke", "µg/m³", 1000000000f, 0f, 50f),

    PM10(false, "_PM10_Sfc","_PM10_Sfc","PM 10", "µg/m³", 1000000000f, 0f, 50f),

    PM10_SMOKE(true, "_PM10-WildfireSmokePlume_Sfc","-FW_PM10_Sfc","PM 10/Smoke", "µg/m³", 1000000000f, 0f, 50f),

    //Raw data is in VMR. We want the max scale to be 20 ppb
    SO2(false, "_SO2_Sfc","_SO2_Sfc","SO2", "ppb", 1000000000f, 0f, 20f),

    //Raw data is in VMR. We want the max scale to be 20 ppb
    NO2(false, "_NO2_Sfc","_NO2_Sfc","NO2", "ppb", 1000000000f, 0f, 40f),

    //Raw data is in VMR. We want the max scale to be 40 ppb
    NO(false, "_NO_Sfc","_NO_Sfc","NO", "ppb", 1000000000f, 0f, 40f),

    //Raw data is in VMR. We want the max scale to be 100 ppb, and the min to be 10 ppb
    O3(false, "_O3_Sfc","_O3_Sfc","O3", "ppb", 1000000000f, 10f, 100f);

    private final boolean smoke;
    private final String datamartForecastName;
    private final String datamartObservationName;
    private final String displayName;
    private final String units;
    private final float unitScale;
    private final float minVal;
    private final float maxVal;


    Pollutant(boolean smoke, String datamartForecastName, String datamartObservationName, String displayName, String units, float unitScale, float minVal, float maxVal) {
        this.smoke = smoke;
        this.datamartForecastName = datamartForecastName;
        this.datamartObservationName = datamartObservationName;
        this.displayName = displayName;
        this.unitScale = unitScale;
        this.minVal = minVal;
        this.maxVal = maxVal;
        this.units = units;
    }

    public boolean isSmoke() { return smoke; }

    public String getDisplayName() {
        return displayName;
    }

    public String getDatamartForecastName() {
        return datamartForecastName;
    }

    public String getDatamartObservationName() {
        return datamartObservationName;
    }

    public float getUnitScale() {
        return unitScale;
    }

    public float getMinVal() {
        return minVal;
    }

    public float getMaxVal() {
        return maxVal;
    }

    public String getUnits() {
        return units;
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
