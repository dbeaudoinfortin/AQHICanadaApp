package com.dbf.aqhi.geomet.station;

import com.dbf.aqhi.geomet.Response;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class StationResponse extends Response {
    @SerializedName(value = "features")
    public List<Station> stations;
}
