package com.dbf.aqhi.data.spatial;

import java.util.Objects;

public class ModelMetaData {
    private String model;
    private String pollutant;
    private String modelRunDate;
    private String modelRunHour;
    private String forecastHour;

    public ModelMetaData() {}

    public ModelMetaData(String model, String pollutant, String modelRunDate, String modelRunHour, String forecastHour) {
        this.model = model;
        this.pollutant = pollutant;
        this.modelRunDate = modelRunDate;
        this.modelRunHour = modelRunHour;
        this.forecastHour = forecastHour;
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

    public String getModelRunDate() {
        return modelRunDate;
    }

    public void setModelRunDate(String modelRunDate) {
        this.modelRunDate = modelRunDate;
    }

    public String getModelRunHour() {
        return modelRunHour;
    }

    public void setModelRunHour(String modelRunHour) {
        this.modelRunHour = modelRunHour;
    }

    public String getForecastHour() {
        return forecastHour;
    }

    public void setForecastHour(String forecastHour) {
        this.forecastHour = forecastHour;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ModelMetaData that = (ModelMetaData) o;
        return Objects.equals(model, that.model)
                && Objects.equals(pollutant, that.pollutant)
                && Objects.equals(modelRunDate, that.modelRunDate)
                && Objects.equals(modelRunHour, that.modelRunHour)
                && Objects.equals(forecastHour, that.forecastHour);
    }

    @Override
    public int hashCode() {
        return Objects.hash(model, pollutant, modelRunDate, modelRunHour, forecastHour);
    }
}
