package com.dbf.aqhi.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public abstract class DataService {

    protected static final String GLOBAL_PREF_KEY = "com.dbf.aqhi.service";

    //How long we can display the data on screen before we throw it out because its too old
    protected static final long DATA_VALIDITY_DURATION = 30 * 60 * 1000; //30 minutes in milliseconds

    //How long we need to wait before loading fresh data, so we don't make to many API calls
    protected static final long DATA_REFRESH_MIN_DURATION = 5 * 60 * 1000; //5 minutes in milliseconds

    protected static final Gson gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX") // ISO 8601 format
            .enableComplexMapKeySerialization() //Wow! https://github.com/google/gson/issues/1328
            .create();

    protected final SharedPreferences sharedPreferences;

    //Callback to make after updates are completed.
    private Runnable onUpdate;
    protected final Context context;

    public DataService(Context context, Runnable onUpdate) {
        this.onUpdate = onUpdate;
        this.context = context;
        this.sharedPreferences = context.getSharedPreferences(GLOBAL_PREF_KEY, Context.MODE_PRIVATE);
    }

    protected boolean isInternetAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null)
            return false;

        Network network = connectivityManager.getActiveNetwork();
        if (network == null)
            return false;

        NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);
        if (networkCapabilities == null)
            return false;

        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    protected void onUpdate() {
        if(null != onUpdate) onUpdate.run();
    }

    public void setOnUpdate(Runnable onUpdate) {
        this.onUpdate = onUpdate;
    }

    public abstract void update();
}
