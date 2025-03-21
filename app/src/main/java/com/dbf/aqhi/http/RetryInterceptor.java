package com.dbf.aqhi.http;

import android.util.Log;

import androidx.annotation.NonNull;

import com.dbf.utils.stacktrace.StackTraceCompactor;

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

    @NonNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        int attempt = 1;
        while (true) {
            try {
                return chain.proceed(chain.request());
            } catch (IOException e) {
                Log.w(LOG_TAG, "HTTP Call failed on attempt " + attempt + ": " + chain.request().method() + " " + chain.request().url() + "\n" + StackTraceCompactor.getCompactStackTrace(e));
                attempt++;
                if (attempt > maxTries) {
                    throw e;
                }
            }
        }
    }
}
