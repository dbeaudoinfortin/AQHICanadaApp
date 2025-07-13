package com.dbf.aqhi.api.weather.location;

import com.dbf.aqhi.api.weather.alert.AlertResponse;

public class LocationResponse {

    private String cgndb;
    private String aqAlertId;
    private AlertResponse alert;

    public String getCgndb() {
        return cgndb;
    }

    public void setCgndb(String cgndb) {
        this.cgndb = cgndb;
    }

    public String getAqAlertId() {
        return aqAlertId;
    }

    public void setAqAlertId(String aqAlertId) {
        this.aqAlertId = aqAlertId;
    }

    public AlertResponse getAlert() {
        return alert;
    }

    public void setAlert(AlertResponse alert) {
        this.alert = alert;
    }
}
