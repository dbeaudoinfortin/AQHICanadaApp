package com.dbf.aqhi.grib2;

public class Grib2GridMetaData {
    private final int gridTemplate;           //See Section 3.1
    private final int gridWidth;
    private final int gridHeight;
    private final double lat1Deg, lon1Deg;    //first grid point Latitude
    private final double lat2Deg, lon2Deg;    //last grid point Latitude
    private final double dLonDeg, dLatDeg;    //increments (sign-corrected by scan flags)
    private final int scanMode;               //bit flags
    private final double southPoleLatDeg;     //for rotated_ll
    private final double southPoleLonDeg;     //for rotated_ll
    private final double angleOfRotationDeg;  //usually 0

    public Grib2GridMetaData(int gridTemplate, int gridWidth, int gridHeight,
                             double lat1Deg, double lon1Deg,
                             double lat2Deg, double lon2Deg,
                             double dLonDeg, double dLatDeg, int scanMode,
                             double southPoleLatDeg, double southPoleLonDeg, double angleOfRotationDeg) {
        this.gridTemplate = gridTemplate;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.lat1Deg = lat1Deg;
        this.lon1Deg = lon1Deg;
        this.lat2Deg = lat2Deg;
        this.lon2Deg = lon2Deg;
        this.dLonDeg = dLonDeg;
        this.dLatDeg = dLatDeg;
        this.scanMode = scanMode;
        this.southPoleLatDeg = southPoleLatDeg;
        this.southPoleLonDeg = southPoleLonDeg;
        this.angleOfRotationDeg = angleOfRotationDeg;
    }

    public int getGridTemplate() {
        return gridTemplate;
    }

    public int getGridWidth() {
        return gridWidth;
    }

    public int getGridHeight() {
        return gridHeight;
    }

    public double getLat1Deg() {
        return lat1Deg;
    }

    public double getLon1Deg() {
        return lon1Deg;
    }

    public double getLat2Deg() {
        return lat2Deg;
    }

    public double getLon2Deg() {
        return lon2Deg;
    }

    public double getdLonDeg() {
        return dLonDeg;
    }

    public double getdLatDeg() {
        return dLatDeg;
    }

    public int getScanMode() {
        return scanMode;
    }

    public double getSouthPoleLatDeg() {
        return southPoleLatDeg;
    }

    public double getSouthPoleLonDeg() {
        return southPoleLonDeg;
    }

    public double getAngleOfRotationDeg() {
        return angleOfRotationDeg;
    }
}
