package com.dbf.aqhi.api;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.dbf.aqhi.http.RetryInterceptor;
import com.google.gson.Gson;

import okhttp3.OkHttpClient;

public abstract class APIService {

    private static final String LOG_TAG = "APIService";

    private static final long HTTP_TIMEOUT = 60000; //1 minute in Milliseconds
    private static final int  HTTP_TRIES = 2;

    protected static final Gson gson = new Gson();

    protected static final OkHttpClient client = new OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .addInterceptor(new RetryInterceptor(HTTP_TRIES, LOG_TAG))
            .callTimeout(HTTP_TIMEOUT, MILLISECONDS)
            .build();

}
