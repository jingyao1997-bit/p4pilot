package com.p4pilot;

/*
 * Stable camera-frame boundary for openpilot integration.
 *
 * The frame payload is NV12:
 *   Y plane first, followed by interleaved UV chroma.
 *
 * timestampSofNs:
 *   Camera2 Image timestamp / SENSOR_TIMESTAMP.
 *   This is the start of exposure of the first sensor row.
 *
 * timestampEofNs:
 *   timestampSofNs + SENSOR_ROLLING_SHUTTER_SKEW.
 *   For a typical rolling-shutter sensor this is the frame readout time.
 */
public final class CameraFrame {

    private final int frameId;
    private final int width;
    private final int height;

    private final long timestampSofNs;
    private final long timestampEofNs;

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
            long timestampSofNs,
            long timestampEofNs,
            long receivedTimestampMs,
            byte[] nv12) {

        if (frameId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid frameId"
            );
        }

        if (width <= 0 ||
                height <= 0 ||
                (width & 1) != 0 ||
                (height & 1) != 0) {

            throw new IllegalArgumentException(
                    "Invalid frame dimensions"
            );
        }

        if (timestampSofNs <= 0) {
            throw new IllegalArgumentException(
                    "Invalid SOF timestamp"
            );
        }

        if (timestampEofNs <= timestampSofNs) {
            throw new IllegalArgumentException(
                    "Invalid EOF timestamp"
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
        this.timestampSofNs = timestampSofNs;
        this.timestampEofNs = timestampEofNs;
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

    public long getTimestampSofNs() {
        return timestampSofNs;
    }

    public long getTimestampEofNs() {
        return timestampEofNs;
    }

    public long getReceivedTimestampMs() {
        return receivedTimestampMs;
    }

    public byte[] getNv12() {
        return nv12;
    }
}
