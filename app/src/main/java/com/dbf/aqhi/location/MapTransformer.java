package com.dbf.aqhi.location;

import android.util.Pair;

import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;

/**
 * Transform latitude and longitude coordinates into X,Y map pixel coordinates.
 * This works only for Lambert Conformal Conic projections
 */
public class MapTransformer {
    //Lambert projection parameters for NAD83
    //Parameters are based on the CBCT3978 map data
    //https://maps-cartes.services.geo.ca/server2_serveur2/rest/services/BaseMaps/CBCT3978/MapServer
    private static final double phi0 = Math.toRadians(49.0);         //latitude_of_origin (radians)
    private static final double lambda0 = Math.toRadians(-95.0);      //central_meridian (radians)
    private static final double phi1 = Math.toRadians(49.0);         //standard_parallel_1 (radians)
    private static final double phi2 = Math.toRadians(77.0);         //standard_parallel_2 (radians)
    private static final double falseEasting = 0;
    private static final double falseNorthing = 0;

    // Computed Lambert projection constants (using a spherical approximation)
    private static final double n;
    private static final double F;
    private static final double rho0;

    //I'm using 3 well spaced-apart know coordinates in order to relatively scale and position the map.
    //In the form of Pair<[lat,lon],[x,y]>
    private static final Pair<double[],int[]> ottawa   = new Pair<double[], int[]>(new double[]{45.43433, -75.676}, new int[]{25059, 25960});
    private static final Pair<double[],int[]> alert    = new Pair<double[], int[]>(new double[]{82.45, -62.5}, new int[]{18398, 399});
    private static final Pair<double[],int[]> victoria = new Pair<double[], int[]>(new double[]{48.44, -123.416}, new int[]{2245, 22295});

    private static double x0, x1, x2; //Affine Transformation X constants
    private static double y0, y1, y2; //Affine Transformation Y constants

    static {
        //Calculate the cone constant n.
        n = Math.log(Math.cos(phi1) / Math.cos(phi2)) /
                Math.log(Math.tan(Math.PI / 4 + phi2 / 2) / Math.tan(Math.PI / 4 + phi1 / 2));

        //Calculate scaling constant F.
        F = (Math.cos(phi1) * Math.pow(Math.tan(Math.PI / 4 + phi1 / 2), n)) / n;

        //Calculate rho0 for the latitude of origin.
        rho0 = F / Math.pow(Math.tan(Math.PI / 4 + phi0 / 2), n);
        computeControlPoints(ottawa, alert, victoria);
    }

    /**
     * Initialize the parameters for the affine transformation equations
     *
     * @param controlPoints
     */
    private static void computeControlPoints(Pair<double[],int[]>... controlPoints) {
        double[][] controlMatrix = new double[controlPoints.length][controlPoints.length];
        double[] x = new double[controlPoints.length];
        double[] y = new double[controlPoints.length];

        //Convert the lat & lon coordinates to x,y coordinates
        //Using the Lambert projection
        for (int i = 0; i < controlPoints.length; i++) {
            Pair<Double, Double> lambertCoordinates = lambertProjection((controlPoints[i].first)[0], (controlPoints[i].first)[1]);
            controlMatrix[i][0] = 1;
            controlMatrix[i][1] = lambertCoordinates.first;
            controlMatrix[i][2] = lambertCoordinates.second;
            x[i] = (controlPoints[i].second)[0];
            y[i] = (controlPoints[i].second)[1];
        }

        //Invert the control matrix
        //Use the Apache Math library instead of implementing this from scratch
        controlMatrix = (new LUDecomposition(MatrixUtils.createRealMatrix(controlMatrix)).getSolver().getInverse()).getData();

        //X
        x0 = controlMatrix[0][0] * x[0] + controlMatrix[0][1] * x[1] + controlMatrix[0][2] * x[2];
        x1 = controlMatrix[1][0] * x[0] + controlMatrix[1][1] * x[1] + controlMatrix[1][2] * x[2];
        x2 = controlMatrix[2][0] * x[0] + controlMatrix[2][1] * x[1] + controlMatrix[2][2] * x[2];

        //Y
        y0 = controlMatrix[0][0] * y[0] + controlMatrix[0][1] * y[1] + controlMatrix[0][2] * y[2];
        y1 = controlMatrix[1][0] * y[0] + controlMatrix[1][1] * y[1] + controlMatrix[1][2] * y[2];
        y2 = controlMatrix[2][0] * y[0] + controlMatrix[2][1] * y[1] + controlMatrix[2][2] * y[2];
    }

    /**
     * Converts a latitude & longitude coordinate to a Lambert-projected
     * x,y coordinate.
     *
     * @param lat Latitude in degrees.
     * @param lon Longitude in degrees.
     * @return Pair<Double, Double> the resulting x,y coordinates (fractional).
     */
    private static Pair<Double, Double> lambertProjection(double lat, double lon) {
        final double phi = Math.toRadians(lat);
        final double lambda = Math.toRadians(lon);
        final double rho = F / Math.pow(Math.tan(Math.PI / 4 + phi / 2), n);
        final double x = falseEasting + rho * Math.sin(n * (lambda - lambda0));
        final double y = falseNorthing + rho0 - rho * Math.cos(n * (lambda - lambda0));
        return new Pair<Double, Double>(x, y);
    }

    /**
     * Transforms a latitude & longitude coordinate to pixel coordinates.
     * The coordinate is first projected using a Lambert projection and then uses affine transformation
     * to get the proper scaling ans positioning.
     *
     * @param lat Latitude, in degrees.
     * @param lon Longitude, in degrees.
     * @return Pair<Integer, Integer> the resulting x,y pixel coordinates.
     */
    public static Pair<Integer, Integer> transform(double lat, double lon) {
        final Pair<Double, Double> lambertCoordinates = lambertProjection(lat, lon);
        final double x = x0 + x1 * lambertCoordinates.first + x2 * lambertCoordinates.second;
        final double y = y0 + y1 * lambertCoordinates.first + y2 * lambertCoordinates.second;
        return new Pair<Integer, Integer>((int) x, (int) y);
    }
}
