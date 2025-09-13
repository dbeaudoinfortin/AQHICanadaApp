package com.dbf.aqhi.map;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.Nullable;

import com.dbf.utils.stacktrace.StackTraceCompactor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import ovh.plrapps.mapview.core.TileStreamProvider;

public class CompositeTileProvider implements TileStreamProvider {
    private static final String LOG_TAG = "CompositeTileProvider";

    private final TileStreamProvider baseTileProvider;
    private volatile OverlayTileProvider overlayTileProvider;

    private static final BitmapFactory.Options DECODE_OPTS = new BitmapFactory.Options();

    private static final int AVG_TILE_SIZE_COMPRESSED = 100 * 1024; ///100kb
    private static final int CACHE_MAX_BYTES = 50 *1024 *1024; //50MB
    private static final int CACHE_MAX_TILES = CACHE_MAX_BYTES / AVG_TILE_SIZE_COMPRESSED;

    private final LruCache<TileKey, byte[]> tileCache = new LruCache<TileKey, byte[]>(CACHE_MAX_TILES);

    //For use as the cache key
    private record TileKey(int row, int col, int zoomLvl) {}

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
        if(null == overlayTileProvider) return baseTileProvider.getTileStream(row, col, zoomLvl);

        TileKey key = new TileKey(row, col, zoomLvl);
        byte[] bytes = tileCache.get(key);
        if (null == bytes) {
            bytes = getTileBytesGated(row, col, zoomLvl);
            if (null == bytes) return null;
            tileCache.put(key, bytes);
        }
        return new ByteArrayInputStream(bytes);
    }

    public byte[] getTileBytesGated(int row, int col, int zoomLvl) {
        OverlayTileProvider oldOverlay = overlayTileProvider;
        byte[] bytes = getTileBytes(row, col, zoomLvl);
        if(!oldOverlay.equals(overlayTileProvider)){
            //Mitigate the issue of the overlay changing during process
            //I am deliberately avoid synchronization,
            //since in the worse case a wrong tile will be display and that's no big deal.
            return getTileBytes(row, col, zoomLvl);
        }
        return bytes;
    }

    public byte[] getTileBytes(int row, int col, int zoomLvl) {
        InputStream base = baseTileProvider.getTileStream(row, col, zoomLvl);
        if (null == base) return null;
        try (base) {
            //Load the base map tile image from disk
            Bitmap baseBmp = BitmapFactory.decodeStream(base, null, DECODE_OPTS);
            if (null == baseBmp) return null;

            if (!baseBmp.isMutable() || baseBmp.getConfig() != Bitmap.Config.ARGB_8888) {
                baseBmp = baseBmp.copy(Bitmap.Config.ARGB_8888, true);
            }

            //Blend overlay into base map tile image
            overlayTileProvider.drawOverlay(new Canvas(baseBmp), row, col, zoomLvl);

            //Encode the blended image
            ByteArrayOutputStream baos = new ByteArrayOutputStream(61440); //60KB
            //Note: on modern hardware WEBP is both the smallest and fastest format
            //due to the support of hardware acceleration.
            if(!baseBmp.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 80, baos)) {
                Log.w(LOG_TAG, "Failed to compress map tile.");
                return null;
            }
            return baos.toByteArray();

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
        tileCache.evictAll();
    }
}
