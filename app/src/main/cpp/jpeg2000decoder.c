#include <jni.h>
#include <openjpeg.h>
#include <string.h>
#include <android/log.h>

#define LOG_TAG "jpeg2000decoder"
#define LOG_ERROR(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOG_WARN(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOG_INFO(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static void error_callback(const char *msg, void *client_data) {
    LOG_ERROR("OpenJPEG error: %s", msg);
}
static void warning_callback(const char *msg, void *client_data) {
    LOG_WARN("OpenJPEG warning: %s", msg);
}
static void info_callback(const char *msg, void *client_data) {
    LOG_INFO("OpenJPEG info: %s", msg);
}

typedef struct {
    const unsigned char* buf;
    OPJ_SIZE_T len;
    OPJ_SIZE_T pos;
} memory_stream_t;

static OPJ_SIZE_T mem_read_fn(void* p_buffer, OPJ_SIZE_T p_nb_bytes, void* p_user_data) {
    memory_stream_t* mem = (memory_stream_t*)p_user_data;
    OPJ_SIZE_T bytes_left = mem->len - mem->pos;
    OPJ_SIZE_T to_copy = (p_nb_bytes < bytes_left) ? p_nb_bytes : bytes_left;
    if (to_copy > 0) {
        memcpy(p_buffer, mem->buf + mem->pos, to_copy);
        mem->pos += to_copy;
    }
    return to_copy;
}

static OPJ_OFF_T mem_skip_fn(OPJ_OFF_T p_nb_bytes, void* p_user_data) {
    memory_stream_t* mem = (memory_stream_t*)p_user_data;
    OPJ_OFF_T newpos = (OPJ_OFF_T)mem->pos + p_nb_bytes;

    if (newpos < 0) {
        newpos = 0;
    } else if ((OPJ_SIZE_T)newpos > mem->len) {
        newpos = (OPJ_OFF_T)mem->len;
    }

    OPJ_OFF_T skipped = newpos - (OPJ_OFF_T)mem->pos;
    mem->pos = (OPJ_SIZE_T)newpos;
    return skipped;
}

static OPJ_BOOL mem_seek_fn(OPJ_OFF_T p_nb_bytes, void* p_user_data) {
    memory_stream_t* mem = (memory_stream_t*)p_user_data;
    if (p_nb_bytes < 0 || (OPJ_SIZE_T)p_nb_bytes > mem->len)
        return OPJ_FALSE;
    mem->pos = (OPJ_SIZE_T)p_nb_bytes;
    return OPJ_TRUE;
}

JNIEXPORT jobject JNICALL
Java_com_dbf_aqhi_codec_Jpeg2000Decoder_decodeJpeg2000(
        JNIEnv *env, jclass clazz, jbyteArray data, jint offset, jint length) {

    //Get pointer to array elements to avoid copying the entire array
    jbyte* all_data = (*env)->GetPrimitiveArrayCritical(env, data, NULL);
    if (all_data == NULL) {
        return NULL; //Not sure how this is possible
    }

    const unsigned char* jpeg2000_data = (const unsigned char*)(all_data + offset);
    OPJ_SIZE_T jpeg2000_len = (OPJ_SIZE_T)length;

    //Initialize memory stream structure
    memory_stream_t mem = {
        .buf = jpeg2000_data,
        .len = jpeg2000_len,
        .pos = 0
    };

    opj_stream_t *l_stream = NULL;
    opj_codec_t* l_codec = NULL;
    opj_image_t* l_image = NULL;
    opj_dparameters_t parameters;

    jobject decodedImageObj = NULL; //Java return object

    opj_set_default_decoder_parameters(&parameters);

    //Create memory stream for input
    l_stream = opj_stream_create(jpeg2000_len, OPJ_TRUE);
    if (!l_stream) {
        LOG_ERROR("Failed to create OpenJPEG stream.");
        goto cleanup;
    }

    opj_stream_set_user_data(l_stream, &mem, NULL);
    opj_stream_set_user_data_length(l_stream, (OPJ_UINT64)length);
    opj_stream_set_read_function(l_stream, mem_read_fn);
    opj_stream_set_skip_function(l_stream, mem_skip_fn);
    opj_stream_set_seek_function(l_stream, mem_seek_fn);

    //Create decoder
    l_codec = opj_create_decompress(OPJ_CODEC_J2K); // Try OPJ_CODEC_JP2 if needed

    //Register a callback handlers for log messages
    opj_set_error_handler(l_codec, error_callback, NULL);
    opj_set_warning_handler(l_codec, warning_callback, NULL);
    opj_set_info_handler(l_codec, info_callback, NULL);

    if (!l_codec) {
        LOG_ERROR("Failed to create OpenJPEG codec.");
        goto cleanup;
    }
    if (!opj_setup_decoder(l_codec, &parameters)) {
        LOG_ERROR("Failed to setup decoder.");
        goto cleanup;
    }
    if (!opj_read_header(l_stream, l_codec, &l_image)) {
        LOG_ERROR("Failed to read image header.");
        goto cleanup;
    }
    if (!opj_decode(l_codec, l_stream, l_image)) {
        LOG_ERROR("Failed to decode image.");
        goto cleanup;
    }

    (*env)->ReleasePrimitiveArrayCritical(env, data, all_data, JNI_ABORT);
    all_data = NULL;

    if (!l_image || l_image->numcomps == 0) {
        LOG_ERROR("No image data found.");
        goto cleanup;
    }

    //Retrieve image format information
    opj_image_comp_t* comp = &l_image->comps[0];
    const int precision = comp->prec;     // e.g., 12, 16, 8
    const int is_signed = comp->sgnd;
    const int width  = comp->w;
    const int height = comp->h;
    const int pixelCount = width * height;

    //Singed ranges from -2^(precision-1) to (2^(precision-1))-1
    //Unsigned ranges from 0 to (2^precision)-1
    const int min_val = is_signed ? -(1 << (precision - 1)) : 0;
    const int max_val = is_signed ? (1 << (precision - 1)) - 1 : (1 << precision) - 1;

    LOG_INFO("Image data: components=%d, precision=%d, signed=%d, min_val=%d, max_val=%d, width=%d, height=%d, pixelCount=%d",
             l_image->numcomps, comp->prec, comp->sgnd, min_val, max_val, width, height, pixelCount);

    //Allocate and fill the output pixel array
    jbyteArray pixelArray = (*env)->NewByteArray(env, pixelCount);
    if (!pixelArray) {
        LOG_ERROR("Failed to allocate pixel array.");
        goto cleanup;
    }

    jbyte* outPixels = (jbyte*)(*env)->GetByteArrayElements(env, pixelArray, NULL);
    //Use first component only (greyscale)
    for (int i = 0; i < pixelCount; ++i) {
        int gray = ((comp->data[i] - min_val) * 255) / (max_val - min_val);
        if (gray < 0) gray = 0;
        if (gray > 255) gray = 255;
        outPixels[i] = (jbyte)gray;
    }
    (*env)->ReleaseByteArrayElements(env, pixelArray, outPixels, 0);

    //Manually invoke the constructor of the output object (RawImage type)
    jclass rawImageCls = (*env)->FindClass(env, "com/dbf/aqhi/codec/RawImage");
    if (!rawImageCls) {
        LOG_ERROR("Could not find RawImage class.");
        goto cleanup;
    }

    jmethodID constructor = (*env)->GetMethodID(env, rawImageCls, "<init>", "(II[B)V");
    if (!constructor) {
        LOG_ERROR("Could not find DecodedImage constructor");
        goto cleanup;
    }
    decodedImageObj = (*env)->NewObject(env, rawImageCls, constructor, width, height, pixelArray);

    //End of processing
    cleanup:
    if (l_image) opj_image_destroy(l_image);
    if (l_codec) opj_destroy_codec(l_codec);
    if (l_stream) opj_stream_destroy(l_stream);
    if (all_data) (*env)->ReleasePrimitiveArrayCritical(env, data, all_data, JNI_ABORT);

    return decodedImageObj;
}

