package com.p4pilot;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.view.Surface;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends Activity {

    private CameraDevice camera;
    private ImageReader reader;
    private CameraCaptureSession session;
    /*
     * Step54:
     *
     * mainHandler:
     *   UI / lifecycle related work only.
     *
     * cameraThread:
     *   ImageReader callback and Camera2 callbacks.
     *
     * processingThread:
     *   Copied YUV -> NV12 processing work.
     *
     * ImageReader Image objects NEVER leave cameraThread.
     */
    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private HandlerThread cameraThread;
    private Handler cameraHandler;

    private HandlerThread processingThread;
    private Handler processingHandler;

    private TextView tv;

    private volatile int frameCount = 0;
    private volatile int processedCount = 0;
    private volatile int droppedCount = 0;

    private long firstFrameTime = 0;

    /*
     * Step58:
     * Camera processing now hands frames through one explicit
     * consumer boundary. Future openpilot integration attaches here.
     */
    private final CameraFrameConsumer cameraFrameConsumer =
            frame -> {
                if (frame.getFrameId() == 1) {
                    System.out.println(
                            "P4Pilot Step58 FRAME_CONSUMER_OK frame=" +
                            frame.getFrameId() +
                            " bytes=" +
                            frame.getNv12().length +
                            " sensorTsNs=" +
                            frame.getSensorTimestampNs() +
                            " receivedTsMs=" +
                            frame.getReceivedTimestampMs()
                    );
                }
            };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tv = new TextView(this);
        tv.setTextSize(16);
        tv.setPadding(30, 30, 30, 30);
        tv.setText("P4Pilot Step 49\n正在初始化...");
        setContentView(tv);


        /*
         * Step54 background execution model.
         */
        cameraThread = new HandlerThread(
                "P4Pilot-CameraThread",
                android.os.Process.THREAD_PRIORITY_DISPLAY
        );
        cameraThread.start();
        cameraHandler = new Handler(
                cameraThread.getLooper()
        );

        processingThread = new HandlerThread(
                "P4Pilot-ProcessingThread",
                android.os.Process.THREAD_PRIORITY_DEFAULT
        );
        processingThread.start();
        processingHandler = new Handler(
                processingThread.getLooper()
        );

        System.out.println(
                "P4Pilot Step54 CAMERA_THREAD=" +
                cameraThread.getName()
        );

        System.out.println(
                "P4Pilot Step54 PROCESSING_THREAD=" +
                processingThread.getName()
        );

        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.CAMERA}, 100);
            return;
        }

        startCamera();
    }

    private void startCamera() {

        try {
            CameraManager manager =
                    (CameraManager) getSystemService(CAMERA_SERVICE);

            String cameraId = null;

            for (String id : manager.getCameraIdList()) {

                CameraCharacteristics c =
                        manager.getCameraCharacteristics(id);

                Integer facing =
                        c.get(CameraCharacteristics.LENS_FACING);

                if (facing != null &&
                        facing == CameraCharacteristics.LENS_FACING_BACK) {

                    cameraId = id;
                    break;
                }
            }

            if (cameraId == null) {
                tv.setText("ERROR: 找不到后置摄像头");
                return;
            }

            CameraCharacteristics c =
                    manager.getCameraCharacteristics(cameraId);

            android.util.Size[] sizes =
                    c.get(
                            CameraCharacteristics
                                    .SCALER_STREAM_CONFIGURATION_MAP
                    ).getOutputSizes(ImageFormat.YUV_420_888);

            android.util.Size selected = sizes[0];

            for (android.util.Size s : sizes) {

                if (s.getWidth() == 1280 &&
                        s.getHeight() == 720) {

                    selected = s;
                    break;
                }
            }

            final int width = selected.getWidth();
            final int height = selected.getHeight();

            reader = ImageReader.newInstance(
                    width,
                    height,
                    ImageFormat.YUV_420_888,
                    4
            );

            reader.setOnImageAvailableListener(
                    imageReader -> {

                        Image image = null;

                        try {

                            /*
                             * Step53:
                             * ImageReader callback must remain lightweight.
                             *
                             * We acquire the newest frame, copy the YUV planes
                             * into independent byte arrays, and CLOSE the Image
                             * before doing any expensive conversion.
                             */

                            image = imageReader.acquireLatestImage();

                            if (image == null) {
                                return;
                            }

                            final int w = image.getWidth();
                            final int h = image.getHeight();

                            Image.Plane[] planes = image.getPlanes();

                            if (planes.length != 3) {
                                System.err.println(
                                        "P4Pilot Step54 INVALID_PLANES=" +
                                        planes.length
                                );
                                return;
                            }

                            /*
                             * Step55: bounded processing backpressure.
                             *
                             * Do this BEFORE allocating copied Y/U/V arrays.
                             * If ProcessingThread already has a pending frame,
                             * discard this camera frame instead of growing the
                             * Handler queue without bound.
                             */
                            if (processingHandler == null ||
                                    !processingHandler.getLooper()
                                            .getQueue()
                                            .isIdle()) {
                                droppedCount++;
                            
                                if (droppedCount <= 5 ||
                                        droppedCount % 30 == 0) {
                            
                                    System.out.println(
                                            "P4Pilot Step55 DROPPED_BUSY count=" +
                                            droppedCount
                                    );
                                }
                            
                                return;
                            }
                            
                            final byte[] yData;
                            final byte[] uData;
                            final byte[] vData;

                            try {

                                ByteBuffer yBuffer =
                                        planes[0].getBuffer().duplicate();

                                ByteBuffer uBuffer =
                                        planes[1].getBuffer().duplicate();

                                ByteBuffer vBuffer =
                                        planes[2].getBuffer().duplicate();

                                yData = new byte[yBuffer.remaining()];
                                uData = new byte[uBuffer.remaining()];
                                vData = new byte[vBuffer.remaining()];

                                yBuffer.get(yData);
                                uBuffer.get(uData);
                                vBuffer.get(vData);

                            } catch (Exception copyException) {

                                System.err.println(
                                        "P4Pilot Step54 YUV_COPY_ERROR"
                                );

                                copyException.printStackTrace();

                                return;
                            }

                            frameCount++;

                            /*
                             * Step54:
                             * acquireLatestImage() intentionally keeps
                             * the newest available frame. Older frames
                             * may therefore be dropped by Camera2.
                             *
                             * We cannot directly count every HAL-level
                             * discarded frame here, so this counter tracks
                             * processing-side drops when the queue is busy.
                             */
                            long now =
                                    System.currentTimeMillis();

                            if (firstFrameTime == 0) {

                                firstFrameTime = now;

                                System.out.println(
                                        "P4Pilot Step54 FIRST_FRAME"
                                );

                                System.out.println(
                                        "P4Pilot Step54 FRAME_START"
                                );

                                System.out.println(
                                        "P4Pilot Step54 width=" +
                                        w +
                                        " height=" +
                                        h +
                                        " format=" +
                                        ImageFormat.YUV_420_888
                                );

                                for (int i = 0;
                                     i < planes.length;
                                     i++) {

                                    Image.Plane plane =
                                            planes[i];

                                    ByteBuffer buffer =
                                            plane.getBuffer();

                                    System.out.println(
                                            "P4Pilot Step54 plane[" +
                                            i +
                                            "] rowStride=" +
                                            plane.getRowStride() +
                                            " pixelStride=" +
                                            plane.getPixelStride() +
                                            " remaining=" +
                                            buffer.remaining() +
                                            " limit=" +
                                            buffer.limit()
                                    );
                                }
                            }

                            /*
                             * CRITICAL:
                             * Image is no longer used after this point.
                             * finally{} below releases the Camera2 buffer.
                             */

                            final long sensorTimestampNs =
                                    image.getTimestamp();

                            final long frameTimestamp =
                                    now;

                            final int currentFrame =
                                    frameCount;

                            final int yRowStride =
                                    planes[0].getRowStride();

                            final int yPixelStride =
                                    planes[0].getPixelStride();

                            final int uRowStride =
                                    planes[1].getRowStride();

                            final int uPixelStride =
                                    planes[1].getPixelStride();

                            final int vRowStride =
                                    planes[2].getRowStride();

                            final int vPixelStride =
                                    planes[2].getPixelStride();

                            /*
                             * Step54:
                             * The Image has already been closed.
                             * Processing now happens exclusively on the
                             * dedicated processing HandlerThread.
                             */
                            if (!processingHandler.post(() -> {

                                long processStart =
                                        System.currentTimeMillis();

                                try {

                                    /*
                                     * Step53 processing is deliberately
                                     * performed outside ImageReader callback.
                                     */

                                    byte[] nv12 =
                                            yuv420ToNv12FromCopiedPlanes(
                                                    w,
                                                    h,
                                                    yData,
                                                    uData,
                                                    vData,
                                                    yRowStride,
                                                    yPixelStride,
                                                    uRowStride,
                                                    uPixelStride,
                                                    vRowStride,
                                                    vPixelStride
                                            );

                                    int expectedNv12Bytes =
                                            w * h * 3 / 2;

                                    if (nv12.length != expectedNv12Bytes) {
                                        throw new IllegalStateException(
                                                "NV12 length mismatch: " +
                                                nv12.length +
                                                " expected=" +
                                                expectedNv12Bytes
                                        );
                                    }

                                    if (currentFrame == 1) {
                                        System.out.println(
                                                "P4Pilot Step59 NV12_OK bytes=" +
                                                nv12.length +
                                                " y0=" +
                                                (nv12[0] & 0xff) +
                                                " u0=" +
                                                (nv12[w * h] & 0xff) +
                                                " v0=" +
                                                (nv12[w * h + 1] & 0xff)
                                        );
                                    }

                                    CameraFrame cameraFrame =
                                            new CameraFrame(
                                                    currentFrame,
                                                    w,
                                                    h,
                                                    sensorTimestampNs,
                                                    frameTimestamp,
                                                    nv12
                                            );

                                    cameraFrameConsumer.onFrame(
                                            cameraFrame
                                    );

                                    processedCount++;

                                    /*
                                     * Step56:
                                     * Legacy Step53 JPEG write/decode
                                     * self-test removed from realtime path.
                                     */

                                    long processElapsed =
                                            System.currentTimeMillis() -
                                            processStart;

                                    System.out.println(
                                            "P4Pilot Step54 PROCESSED frame=" +
                                            currentFrame +
                                            " processMs=" +
                                            processElapsed +
                                            " totalMs=" +
                                            (System.currentTimeMillis() -
                                             frameTimestamp)
                                    );

                                    if (currentFrame == 10 ||
                                        currentFrame == 30 ||
                                        currentFrame == 60 ||
                                        currentFrame == 100) {

                                        long elapsed =
                                                System.currentTimeMillis() -
                                                firstFrameTime;

                                        double fps =
                                                elapsed > 0
                                                ? (currentFrame - 1) *
                                                  1000.0 /
                                                  elapsed
                                                : 0;

                                        System.out.println(
                                                "P4Pilot Step54 FRAME=" +
                                                currentFrame +
                                                " elapsedMs=" +
                                                elapsed +
                                                " fps=" +
                                                String.format(
                                                        "%.2f",
                                                        fps
                                                )
                                        );
                                    }

                                    final int displayedFrame =
                                            currentFrame;

                                    runOnUiThread(() ->
                                            tv.setText(
                                                    "P4Pilot Step 54\n\n" +
                                                    "Camera: BACK\n" +
                                                    "Format: YUV_420_888\n" +
                                                    "Resolution: " +
                                                    w + " x " + h + "\n" +
                                                    "Captured: " +
                                                    frameCount + "\n" +
                                                    "Processed: " +
                                                    processedCount + "\n" +
                                                    "Current frame: " +
                                                    displayedFrame + "\n\n" +
                                                    "Status: BACKGROUND PROCESSING"
                                            )
                                    );


                                } catch (Exception processingException) {

                                    System.err.println(
                                            "P4Pilot Step54 PROCESS_ERROR frame=" +
                                            currentFrame
                                    );

                                    processingException.printStackTrace();
                                }
                            })) {
                                droppedCount++;

                                System.out.println(
                                        "P4Pilot Step54 DROPPED frame=" +
                                        currentFrame +
                                        " reason=PROCESS_QUEUE_REJECTED"
                                );
                            }

                        } catch (Exception e) {

                            System.err.println(
                                    "P4Pilot Step54 FRAME_ERROR"
                            );

                            e.printStackTrace();

                        } finally {

                            /*
                             * Camera2 buffer is ALWAYS released here.
                             * No background task owns the Image object.
                             */

                            if (image != null) {
                                image.close();
                            }
                        }

                    },
                    cameraHandler
            );

manager.openCamera(
                    cameraId,
                    new CameraDevice.StateCallback() {

                        @Override
                        public void onOpened(
                                CameraDevice cameraDevice) {

                            camera = cameraDevice;

                            try {

                                Surface surface =
                                        reader.getSurface();

                                CaptureRequest.Builder request =
                                        camera.createCaptureRequest(
                                                CameraDevice
                                                        .TEMPLATE_PREVIEW
                                        );

                                request.addTarget(surface);

                                camera.createCaptureSession(
                                        Arrays.asList(surface),

                                        new CameraCaptureSession
                                                .StateCallback() {

                                            @Override
                                            public void onConfigured(
                                                    CameraCaptureSession s) {

                                                session = s;

                                                try {

                                                    session
                                                            .setRepeatingRequest(
                                                                    request
                                                                            .build(),
                                                                    null,
                                                                    cameraHandler
                                                            );

                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                            }

                                            @Override
                                            public void onConfigureFailed(
                                                    CameraCaptureSession s) {

                                                runOnUiThread(() ->
                                                        tv.setText(
                                                                "ERROR: " +
                                                                "CameraSession配置失败"
                                                        ));
                                            }
                                        },

                                        cameraHandler
                                );

                            } catch (Exception e) {

                                e.printStackTrace();

                                runOnUiThread(() ->
                                        tv.setText(
                                                "Camera exception:\n" +
                                                e.toString()
                                        ));
                            }
                        }

                        @Override
                        public void onDisconnected(
                                CameraDevice cameraDevice) {

                            cameraDevice.close();

                            runOnUiThread(() ->
                                    tv.setText(
                                            "Camera disconnected"
                                    ));
                        }

                        @Override
                        public void onError(
                                CameraDevice cameraDevice,
                                int error) {

                            cameraDevice.close();

                            runOnUiThread(() ->
                                    tv.setText(
                                            "Camera ERROR: " +
                                            error
                                    ));
                        }
                    },
                    cameraHandler
            );

        } catch (Exception e) {

            e.printStackTrace();

            tv.setText(
                    "Camera exception:\n" +
                    e.toString()
            );
        }
    }

    
    private byte[] yuv420ToNv12FromCopiedPlanes(
            int width,
            int height,
            byte[] yData,
            byte[] uData,
            byte[] vData,
            int yRowStride,
            int yPixelStride,
            int uRowStride,
            int uPixelStride,
            int vRowStride,
            int vPixelStride) {

        if ((width & 1) != 0 ||
                (height & 1) != 0) {
            throw new IllegalArgumentException(
                    "NV12 requires even dimensions"
            );
        }

        int frameSize =
                width * height;

        byte[] nv12 =
                new byte[
                        frameSize +
                        frameSize / 2
                ];

        int outputIndex = 0;

        /*
         * Y plane.
         */
        for (int row = 0;
                row < height;
                row++) {

            int rowBase =
                    row * yRowStride;

            for (int col = 0;
                    col < width;
                    col++) {

                int sourceIndex =
                        rowBase +
                        col * yPixelStride;

                if (sourceIndex < 0 ||
                        sourceIndex >= yData.length) {

                    throw new IllegalStateException(
                            "Y index out of range: " +
                            sourceIndex
                    );
                }

                nv12[outputIndex++] =
                        yData[sourceIndex];
            }
        }

        /*
         * NV12 chroma:
         *
         *   U V U V U V ...
         *
         * NV12 chroma ordering is U V U V.
         * The previous V U ordering is intentionally not used here.
         */
        int chromaWidth =
                width / 2;

        int chromaHeight =
                height / 2;

        for (int row = 0;
                row < chromaHeight;
                row++) {

            int uRowBase =
                    row * uRowStride;

            int vRowBase =
                    row * vRowStride;

            for (int col = 0;
                    col < chromaWidth;
                    col++) {

                int uIndex =
                        uRowBase +
                        col * uPixelStride;

                int vIndex =
                        vRowBase +
                        col * vPixelStride;

                if (uIndex < 0 ||
                        uIndex >= uData.length) {

                    throw new IllegalStateException(
                            "U index out of range: " +
                            uIndex
                    );
                }

                if (vIndex < 0 ||
                        vIndex >= vData.length) {

                    throw new IllegalStateException(
                            "V index out of range: " +
                            vIndex
                    );
                }

                nv12[outputIndex++] =
                        uData[uIndex];

                nv12[outputIndex++] =
                        vData[vIndex];
            }
        }

        if (outputIndex != nv12.length) {
            throw new IllegalStateException(
                    "NV12 output size=" +
                    outputIndex +
                    " expected=" +
                    nv12.length
            );
        }

        return nv12;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        System.out.println(
                "P4Pilot Step54 onDestroy"
        );

        try {
            if (session != null) {
                session.close();
                session = null;
            }
        } catch (Exception ignored) {
        }

        try {
            if (camera != null) {
                camera.close();
                camera = null;
            }
        } catch (Exception ignored) {
        }

        try {
            if (reader != null) {
                reader.close();
                reader = null;
            }
        } catch (Exception ignored) {
        }

        try {
            if (cameraThread != null) {
                cameraThread.quitSafely();
                cameraThread = null;
                cameraHandler = null;
            }
        } catch (Exception ignored) {
        }

        try {
            if (processingThread != null) {
                processingThread.quitSafely();
                processingThread = null;
                processingHandler = null;
            }
        } catch (Exception ignored) {
        }
    }

}
