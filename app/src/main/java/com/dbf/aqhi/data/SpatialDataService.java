package com.dbf.aqhi.data;

import android.content.Context;
import android.util.Log;

import com.dbf.aqhi.api.datamart.DatamartService;
import com.dbf.aqhi.api.datamart.Pollutant;
import com.dbf.aqhi.codec.RawImage;
import com.dbf.aqhi.grib2.Grib2Parser;
import com.dbf.utils.stacktrace.StackTraceCompactor;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class SpatialDataService extends DataService {

    private static final String LOG_TAG = "SpatialDataService";
    private static final Object GLOBAL_SYNC_OBJECT = new Object();
    private static final String CACHE_DIR_NAME = "spatial_data";

    //Use the same buffer size for the ZIP compression and for writing to the filesystem
    private static final int FILE_CACHE_BUFFER_SIZE = 16*1024;
    private static final long MAX_CACHE_DURATION = 1000*60*60*24; //1 day

    private final DatamartService datamartService = new DatamartService();
    private File cacheDir;

    public SpatialDataService(Context context, Runnable onUpdate) {
        super(context, onUpdate);
        initCache();
        Log.i(LOG_TAG, "Spatial data service initialized.");
    }

    private void initCache() {
        cacheDir = new File(context.getCacheDir(), CACHE_DIR_NAME);
        if (!cacheDir.exists() && !cacheDir.mkdirs())
            throw new IllegalStateException("Failed to initialize cache directory: " + cacheDir);
    }

    /**
     * Updates the spatial data (pollutant maps) asynchronously by executing {@link SpatialDataService#updateSync} in a new thread.
     * This may take several minutes to complete depending on the internet connection quality.
     * The optional callback onUpdate will be executed after the update is complete.
     */
    public void update() {
        Log.i(LOG_TAG, "Updating spatial data.");
        //We want to perform these calls not on the main thread
        new Thread(() -> {
            try {
                updateSync();
                onUpdate();
            } catch (Throwable t) { //Catch all
                Log.e(LOG_TAG, "Failed to update spatial data.\n" + StackTraceCompactor.getCompactStackTrace(t));
            }
        }).start();
    }

    /**
     * Updates the spatial data for all of the pollutants, if the most recent data isn't already present.
     * The data is stored on disk.
     * The update is executed synchronously and may take several minutes to complete depending on the internet connection quality.
     */
    private void updateSync() {

        if(!isInternetAvailable()) {
            Log.i(LOG_TAG, "Network is down. Skipping spatial update.");
            return;
        }

        //Load the data one-by-one so we don't run out of memory
        //This code is stateful, we don't want to run multiple updates at the same time
        synchronized (GLOBAL_SYNC_OBJECT) {
            for(Pollutant pollutant : Pollutant.values()){
                downloadData(pollutant);
            }
        }
    }

    private void downloadData(Pollutant pollutant) {
        //TODO: Check if the data hasn't changed since we last downloaded it
        //TODO: Record the time the model was last run at, to display it in the UI

        //Check how old the last file is and delete it if it's more than 24 hours old
        //We want to do this as cleanup, regardless if new data is downloaded or not
        File cacheFile = new File(cacheDir, pollutant.getValue());
        if(cacheFile.exists() && (System.currentTimeMillis() - cacheFile.lastModified()) > MAX_CACHE_DURATION) {
            cacheFile.delete();
        }

        RawImage rawImg = null;
        try {
            rawImg = Grib2Parser.parse(datamartService.getObservation(pollutant));
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to parse image data for " + pollutant + " from Datamart.\n" + StackTraceCompactor.getCompactStackTrace(e));
            return;
        }

        //Sanity Check
        if (null == rawImg || null == rawImg.pixels || rawImg.pixels.length < 1) {
            Log.e(LOG_TAG, "No image data returned for " + pollutant + " from Datamart.");
            return;
        }

        if (rawImg.pixels.length != rawImg.width * rawImg.height) {
            Log.e(LOG_TAG, "Invalid image data returned for " + pollutant + " from Datamart.");
            return;
        }

        writeCacheFile(rawImg, cacheFile);
    }

    private void writeCacheFile(RawImage rawImg, File cacheFile) {
        try {
            //We have valid data, so delete the existing file before writing the new one
            if(cacheFile.exists()) {
                Log.i(LOG_TAG, "Deleting existing cache file " + cacheFile.getAbsolutePath() + ".");
                cacheFile.delete();
            }

            Log.i(LOG_TAG, "Saving data to cache file " + cacheFile.getAbsolutePath() + ".");

            //Save to cache
            try (DataOutputStream dos =
                         new DataOutputStream(
                                 new GZIPOutputStream(
                                         new BufferedOutputStream(
                                                 new FileOutputStream(cacheFile), FILE_CACHE_BUFFER_SIZE), FILE_CACHE_BUFFER_SIZE))) {
                dos.writeInt(rawImg.width);
                dos.writeInt(rawImg.height);
                dos.writeInt(rawImg.pixels.length);
                dos.write(rawImg.pixels);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to update the cache file " + cacheFile.getAbsolutePath() + ".\n" + StackTraceCompactor.getCompactStackTrace(e));
        }
    }

    public RawImage readCachedData(Pollutant pollutant) {
        File cacheFile = new File(cacheDir, pollutant.getValue());
        Log.i(LOG_TAG, "Reading data from cache file " + cacheFile.getAbsolutePath() + ".");

        try {
            if(!cacheFile.exists()) {
                Log.i(LOG_TAG, "Cache file does not exist: " + cacheFile.getAbsolutePath() + ".");
                return null;
            }

            try (DataInputStream dis =
                         new DataInputStream(
                                 new GZIPInputStream(
                                         new BufferedInputStream(
                                                 new FileInputStream(cacheFile), FILE_CACHE_BUFFER_SIZE),FILE_CACHE_BUFFER_SIZE))) {
                final int width   = dis.readInt();
                final int height   = dis.readInt();
                final int dataLength = dis.readInt();

                if (dataLength != width * height) {
                    Log.e(LOG_TAG, "Invalid image data contained in cache file " + cacheFile.getAbsolutePath());
                    return null;
                }
                return new RawImage(width, height, dis.readNBytes(dataLength));
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to read cache file " + cacheFile.getAbsolutePath() + ".\n" + StackTraceCompactor.getCompactStackTrace(e));
            return null;
        }
    }
}