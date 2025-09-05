package com.dbf.aqhi.data.spatial;

import com.dbf.aqhi.grib2.Grib2;

public class SpatialData {

    private ModelMetaData model;
    private Grib2 grib2;

    public SpatialData() {}

    public SpatialData(ModelMetaData model, Grib2 grib2) {
        this.model = model;
        this.grib2 = grib2;
    }

    public ModelMetaData getModel() {
        return model;
    }

    public void setModel(ModelMetaData model) {
        this.model = model;
    }

    public Grib2 getGrib2() {
        return grib2;
    }

    public void setGrib2(Grib2 grib2) {
        this.grib2 = grib2;
    }
}
