cmake_minimum_required(VERSION 3.22.1)

project(p4pilot_bridge)


set(
    MSGQ_ROOT
    "${CMAKE_CURRENT_SOURCE_DIR}/third_party/msgq_upstream"
)


add_library(
    p4pilot_bridge
    SHARED

    openpilot_frame_bridge.cpp

    # commaai/msgq core
    ${MSGQ_ROOT}/msgq/ipc.cc
    ${MSGQ_ROOT}/msgq/event.cc
    ${MSGQ_ROOT}/msgq/impl_msgq.cc
    ${MSGQ_ROOT}/msgq/impl_fake.cc
    ${MSGQ_ROOT}/msgq/msgq.cc

    # commaai/msgq VisionIPC
    ${MSGQ_ROOT}/msgq/visionipc/visionipc.cc
    ${MSGQ_ROOT}/msgq/visionipc/visionipc_server.cc
    ${MSGQ_ROOT}/msgq/visionipc/visionipc_client.cc

    # Android replacement for upstream Linux VisionBuf
    visionbuf_android.cpp
)


target_include_directories(
    p4pilot_bridge
    PRIVATE
    ${MSGQ_ROOT}
)


target_compile_features(
    p4pilot_bridge
    PRIVATE
    cxx_std_17
)


target_compile_options(
    p4pilot_bridge
    PRIVATE
    -Wno-vla-cxx-extension
)


find_library(
    android_log
    log
)

find_library(
    android_native
    android
)


target_link_libraries(
    p4pilot_bridge
    ${android_log}
    ${android_native}
)
