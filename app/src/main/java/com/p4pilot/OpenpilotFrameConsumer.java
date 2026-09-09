package com.p4pilot;

/*
 * CameraFrame -> native openpilot/VisionIPC boundary.
 */
public final class OpenpilotFrameConsumer
        implements CameraFrameConsumer {

    static {
        System.loadLibrary("p4pilot_bridge");
    }

    private static native boolean nativeInitialize(
            String appCacheDirectory);

    private static native boolean nativePushFrame(
            byte[] nv12,
            int frameId,
            int width,
            int height,
            long timestampSofNs,
            long timestampEofNs);

    public OpenpilotFrameConsumer(
            String appCacheDirectory) {

        if (appCacheDirectory == null ||
                appCacheDirectory.isEmpty()) {

            throw new IllegalArgumentException(
                    "App cache directory is empty"
            );
        }

        if (!nativeInitialize(
                appCacheDirectory)) {

            throw new IllegalStateException(
                    "Native VisionIPC initialization failed"
            );
        }
    }

    @Override
    public void onFrame(
            CameraFrame frame) {

        boolean accepted =
                nativePushFrame(
                        frame.getNv12(),
                        frame.getFrameId(),
                        frame.getWidth(),
                        frame.getHeight(),
                        frame.getTimestampSofNs(),
                        frame.getTimestampEofNs()
                );

        if (!accepted) {

            throw new IllegalStateException(
                    "Native VisionIPC rejected frame " +
                    frame.getFrameId()
            );
        }

        if (frame.getFrameId() == 1) {

            System.out.println(
                    "P4Pilot Step62 TIMING_FRAME_OK frame=" +
                    frame.getFrameId() +
                    " sofNs=" +
                    frame.getTimestampSofNs() +
                    " eofNs=" +
                    frame.getTimestampEofNs() +
                    " readoutNs=" +
                    (
                            frame.getTimestampEofNs() -
                            frame.getTimestampSofNs()
                    )
            );
        }
    }
}
