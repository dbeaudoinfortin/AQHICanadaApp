package com.dbf.aqhi.map;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.util.Log;

import androidx.annotation.Nullable;

import com.dbf.utils.stacktrace.StackTraceCompactor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import ovh.plrapps.mapview.core.TileStreamProvider;

public class CompositeTileProvider implements TileStreamProvider {
    private static final String LOG_TAG = "CompositeTileProvider";

    private final TileStreamProvider baseTileProvider;
    private OverlayTileProvider overlayTileProvider;

    public CompositeTileProvider(TileStreamProvider baseTileProvider) {
        this.baseTileProvider = baseTileProvider;
    }

    public CompositeTileProvider(TileStreamProvider baseTileProvider, OverlayTileProvider overlayTileProvider) {
        this.baseTileProvider = baseTileProvider;
        this.overlayTileProvider = overlayTileProvider;
    }

    @Nullable
    @Override
    public InputStream getTileStream(int row, int col, int zoomLvl) {
        try (InputStream base = baseTileProvider.getTileStream(row, col, zoomLvl)) {
            if(null == overlayTileProvider) return base;
            if (base == null) return null;

            //Load the base image from disk
            Bitmap baseBmp = BitmapFactory.decodeStream(base);
            if (baseBmp == null) return null;

            if (!baseBmp.isMutable()) {
                baseBmp = baseBmp.copy(Bitmap.Config.ARGB_8888, true);
            } else if (baseBmp.getConfig() != Bitmap.Config.ARGB_8888) {
                baseBmp = baseBmp.copy(Bitmap.Config.ARGB_8888, true);
            }

            //Blend computedOverlay into base
            Bitmap overlayBmp = overlayTileProvider.getTile(row, col, zoomLvl);
            Canvas c = new Canvas(baseBmp);
            c.drawBitmap(overlayBmp, 0f, 0f, null);

            //Encode result (lossless WebP keeps alpha; PNG is fine too)
            ByteArrayOutputStream baos = new ByteArrayOutputStream(32 * 1024);
            boolean ok = baseBmp.compress(Bitmap.CompressFormat.PNG, 100, baos);
            return new ByteArrayInputStream(baos.toByteArray());
        } catch (Throwable t) {
            Log.e(LOG_TAG, String.format("Composite tile failed row:%d column:%d zoom:%d:\n%s", row, col, zoomLvl, StackTraceCompactor.getCompactStackTrace(t)));
            return null;
        }
    }

    public OverlayTileProvider getOverlayTileProvider() {
        return overlayTileProvider;
    }

    public void setOverlayTileProvider(OverlayTileProvider overlayTileProvider) {
        this.overlayTileProvider = overlayTileProvider;
    }
}
