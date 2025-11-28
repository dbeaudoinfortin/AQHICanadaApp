package com.dbf.aqhi.jpeg;

import java.nio.ByteBuffer;

public class Jpeg2000Decoder {

    //Reuse byte buffers for each thread to save allocation time
    //Expected to be about 600kb each, and there are 4 threads
    private static final ThreadLocal<ByteBuffer> BYTE_BUFFER = new ThreadLocal<ByteBuffer>();

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
     * @param maxAlpha    Maximum output pixel value between 0-255. All values above this will be clamped.
     * @return A {@link RawImage} object containing the image dimensions and grayscale pixel data,
     *         or {@code null} if decoding fails.
     */
    public static RawImage decodeJpeg2000(byte[] jpeg2000Data, int offset, int length, float scale, float minVal, float maxVal, int maxAlpha) {
        final ByteBuffer buf = getByteBuffer(length);
        buf.put(jpeg2000Data, offset, length);
        return decodeJpeg2000(buf, 0, length, scale, minVal, maxVal, maxAlpha);
    }

    private static ByteBuffer getByteBuffer(int length) {
        if (length > 10000000) {
            //Set some reasonable upper bound so we don't hold too much memory
            //Just return it, don't set it to thread local
            BYTE_BUFFER.remove();
            return ByteBuffer.allocateDirect(length);
        }

        //If the existing buffer is small then discard it and create a new one
        ByteBuffer buf = BYTE_BUFFER.get();
        if (null == buf || buf.capacity() < length) {
            buf = ByteBuffer.allocateDirect(length);
            BYTE_BUFFER.set(buf);
        } else {
            buf.clear();
        }

        return buf;
    }

    private static native RawImage decodeJpeg2000(ByteBuffer jpeg2000Data, int offset, int length, float scale, float minVal, float maxVal, int maxAlpha);
}
