#pragma once

#include <android/log.h>

#define LOGV(...) \
    __android_log_print( \
        ANDROID_LOG_VERBOSE, \
        "P4PilotVipc", \
        __VA_ARGS__ \
    )

#define LOGD(...) \
    __android_log_print( \
        ANDROID_LOG_DEBUG, \
        "P4PilotVipc", \
        __VA_ARGS__ \
    )

#define LOGI(...) \
    __android_log_print( \
        ANDROID_LOG_INFO, \
        "P4PilotVipc", \
        __VA_ARGS__ \
    )

#define LOGW(...) \
    __android_log_print( \
        ANDROID_LOG_WARN, \
        "P4PilotVipc", \
        __VA_ARGS__ \
    )

#define LOGE(...) \
    __android_log_print( \
        ANDROID_LOG_ERROR, \
        "P4PilotVipc", \
        __VA_ARGS__ \
    )
