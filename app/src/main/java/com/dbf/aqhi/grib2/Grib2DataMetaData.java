package com.dbf.aqhi.grib2;

public class Grib2DataMetaData {
    private final float referenceValueR;   //R
    private final short binaryScaleE;      //E (signed)
    private final short decimalScaleD;     //D (signed)
    private final int bitsPerValue;        //Nb
    private final int dataTemplateNumber; //40 for JPEG2000
    private final long dataPoints;

    // Precomputed factors for speed.
    private final double twoPowE;
    private final double tenPowNegD;

    public Grib2DataMetaData(float R, short E, short D, int nb, int dataTemplateNumber, long dataPoints) {
        this.referenceValueR = R;
        this.binaryScaleE    = E;
        this.decimalScaleD   = D;
        this.bitsPerValue    = nb;
        this.dataTemplateNumber = dataTemplateNumber;
        this.dataPoints = dataPoints;
        this.twoPowE         = Math.scalb(1.0, E);             // 2^E (fast, exact)
        this.tenPowNegD      = Math.pow(10.0, -D);             // 10^-D
    }

    /** Apply GRIB2 scaling: X = (R + Y*2^E) * 10^-D */
    public double scale(int y) {
        return (referenceValueR + y * twoPowE) * tenPowNegD;
    }

    public float getReferenceValueR() {
        return referenceValueR;
    }

    public short getBinaryScaleE() {
        return binaryScaleE;
    }

    public short getDecimalScaleD() {
        return decimalScaleD;
    }

    public int getBitsPerValue() {
        return bitsPerValue;
    }

    public int getDataTemplateNumber() {
        return dataTemplateNumber;
    }

    public long getDataPoints() {
        return dataPoints;
    }
}
