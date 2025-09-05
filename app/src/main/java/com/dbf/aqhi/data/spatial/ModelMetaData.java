package com.dbf.aqhi.data.spatial;

import java.util.Objects;

public class ModelMetaData {
    private String model;
    private String pollutant;
    private String date;
    private String modelRunTime;
    private String hour;

    public ModelMetaData() {}

    public ModelMetaData(String model, String pollutant, String date, String modelRunTime, String hour) {
        this.model = model;
        this.pollutant = pollutant;
        this.date = date;
        this.modelRunTime = modelRunTime;
        this.hour = hour;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPollutant() {
        return pollutant;
    }

    public void setPollutant(String pollutant) {
        this.pollutant = pollutant;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getModelRunTime() {
        return modelRunTime;
    }

    public void setModelRunTime(String modelRunTime) {
        this.modelRunTime = modelRunTime;
    }

    public String getHour() {
        return hour;
    }

    public void setHour(String hour) {
        this.hour = hour;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ModelMetaData that = (ModelMetaData) o;
        return Objects.equals(model, that.model)
                && Objects.equals(pollutant, that.pollutant)
                && Objects.equals(date, that.date)
                && Objects.equals(modelRunTime, that.modelRunTime)
                && Objects.equals(hour, that.hour);
    }

    @Override
    public int hashCode() {
        return Objects.hash(model, pollutant, date, modelRunTime, hour);
    }
}
