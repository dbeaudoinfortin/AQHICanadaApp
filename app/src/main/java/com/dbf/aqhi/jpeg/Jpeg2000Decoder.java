package com.dbf.aqhi.jpeg;

public class Jpeg2000Decoder {

    static {
        System.loadLibrary("jpeg2000decoder");
    }

    /**
     * Decodes a single channel JPEG 2000 image embedded within a larger in-memory byte array.
     *
     * The returned {@link RawImage} object contains the image width, height, and a grayscale
     * pixel buffer. The {@code pixels} array is in row-major order (left-to-right, top-to-bottom),
     * with each {@code byte} representing one grayscale pixel (0–255).
     *
     * If decoding fails or the input parameters are invalid, {@code null} is returned.
     *
     * @param jpeg2000Data The byte array containing the JPEG 2000 image.
     * @param offset       The offset (in bytes) from the start of {@code jpeg2000Data}
     *                     where the JPEG 2000 image begins.
     * @param length       The length (in bytes) of the JPEG 2000 image within {@code jpeg2000Data}.
     * @param scale        A linear scaling factor applied to each pixel.
     * @param minVal       The minimum post-scaled value that represents 0 alpha
     * @param maxVal       The maximum post-scaled value that represents max_alpha
     * @param max_alpha    Maximum output pixel value between 0-255. All values above this will be clamped.
     * @return A {@link RawImage} object containing the image dimensions and grayscale pixel data,
     *         or {@code null} if decoding fails.
     */
    public static native RawImage decodeJpeg2000(byte[] jpeg2000Data, int offset, int length, float scale, float minVal, float maxVal, int max_alpha);
}
