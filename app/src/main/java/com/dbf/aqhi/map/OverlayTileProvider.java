package com.dbf.aqhi.map;

import static com.dbf.aqhi.AQHIFeature.MAP_LEVEL_COUNT;
import static com.dbf.aqhi.AQHIFeature.MAP_TILE_SIZE;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import com.dbf.aqhi.Utils;
import com.dbf.aqhi.data.spatial.SpatialData;
import com.dbf.aqhi.grib2.Grib2GridMetaData;

public class OverlayTileProvider {

    //Performance optimizations
    private static final double DEG_TO_RAD = Math.PI / 180.0;
    private static final double RAD_TO_DEG = 180.0 / Math.PI;

    private final double gridScaleXInv; //= 0.09;
    private final double gridScaleYInv; //= 0.09;

    public final int gridWidth; //= 729;
    public final int gridHeight; //= 599;

    private final double rLatZero; //= -31.860001;
    private final double rLonZero; //= -39.537223;

    private final double rLatNorthPole; //= 31.758312225341797;
    private final double rLonNorthPole; //= -87.59701538085938;
    private final double rLatSouthPole; //= -31.758312225341797;
    private final double rLonSouthPole; //= -92.402969;

    //Precompute rotation parameters in radians
    private final double angleRotDeg;
    private final double phiP;
    private final double lamP;
    private final double sinPhiP;
    private final double cosPhiP;

    private final SpatialData overlay;
    private final Grib2GridMetaData grid;
    private final byte[] rawPixels;

    private final int overlayColourMask;

    public OverlayTileProvider(SpatialData overlay, int overlayColour) {
        this.overlay   = overlay;
        this.rawPixels = overlay.getGrib2().getRawImage().pixels;
        this.grid = overlay.getGrib2().getGridMetaData();

        gridScaleXInv = 1.0 / grid.getdLonDeg();
        gridScaleYInv = 1.0 / grid.getdLatDeg();

        gridWidth = grid.getGridWidth();
        gridHeight = grid.getGridHeight();

        rLatZero = grid.getLat1Deg();
        rLonZero = grid.getLon1Deg();

        rLatSouthPole = grid.getSouthPoleLatDeg();
        rLonSouthPole = grid.getSouthPoleLonDeg();
        rLatNorthPole = -rLatSouthPole;
        rLonNorthPole = Utils.wrapLongitude(-rLonSouthPole + 180.0);

        angleRotDeg = grid.getAngleOfRotationDeg();
        phiP = Math.toRadians(rLatNorthPole);
        lamP = Math.toRadians(rLonNorthPole);
        sinPhiP = Math.sin(phiP);
        cosPhiP = Math.cos(phiP);

        overlayColourMask = overlayColour & 0x00FFFFFF;
    }

    /** Bilinear sample of 8-bit grid at fractional (fi,fj). Returns [0..255]. */
    private static int sampleAlphaBilinear(double fi, double fj, final byte[] pixels, int width, int height) {
        //Outside grid → transparent
        if (fi < 0 || fj < 0 || fi > width - 1 || fj > height - 1) return 0;

        final int i0 = (int) fi;
        final int j0 = (int) fj;
        final int i1 = Math.min(i0 + 1, width - 1);
        final int j1 = Math.min(j0 + 1, height - 1);

        final double dx = (fi - i0);
        final double dy = (fj - j0);

        final int idx00 = j0 * width + i0;
        final int idx10 = j0 * width + i1;
        final int idx01 = j1 * width + i0;
        final int idx11 = j1 * width + i1;

        final int a00 = pixels[idx00] & 0xFF;
        final int a10 = pixels[idx10] & 0xFF;
        final int a01 = pixels[idx01] & 0xFF;
        final int a11 = pixels[idx11] & 0xFF;

        final double a0 = a00 + dx * (a10 - a00);
        final double a1 = a01 + dx * (a11 - a01);
        final int a = (int) (a0 + dy * (a1 - a0) + 0.5f); //+ 0.5f is for rounding

        return (a < 0) ? 0 : (a > 255 ? 255 : a);
    }

    public SpatialData getOverlay() {
        return overlay;
    }

    public void drawOverlay(Canvas canvas, int row, int col, int zoomLvl) {
        //Determine the current scaling of the base bitmap image based on the tile level
        final double scale = Math.pow(2.0, zoomLvl - (MAP_LEVEL_COUNT - 1));
        final double invScale = 1.0 / scale;

        //Determine the absolute x and y coordinates of the top left of this current tile
        final double tileScale = MAP_TILE_SIZE * invScale;
        final double tileWorldOriginX = col * tileScale;
        final double tileWorldOriginY = row * tileScale;

        //Create computedOverlay ARGB buffer (per-pixel alpha from RawImage)
        final int[] computedOverlay = new int[MAP_TILE_SIZE * MAP_TILE_SIZE];

        //For each pixel in the tile, sample the RawImage with bilinear interpolation
        int idx = 0;
        double[] latLon = new double[2]; //Allocated once, better performance

        double worldY = tileWorldOriginY;
        for (int tileY = 0; tileY < MAP_TILE_SIZE; tileY++, worldY += invScale) {
            double worldX = tileWorldOriginX;
            for (int tileX = 0; tileX < MAP_TILE_SIZE; tileX++, worldX += invScale) {
                //Transform from global pixel location to latitude and longitude coordinates
                MapTransformer.transformXY(worldX, worldY, latLon);

                //Fudge factor
                latLon[1] += 4.8;

                //Convert from degrees to rotated radian coordinates
                latLon[0] *= DEG_TO_RAD;
                latLon[1] *= DEG_TO_RAD;

                final double sinLat = Math.sin(latLon[0]);
                final double cosLat = Math.cos(latLon[0]);

                final double dLam = latLon[1] - lamP;
                final double cosLatCosDLam = Math.cos(dLam) * cosLat;

                final double phiR = Math.asin((sinLat * sinPhiP) - (cosPhiP * cosLatCosDLam)); //Faster than Math.atan2(sinPhiR, Math.hypot(cosPhiR_sinLamR, cosPhiR_cosLamR));
                final double lamR = Math.atan2(cosLat * Math.sin(dLam), (sinLat * cosPhiP) + (sinPhiP * cosLatCosDLam));

                //Convert from rotated radian coordinates to grid fractional indices (i,j) in degrees
                latLon[0] = ((phiR * RAD_TO_DEG) - rLatZero) * gridScaleYInv;
                latLon[1] = ((lamR * RAD_TO_DEG) - rLonZero) * gridScaleXInv;

                int color = 0; //transparent by default
                final int a = sampleAlphaBilinear(latLon[1], latLon[0], rawPixels, gridWidth, gridHeight);
                if (a > 0) {
                    //Compose ARGB with per-pixel alpha
                    color = (a & 0xFF) << 24 | overlayColourMask;
                }
                computedOverlay[idx++] = color;
            }
        }
        if(canvas.isHardwareAccelerated()) {
            canvas.drawBitmap(Bitmap.createBitmap(computedOverlay, MAP_TILE_SIZE, MAP_TILE_SIZE, Bitmap.Config.ARGB_8888), 0f, 0f, null);
        } else {
            canvas.drawBitmap(computedOverlay, 0, MAP_TILE_SIZE, 0f, 0f, MAP_TILE_SIZE, MAP_TILE_SIZE, true, null);
        }
    }

}