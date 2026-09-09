package com.p4pilot;

/*
 * Native hand-off boundary toward openpilot.
 *
 * Step60 does not yet instantiate VisionIpcServer.
 * It establishes and validates the Java -> C++ frame path
 * that VisionIPC will attach to next.
 */
public final class OpenpilotFrameConsumer
        implements CameraFrameConsumer {

    static {
        System.loadLibrary("p4pilot_bridge");
    }

    private static native boolean nativePushFrame(
            byte[] nv12,
            int frameId,
            int width,
            int height,
            long sensorTimestampNs);

    @Override
    public void onFrame(CameraFrame frame) {

        boolean accepted =
                nativePushFrame(
                        frame.getNv12(),
                        frame.getFrameId(),
                        frame.getWidth(),
                        frame.getHeight(),
                        frame.getSensorTimestampNs()
                );

        if (!accepted) {
            throw new IllegalStateException(
                    "Native openpilot bridge rejected frame " +
                    frame.getFrameId()
            );
        }

        if (frame.getFrameId() == 1) {
            System.out.println(
                    "P4Pilot Step60 FRAME_CONSUMER_NATIVE_OK frame=" +
                    frame.getFrameId() +
                    " bytes=" +
                    frame.getNv12().length +
                    " sensorTsNs=" +
                    frame.getSensorTimestampNs()
            );
        }
    }
}
