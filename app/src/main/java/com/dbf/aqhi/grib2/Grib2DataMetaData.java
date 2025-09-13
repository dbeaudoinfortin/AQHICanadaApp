package com.dbf.aqhi.grib2;

public class Grib2DataMetaData {
    private final float referenceValueR;   //R
    private final int binaryScaleE;      //E (signed)
    private final int decimalScaleD;     //D (signed)
    private final int bitsPerValue;        //Nb
    private final int originalType;        //Type of original field values
    private final int dataTemplateNumber;  //40 for JPEG2000
    private final long dataPoints;


    public Grib2DataMetaData(float referenceValueR, int binaryScaleE, int decimalScaleD, int bitsPerValue, int originalType, int dataTemplateNumber, long dataPoints) {
        this.referenceValueR    = referenceValueR;
        this.binaryScaleE       = binaryScaleE;
        this.decimalScaleD      = decimalScaleD;
        this.bitsPerValue       = bitsPerValue;
        this.dataTemplateNumber = dataTemplateNumber;
        this.dataPoints         = dataPoints;
        this.originalType       = originalType;
    }

    public float getReferenceValueR() {
        return referenceValueR;
    }

    public int getBinaryScaleE() {
        return binaryScaleE;
    }

    public int getDecimalScaleD() {
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
