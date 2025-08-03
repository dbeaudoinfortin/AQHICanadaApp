package com.dbf.aqhi.grib2;

public enum Grib2Section {
    INDICATOR(0),
    INDENT(1),
    LOCAL_USE(2),
    GRID_DEF(3),
    PROD_DEF(4),
    DATA_REP(5),
    BITMAP(6),
    DATA(7),
    END(8);

    private final int code;

    Grib2Section(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static Grib2Section fromCode(int code) {
        for (Grib2Section section : values()) {
            if (section.code == code) {
                return section;
            }
        }
        throw new IllegalArgumentException("Bad GRIB2 Section code: " + code);
    }
}
