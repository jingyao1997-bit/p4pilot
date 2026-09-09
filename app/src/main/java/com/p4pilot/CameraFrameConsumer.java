package com.p4pilot;

/*
 * Single hand-off point for processed camera frames.
 *
 * A future openpilot adapter will implement this interface.
 */
public interface CameraFrameConsumer {

    void onFrame(CameraFrame frame);
}
