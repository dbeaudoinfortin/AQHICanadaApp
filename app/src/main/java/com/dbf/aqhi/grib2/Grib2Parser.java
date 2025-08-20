package com.dbf.aqhi.grib2;

import android.util.Log;

import com.dbf.aqhi.codec.Jpeg2000Decoder;
import com.dbf.aqhi.codec.RawImage;

import java.io.IOException;
import java.util.Arrays;

public class Grib2Parser {

    private static final String LOG_TAG = "Grib2Parser";

    private static final long MAX_BYTES = 100*1024*1024; //100mb, reasonable upper limit
    private static final byte[] GRIB_HEADER = {71, 82, 73, 66}; //"GRIB"

    public static Grib2 parse(byte[] bytes) throws IOException {
        if(null == bytes || bytes.length == 0) return null;

        checkHeader(bytes);
        int byteOffset = 16;

        Grib2GridMetaData gridMeta = null;
        Grib2DataMetaData scaleMeta = null;
        RawImage rawImage = null;

        //Move through the byte array until we reach the end
        while (true) {
            final int sectionStart = byteOffset;
            final long sectionLength = readUInt32(bytes, sectionStart);
            if(bytes.length - byteOffset <= 5) break; //End of file
            switch (Grib2Section.fromCode(bytes[sectionStart + 4])) { //Section number
                case GRID_DEF: //Spec section 3
                    gridMeta = parseGridDef(bytes, sectionStart);
                    break;
                case DATA_REP: //Spec section 5
                    scaleMeta = parseDataRep(bytes, sectionStart);
                    break;
                case DATA: //Spec section 7
                    rawImage = parseData(bytes, sectionStart, sectionLength, scaleMeta);
                default:
                    break;
            }
            byteOffset += sectionLength;
            if(byteOffset >= bytes.length) break; //End of file
        }

        return new Grib2(gridMeta, scaleMeta, rawImage);
    }

    private static RawImage parseData(byte[] bytes, int sectionStart, long sectionLength, Grib2DataMetaData scaleMeta) {
        if(null == scaleMeta)
            throw new IllegalArgumentException("Invalid GRIB2 fill, metadata was not parsed correctly.");

        if(scaleMeta.getDataTemplateNumber() != 40)
            throw new IllegalArgumentException("Invalid data type. Only JPEG 2000 codestream is supported.");

        RawImage img = Jpeg2000Decoder.decodeJpeg2000(bytes, sectionStart + 5, (int) (sectionLength-4));
        //Bitmap bitmap = Bitmap.createBitmap(img.width, img.height, Bitmap.Config.ALPHA_8);
        //bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(img.pixels));
        //return bitmap;
        return img;
    }

    private static Grib2GridMetaData parseGridDef(byte[] bytes, int sectionStart) {
        int gridTemplate = readUInt16(bytes, sectionStart + 12);
        return parseGridTemplateLatLon(bytes, sectionStart + 14, gridTemplate);
    }

    private static Grib2DataMetaData parseDataRep(byte[] bytes, int sectionStart) {
        final long dataPoints = readUInt32(bytes, sectionStart + 5);
        final int dataTemplateNumber = readUInt16(bytes, sectionStart + 9);
        Log.i(LOG_TAG, "Grib2 file contains " + dataPoints + " data point(s) of type " + dataTemplateNumber);

        final float R = readFloat32(bytes, sectionStart + 11);
        short E  = readInt16(bytes, sectionStart + 15);
        short D  = readInt16(bytes, sectionStart + 17);
        int nb = readUInt16(bytes, sectionStart + 19);
        int originalType = readUInt16(bytes, sectionStart + 21);

        return new Grib2DataMetaData(R, E, D, nb, dataTemplateNumber, dataPoints);
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

    private static Grib2GridMetaData parseGridTemplateLatLon(byte[] bytes, int offset, int gridTemplate) {
        if(gridTemplate != 0 && gridTemplate != 1)
            throw new IllegalArgumentException("Unsupported grid template: " + gridTemplate + ". Only O (Latitude/Longitude) and 1 (Rotated Latitude/Longitude) are supported.");

        int idx = offset;

        //Common to all 3.x templates
        int shapeEarth = readUByte8(bytes, idx++);

        int scaleFactorEarthSphere = readUByte8(bytes, idx++);
        long scaleValueEarthSphere = readUInt32(bytes, idx); idx += 4;

        int scaleFactorEarthOblateMajor = readUByte8(bytes, idx++);
        long scaleValueEarthOblateMajor = readUInt32(bytes, idx); idx += 4;

        int scaleFactorEarthOblateMinor = readUByte8(bytes, idx++);
        long scaleValueEarthOblateMinor = readUInt32(bytes, idx); idx += 4;

        int Ni = (int) readUInt32(bytes, idx); idx += 4;
        int Nj = (int) readUInt32(bytes, idx); idx += 4;

        long basicAngle = readUInt32(bytes, idx); idx += 4;
        long subDiv     = readUInt32(bytes, idx); idx += 4;
        final double degPerUnit = (basicAngle != 0 && subDiv != 0)
                ? ((double) basicAngle / (double) subDiv)
                : 1e-6; //default is microdegrees

        int La1_i = readInt32(bytes, idx); idx += 4;
        int Lo1_i = readInt32(bytes, idx); idx += 4;
        double La1 = La1_i * degPerUnit;
        double Lo1 = Lo1_i * degPerUnit;

        int resFlags = readUByte8(bytes, idx++);

        int La2_i = readInt32(bytes, idx); idx += 4;
        int Lo2_i = readInt32(bytes, idx); idx += 4;
        int Di_i  = readInt32(bytes, idx); idx += 4;
        int Dj_i  = readInt32(bytes, idx); idx += 4;
        int scan  = readUByte8(bytes, idx++);

        double dLon = Di_i * degPerUnit;
        double dLat = Dj_i * degPerUnit;

        //Scan mode (Table 3.4): bit7 i-direction, bit6 j-direction
        boolean iScansNegatively = (scan & 0x80) != 0; // 1 = westward
        boolean jScansPositively = (scan & 0x40) == 0; // 1 = northward

        if (iScansNegatively) dLon = -dLon;
        if (!jScansPositively) dLat = -dLat;

        double southPoleLat = Double.NaN;
        double southPoleLon = Double.NaN;
        double angleRotDeg  = Double.NaN;

        if (gridTemplate == 1) { //Rotated
            //Rotated LL: extra 12 octets
            int latSP_i = readInt32(bytes, idx); idx += 4;
            int lonSP_i = readInt32(bytes, idx); idx += 4;
            int angle_i = readInt32(bytes, idx); idx += 4;
            southPoleLat = latSP_i * degPerUnit;
            southPoleLon = lonSP_i * degPerUnit;
            angleRotDeg  = angle_i  * degPerUnit;
        }

        return new Grib2GridMetaData(
                gridTemplate, Ni, Nj,
                La1, Lo1,
                dLon, dLat, scan,
                southPoleLat, southPoleLon, angleRotDeg
        );
    }

    private static int readUByte8(byte[] bytes, int offset) {
        return bytes[offset] & 0xFF;
    }

    private static int readInt32(byte[] bytes, int offset) {
        //GRIB2 is big-endian
        return ((bytes[offset]      & 0xFF) << 24) |
                ((bytes[offset + 1] & 0xFF) << 16) |
                ((bytes[offset + 2] & 0xFF) << 8)  |
                ((bytes[offset + 3] & 0xFF));
    }

    private static long readUInt32(byte[] bytes, int offset) {
        //GRIB2 is big-endian
        return ((long) (bytes[offset]      & 0xFF) << 24) |
                ((long) (bytes[offset + 1] & 0xFF) << 16) |
                ((long) (bytes[offset + 2] & 0xFF) << 8)  |
                ((long) (bytes[offset + 3] & 0xFF));
    }

    private static short readInt16(byte[] bytes, int offset) {
        //GRIB2 is big-endian
        return (short) (((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF));
    }

    private static int readUInt16(byte[] bytes, int offset) {
        //GRIB2 is big-endian
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static float readFloat32(byte[] bytes, int offset) {
        return Float.intBitsToFloat(readInt32(bytes, offset));
    }
}