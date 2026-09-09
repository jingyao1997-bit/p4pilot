cmake_minimum_required(VERSION 3.22.1)

project(p4pilot_bridge)

add_library(
        p4pilot_bridge
        SHARED
        openpilot_frame_bridge.cpp
)

find_library(
        android_log
        log
)

target_compile_features(
        p4pilot_bridge
        PRIVATE
        cxx_std_17
)

target_link_libraries(
        p4pilot_bridge
        ${android_log}
)
