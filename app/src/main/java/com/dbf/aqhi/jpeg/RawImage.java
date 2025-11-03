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

    /**
     *  Bilinear sampling of the 8-bit pixel grid of this image.
     *  This method is optimized for high performance.
     *
     * @param fractionalX X position to sample
     * @param fractionalY Y position to sample
     * @return [0..255]
     */
    public int samplePixelsBilinear(double fractionalX, double fractionalY) {
        //Outside grid
        if (fractionalX < 0 || fractionalY < 0 || fractionalX > width - 1 || fractionalY > height - 1) return -1;

        final int i0 = (int) fractionalX;
        final int j0 = (int) fractionalY;
        final int i1 = Math.min(i0 + 1, width - 1);
        final int j1 = Math.min(j0 + 1, height - 1);

        final int row0 = j0 * width;
        final int row1 = j1 * width;

        //Java bytes are signed, need to convert to int
        final int a00 = pixels[row0 + i0] & 0xFF;
        final int a10 = pixels[row0 + i1] & 0xFF;
        final int a01 = pixels[row1 + i0] & 0xFF;
        final int a11 = pixels[row1 + i1] & 0xFF;

        final double dx = (fractionalX - i0);
        final double a0 = a00 + dx * (a10 - a00);
        final double a1 = a01 + dx * (a11 - a01);
        final int a = (int) (a0 + (fractionalY - j0) * (a1 - a0) + 0.5f); //+ 0.5f is for rounding
        return (a < 0) ? 0 : (a > 255 ? 255 : a);
    }


    /**
     *  Bilinear sampling of the 32-bit float value grid of this image.
     *  This method is optimized for high performance.
     *
     * @param fractionalX X position to sample
     * @param fractionalY Y position to sample
     * @return the sampled value as a float, NaN if the provided X, Y position was out of bounds
     */
    public float sampleValuesBilinear(float fractionalX, float fractionalY) {
        //Outside grid
        if (fractionalX < 0 || fractionalY < 0 || fractionalX > width - 1 || fractionalY > height - 1) return Float.NaN;

        final int i0 = (int) fractionalX;
        final int j0 = (int) fractionalY;
        final int i1 = Math.min(i0 + 1, width - 1);
        final int j1 = Math.min(j0 + 1, height - 1);

        final int row0 = j0 * width;
        final int row1 = j1 * width;
        final float a00 = values[row0 + i0];
        final float a10 = values[row0 + i1];
        final float a01 = values[row1 + i0];
        final float a11 = values[row1 + i1];

        final float dx = (fractionalX - i0);
        final float a0 = a00 + dx * (a10 - a00);
        final float a1 = a01 + dx * (a11 - a01);
        return a0 + (fractionalY - j0) * (a1 - a0);
    }
}
