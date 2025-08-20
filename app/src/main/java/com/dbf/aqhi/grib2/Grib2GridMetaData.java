package com.dbf.aqhi.grib2;

public class Grib2GridMetaData {
    public final int gridTemplate;           //3.x
    public final int Ni, Nj;                 //Number of points along i, j
    public final double rlat0Deg, rlon0Deg;  //first grid point (rotated if grid is rotated_ll)
    public final double dLonDeg, dLatDeg;    //increments (sign-corrected by scan flags)
    public final int scanMode;               //bit flags
    public final double southPoleLatDeg;     //for rotated_ll
    public final double southPoleLonDeg;     //for rotated_ll
    public final double angleOfRotationDeg;  //usually 0

    public Grib2GridMetaData(int gridTemplate, int ni, int nj,
                             double rlat0Deg, double rlon0Deg,
                             double dLonDeg, double dLatDeg, int scanMode,
                             double southPoleLatDeg, double southPoleLonDeg, double angleOfRotationDeg) {
        this.gridTemplate = gridTemplate;
        this.Ni = ni;
        this.Nj = nj;
        this.rlat0Deg = rlat0Deg;
        this.rlon0Deg = rlon0Deg;
        this.dLonDeg = dLonDeg;
        this.dLatDeg = dLatDeg;
        this.scanMode = scanMode;
        this.southPoleLatDeg = southPoleLatDeg;
        this.southPoleLonDeg = southPoleLonDeg;
        this.angleOfRotationDeg = angleOfRotationDeg;
    }
}
