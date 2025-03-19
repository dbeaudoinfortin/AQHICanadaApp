package com.dbf.aqhi.geomet.data;

import java.time.Instant;
import java.util.Date;

public abstract class DataProperties {
    public Double aqhi;

    public Date getDate() {
        //ISO 8601 by default
        return Date.from(Instant.parse(getDateTimeString()));
    }

    public abstract String getDateTimeString();
}
