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

import java.io.File;
import java.io.FileOutputStream;
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
     *   YUV -> Bitmap -> JPEG CPU work.
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
    private volatile int savedCount = 0;

    private long firstFrameTime = 0;

    private File outputDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tv = new TextView(this);
        tv.setTextSize(16);
        tv.setPadding(30, 30, 30, 30);
        tv.setText("P4Pilot Step 49\n正在初始化...");
        setContentView(tv);

        File externalDir = getExternalFilesDir(null);

        outputDir = new File(
                externalDir,
                "P4Pilot/step41"
        );

        if (!outputDir.exists()) {
            boolean created = outputDir.mkdirs();
            System.out.println(
                    "P4Pilot outputDir mkdirs=" + created
            );
        }

        System.out.println(
                "P4Pilot outputDir=" +
                outputDir.getAbsolutePath()
        );

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

                                    android.graphics.Bitmap bitmap =
                                            yuv420ToBitmapFromCopiedPlanes(
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

                                    if (bitmap == null) {

                                        System.err.println(
                                                "P4Pilot Step54 BITMAP_NULL frame=" +
                                                currentFrame
                                        );

                                        return;
                                    }

                                    processedCount++;
                                    savedCount++;

                                    if (currentFrame == 1) {

                                        System.out.println(
                                                "P4Pilot Step54 BITMAP_OK width=" +
                                                bitmap.getWidth() +
                                                " height=" +
                                                bitmap.getHeight()
                                        );

                                        File testFile =
                                                new File(
                                                        outputDir,
                                                        "step53_test.jpg"
                                                );

                                        FileOutputStream fos =
                                                new FileOutputStream(
                                                        testFile
                                                );

                                        bitmap.compress(
                                                android.graphics.Bitmap
                                                        .CompressFormat.JPEG,
                                                90,
                                                fos
                                        );

                                        fos.flush();
                                        fos.close();

                                        android.graphics.Bitmap decoded =
                                                android.graphics.BitmapFactory
                                                        .decodeFile(
                                                                testFile
                                                                        .getAbsolutePath()
                                                        );

                                        if (decoded != null) {

                                            System.out.println(
                                                    "P4Pilot Step54 JPEG_DECODE_OK"
                                            );

                                            System.out.println(
                                                    "P4Pilot Step54 JPEG_PATH=" +
                                                    testFile.getAbsolutePath()
                                            );

                                            decoded.recycle();

                                        } else {

                                            System.err.println(
                                                    "P4Pilot Step54 JPEG_DECODE_FAIL"
                                            );
                                        }
                                    }

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

                                    bitmap.recycle();

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

    
    private android.graphics.Bitmap yuv420ToBitmapFromCopiedPlanes(
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

        byte[] nv21 =
                new byte[
                        width * height +
                        (width * height) / 2
                ];

        int outputIndex = 0;

        for (int row = 0; row < height; row++) {

            int rowOffset =
                    row * yRowStride;

            for (int col = 0; col < width; col++) {

                int index =
                        rowOffset +
                        col * yPixelStride;

                if (index < 0 || index >= yData.length) {
                    throw new IllegalArgumentException(
                            "Y index out of range: " + index +
                            " / " + yData.length
                    );
                }

                nv21[outputIndex++] =
                        yData[index];
            }
        }

        int chromaHeight =
                height / 2;

        int chromaWidth =
                width / 2;

        for (int row = 0;
             row < chromaHeight;
             row++) {

            int uRowOffset =
                    row * uRowStride;

            int vRowOffset =
                    row * vRowStride;

            for (int col = 0;
                 col < chromaWidth;
                 col++) {

                int uIndex =
                        uRowOffset +
                        col * uPixelStride;

                int vIndex =
                        vRowOffset +
                        col * vPixelStride;

                if (uIndex < 0 ||
                    uIndex >= uData.length ||
                    vIndex < 0 ||
                    vIndex >= vData.length) {

                    throw new IllegalArgumentException(
                            "UV index out of range: U=" +
                            uIndex +
                            "/" +
                            uData.length +
                            " V=" +
                            vIndex +
                            "/" +
                            vData.length
                    );
                }

                nv21[outputIndex++] =
                        vData[vIndex];

                nv21[outputIndex++] =
                        uData[uIndex];
            }
        }

        android.graphics.YuvImage yuv =
                new android.graphics.YuvImage(
                        nv21,
                        ImageFormat.NV21,
                        width,
                        height,
                        null
                );

        java.io.ByteArrayOutputStream out =
                new java.io.ByteArrayOutputStream(
                        width * height / 2
                );

        boolean ok =
                yuv.compressToJpeg(
                        new android.graphics.Rect(
                                0,
                                0,
                                width,
                                height
                        ),
                        90,
                        out
                );

        if (!ok) {
            return null;
        }

        byte[] jpeg =
                out.toByteArray();

        return android.graphics.BitmapFactory
                .decodeByteArray(
                        jpeg,
                        0,
                        jpeg.length
                );
    }

private android.graphics.Bitmap yuv420ToBitmap(Image image) {

        int width = image.getWidth();
        int height = image.getHeight();

        Image.Plane[] planes = image.getPlanes();

        byte[] nv21 = new byte[
                width * height +
                width * height / 2
        ];

        int offset = 0;

        Image.Plane yPlane = planes[0];
        ByteBuffer yBuffer = yPlane.getBuffer();

        int yRowStride = yPlane.getRowStride();
        int yPixelStride = yPlane.getPixelStride();

        for (int row = 0; row < height; row++) {

            int rowStart =
                    row * yRowStride;

            for (int col = 0; col < width; col++) {

                int index =
                        rowStart +
                        col * yPixelStride;

                nv21[offset++] =
                        yBuffer.get(index);
            }
        }

        Image.Plane uPlane = planes[1];
        Image.Plane vPlane = planes[2];

        ByteBuffer uBuffer = uPlane.getBuffer();
        ByteBuffer vBuffer = vPlane.getBuffer();

        int uRowStride = uPlane.getRowStride();
        int uPixelStride = uPlane.getPixelStride();

        int vRowStride = vPlane.getRowStride();
        int vPixelStride = vPlane.getPixelStride();

        int chromaWidth = width / 2;
        int chromaHeight = height / 2;

        for (int row = 0; row < chromaHeight; row++) {

            int uRowStart =
                    row * uRowStride;

            int vRowStart =
                    row * vRowStride;

            for (int col = 0; col < chromaWidth; col++) {

                int uIndex =
                        uRowStart +
                        col * uPixelStride;

                int vIndex =
                        vRowStart +
                        col * vPixelStride;

                nv21[offset++] =
                        vBuffer.get(vIndex);

                nv21[offset++] =
                        uBuffer.get(uIndex);
            }
        }

        android.graphics.YuvImage yuv =
                new android.graphics.YuvImage(
                        nv21,
                        ImageFormat.NV21,
                        width,
                        height,
                        null
                );

        java.io.ByteArrayOutputStream out =
                new java.io.ByteArrayOutputStream();

        boolean ok =
                yuv.compressToJpeg(
                        new android.graphics.Rect(
                                0,
                                0,
                                width,
                                height
                        ),
                        90,
                        out
                );

        if (!ok) {
            return null;
        }

        byte[] jpeg =
                out.toByteArray();

        return android.graphics.BitmapFactory
                .decodeByteArray(
                        jpeg,
                        0,
                        jpeg.length
                );
    }








    private void saveYuvAsJpeg(Image image) {

        if (savedCount >= 5) {
            return;
        }

        try {

            int width = image.getWidth();
            int height = image.getHeight();

            Image.Plane[] planes = image.getPlanes();

            // =================================================
            // Step 43: inspect actual Pixel 4 XL YUV layout
            // =================================================

            if (frameCount == 1) {

                System.out.println(
                        "P4Pilot Step43 YUV layout:"
                );

                System.out.println(
                        "P4Pilot Step43 image width=" +
                        width +
                        " height=" +
                        height
                );

                for (int i = 0; i < planes.length; i++) {

                    Image.Plane plane = planes[i];

                    ByteBuffer buffer = plane.getBuffer();

                    System.out.println(
                            "P4Pilot Step43 plane[" + i + "]" +
                            " rowStride=" +
                            plane.getRowStride() +
                            " pixelStride=" +
                            plane.getPixelStride() +
                            " remaining=" +
                            buffer.remaining() +
                            " position=" +
                            buffer.position() +
                            " limit=" +
                            buffer.limit()
                    );
                }

                int yMaxIndex =
                        (height - 1) * planes[0].getRowStride() +
                        (width - 1) * planes[0].getPixelStride();

                int yRequiredBytes = yMaxIndex + 1;

                System.out.println(
                        "P4Pilot Step43 Y requiredBytes=" +
                        yRequiredBytes
                );

                int chromaWidth = width / 2;
                int chromaHeight = height / 2;

                int uMaxIndex =
                        (chromaHeight - 1) *
                        planes[1].getRowStride() +
                        (chromaWidth - 1) *
                        planes[1].getPixelStride();

                int vMaxIndex =
                        (chromaHeight - 1) *
                        planes[2].getRowStride() +
                        (chromaWidth - 1) *
                        planes[2].getPixelStride();

                System.out.println(
                        "P4Pilot Step43 Y maxIndex=" +
                        yMaxIndex +
                        " bufferLimit=" +
                        planes[0].getBuffer().limit() +
                        " safe=" +
                        (yMaxIndex < planes[0].getBuffer().limit())
                );

                System.out.println(
                        "P4Pilot Step43 U maxIndex=" +
                        uMaxIndex +
                        " bufferLimit=" +
                        planes[1].getBuffer().limit() +
                        " safe=" +
                        (uMaxIndex < planes[1].getBuffer().limit())
                );

                System.out.println(
                        "P4Pilot Step43 V maxIndex=" +
                        vMaxIndex +
                        " bufferLimit=" +
                        planes[2].getBuffer().limit() +
                        " safe=" +
                        (vMaxIndex < planes[2].getBuffer().limit())
                );

                boolean ySafe =
                        yMaxIndex <
                        planes[0].getBuffer().limit();

                boolean uSafe =
                        uMaxIndex <
                        planes[1].getBuffer().limit();

                boolean vSafe =
                        vMaxIndex <
                        planes[2].getBuffer().limit();

                System.out.println(
                        "P4Pilot Step44 boundaryCheck Y=" +
                        ySafe +
                        " U=" +
                        uSafe +
                        " V=" +
                        vSafe
                );

                if (!ySafe || !uSafe || !vSafe) {
                    System.out.println(
                            "P4Pilot Step44 ERROR: YUV buffer boundary unsafe"
                    );
                    return;
                }

                System.out.println(
                        "P4Pilot Step44 YUV buffer boundary SAFE"
                );
            }

            /*
             * YUV_420_888:
             *
             * Plane 0 = Y
             * Plane 1 = U
             * Plane 2 = V
             *
             * Pixel 4 XL 的 Camera HAL 可能具有 padding，
             * 因此不能简单地把 Plane Buffer 直接当作连续 NV21。
             *
             * 这里根据 rowStride / pixelStride 正确读取。
             */

            byte[] nv21 = new byte[
                    width * height +
                    (width * height / 2)
            ];

            int offset = 0;

            // -------------------------------------------------
            // Y plane
            // -------------------------------------------------

            Image.Plane yPlane = planes[0];

            ByteBuffer yBuffer = yPlane.getBuffer();

            int yRowStride = yPlane.getRowStride();
            int yPixelStride = yPlane.getPixelStride();

            for (int row = 0; row < height; row++) {

                int rowStart =
                        row * yRowStride;

                for (int col = 0; col < width; col++) {

                    int index =
                            rowStart +
                            col * yPixelStride;

                    nv21[offset++] =
                            yBuffer.get(index);
                }
            }

            // -------------------------------------------------
            // VU planes -> NV21
            // -------------------------------------------------

            Image.Plane uPlane = planes[1];
            Image.Plane vPlane = planes[2];

            ByteBuffer uBuffer = uPlane.getBuffer();
            ByteBuffer vBuffer = vPlane.getBuffer();

            int uRowStride = uPlane.getRowStride();
            int uPixelStride = uPlane.getPixelStride();

            int vRowStride = vPlane.getRowStride();
            int vPixelStride = vPlane.getPixelStride();

            int chromaWidth = width / 2;
            int chromaHeight = height / 2;

            for (int row = 0; row < chromaHeight; row++) {

                int uRowStart =
                        row * uRowStride;

                int vRowStart =
                        row * vRowStride;

                for (int col = 0; col < chromaWidth; col++) {

                    int uIndex =
                            uRowStart +
                            col * uPixelStride;

                    int vIndex =
                            vRowStart +
                            col * vPixelStride;

                    // NV21 = V U V U V U ...
                    nv21[offset++] =
                            vBuffer.get(vIndex);

                    nv21[offset++] =
                            uBuffer.get(uIndex);
                }
            }

            android.graphics.YuvImage yuv =
                    new android.graphics.YuvImage(
                            nv21,
                            ImageFormat.NV21,
                            width,
                            height,
                            null
                    );

            File file =
                    new File(
                            outputDir,
                            String.format(
                                    "frame_%03d.jpg",
                                    savedCount + 1
                            )
                    );

            FileOutputStream fos =
                    new FileOutputStream(file);

            boolean compressed =
                    yuv.compressToJpeg(
                            new android.graphics.Rect(
                                    0,
                                    0,
                                    width,
                                    height
                            ),
                            90,
                            fos
                    );

            fos.flush();
            fos.close();

            if (compressed && file.exists()) {

                long size = file.length();

                savedCount++;

                System.out.println(
                        "P4Pilot JPEG saved: " +
                        file.getAbsolutePath() +
                        " size=" +
                        size
                );

            } else {

                System.out.println(
                        "P4Pilot JPEG compression FAILED"
                );
            }

        } catch (Exception e) {

            System.err.println(
                    "P4Pilot JPEG ERROR: " +
                    e
            );

            e.printStackTrace();
        }
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
