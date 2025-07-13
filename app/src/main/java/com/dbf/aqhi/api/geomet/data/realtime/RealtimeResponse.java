package com.dbf.aqhi.api.geomet.data.realtime;

import com.dbf.aqhi.api.geomet.data.DataResponse;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class RealtimeResponse extends DataResponse<RealtimeData> {

    @SerializedName(value = "features")
    public List<RealtimeData> data;

    @Override
    public List<RealtimeData> getData() {
        return data;
    }
}
