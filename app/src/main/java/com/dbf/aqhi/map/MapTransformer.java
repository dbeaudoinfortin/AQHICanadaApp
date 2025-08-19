package com.dbf.aqhi.map;

import android.util.Pair;

import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;

/**
 * Transform latitude and longitude coordinates into X,Y map pixel coordinates.
 * This works only for Lambert Conformal Conic projections
 */
public class MapTransformer {
    private static final double quarterPI = Math.PI/4;

    //Lambert projection parameters for NAD83 EPSG:3978
    //Parameters are based on the CBCT3978 map data
    //https://maps-cartes.services.geo.ca/server2_serveur2/rest/services/BaseMaps/CBCT3978/MapServer
    private static final double phi0 = Math.toRadians(49.0);         //Latitude of Origin (radians)
    private static final double lambda0 = Math.toRadians(-95.0);     //Central Meridian (radians)
    private static final double phi1 = Math.toRadians(49.0);         //Standard Parallel 1 (radians)
    private static final double phi2 = Math.toRadians(77.0);         //Standard Parallel 2 (radians)
    private static final double a = 6378137;                         //Semi-major Axis
    private static final double f = 1.0 / 298.257222101;             //Flattening
    private static final double e = Math.sqrt((2 * f) - (f*f));      //Eccentricity
    private static final double falseEasting = 0;
    private static final double falseNorthing = 0;

    //Computed Lambert projection constants (using ellipsoidal definitions)
    private static final double n;
    private static final double F;
    private static final double rho0;
    private static final double aF;

    //I'm using 3 well spaced-apart know coordinates in order to relatively scale and position the map.
    //In the form of Pair<[lat,lon],[x,y]>
    private static final Pair<double[],int[]> ottawa   = new Pair<double[], int[]>(new double[]{45.43433, -75.676}, new int[]{25059, 25960});
    private static final Pair<double[],int[]> alert    = new Pair<double[], int[]>(new double[]{82.45, -62.5}, new int[]{18398, 399});
    private static final Pair<double[],int[]> victoria = new Pair<double[], int[]>(new double[]{48.44, -123.416}, new int[]{2245, 22295});

    private static double x0, x1, x2; //Affine Transformation X constants
    private static double y0, y1, y2; //Affine Transformation Y constants

    static {
        //Ellipsoidal LCC (2SP), GRS80 using φ1, φ2, φ0, λ0
        final double m1 = mFromPhi(phi1);
        final double m2 = mFromPhi(phi2);
        final double t1 = tFromPhi(phi1);
        final double t2 = tFromPhi(phi2);
        final double t0 = tFromPhi(phi0);

        //Calculate the cone constant n.
        n = (Math.log(m1) - Math.log(m2)) / (Math.log(t1) - Math.log(t2));

        //Calculate scaling constant F.
        F = m1 / (n * Math.pow(t1, n));

        //Save a bit of calculation later
        aF = a*F;

        //Calculate rho0 for the latitude of origin.
        rho0 = aF * Math.pow(t0, n);

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

        final double det = x1 * y2 - x2 * y1;
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
        final double t = tFromPhi(phi);
        final double rho = aF * Math.pow(t, n);
        final double theta = n * (lambda - lambda0);
        final double x = falseEasting + rho * Math.sin(theta);
        final double y = falseNorthing + rho0 - rho * Math.cos(theta);
        return new Pair<Double, Double>(x, y);
    }

    /**
     * Inverts a Lambert projected pair back to geographic (lat, lon) coordinates.
     * @param x X Coordinate (fractional).
     * @param y Y Coordinate (fractional).
     * @return Pair<Double, Double> the resulting lat,lon pair.
     */
    private static Pair<Double, Double> lambertInverse(double x, double y) {
        y = rho0 - y;

        final double rho = Math.copySign(Math.hypot(x, y), n); //preserve sign of n
        final double theta = Math.atan2(x, y);

        // t = (ρ / (a*F))^(1/n)
        final double t = Math.pow(rho / aF, 1.0 / n);

        // φ from t via iteration (ellipsoidal case)
        final double phi = phiFromT_fast(t);

        // λ = λ0 + θ/n
        final double lambda = lambda0 + (theta / n);
        double lon = Math.toDegrees(lambda);
        double lat = Math.toDegrees(phi);

        // Normalize lon to [-180, 180)
        if (lon >= 180.0) lon -= 360.0;
        if (lon <  -180.0) lon += 360.0;

        return new Pair<Double, Double>(lat, lon);
    }

    /**
     * Transforms a latitude & longitude coordinate to pixel coordinates.
     * The coordinate is first projected using a Lambert projection and then uses affine transformation
     * to get the proper scaling and positioning.
     *
     * @param lat Latitude, in degrees.
     * @param lon Longitude, in degrees.
     * @return Pair<Integer, Integer> the resulting x,y pixel coordinates.
     */
    public static Pair<Integer, Integer> transformLatLon(double lat, double lon) {
        final Pair<Double, Double> lambertCoordinates = lambertProjection(lat, lon);
        final double x = x0 + x1 * lambertCoordinates.first + x2 * lambertCoordinates.second;
        final double y = y0 + y1 * lambertCoordinates.first + y2 * lambertCoordinates.second;
        return new Pair<Integer, Integer>((int) Math.round(x), (int) Math.round(y));
    }

    /**
     * Transforms X & Y pixel coordinate to latitude & longitude coordinates.
     *
     * @param x X in pixels
     * @param y Y in pixels
     * @return Pair<Double, Double> the resulting the resulting lat,lon pair.
     */
    public static Pair<Double, Double> transformXY(Double x, Double y) {
        // Invert affine transform
        final double dx = x - x0;
        final double dy = y - y0;

        //TODO: Calculate these only once
        final double det = x1 * y2 - x2 * y1;
        final double inv11 =  y2 / det;
        final double inv12 = -x2 / det;
        final double inv21 = -y1 / det;
        final double inv22 =  x1 / det;

        final double xLambert = inv11 * dx + inv12 * dy; //Lambert X
        final double yLambert = inv21 * dx + inv22 * dy; //Lambert Y

        return lambertInverse(xLambert, yLambert);
    }

    /* t(φ) = tan(π/4 - φ/2) / [((1 - e sinφ)/(1 + e sinφ))^(e/2)] */
    private static double tFromPhi(double phi) {
        final double esin = e * Math.sin(phi);
        final double ratio = (1.0 - esin) / (1.0 + esin);
        return Math.tan(quarterPI - phi * 0.5) / Math.pow(ratio, e * 0.5);
    }

    /** m(φ) = cosφ / sqrt(1 - e^2 sin^2φ) */
    private static double mFromPhi(double phi) {
        final double s = Math.sin(phi);
        return Math.cos(phi) / Math.sqrt(1.0 - (e * e) * s * s);
    }

    //Precomputed powers of e for performance optimization
    private static final double e2 = e * e;
    private static final double e4 = e2 * e2;
    private static final double e6 = e4 * e2;
    private static final double e8 = e4 * e4;

    //Precomputed conformal-to-geodetic coefficients (EPSG/Snyder series) for performance optimization
    private static final double C2 =  0.5     * e2 +  5.0/24.0 * e4 + 1.0/12.0 * e6 + 13.0/360.0   * e8;
    private static final double C4 =  7.0/48.0* e4 + 29.0/240.0* e6 + 811.0/11520.0 * e8;
    private static final double C6 =  7.0/120.0*e6 + 81.0/1120.0* e8;
    private static final double C8 = 4279.0/161280.0 * e8;

    //Since we only need about 100m of precision, we can use some fast approximation
    private static double phiFromT_fast(double t) {
        final double chi = (Math.PI * 0.5) - 2.0 * Math.atan(t);
        final double s = Math.sin(chi);
        final double c = Math.cos(chi);
        final double s2 = 2.0 * s * c;
        final double c2 = c * c - s * s;
        final double s4 = 2.0 * s2 * c2;
        final double c4 = c2 * c2 - s2 * s2;
        final double s6 = 2.0 * s4 * c2 - s2;   //sin(6χ) = 2 sin(4χ)cos(2χ) − sin(2χ)
        final double s8 = 2.0 * s4 * c4;        //sin(8χ) = 2 sin(4χ)cos(4χ)
        return chi + C2 * s2 + C4 * s4 + C6 * s6 + C8 * s8;
    }
}
