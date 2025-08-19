package com.dbf.aqhi.map;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.dbf.aqhi.codec.RawImage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import ovh.plrapps.mapview.core.TileStreamProvider;

public class CompositeTileProvider implements TileStreamProvider {

    //TODO: make all this flexible
    private final int overlayColour = Color.rgb(0x6B, 0x3A, 0x1E); //Dark brown

    private final int tileSize = 256;
    private final int levelCount = 9;

    private final double rLatZero = -32.0;
    private final double rLonZero = -39.5;
    private final double rLatNorthPole = 31.758312225341797;
    private final double rLonNorthPole = 87.59701538085938;
    private final double gridScaleDegrees = 0.09;
    public final int gridWidth = 729;
    public final int gridHeight = 599;

    private final TileStreamProvider baseTileProvider;
    private RawImage rawOverlay;

    // Precompute rotation parameters (radians)
    private final double phiP = Math.toRadians(rLatNorthPole);
    private final double lamP = Math.toRadians(rLonNorthPole);
    private final double sinPhiP = Math.sin(phiP), cosPhiP = Math.cos(phiP);

    public CompositeTileProvider(TileStreamProvider baseTileProvider) {
        this.baseTileProvider = baseTileProvider;
    }

    @Nullable
    @Override
    public InputStream getTileStream(int row, int col, int zoomLvl) {
        try (InputStream base = baseTileProvider.getTileStream(row, col, zoomLvl)) {
            if(null == rawOverlay) return base;
            if (base == null) return null;

            //Load the base image from disk
            Bitmap baseBmp = BitmapFactory.decodeStream(base);
            if (baseBmp == null) return null;

            if (!baseBmp.isMutable()) {
                baseBmp = baseBmp.copy(Bitmap.Config.ARGB_8888, true);
            } else if (baseBmp.getConfig() != Bitmap.Config.ARGB_8888) {
                baseBmp = baseBmp.copy(Bitmap.Config.ARGB_8888, true);
            }

            //Determine the current scaling of the base bitmap image based on the tile level
            final double scale = Math.pow(2.0, zoomLvl - (levelCount - 1));

            //Determine the absolute x&y coordinates of the top left of this current tile
            final double tileScale = ((double) tileSize) /scale;
            final double tileWorldOriginX = col * tileScale;
            final double tileOriginY = row * tileScale;

            //Create computedOverlay ARGB buffer (per-pixel alpha from RawImage)
            final int[] computedOverlay = new int[tileSize * tileSize];

            //For each pixel in the tile, sample the RawImage with bilinear interpolation
            int idx = 0;
            for (int tileY = 0; tileY < tileSize; tileY++) {
                double worldY = tileOriginY + tileY / scale;
                for (int tileX = 0; tileX < tileSize; tileX++) {
                    double worldX = tileWorldOriginX + tileX / scale;

                    //Transform from global pixel location to latitude and logitude coordinates
                    Pair<Double, Double> latLon = MapTransformer.transformXY(worldX, worldY);
                    if (latLon == null) continue;

                    //Convert to rotated radian coordinates
                    final double lat = Math.toRadians(latLon.first);
                    final double lon = Math.toRadians(latLon.second);

                    final double dLam = lon - lamP;
                    final double sinLat = Math.sin(lat), cosLat = Math.cos(lat);
                    final double sinPhiR = sinLat * sinPhiP - cosLat * cosPhiP * Math.cos(dLam);
                    final double cosPhiR_sinLamR = cosLat * Math.sin(dLam);
                    final double cosPhiR_cosLamR = sinLat * cosPhiP + cosLat * sinPhiP * Math.cos(dLam);

                    final double phiR = Math.atan2(sinPhiR, Math.hypot(cosPhiR_sinLamR, cosPhiR_cosLamR));
                    final double lamR = Math.atan2(cosPhiR_sinLamR, cosPhiR_cosLamR);

                    //convert from rotated coordinates to grid fractional indices (i,j)
                    double rlatDeg = Math.toDegrees(phiR);
                    double rlonDeg = Math.toDegrees(lamR);
                    // normalize rlonDeg near your grid convention if needed (e.g., [-180,180) vs [0,360))
                    double fi = (rlonDeg - rLonZero) / gridScaleDegrees;
                    double fj = (rlatDeg - rLatZero) / gridScaleDegrees;

                    int color = 0; // transparent by default
                    int a = sampleAlphaBilinear(fi, fj, rawOverlay, gridWidth, gridHeight);
                    if (a > 0) {
                        // Compose ARGB with per-pixel alpha (only computedOverlay color)
                        color = (a & 0xFF) << 24 | (overlayColour & 0x00FFFFFF);
                    }
                    computedOverlay[idx++] = color;
                }
            }

            // 4) Blend computedOverlay into base
            Bitmap overlayBmp = Bitmap.createBitmap(computedOverlay, tileSize, tileSize, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(baseBmp);
            c.drawBitmap(overlayBmp, 0f, 0f, null);

            // 5) Encode result (lossless WebP keeps alpha; PNG is fine too)
            ByteArrayOutputStream baos = new ByteArrayOutputStream(32 * 1024);
            // Prefer WEBP_LOSSLESS on API >= 30, else fallback to PNG
            boolean ok = baseBmp.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, baos);
            if (!ok) {
                baos.reset();
                baseBmp.compress(Bitmap.CompressFormat.PNG, 100, baos);
            }
            return new ByteArrayInputStream(baos.toByteArray());
        } catch (Throwable t) {
            //Log.e(TAG, String.format(Locale.US,
            //        "Composite tile failed r=%d c=%d z=%d : %s", row, col, zoomLvl, t));
            return null;
        }
    }

    /** Bilinear sample of 8-bit grid at fractional (fi,fj). Returns [0..255]. */
    private static int sampleAlphaBilinear(double fi, double fj, RawImage img, int w, int h) {
        // Outside grid → transparent
        if (fi < 0 || fj < 0 || fi > w - 1 || fj > h - 1) return 0;

        int i0 = (int) Math.floor(fi);
        int j0 = (int) Math.floor(fj);
        int i1 = Math.min(i0 + 1, w - 1);
        int j1 = Math.min(j0 + 1, h - 1);

        double dx = fi - i0;
        double dy = fj - j0;

        int idx00 = j0 * w + i0;
        int idx10 = j0 * w + i1;
        int idx01 = j1 * w + i0;
        int idx11 = j1 * w + i1;

        int a00 = img.pixels[idx00] & 0xFF;
        int a10 = img.pixels[idx10] & 0xFF;
        int a01 = img.pixels[idx01] & 0xFF;
        int a11 = img.pixels[idx11] & 0xFF;

        double a0 = a00 + dx * (a10 - a00);
        double a1 = a01 + dx * (a11 - a01);
        int a = (int) Math.round(a0 + dy * (a1 - a0));

        return (a < 0) ? 0 : (a > 255 ? 255 : a);
    }

    public RawImage getOverlay() {
        return rawOverlay;
    }

    public void setOverlay(RawImage overlay) {
        this.rawOverlay = overlay;
    }
}
