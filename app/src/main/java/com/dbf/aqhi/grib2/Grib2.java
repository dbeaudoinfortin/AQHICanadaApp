package com.dbf.aqhi.grib2;

import com.dbf.aqhi.codec.RawImage;

public class Grib2 {

    private final Grib2GridMetaData gridMetaData;
    private final Grib2DataMetaData dataMetaData;
    private final RawImage rawImage;

    public Grib2(Grib2GridMetaData gridMeta, Grib2DataMetaData dataMetaData, RawImage rawImage) {
        this.gridMetaData = gridMeta;
        this.dataMetaData = dataMetaData;
        this.rawImage = rawImage;
    }

    public Grib2GridMetaData getGridMetaData() {
        return gridMetaData;
    }

    public Grib2DataMetaData getDataMetaData() {
        return dataMetaData;
    }

    public RawImage getRawImage() {
        return rawImage;
    }

}
