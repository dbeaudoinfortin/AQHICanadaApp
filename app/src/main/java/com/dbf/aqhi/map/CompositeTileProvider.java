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

    private static final BitmapFactory.Options DECODE_OPTS = new BitmapFactory.Options();

    static {
        DECODE_OPTS.inPreferredConfig = Bitmap.Config.ARGB_8888;
        DECODE_OPTS.inMutable = true;
    }


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

            //Load the base map tile image from disk
            Bitmap baseBmp = BitmapFactory.decodeStream(base, null, DECODE_OPTS);
            if (baseBmp == null) return null;

            if (!baseBmp.isMutable() || baseBmp.getConfig() != Bitmap.Config.ARGB_8888) {
                baseBmp = baseBmp.copy(Bitmap.Config.ARGB_8888, true);
            }

            //Blend overlay into base map tile image
            overlayTileProvider.drawOverlay(new Canvas(baseBmp), row, col, zoomLvl);

            //Encode the blended image
            ByteArrayOutputStream baos = new ByteArrayOutputStream(baseBmp.getByteCount());
            if(!baseBmp.compress(Bitmap.CompressFormat.PNG, 100, baos)) {
                Log.w(LOG_TAG, "Failed to compress map tile.");
                return null;
            }
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
