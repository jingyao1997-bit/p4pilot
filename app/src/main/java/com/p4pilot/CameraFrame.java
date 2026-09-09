package com.p4pilot;

/*
 * Stable camera-frame boundary for future openpilot integration.
 *
 * The frame payload is NV12:
 *   Y plane first, followed by interleaved VU chroma.
 */
public final class CameraFrame {

    private final int frameId;
    private final int width;
    private final int height;

    /*
     * Timestamp supplied by Camera2 Image.
     * Unit: nanoseconds.
     */
    private final long sensorTimestampNs;

    /*
     * Host-side time when the frame entered our Java pipeline.
     * Unit: milliseconds.
     */
    private final long receivedTimestampMs;

    private final byte[] nv12;

    public CameraFrame(
            int frameId,
            int width,
            int height,
            long sensorTimestampNs,
            long receivedTimestampMs,
            byte[] nv12) {

        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "Invalid frame dimensions"
            );
        }

        if (sensorTimestampNs <= 0) {
            throw new IllegalArgumentException(
                    "Invalid sensor timestamp"
            );
        }

        if (nv12 == null) {
            throw new IllegalArgumentException(
                    "NV12 payload is null"
            );
        }

        int expected =
                width * height * 3 / 2;

        if (nv12.length != expected) {
            throw new IllegalArgumentException(
                    "NV12 size=" +
                    nv12.length +
                    " expected=" +
                    expected
            );
        }

        this.frameId = frameId;
        this.width = width;
        this.height = height;
        this.sensorTimestampNs = sensorTimestampNs;
        this.receivedTimestampMs = receivedTimestampMs;
        this.nv12 = nv12;
    }

    public int getFrameId() {
        return frameId;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getSensorTimestampNs() {
        return sensorTimestampNs;
    }

    public long getReceivedTimestampMs() {
        return receivedTimestampMs;
    }

    public byte[] getNv12() {
        return nv12;
    }
}
