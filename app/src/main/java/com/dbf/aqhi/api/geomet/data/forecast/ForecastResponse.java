package com.dbf.aqhi.api.geomet.data.forecast;

import com.dbf.aqhi.api.geomet.data.DataResponse;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ForecastResponse extends DataResponse<ForecastData> {

    @SerializedName(value = "features")
    public List<ForecastData> data;

    @Override
    public List<ForecastData> getData() {
        return data;
    }
}
