package com.dbf.aqhi.grib2;

import android.graphics.Bitmap;
import android.util.Log;

import com.dbf.aqhi.codec.Jpeg2000Decoder;
import com.dbf.aqhi.codec.RawImage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Grib2Parser {

    private static final String LOG_TAG = "Grib2Parser";

    private static final long MAX_BYTES = 100*1024*1024; //100mb, reasonable upper limit
    private static final byte[] GRIB_HEADER = {71, 82, 73, 66}; //"GRIB"

    public static Bitmap parse(byte[] bytes) throws IOException {
        if(null == bytes || bytes.length == 0) return null;

        checkHeader(bytes);
        int byteOffset = 16;

        int dataTemplateNumber = -1;

        //Move through the byte array until we reach the end
        while (true) {
            final int sectionStart = byteOffset;
            final long sectionLength = readUInt32(bytes, sectionStart);
            if(bytes.length - byteOffset <= 5) break; //End of file
            switch (Grib2Section.fromCode(bytes[sectionStart + 4])) { //Section number
                case GRID_DEF:
                    //Grid Definition Section
                    //parseGDS(sectionlength);
                    break;
                case DATA_REP:
                    dataTemplateNumber = parseDataRep(bytes, sectionStart);
                    break;
                case DATA:
                    return parseData(bytes, sectionStart, sectionLength, dataTemplateNumber);
                default:
                    break;
            }
            byteOffset += sectionLength;
            if(byteOffset >= bytes.length) break; //End of file
        }

        return null;
    }

    private static Bitmap parseData(byte[] bytes, int sectionStart, long sectionLength, int dataTemplateNumber) {
        if(dataTemplateNumber != 40)
            throw new IllegalArgumentException("Invalid data type. Only JPEG 2000 codestream is supported.");

        RawImage img = Jpeg2000Decoder.decodeJpeg2000(bytes, sectionStart + 5, (int) (sectionLength-4));
        Bitmap bitmap = Bitmap.createBitmap(img.width, img.height, Bitmap.Config.ALPHA_8);
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(img.pixels));
        return bitmap;
    }

    private static int parseDataRep(byte[] bytes, int sectionStart) {
        long dataPoints = readUInt32(bytes, sectionStart + 5);
        int dataTemplateNumber = readUInt16(bytes, sectionStart + 9);
        Log.i(LOG_TAG, "Grib2 file contains " + dataPoints + " data point(s) of type " + dataTemplateNumber);
        return dataTemplateNumber;
    }

    private static void checkHeader(byte[] bytes) {
        if (bytes.length < 100 ) //sanity check
            throw new IllegalArgumentException("Invalid GRIB file, too small.");

        if (bytes.length > MAX_BYTES )
            throw new IllegalArgumentException("GRIB file too big.");

        try {
            if(!Arrays.equals(bytes, 0, 3, GRIB_HEADER, 0, 3))
                throw new IllegalArgumentException("Invalid GRIB header, missing \"GRIB\" magic bytes.");
        } catch(Exception e) {
            throw new IllegalArgumentException("Invalid GRIB file.", e);
        }

        final byte gribVersion = bytes[7];
        if(gribVersion != 2)
            throw new IllegalArgumentException("Unsupported GRIB version: " + gribVersion);
    }

    private static long readUInt32(byte[] bytes, int offset) {
        //GRIB2 is big-endian
        return ((long) (bytes[offset]      & 0xFF) << 24) |
                ((long) (bytes[offset + 1] & 0xFF) << 16) |
                ((long) (bytes[offset + 2] & 0xFF) << 8)  |
                ((long) (bytes[offset + 3] & 0xFF));
    }

    private static int readUInt16(byte[] bytes, int offset) {
        //GRIB2 is big-endian
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }
}