package com.dbf.aqhi.service;

import android.content.Context;
import android.util.Log;

import com.dbf.utils.stacktrace.StackTraceCompactor;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AQHIBackgroundWorker {

    private static final String LOG_TAG = "AQHIBackgroundWorker";

    private static final long UPDATE_INTERVAL = 600; //In seconds, 5 minutes

    private final AQHIService aqhiService;

    private ScheduledExecutorService updateScheduler;

    public AQHIBackgroundWorker(Context context, Runnable onChange) {
        aqhiService = new AQHIService(context, onChange, true);
        initScheduler();
    }

    private synchronized void initScheduler(){
        if (updateScheduler == null || updateScheduler.isShutdown()) {
            updateScheduler = Executors.newScheduledThreadPool(1);
            Log.i(LOG_TAG, "Starting background AQHI worker.");
            updateScheduler.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.i(LOG_TAG, "Running automatic AQHI update in background.");
                        aqhiService.update();
                    } catch (Throwable t) { //Catch all, protect the scheduler
                        Log.e(LOG_TAG, "Failed to run scheduled AQHI update:\n" + StackTraceCompactor.getCompactStackTrace(t));
                    }
                }
                //Update every 10 minutes, starting 10 minutes from now
            }, UPDATE_INTERVAL, UPDATE_INTERVAL, TimeUnit.SECONDS);
        }
    }

    private synchronized void stopScheduler() {
        if (updateScheduler != null && !updateScheduler.isShutdown()) {
            Log.i(LOG_TAG, "Stopping background AQHI worker.");
            updateScheduler.shutdown();
        }
    }

    public AQHIService getAQHIService() {
        return aqhiService;
    }

    /**
     * Forces an update to run immediately.
     */
    public void updateNow() {
        Log.i(LOG_TAG, "Forcing manual AQHI update.");
        aqhiService.update();
    }

    /**
     * Stops the scheduler to stop background data updates.
     */
    public void stop() {
        stopScheduler();
    }

    /**
     * Resumes the scheduler to continue background data updates.
     */
    public void resume() {
        initScheduler();
    }
}
