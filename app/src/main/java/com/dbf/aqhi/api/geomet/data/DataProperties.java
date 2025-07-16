package com.dbf.aqhi.api.geomet.data;

import java.time.Instant;
import java.util.Date;

public abstract class DataProperties {
    public Double aqhi;
    public String aqhi_type;

    public Date getDate() {
        //ISO 8601 by default
        return Date.from(Instant.parse(getDateTimeString()));
    }

    public abstract String getDateTimeString();
}
