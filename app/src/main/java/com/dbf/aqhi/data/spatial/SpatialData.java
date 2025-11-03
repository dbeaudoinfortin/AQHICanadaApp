package com.dbf.aqhi.data.spatial;

import static com.dbf.aqhi.Utils.DEG_TO_RAD;
import static com.dbf.aqhi.Utils.RAD_TO_DEG;

import com.dbf.aqhi.Utils;
import com.dbf.aqhi.grib2.Grib2;
import com.dbf.aqhi.grib2.Grib2GridMetaData;

public class SpatialData {

    private ModelMetaData model;
    private Grib2 grib2;

    private final double gridScaleXInv; //= 0.09;
    private final double gridScaleYInv; //= 0.09;

    private final double rLatZero; //= -31.860001;
    private final double rLonZero; //= -39.537223;

    //Precompute rotation parameters in radians
    private final double lamP;
    private final double sinPhiP;
    private final double cosPhiP;

    public SpatialData(ModelMetaData model, Grib2 grib2) {
        if(model == null) throw new IllegalArgumentException("Model cannot be null.");
        if(grib2 == null) throw new IllegalArgumentException("Grib2 data cannot be null.");
        if(grib2.getRawImage() == null) throw new IllegalArgumentException("Grib2 raw image cannot be null.");

        final Grib2GridMetaData grid = grib2.getGridMetaData();
        if(grid == null) throw new IllegalArgumentException("Grib2 grid metadata cannot be null.");

        this.model = model;
        this.grib2 = grib2;

        this.gridScaleXInv = 1.0 / grid.getdLonDeg();
        this.gridScaleYInv = 1.0 / grid.getdLatDeg();

        this.rLatZero = grid.getLat1Deg();
        this.rLonZero = grid.getLon1Deg();

        final double rLatNorthPole = -grid.getSouthPoleLatDeg();
        final double rLonNorthPole = Utils.wrapLongitude(-grid.getSouthPoleLonDeg() + 180.0);
        this.lamP = Math.toRadians(rLonNorthPole);

        final double phiP = Math.toRadians(rLatNorthPole);
        this.sinPhiP = Math.sin(phiP);
        this.cosPhiP = Math.cos(phiP);
    }

    public ModelMetaData getModel() {
        return model;
    }

    public Grib2 getGrib2() {
        return grib2;
    }

    /**
     * Lookup the overlay alpha pixel value at a given latitude and longitude coordinate pair.
     *
     * @param lat coordinate
     * @param lon coordinate
     *
     * @return Overlay value
     */
    public int pixelLookup(double lat, double lon) {
        final double[] yxCoords = latLonYXLookup(lat, lon);
        return grib2.getRawImage().samplePixelsBilinear(yxCoords[1], yxCoords[0]);
    }

    /**
     * Lookup the overlay raw value at a given latitude and longitude coordinate pair
     *
     * @param lat coordinate
     * @param lon coordinate
     *
     * @return Overlay value
     */
    public float overlayValueLookup(double lat, double lon) {
        final double[] yxCoords = latLonYXLookup(lat, lon);
        return grib2.getRawImage().sampleValuesBilinear((float) yxCoords[1], (float) yxCoords[0]);
    }

    private double[] latLonYXLookup(double lat, double lon) {
        double[] latLon = new double[] {lat, lon};

        //Fudge factor
        latLon[1] += 4.8;

        //Convert from degrees to rotated radian coordinates
        latLon[0] *= DEG_TO_RAD;
        latLon[1] *= DEG_TO_RAD;

        final double sinLat = Math.sin(latLon[0]);
        final double cosLat = Math.cos(latLon[0]);

        final double dLam = latLon[1] - lamP;
        final double cosLatCosDLam = Math.cos(dLam) * cosLat;

        final double phiR = Math.asin((sinLat * sinPhiP) - (cosPhiP * cosLatCosDLam)); //Faster than Math.atan2(sinPhiR, Math.hypot(cosPhiR_sinLamR, cosPhiR_cosLamR));
        final double lamR = Math.atan2(cosLat * Math.sin(dLam), (sinLat * cosPhiP) + (sinPhiP * cosLatCosDLam));

        //Convert from rotated radian coordinates to grid fractional indices (i,j) in degrees
        latLon[0] = ((phiR * RAD_TO_DEG) - rLatZero) * gridScaleYInv;
        latLon[1] = ((lamR * RAD_TO_DEG) - rLonZero) * gridScaleXInv;

        return latLon;
    }

    public double getGridScaleXInv() {
        return gridScaleXInv;
    }

    public double getGridScaleYInv() {
        return gridScaleYInv;
    }

    public double getrLatZero() {
        return rLatZero;
    }

    public double getrLonZero() {
        return rLonZero;
    }

    public double getLamP() {
        return lamP;
    }

    public double getSinPhiP() {
        return sinPhiP;
    }

    public double getCosPhiP() {
        return cosPhiP;
    }
}