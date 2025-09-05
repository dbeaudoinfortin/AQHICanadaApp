package com.dbf.aqhi.api.datamart;

import com.dbf.aqhi.data.spatial.ModelMetaData;

public class DatamartData {

    private ModelMetaData model;
    private byte[] rawData;

    public DatamartData() {}

    public DatamartData(String model, String pollutant, String date, String modelRunTime, String hour, byte[] rawData) {
        this.model = new ModelMetaData(model, pollutant, date, modelRunTime, hour);
        this.rawData = rawData;
    }

    public byte[] getRawData() {
        return rawData;
    }

    public void setRawData(byte[] rawData) {
        this.rawData = rawData;
    }

    public ModelMetaData getModel() {
        return model;
    }

    public void setModel(ModelMetaData model) {
        this.model = model;
    }

}
