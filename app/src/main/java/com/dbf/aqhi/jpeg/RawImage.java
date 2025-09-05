package com.dbf.aqhi.jpeg;

public class RawImage {
    public final int width;
    public final int height;
    public final byte[] pixels; //1 byte per pixel, grayscale 0-255

    public RawImage(int width, int height, byte[] pixels) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }
}
