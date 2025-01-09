package com.dbf.aqhi.geomet.realtime;

import com.dbf.aqhi.geomet.Response;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class RealtimeResponse extends Response {

    @SerializedName(value = "features")
    public List<RealtimeData> data;
}
