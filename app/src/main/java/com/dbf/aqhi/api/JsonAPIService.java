package com.dbf.aqhi.api;

import com.google.gson.Gson;

public abstract class JsonAPIService extends APIService {

    private static final String LOG_TAG = "JsonAPIService";
    protected static final Gson gson = new Gson();
}
