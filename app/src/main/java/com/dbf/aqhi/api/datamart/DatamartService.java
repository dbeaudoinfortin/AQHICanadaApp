package com.dbf.aqhi.api.datamart;

import android.util.Log;

import com.dbf.aqhi.api.APIService;
import com.dbf.utils.stacktrace.StackTraceCompactor;

import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import okhttp3.Request;
import okhttp3.Response;

public class DatamartService extends APIService {

    private static final String LOG_TAG = "DatamartService";

    private static final DateTimeFormatter DATAMART_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATAMART_HOUR_FORMAT = DateTimeFormatter.ofPattern("HH");

    private static final String DATAMART_BASE_URL = "https://dd.weather.gc.ca";
    private static final String DATAMART_SUB_DIR = "WXO-DD";

    private static final String RAQDPS_MODEL = "RAQDPS";
    private static final String RAQDPS_DIR = "model_raqdps/10km/grib2";
    private static final String RAQDPS_FILE_TRANSFORM = "_RLatLon0.09_PT";
    private static final String RAQDPS_FILE_SUFFIX = "H.grib2";

    private static final String RDAQA_MODEL = "RDAQA";
    private static final String RDAQA_DIR = "model_rdaqa/10km";
    private static final String RDAQA_FILE_SUFFIX = "_RLatLon0.09_PT0H.grib2";

    public DatamartData getObservation(Pollutant pollutant) {
        return getObservation(pollutant, false);
    }

    public DatamartData getObservation(Pollutant pollutant, boolean metaOnly) {
        return getRDAQAObservation(pollutant.getValue(), false, metaOnly);
    }

    public DatamartData getForecast(Pollutant pollutant) {
        return getForecast(pollutant, false);
    }

    public DatamartData getForecast(Pollutant pollutant, boolean metaOnly) {
        return getRAQDPSForecast(pollutant.getValue(), metaOnly);
    }

    private DatamartData getRDAQAObservation(String pollutant, boolean allowPrelim, boolean metaOnly) {
        //Preliminary results are available 1 hour later and final results are available 2 hours later
        ZonedDateTime modelDate = ZonedDateTime.now(ZoneOffset.UTC);

        //We try up to 3 hours before giving up
        for(int i =0; i < 3; i++) {
            modelDate = modelDate.minusHours(1);

            DatamartData data = getRDAQAObservation(pollutant, modelDate, false, false, metaOnly);
            if(null != data) return data;

            if(allowPrelim) { //Try again, with prelim data
                data = getRDAQAObservation(pollutant, modelDate, true, false, metaOnly);
                if(null != data) return data;
            }
        }
        return null;
    }

    private DatamartData getRDAQAObservation(String pollutant, ZonedDateTime modelDate, boolean prelim, boolean firework, boolean metaOnly) {
        final String model = prelim ? RDAQA_MODEL + "-Prelim" : (firework ? RDAQA_MODEL + "-FW" : RDAQA_MODEL);
        return getData(RDAQA_MODEL, null, RDAQA_DIR, RDAQA_FILE_SUFFIX, pollutant, modelDate.format(DATAMART_DATE_FORMAT), null, modelDate.format(DATAMART_HOUR_FORMAT), null, metaOnly);
    }

    private DatamartData getRAQDPSForecast(String pollutant, boolean metaOnly) {
        ZonedDateTime modelDate = ZonedDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC);
        DatamartData data = getRAQDPSForecast(pollutant, modelDate, metaOnly);
        if(null == data) {
            //Try the previous day
            modelDate.minusDays(1);
            data = getRAQDPSForecast(pollutant, modelDate, metaOnly);
        }
        return data;
    }

    private DatamartData getRAQDPSForecast(String pollutant, ZonedDateTime modelDate, boolean metaOnly) {
        //The model is run at 0 and 12 hours UTC each day. Try the 12 model run first, if it exists.
        DatamartData data = getRAQDPSForecast(pollutant, modelDate.plusHours(12), "12", metaOnly);
        if(null == data) {
            //Try the 0 model run now
            data = getRAQDPSForecast(pollutant, modelDate, "00", metaOnly);
        }
        return data;
    }

    private DatamartData getRAQDPSForecast(String pollutant, ZonedDateTime modelDate, String modelRunTime, boolean metaOnly) {
        long modelOffset = determineModelTime(modelDate);
        if(modelOffset < 0) {
            //Model hasn't run yet!
            return null;
        }
        final String hour = StringUtils.leftPad("" + modelOffset,3,'0');
        final String fileSuffix = RAQDPS_FILE_TRANSFORM + hour +  RAQDPS_FILE_SUFFIX;
        final String dateString = modelDate.format(DATAMART_DATE_FORMAT);
        return getData(RAQDPS_MODEL, DATAMART_SUB_DIR, RAQDPS_DIR, fileSuffix, pollutant, dateString, dateString, modelRunTime, hour, metaOnly);
    }

    private long determineModelTime(ZonedDateTime modelDate) {
        return Duration.between(modelDate, ZonedDateTime.now(ZoneOffset.UTC)).toHours(); //TODO: Round to the closest hour. 55 minutes past the hour shouldn't result in the previous hour
    }

    private DatamartData getData(String model, String subDir, String modelDir, String fileSuffix, String pollutant, String date, String dateDir, String modelRunTime, String hour, boolean metaOnly) {
        //Example URL: https://dd.weather.gc.ca/20250802/WXO-DD/model_raqdps/10km/grib2/12/025/20250802T12Z_MSC_RAQDPS_PM10-WildfireSmokePlume_Sfc_RLatLon0.09_PT025H.grib2
        //Or           https://dd.weather.gc.ca/20250803/WXO-DD/model_rdaqa/10km/13/20250803T13Z_MSC_RDAQA_PM2.5_Sfc_RLatLon0.09_PT0H.grib2
        final String fileName = buildFileName(date, modelRunTime, model, pollutant, fileSuffix);
        final String url = buildUrl(DATAMART_BASE_URL, dateDir, subDir, modelDir, modelRunTime, hour, fileName);
        final byte[] rawData = callDatamart(url, metaOnly);
        if (null == rawData) return null;
        return new DatamartData(model, pollutant, date, modelRunTime, hour, rawData);
    }

    private byte[] callDatamart(String url, boolean metaOnly) {
        Log.i(LOG_TAG, "Calling the Datamart HTTP service. URL: " + url);
        try (Response response = client.newCall(new Request.Builder()
                    .method(metaOnly ? "HEAD" : "GET", null)
                    .url(url)
                    .build())
                .execute()) {

            if (response.code() == 404) {
                Log.i(LOG_TAG, "No data returned from Datamart (404). URL: " + url);
                return null;
            }
            if (!response.isSuccessful()) {
                //Service might be down, might not be our fault.
                Log.w(LOG_TAG, "Call to Datamart failed. URL: " + url + ". HTTP Code: " + response.code());
                return null;
            }

            if(metaOnly) return new byte[0]; //HEAD requests have no body

            if (response.body() == null) {
                Log.e(LOG_TAG, "Call to Datamart API failed. URL: " + url + ". Empty response body.");
                return null;
            }
            Log.i(LOG_TAG, "Data successfully returned from Datamart. Content Length: " + response.body().contentLength() + ",  URL: " + url);
            //TODO: Manually read the input stream so that we can protect against reading too many bytes.
            return response.body().bytes();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to call Datamart. URL: " + url + "\n" + StackTraceCompactor.getCompactStackTrace(e));
            return null;
        }
    }

    private static String buildFileName(String date, String modelRunTime, String model, String pollutant, String fileSuffix) {
        StringBuilder sb = new StringBuilder();
        sb.append(date)
            .append("T")
            .append(modelRunTime)
            .append("Z_MSC_")
            .append(model)
            .append("_")
            .append(pollutant)
            .append(fileSuffix);
        return sb.toString();
    }

    private static String buildUrl(String baseUrl, String dateDir, String subDir, String modelDir, String modelRunTime, String hour, String fileName) {
        StringBuilder sb = new StringBuilder();
        sb.append(baseUrl)
            .append("/")
            .append(dateDir == null ? "" : dateDir)
            .append(dateDir == null ? "" : "/")
            .append(subDir == null ? "" : subDir)
            .append(subDir == null ? "" : "/")
            .append(modelDir == null ? "" : modelDir)
            .append(modelDir == null ? "" :"/")
            .append(modelRunTime)
            .append("/")
            .append(hour == null ? "" : hour)
            .append(hour == null ? "" : "/")
            .append(fileName);
        return sb.toString();
    }
}