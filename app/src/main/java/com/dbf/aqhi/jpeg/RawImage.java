package com.dbf.aqhi.jpeg;

public class RawImage {
    public final int width;
    public final int height;
    public final byte[] pixels; //1 byte per pixel, grayscale 0-255
    public final float[] values; //Scaled values prior to conversion to pixel data

    public RawImage(int width, int height, byte[] pixels, float[] values) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
        this.values = values;
    }
}
