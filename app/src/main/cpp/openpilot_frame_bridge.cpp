#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <thread>

#include <cerrno>
#include <cstdlib>

#include <sys/stat.h>
#include <sys/un.h>
#include <unistd.h>

#include "msgq/visionipc/visionipc.h"
#include "msgq/visionipc/visionbuf.h"
#include "msgq/visionipc/visionipc_server.h"
#include "msgq/visionipc/visionipc_client.h"


namespace {

constexpr const char *TAG =
    "P4PilotNative";

/*
 * Verified by Step61 against the exact current
 * openpilot cereal/visionipc.py before building.
 */
constexpr VisionStreamType
    kNarrowRoadStream =
        static_cast<VisionStreamType>(0);

std::mutex g_mutex;

std::unique_ptr<VisionIpcServer>
    g_server;

std::string g_ipc_dir;

int g_width = 0;
int g_height = 0;

std::atomic<bool>
    g_client_test_started{false};

std::atomic<bool>
    g_client_test_ok{false};

std::atomic<uint64_t>
    g_send_count{0};


void log_error(
    const char *message) {

  __android_log_print(
      ANDROID_LOG_ERROR,
      TAG,
      "P4Pilot Step61 VIPC_ERROR %s",
      message
  );
}


bool ensure_ipc_directory(
    const std::string &cache_dir) {

  if (cache_dir.empty()) {
    return false;
  }

  g_ipc_dir =
      cache_dir + "/p4pilot_ipc";

  if (mkdir(
          g_ipc_dir.c_str(),
          0700
      ) != 0 &&
      errno != EEXIST) {

    return false;
  }

  /*
   * sockaddr_un::sun_path is small.
   * Reject an unexpectedly long Android cache path.
   */
  std::string socket_path =
      g_ipc_dir +
      "/visionipc_camerad";

  if (socket_path.size() >=
      sizeof(sockaddr_un::sun_path)) {

    return false;
  }

  /*
   * Remove stale state from a previous app process.
   */
  unlink(
      socket_path.c_str()
  );

  unlink(
      (
          g_ipc_dir +
          "/msgq_visionipc_camerad_0"
      ).c_str()
  );

  unsetenv(
      "OPENPILOT_PREFIX"
  );

  if (setenv(
          "P4PILOT_IPC_DIR",
          g_ipc_dir.c_str(),
          1
      ) != 0) {

    return false;
  }

  return true;
}


void start_native_client_test() {

  bool expected = false;

  if (!g_client_test_started
          .compare_exchange_strong(
              expected,
              true
          )) {

    return;
  }


  std::thread([]() {

    /*
     * This is the upstream VisionIpcClient from the
     * exact msgq commit pinned by current openpilot.
     */
    VisionIpcClient client(
        "camerad",
        kNarrowRoadStream,
        true
    );

    bool connected = false;

    for (int attempt = 0;
         attempt < 100;
         ++attempt) {

      if (client.connect(false)) {
        connected = true;
        break;
      }

      std::this_thread::sleep_for(
          std::chrono::milliseconds(20)
      );
    }

    if (!connected) {

      log_error(
          "client connect timeout"
      );

      return;
    }


    for (int attempt = 0;
         attempt < 100;
         ++attempt) {

      VisionIpcBufExtra extra{};

      VisionBuf *buf =
          client.recv(
              &extra,
              100
          );

      if (buf == nullptr) {
        continue;
      }

      if (buf->addr == nullptr) {

        log_error(
            "client received null buffer"
        );

        return;
      }

      const size_t expected_bytes =
          static_cast<size_t>(buf->width) *
          static_cast<size_t>(buf->height) *
          3U / 2U;

      if (buf->len != expected_bytes) {

        log_error(
            "client buffer size mismatch"
        );

        return;
      }

      if (buf->width == 0 ||
          buf->height == 0 ||
          buf->stride != buf->width ||
          buf->uv_offset !=
              buf->width * buf->height) {

        log_error(
            "client buffer metadata mismatch"
        );

        return;
      }

      if (!extra.valid ||
          extra.timestamp_sof == 0 ||
          extra.timestamp_eof <=
              extra.timestamp_sof) {

        log_error(
            "client timestamp metadata invalid"
        );

        return;
      }

      const uint64_t shared_frame_id =
          buf->get_frame_id();

      if (shared_frame_id !=
          extra.frame_id) {

        log_error(
            "shared frame id mismatch"
        );

        return;
      }

      auto *bytes =
          static_cast<uint8_t *>(
              buf->addr
          );

      const size_t uv_offset =
          buf->uv_offset;

      __android_log_print(
          ANDROID_LOG_INFO,
          TAG,
          "P4Pilot Step61 VIPC_CLIENT_OK "
          "stream=0 frame=%u "
          "sharedFrame=%llu "
          "bytes=%zu size=%zux%zu "
          "stride=%zu uvOffset=%zu "
          "timestampSof=%llu "
          "timestampEof=%llu "
          "y0=%u u0=%u v0=%u",
          extra.frame_id,
          static_cast<unsigned long long>(
              shared_frame_id
          ),
          buf->len,
          buf->width,
          buf->height,
          buf->stride,
          buf->uv_offset,
          static_cast<unsigned long long>(
              extra.timestamp_sof
          ),
          static_cast<unsigned long long>(
              extra.timestamp_eof
          ),
          static_cast<unsigned>(
              bytes[0]
          ),
          static_cast<unsigned>(
              bytes[uv_offset]
          ),
          static_cast<unsigned>(
              bytes[uv_offset + 1]
          )
      );

      __android_log_print(
          ANDROID_LOG_INFO,
          TAG,
          "P4Pilot Step62 VIPC_TIMING_OK "
          "frame=%u sofNs=%llu eofNs=%llu "
          "readoutNs=%llu",
          extra.frame_id,
          static_cast<unsigned long long>(
              extra.timestamp_sof
          ),
          static_cast<unsigned long long>(
              extra.timestamp_eof
          ),
          static_cast<unsigned long long>(
              extra.timestamp_eof -
              extra.timestamp_sof
          )
      );

      g_client_test_ok = true;

      return;
    }

    log_error(
        "client receive timeout"
    );

  }).detach();
}


bool ensure_server(
    int width,
    int height) {

  std::lock_guard<std::mutex>
      lock(g_mutex);

  if (g_server) {

    return
        width == g_width &&
        height == g_height;
  }


  if (width <= 0 ||
      height <= 0 ||
      (width & 1) != 0 ||
      (height & 1) != 0) {

    return false;
  }


  g_width = width;
  g_height = height;

  g_server =
      std::make_unique<
          VisionIpcServer
      >(
          "camerad"
      );

  /*
   * Upstream create_buffers() creates tightly packed
   * YUV420/NV12 buffers:
   *
   * size      = width * height * 3 / 2
   * stride    = width
   * uv_offset = width * height
   */
  g_server->create_buffers(
      kNarrowRoadStream,
      4,
      static_cast<size_t>(width),
      static_cast<size_t>(height)
  );

  VisionBuf *check =
      g_server->get_buffer(
          kNarrowRoadStream,
          0
      );

  if (check == nullptr ||
      check->addr == nullptr ||
      check->fd < 0 ||
      check->len !=
          static_cast<size_t>(
              width * height * 3 / 2
          )) {

    g_server.reset();

    return false;
  }

  g_server->start_listener();

  __android_log_print(
      ANDROID_LOG_INFO,
      TAG,
      "P4Pilot Step61 VIPC_SERVER_OK "
      "name=camerad stream=0 "
      "buffers=4 size=%dx%d "
      "bytes=%zu stride=%zu "
      "uvOffset=%zu ipcDir=%s",
      width,
      height,
      check->len,
      check->stride,
      check->uv_offset,
      g_ipc_dir.c_str()
  );

  start_native_client_test();

  return true;
}

}  // namespace


extern "C"
JNIEXPORT jboolean JNICALL
Java_com_p4pilot_OpenpilotFrameConsumer_nativeInitialize(
    JNIEnv *env,
    jclass,
    jstring cache_directory) {

  if (cache_directory == nullptr) {

    log_error(
        "null cache directory"
    );

    return JNI_FALSE;
  }

  const char *chars =
      env->GetStringUTFChars(
          cache_directory,
          nullptr
      );

  if (chars == nullptr) {

    log_error(
        "GetStringUTFChars failed"
    );

    return JNI_FALSE;
  }

  std::string cache_dir(chars);

  env->ReleaseStringUTFChars(
      cache_directory,
      chars
  );

  if (!ensure_ipc_directory(
          cache_dir)) {

    log_error(
        "IPC directory initialization failed"
    );

    return JNI_FALSE;
  }

  __android_log_print(
      ANDROID_LOG_INFO,
      TAG,
      "P4Pilot Step61 VIPC_ANDROID_INIT_OK "
      "ipcDir=%s",
      g_ipc_dir.c_str()
  );

  return JNI_TRUE;
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
    jlong timestamp_sof_ns,
    jlong timestamp_eof_ns) {

  if (nv12 == nullptr) {

    log_error(
        "null NV12 frame"
    );

    return JNI_FALSE;
  }

  if (timestamp_sof_ns <= 0 ||
      timestamp_eof_ns <= timestamp_sof_ns) {

    log_error(
        "invalid camera SOF/EOF timestamps"
    );

    return JNI_FALSE;
  }


  const int64_t expected_size =
      static_cast<int64_t>(width) *
      static_cast<int64_t>(height) *
      3LL / 2LL;

  const jsize actual_size =
      env->GetArrayLength(
          nv12
      );

  if (expected_size <= 0 ||
      actual_size != expected_size) {

    log_error(
        "NV12 size mismatch"
    );

    return JNI_FALSE;
  }


  if (!ensure_server(
          width,
          height)) {

    log_error(
        "VisionIPC server init failed"
    );

    return JNI_FALSE;
  }


  VisionBuf *buf =
      g_server->get_buffer(
          kNarrowRoadStream
      );

  if (buf == nullptr ||
      buf->addr == nullptr ||
      buf->len !=
          static_cast<size_t>(
              actual_size
          )) {

    log_error(
        "VisionIPC output buffer invalid"
    );

    return JNI_FALSE;
  }


  env->GetByteArrayRegion(
      nv12,
      0,
      actual_size,
      reinterpret_cast<jbyte *>(
          buf->addr
      )
  );

  if (env->ExceptionCheck()) {

    env->ExceptionClear();

    log_error(
        "JNI NV12 copy failed"
    );

    return JNI_FALSE;
  }


  buf->set_frame_id(
      static_cast<uint64_t>(
          frame_id
      )
  );


  VisionIpcBufExtra extra{};

  extra.frame_id =
      static_cast<uint32_t>(
          frame_id
      );

  extra.timestamp_sof =
      static_cast<uint64_t>(
          timestamp_sof_ns
      );

  extra.timestamp_eof =
      static_cast<uint64_t>(
          timestamp_eof_ns
      );

  extra.valid = true;


  g_server->send(
      buf,
      &extra,
      false
  );


  const uint64_t send_count =
      ++g_send_count;


  if (send_count == 1) {

    auto *bytes =
        static_cast<uint8_t *>(
            buf->addr
        );

    const size_t uv_offset =
        buf->uv_offset;

    __android_log_print(
        ANDROID_LOG_INFO,
        TAG,
        "P4Pilot Step61 VIPC_SEND_OK "
        "stream=0 frame=%d "
        "bytes=%d sofNs=%lld eofNs=%lld "
        "y0=%u u0=%u v0=%u",
        frame_id,
        actual_size,
        static_cast<long long>(
            timestamp_sof_ns
        ),
        static_cast<long long>(
            timestamp_eof_ns
        ),
        static_cast<unsigned>(
            bytes[0]
        ),
        static_cast<unsigned>(
            bytes[uv_offset]
        ),
        static_cast<unsigned>(
            bytes[uv_offset + 1]
        )
    );
  }


  return JNI_TRUE;
}
