package com.dbf.aqhi.api.geomet.data;

public abstract class Data {
    public String type;
    public String id;

    public abstract DataProperties getProperties();
}
