package com.dbf.aqhi.jpeg;

public class Jpeg2000Decoder {

    static {
        System.loadLibrary("jpeg2000decoder");
    }

    /**
     * Decodes a JPEG 2000 image embedded within a larger in-memory byte array.
     * <p>
     * The input {@code jpeg2000Data} may represent an entire file (such as a GRIB2 file)
     * containing a JPEG 2000 image at a known offset. This method uses native code to
     * decode only the specified subrange of the byte array, as given by {@code offset}
     * and {@code length}. No copying of the byte array occurs.
     * <p>
     * The returned {@link RawImage} object contains the image width, height, and a grayscale
     * pixel buffer. The {@code pixels} array is in row-major order (left-to-right, top-to-bottom),
     * with each {@code byte} representing one grayscale pixel (0–255).
     * <p>
     * If decoding fails or the input parameters are invalid, {@code null} is returned.
     *
     * @param jpeg2000Data The byte array containing the JPEG 2000 image (may be a larger file).
     * @param offset       The offset (in bytes) from the start of {@code jpeg2000Data}
     *                     where the JPEG 2000 image begins.
     * @param length       The length (in bytes) of the JPEG 2000 image within {@code jpeg2000Data}.
     * @return A {@link RawImage} object containing the image dimensions and grayscale pixel data,
     *         or {@code null} if decoding fails.
     */
    public static native RawImage decodeJpeg2000(byte[] jpeg2000Data, int offset, int length);
}
