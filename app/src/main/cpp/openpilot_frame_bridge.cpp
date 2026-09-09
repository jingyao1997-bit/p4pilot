#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <cstdint>

namespace {

constexpr const char *TAG = "P4PilotNative";

std::atomic<uint64_t> frame_count{0};

}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_p4pilot_OpenpilotFrameConsumer_nativePushFrame(
        JNIEnv *env,
        jclass,
        jbyteArray nv12,
        jint frame_id,
        jint width,
        jint height,
        jlong sensor_timestamp_ns) {

    if (nv12 == nullptr) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                TAG,
                "P4Pilot Step60 NATIVE_REJECT null frame"
        );

        return JNI_FALSE;
    }

    if (width <= 0 ||
            height <= 0 ||
            (width & 1) != 0 ||
            (height & 1) != 0) {

        __android_log_print(
                ANDROID_LOG_ERROR,
                TAG,
                "P4Pilot Step60 NATIVE_REJECT dimensions=%dx%d",
                width,
                height
        );

        return JNI_FALSE;
    }

    if (sensor_timestamp_ns <= 0) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                TAG,
                "P4Pilot Step60 NATIVE_REJECT timestamp=%lld",
                static_cast<long long>(
                        sensor_timestamp_ns
                )
        );

        return JNI_FALSE;
    }

    const jsize actual_size =
            env->GetArrayLength(nv12);

    const int64_t expected_size =
            static_cast<int64_t>(width) *
            static_cast<int64_t>(height) *
            3LL / 2LL;

    if (actual_size != expected_size) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                TAG,
                "P4Pilot Step60 NATIVE_REJECT bytes=%d expected=%lld",
                actual_size,
                static_cast<long long>(
                        expected_size
                )
        );

        return JNI_FALSE;
    }

    /*
     * Hold the Java byte[] only briefly.
     *
     * We deliberately do not retain this pointer after returning.
     * The future VisionIPC implementation will copy/write the frame
     * into its own server-owned shared buffer here.
     */
    auto *data =
            static_cast<jbyte *>(
                    env->GetPrimitiveArrayCritical(
                            nv12,
                            nullptr
                    )
            );

    if (data == nullptr) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                TAG,
                "P4Pilot Step60 NATIVE_REJECT array access"
        );

        return JNI_FALSE;
    }

    const int y0 =
            static_cast<uint8_t>(
                    data[0]
            );

    const int uv_offset =
            width * height;

    const int u0 =
            static_cast<uint8_t>(
                    data[uv_offset]
            );

    const int v0 =
            static_cast<uint8_t>(
                    data[uv_offset + 1]
            );

    env->ReleasePrimitiveArrayCritical(
            nv12,
            data,
            JNI_ABORT
    );

    const uint64_t count =
            ++frame_count;

    if (count == 1) {
        __android_log_print(
                ANDROID_LOG_INFO,
                TAG,
                "P4Pilot Step60 NATIVE_BRIDGE_OK "
                "frame=%d bytes=%d size=%dx%d "
                "sensorTsNs=%lld y0=%d u0=%d v0=%d",
                frame_id,
                actual_size,
                width,
                height,
                static_cast<long long>(
                        sensor_timestamp_ns
                ),
                y0,
                u0,
                v0
        );
    }

    return JNI_TRUE;
}
