package com.dbf.aqhi.data.spatial;

import android.content.Context;
import android.util.Log;

import com.dbf.aqhi.api.datamart.DatamartData;
import com.dbf.aqhi.api.datamart.DatamartService;
import com.dbf.aqhi.api.datamart.Pollutant;
import com.dbf.aqhi.data.DataService;
import com.dbf.aqhi.jpeg.RawImage;
import com.dbf.aqhi.grib2.Grib2;
import com.dbf.aqhi.grib2.Grib2Parser;
import com.dbf.utils.stacktrace.StackTraceCompactor;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class SpatialDataService extends DataService {

    private static final String LOG_TAG = "SpatialDataService";

    //Prevents multiple simultaneous remote updates
    private static final Object UPDATE_SYNC_OBJECT = new Object();

    //Keeps the file system cache in sync with shared preferences
    private static final Map<Pollutant, Object> POLLUTANT_SYNC_OBJECTS;

    //Keeps track of all the pollutants that are available
    private static LinkedHashSet<Pollutant> loadedPollutants;
    private static final Object POLLUTANT_LIST_SYNC_OBJECT = new Object();

    private static final long DATA_VALIDITY_DURATION = 1000*60*60*2; //2 hours
    //File system cache
    //Use the same buffer size for the ZIP compression and for writing to the filesystem
    private static final int    FILE_CACHE_BUFFER_SIZE = 16*1024;
    private static final long   MAX_CACHE_DURATION = 1000*60*60*24; //1 day
    private static final String CACHE_DIR_NAME = "spatial_data";

    //Shared preferences keys
    private static final String SPATIAL_DATA_KEY = "SPATIAL_DATA_VAL";
    private static final String SPATIAL_DATA_TS_KEY = "SPATIAL_DATA_TS";

    private static final Type gsonSpatialDataType = new TypeToken<SpatialData>(){}.getType();

    private final DatamartService datamartService = new DatamartService();
    private File cacheDir;

    static {
        //Don't need a concurrent map since it will never be modified afterwards
        POLLUTANT_SYNC_OBJECTS = new HashMap<>();
        for (Pollutant pollutant : Pollutant.values()) {
            POLLUTANT_SYNC_OBJECTS.put(pollutant, new Object());
        }
    }

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
        synchronized (UPDATE_SYNC_OBJECT) {
            for(Pollutant pollutant : Pollutant.values()){
                updateSpatialData(pollutant);
            }
        }
    }

    /**
     * Retrieves pre-download spatial data, if it exists and not stale.
     * The meta data is retrieved from shared preferences and the image data is retrieved from the
     * Android file system cache.
     *
     * @param pollutant
     * @return SpatialData
     */
    public SpatialData getSpatialData(Pollutant pollutant) {
        synchronized (POLLUTANT_SYNC_OBJECTS.get(pollutant)) {
            SpatialData spatialData = getSpatialMetaData(pollutant);
            if(null == spatialData) return null;

            //Image data is stored in the filesystem cache
            RawImage rawImage = getCachedData(pollutant);
            if(null == rawImage) {
                //Cache was cleared by the system or is too old
                //We need to also delete the metadata
                clearSpatialMetaData(pollutant);
                return null;
            }
            spatialData.getGrib2().setRawImage(rawImage);
            return spatialData;
        }
    }

    /**
     * Retrieves just the spatial meta data from shared preferences.
     *
     * @param pollutant
     * @return SpatialData
     */
    public SpatialData getSpatialMetaData(Pollutant pollutant) {
        synchronized (POLLUTANT_SYNC_OBJECTS.get(pollutant)) {
            //Determine if the data is fresh enough
            long ts = sharedPreferences.getLong(SPATIAL_DATA_TS_KEY + "_" + pollutant, Integer.MIN_VALUE);
            if(System.currentTimeMillis() - ts > DATA_VALIDITY_DURATION) {
                return null; //Data is too old or missing
            }

            String spatialString = sharedPreferences.getString(SPATIAL_DATA_KEY + "_" + pollutant, null);
            if (null == spatialString || spatialString.isEmpty()) {
                //We are in an inconsistent state. Clear the data.
                clearSpatialMetaData(pollutant);
                return null;
            }

            try {
                //Metadata is stored in the shared preferences DB
                return gson.fromJson(spatialString, gsonSpatialDataType);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to read the saved spatial data for " + pollutant + ": " + spatialString, e);
                //No point keeping this bad data
                clearSpatialMetaData(pollutant);
                return null;
            }
        }
    }

    /**
     *  Saves the spatial data.
     *  Image data is saved to the local file system cache and
     *  meta data is saved to the shared preferences.
     *
     * @param spatialData
     * @param pollutant
     */
    private void writeSpatialData(SpatialData spatialData, Pollutant pollutant) {
        synchronized (POLLUTANT_SYNC_OBJECTS.get(pollutant)) {
            if (null == spatialData || null == spatialData.getGrib2() || null == spatialData.getGrib2().getRawImage()) {
                clearSpatialMetaData(pollutant);
                return;
            }

            //We don't want to encode the raw image data as JSON since it's much too large
            //Extract it from the metadata
            RawImage rawImage = spatialData.getGrib2().getRawImage();
            spatialData.getGrib2().setRawImage(null);

            //Save the image cache first to reduce the race condition
            //No need for a sync block though
            writeCacheFile(rawImage, pollutant);

            final String prefDataKey = SPATIAL_DATA_KEY    + "_" + pollutant;
            final String prefTSKey   = SPATIAL_DATA_TS_KEY + "_" + pollutant;
            sharedPreferences.edit()
                    .putString(prefDataKey, gson.toJson(spatialData, gsonSpatialDataType))
                    .putLong(prefTSKey, System.currentTimeMillis())
                    .apply();

            addLoadedPollutant(pollutant);
        }
    }

    /**
     * Removed the spatial meta data from the shared preferences.
     * Note: This method should be externally synchronized.
     *
     * @param pollutant
     */
    private void clearSpatialMetaData(Pollutant pollutant) {
        final String prefDataKey = SPATIAL_DATA_KEY + "_" + pollutant;
        final String prefTSKey = SPATIAL_DATA_TS_KEY + "_" + pollutant;
        sharedPreferences.edit()
                .remove(prefDataKey)
                .remove(prefTSKey)
                .apply();

        removeLoadedPollutant(pollutant);
    }

    /**
     * Updates the locally stored spatial data, if needed, using the latest remote data.
     *
     * @param pollutant
     */
    private void updateSpatialData(Pollutant pollutant) {
        SpatialData oldSpatialData;

        synchronized (POLLUTANT_SYNC_OBJECTS.get(pollutant)) {
            //Clear out old data so we don't waste disk space.
            //This is just a precaution
            final File cacheFile = new File(cacheDir, pollutant.getDatamartForecastName());
            try {
                checkCacheFile(cacheFile);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Failed to inspect cache file " + cacheFile.getAbsolutePath() + ".\n" + StackTraceCompactor.getCompactStackTrace(e));
            }

            //Determine if we have fresh data already
            //Don't bother checking for new data if what we have is less than 5 minutes old
            long ts = sharedPreferences.getLong(SPATIAL_DATA_TS_KEY + "_" + pollutant, Integer.MIN_VALUE);
            if(System.currentTimeMillis() - ts <= DATA_REFRESH_MIN_DURATION) {
                return; //We already have fresh data
            }

            //We need to call the remote API to look for fresh data.
            //Since the image data is large, we just check the meta data first to see if data hasn't
            //changed since we last downloaded it.
            oldSpatialData = getSpatialMetaData(pollutant);
        }

        if (null == oldSpatialData) {
            //We don't have anything saved for this pollutant, or it's too old. Don't bother doing a HEAD request.
            fetchLatestSpatialData(pollutant);
            return;
        }

        //Do only an HTTP HEAD request and compare the latest that we find with what we have now
        DatamartData newData = datamartService.getObservation(pollutant, true);
        if(null == newData) {
            //There is no data available right now, keep what we have even if it's out of date
            return;
        }

        if(!newData.getModel().equals(oldSpatialData.getModel())) {
            //Assume that the latest data is always fresher than what we have now.
            //Note: the data may have changed again since we did the HEAD call.
            fetchLatestSpatialData(pollutant);
        } else {
            //We confirmed that the data we are storing is still current, so update the timestamp
            updateSpatialDataTimestamp(pollutant);
        }
    }

    /**
     * Updates only the the timestamp for the given pollutant.
     *
     * @param pollutant
     */
    private void updateSpatialDataTimestamp(Pollutant pollutant) {
        synchronized (POLLUTANT_SYNC_OBJECTS.get(pollutant)) {
            final String prefTSKey = SPATIAL_DATA_TS_KEY + "_" + pollutant;
            sharedPreferences.edit()
                    .putLong(prefTSKey, System.currentTimeMillis())
                    .apply();
        }
    }

    /**
     * Retrieve spatial data, both meta data and image data, from the remote API.
     * Saves the image data to the local file system cache and the meta data to the shared preferences.
     *
     * @param pollutant
     */
    private void fetchLatestSpatialData(Pollutant pollutant) {
        final DatamartData datamartData = datamartService.getObservation(pollutant);
        if(null == datamartData) return; //There is no data available right now, keep what we have even if it's out of date

        Grib2 grib2 = null;
        try {
            grib2 = Grib2Parser.parse(datamartData.getRawData(), pollutant);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to parse grib2 data for " + pollutant + " from Datamart.\n" + StackTraceCompactor.getCompactStackTrace(e));
            return;
        }

        //Sanity checks
        if(null == grib2) {
            Log.e(LOG_TAG, "Failed to parse grib2 data for " + pollutant + " from Datamart.");
            return;
        }
        final RawImage rawImg = grib2.getRawImage();
        if (null == rawImg || null == rawImg.pixels || rawImg.pixels.length < 1) {
            Log.e(LOG_TAG, "No image data returned for " + pollutant + " from Datamart.");
            return;
        }
        if (rawImg.pixels.length != rawImg.width * rawImg.height) {
            Log.e(LOG_TAG, "Invalid image data returned for " + pollutant + " from Datamart.");
            return;
        }

        //The image data was successfully parsed, we can now save it.
        writeSpatialData(new SpatialData(datamartData.getModel(), grib2), pollutant);
    }

    /**
     * Checks a cached image file and deletes it if it is older than MAX_CACHE_DURATION.
     * @param cacheFile
     */
    private void checkCacheFile(File cacheFile) throws IOException {
        //Check how old the last file is and delete it if it's more than 24 hours old
        //We want to do this as cleanup, regardless if new data is downloaded or not
        if(cacheFile.exists() && (System.currentTimeMillis() - cacheFile.lastModified()) > MAX_CACHE_DURATION) {
            Log.i(LOG_TAG, "Cache file is too old. Deleting " + cacheFile.getAbsolutePath() + ".");
            cacheFile.delete();
        }
    }

    /**
     * Saves image data to the file system cache.
     *
     * @param rawImg data
     * @param pollutant
     */
    private void writeCacheFile(RawImage rawImg, Pollutant pollutant) {
        final File cacheFile = new File(cacheDir, pollutant.getDatamartForecastName());
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
                for (int i = 0 ; i < rawImg.values.length ; i++) {
                    dos.writeFloat(rawImg.values[i]);
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to update the cache file " + cacheFile.getAbsolutePath() + ".\n" + StackTraceCompactor.getCompactStackTrace(e));
        }
    }

    /**
     * Retrieves cached image data from the file system, if present.
     *
     * @param pollutant
     * @return RawImage data
     */
    private RawImage getCachedData(Pollutant pollutant) {
        final File cacheFile = new File(cacheDir, pollutant.getDatamartForecastName());

        Log.i(LOG_TAG, "Reading data from cache file " + cacheFile.getAbsolutePath() + ".");
        try {
            //Data might be old and stale
            checkCacheFile(cacheFile);

            if(!cacheFile.exists()) {
                Log.i(LOG_TAG, "Cache file does not exist: " + cacheFile.getAbsolutePath() + ".");
                return null;
            }

            try (DataInputStream dis =
                         new DataInputStream(
                                 new GZIPInputStream(
                                         new BufferedInputStream(
                                                 new FileInputStream(cacheFile), FILE_CACHE_BUFFER_SIZE),FILE_CACHE_BUFFER_SIZE))) {
                final int width = dis.readInt();
                final int height = dis.readInt();
                final int dataLength = dis.readInt();

                if (dataLength != width * height) {
                    Log.e(LOG_TAG, "Invalid image data contained in cache file " + cacheFile.getAbsolutePath());
                    return null;
                }
                final byte[] pixels = dis.readNBytes(dataLength);
                float[] values = new float[dataLength];
                try {
                    for (int i = 0; i < dataLength; i++) {
                        values[i] = dis.readFloat();
                    }
                } catch (EOFException e) {
                    //Backwards compatibility if raw values are not present
                    values = null;
                }
                return new RawImage(width, height, pixels, values);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to read cache file " + cacheFile.getAbsolutePath() + ".\n" + StackTraceCompactor.getCompactStackTrace(e));
            return null;
        }
    }

    private void addLoadedPollutant(Pollutant pollutant){
        synchronized(POLLUTANT_LIST_SYNC_OBJECT) {
            if(null == loadedPollutants) loadPollutants();
            loadedPollutants.add(pollutant);
        }
    }

    private void removeLoadedPollutant(Pollutant pollutant){
        synchronized(POLLUTANT_LIST_SYNC_OBJECT) {
            if(null == loadedPollutants) return;
            loadedPollutants.remove(pollutant.getDisplayName());
        }
    }

    private void loadPollutants() {
        //Build a list of all the available pollutants that aren't expired
        loadedPollutants = new LinkedHashSet<Pollutant>();

        long currentTime = System.currentTimeMillis();
        for(Pollutant pollutant : Pollutant.values()){
            long ts = sharedPreferences.getLong(SPATIAL_DATA_TS_KEY + "_" + pollutant, Integer.MIN_VALUE);
            if(currentTime - ts > DATA_VALIDITY_DURATION) continue;

            loadedPollutants.add(pollutant);
        }
    }

    public LinkedHashSet<Pollutant> getLoadedPollutants(){
        synchronized(POLLUTANT_LIST_SYNC_OBJECT) {
            if(null == loadedPollutants) loadPollutants();
            return new LinkedHashSet<Pollutant>(loadedPollutants); //Always clone
        }
    }

    public void clearLoadedPollutants(){
        synchronized(POLLUTANT_LIST_SYNC_OBJECT) {
            loadedPollutants = null;
        }
    }
}