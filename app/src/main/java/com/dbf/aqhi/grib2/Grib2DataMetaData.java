package com.dbf.aqhi.grib2;

public class Grib2DataMetaData {
    private final float referenceValueR;   //R
    private final short binaryScaleE;      //E (signed)
    private final short decimalScaleD;     //D (signed)
    private final int bitsPerValue;        //Nb
    private final int originalType;        //Type of original field values
    private final int dataTemplateNumber;  //40 for JPEG2000
    private final long dataPoints;

    //Precomputed factors for speed.
    private final double twoPowE;
    private final double tenPowNegD;

    public Grib2DataMetaData(float referenceValueR, short binaryScaleE, short decimalScaleD, int bitsPerValue, int originalType, int dataTemplateNumber, long dataPoints) {
        this.referenceValueR    = referenceValueR;
        this.binaryScaleE       = binaryScaleE;
        this.decimalScaleD      = decimalScaleD;
        this.bitsPerValue       = bitsPerValue;
        this.dataTemplateNumber = dataTemplateNumber;
        this.dataPoints         = dataPoints;
        this.originalType       = originalType;
        this.twoPowE            = Math.scalb(1.0, binaryScaleE);
        this.tenPowNegD         = Math.pow(10.0, -decimalScaleD);
    }

    /** Apply GRIB2 scaling: X = (R + Y*2^E) * 10^-D */
    public double scale(int y) {
        return (referenceValueR + (y * twoPowE)) * tenPowNegD;
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

    public int getOriginalType() {
        return originalType;
    }
}
