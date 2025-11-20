package com.dbf.aqhi.map;

import static com.dbf.aqhi.AQHIFeature.MAP_LEVEL_COUNT;
import static com.dbf.aqhi.AQHIFeature.MAP_TILE_SIZE;
import static com.dbf.aqhi.Utils.DEG_TO_RAD;
import static com.dbf.aqhi.Utils.RAD_TO_DEG;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import com.dbf.aqhi.api.datamart.Pollutant;
import com.dbf.aqhi.data.spatial.SpatialData;
import com.dbf.aqhi.jpeg.RawImage;

import java.text.DecimalFormat;

public class OverlayTileProvider {

    public static final int MAX_PIXEL_VALUE = 230; //Ensure a little bit of transparency

    private final double gridScaleXInv;
    private final double gridScaleYInv;

    private final double rLatZero;
    private final double rLonZero;

    private final double lamP;
    private final double sinPhiP;
    private final double cosPhiP;

    private final SpatialData overlay;
    private final RawImage rawImage;

    private final int overlayColourMask;

    public OverlayTileProvider(SpatialData overlay, int overlayColour) {
        this.overlay   = overlay;
        this.rawImage = overlay.getGrib2().getRawImage();

        this.gridScaleXInv = overlay.getGridScaleXInv();
        this.gridScaleYInv = overlay.getGridScaleYInv();

        this.rLatZero = overlay.getrLatZero();
        this.rLonZero = overlay.getrLonZero();

        this.lamP = overlay.getLamP();
        this.sinPhiP = overlay.getSinPhiP();
        this.cosPhiP = overlay.getCosPhiP();

        this.overlayColourMask = overlayColour & 0x00FFFFFF;
    }

    public SpatialData getOverlay() {
        return overlay;
    }

    /**
     * Draws the overlay in onto a canvas for a single map tiles located at (col, row) and zoom level.
     * This method is deliberately redundant with SpatialData.overlayLookup for performance reasons.
     *
     * @param canvas
     * @param row
     * @param col
     * @param zoomLvl
     */
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
                //NOTE: This is a performance critical tight loop.
                //So the code is copied from overlayLookup() to avoid the call overhead.
                //This is where a macro function would be useful in Java.

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
                final int a = rawImage.samplePixelsBilinear(latLon[1], latLon[0]);
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

    /**
     * Lookup the text to display on the map overlay at the provided x,y pixel coordinates.
     *
     * @param x pixel coordinate
     * @param y pixel coordinate
     *
     * @return Overlay text
     */
    public String overlayTextLookup(int x, int y, Pollutant pollutant) {

        final double[] latLon = new double[2];
        MapTransformer.transformXY(x, y, latLon);

        if(null != rawImage.values) {
            //First try to use raw value if possible
            final float overlayValue = overlay.overlayValueLookup(latLon[0], latLon[1]);
            if(overlayValue < 0.0 || Float.isNaN(overlayValue)) {
                return "-- " + pollutant.getUnits(); //Outside the grid
            }
            return (new DecimalFormat("0.0")).format(overlayValue) + " " + pollutant.getUnits();
        } else {
            //Fall back to the alpha pixel transparency of the overlay
            //Convert to the actual unit value
            final int overlayValue = overlay.pixelLookup(latLon[0], latLon[1]);
            if(overlayValue >= MAX_PIXEL_VALUE) {
                return "≥" + pollutant.getOverlayMaxVal() + " " + pollutant.getUnits();
            } else if (overlayValue < 0) {
                return "-- " + pollutant.getUnits(); //Outside the grid
            } else if (overlayValue == 0) {
                return "≤" + pollutant.getOverlayMinVal() + " " + pollutant.getUnits();
            }

            //Interpolated value
            final float pollutionValue = pollutant.getOverlayMinVal() + ((((float)overlayValue) / MAX_PIXEL_VALUE) * (pollutant.getOverlayMaxVal() - pollutant.getOverlayMinVal()));
            return (new DecimalFormat("0.0")).format(pollutionValue) + " " + pollutant.getUnits();
        }
    }
}