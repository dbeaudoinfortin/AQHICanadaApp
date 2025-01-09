package com.dbf.aqhi.http;

import android.util.Log;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Response;

public class RetryInterceptor implements Interceptor {
    private final int maxTries;
    private final String LOG_TAG;

    public RetryInterceptor(int maxTries, String LOG_TAG) {
        this.maxTries = maxTries;
        this.LOG_TAG = LOG_TAG;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        int attempt = 0;
        while (true) {
            try {
                return chain.proceed(chain.request());
            } catch (IOException e) {
                Log.w(LOG_TAG, "HTTP Call failed: " + chain.request().method() + " " + chain.request().url(), e);
                attempt++;
                if (attempt >= maxTries) {
                    throw e;
                }
            }
        }
    }
}
