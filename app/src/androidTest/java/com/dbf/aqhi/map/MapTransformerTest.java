package com.dbf.aqhi.map;

import static org.junit.Assert.assertEquals;

import android.util.Pair;
import org.junit.Test;

public class MapTransformerTest {

    private static final double DEGREES_TOLERANCE = 1e-2;

    double[][] testPoints = new double[][]{
            {45.4215, -75.6972},  // Ottawa
            {43.6532, -79.3832},  // Toronto
            {46.8139, -71.2080},  // Québec City
            {49.2827, -123.1207}, // Vancouver
            {53.5461, -113.4938}  // Edmonton
    };

    @Test
    public void testControlPoints() {
        for (double[] p : testPoints) {
            double lat = p[0];
            double lon = p[1];

            //Transform to lambert
            Pair<Integer, Integer> xy = MapTransformer.transformLatLon(lat, lon);

            //Transform back to lat,lon
            double[] latLon = new double[2];
            MapTransformer.transformXY((double) xy.first, (double) xy.second, latLon);

            //Compare the two sets of coordinates within a decent tolerance
            assertEquals("lat mismatch", lat, latLon[0], DEGREES_TOLERANCE);
            assertEquals("lon mismatch", lon, latLon[1], DEGREES_TOLERANCE);
        }
    }
}
