package com.p4pilot;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
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
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView tv;

    private int frameCount = 0;
    private int savedCount = 0;
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

                            image = imageReader.acquireLatestImage();

                            if (image == null) {
                                return;
                            }

                            frameCount++;

                            long now =
                                    System.currentTimeMillis();

                            if (firstFrameTime == 0) {
                                firstFrameTime = now;

                                System.out.println(
                                        "P4Pilot Step49 FIRST_FRAME"
                                );
                            }

                            int w = image.getWidth();
                            int h = image.getHeight();

                            Image.Plane[] planes =
                                    image.getPlanes();

                            if (frameCount == 1) {

                                System.out.println(
                                        "P4Pilot Step49 FRAME_START"
                                );

                                System.out.println(
                                        "P4Pilot Step49 width=" +
                                        w +
                                        " height=" +
                                        h +
                                        " format=" +
                                        image.getFormat()
                                );

                                for (int i = 0;
                                     i < planes.length;
                                     i++) {

                                    Image.Plane plane =
                                            planes[i];

                                    ByteBuffer buffer =
                                            plane.getBuffer();

                                    System.out.println(
                                            "P4Pilot Step49 plane[" +
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

                            if (frameCount == 1) {

                                System.out.println(
                                        "P4Pilot Step49 CONTENT_TEST_BEGIN"
                                );

                                try {

                                    android.graphics.Bitmap bitmap =
                                            yuv420ToBitmap(image);

                                    if (bitmap != null) {

                                        System.out.println(
                                                "P4Pilot Step49 BITMAP_OK width=" +
                                                bitmap.getWidth() +
                                                " height=" +
                                                bitmap.getHeight()
                                        );

                                        File testFile =
                                                new File(
                                                        outputDir,
                                                        "step49_test.jpg"
                                                );

                                        FileOutputStream fos =
                                                new FileOutputStream(testFile);

                                        bitmap.compress(
                                                android.graphics.Bitmap.CompressFormat.JPEG,
                                                90,
                                                fos
                                        );

                                        fos.flush();
                                        fos.close();

                                        android.graphics.Bitmap decoded =
                                                android.graphics.BitmapFactory
                                                        .decodeFile(
                                                                testFile.getAbsolutePath()
                                                        );

                                        if (decoded != null) {

                                            System.out.println(
                                                    "P4Pilot Step49 JPEG_DECODE_OK"
                                            );

                                            System.out.println(
                                                    "P4Pilot Step49 JPEG_PATH=" +
                                                    testFile.getAbsolutePath()
                                            );

                                            decoded.recycle();

                                        } else {

                                            System.out.println(
                                                    "P4Pilot Step49 JPEG_DECODE_FAIL"
                                            );
                                        }

                                        bitmap.recycle();

                                    } else {

                                        System.out.println(
                                                "P4Pilot Step49 BITMAP_NULL"
                                        );
                                    }

                                } catch (Exception e) {

                                    System.err.println(
                                            "P4Pilot Step49 CONTENT_TEST_ERROR"
                                    );

                                    e.printStackTrace();
                                }

                                System.out.println(
                                        "P4Pilot Step49 CONTENT_TEST_END"
                                );
                            }

                            if (frameCount == 1) {

                                System.out.println(
                                        "P4Pilot Step49 CONTENT_TEST_BEGIN"
                                );

                                try {

                                    android.graphics.Bitmap bitmap =
                                            yuv420ToBitmap(image);

                                    if (bitmap != null) {

                                        System.out.println(
                                                "P4Pilot Step49 BITMAP_OK width=" +
                                                bitmap.getWidth() +
                                                " height=" +
                                                bitmap.getHeight()
                                        );

                                        File testFile =
                                                new File(
                                                        outputDir,
                                                        "step49_test.jpg"
                                                );

                                        FileOutputStream fos =
                                                new FileOutputStream(testFile);

                                        bitmap.compress(
                                                android.graphics.Bitmap.CompressFormat.JPEG,
                                                90,
                                                fos
                                        );

                                        fos.flush();
                                        fos.close();

                                        android.graphics.Bitmap decoded =
                                                android.graphics.BitmapFactory
                                                        .decodeFile(
                                                                testFile.getAbsolutePath()
                                                        );

                                        if (decoded != null) {

                                            System.out.println(
                                                    "P4Pilot Step49 JPEG_DECODE_OK"
                                            );

                                            System.out.println(
                                                    "P4Pilot Step49 JPEG_PATH=" +
                                                    testFile.getAbsolutePath()
                                            );

                                            decoded.recycle();

                                        } else {

                                            System.out.println(
                                                    "P4Pilot Step49 JPEG_DECODE_FAIL"
                                            );
                                        }

                                        bitmap.recycle();

                                    } else {

                                        System.out.println(
                                                "P4Pilot Step49 BITMAP_NULL"
                                        );
                                    }

                                } catch (Exception e) {

                                    System.err.println(
                                            "P4Pilot Step49 CONTENT_TEST_ERROR"
                                    );

                                    e.printStackTrace();
                                }

                                System.out.println(
                                        "P4Pilot Step49 CONTENT_TEST_END"
                                );
                            }

                            if (frameCount == 1) {

                                System.out.println(
                                        "P4Pilot Step49 CONTENT_TEST_BEGIN"
                                );

                                try {

                                    android.graphics.Bitmap bitmap =
                                            yuv420ToBitmap(image);

                                    if (bitmap != null) {

                                        System.out.println(
                                                "P4Pilot Step49 BITMAP_OK width=" +
                                                bitmap.getWidth() +
                                                " height=" +
                                                bitmap.getHeight()
                                        );

                                        File testFile =
                                                new File(
                                                        outputDir,
                                                        "step49_test.jpg"
                                                );

                                        FileOutputStream fos =
                                                new FileOutputStream(testFile);

                                        bitmap.compress(
                                                android.graphics.Bitmap.CompressFormat.JPEG,
                                                90,
                                                fos
                                        );

                                        fos.flush();
                                        fos.close();

                                        android.graphics.Bitmap decoded =
                                                android.graphics.BitmapFactory
                                                        .decodeFile(
                                                                testFile.getAbsolutePath()
                                                        );

                                        if (decoded != null) {

                                            System.out.println(
                                                    "P4Pilot Step49 JPEG_DECODE_OK"
                                            );

                                            System.out.println(
                                                    "P4Pilot Step49 JPEG_PATH=" +
                                                    testFile.getAbsolutePath()
                                            );

                                            decoded.recycle();

                                        } else {

                                            System.out.println(
                                                    "P4Pilot Step49 JPEG_DECODE_FAIL"
                                            );
                                        }

                                        bitmap.recycle();

                                    } else {

                                        System.out.println(
                                                "P4Pilot Step49 BITMAP_NULL"
                                        );
                                    }

                                } catch (Exception e) {

                                    System.err.println(
                                            "P4Pilot Step49 CONTENT_TEST_ERROR"
                                    );

                                    e.printStackTrace();
                                }

                                System.out.println(
                                        "P4Pilot Step49 CONTENT_TEST_END"
                                );
                            }

                            if (frameCount == 10 ||
                                frameCount == 30 ||
                                frameCount == 60 ||
                                frameCount == 100) {

                                long elapsed =
                                        now - firstFrameTime;

                                double fps =
                                        elapsed > 0
                                        ? (frameCount - 1) *
                                          1000.0 /
                                          elapsed
                                        : 0;

                                System.out.println(
                                        "P4Pilot Step49 FRAME=" +
                                        frameCount +
                                        " elapsedMs=" +
                                        elapsed +
                                        " fps=" +
                                        String.format(
                                                "%.2f",
                                                fps
                                        )
                                );
                            }

                            long elapsed =
                                    now - firstFrameTime;

                            double fps =
                                    elapsed > 0
                                    ? (frameCount - 1) *
                                      1000.0 /
                                      elapsed
                                    : 0;

                            runOnUiThread(() ->
                                    tv.setText(
                                            "P4Pilot Step 49\n\n" +
                                            "Camera: BACK\n" +
                                            "Format: YUV_420_888\n" +
                                            "Resolution: " +
                                            w + " x " + h + "\n" +
                                            "Frames: " +
                                            frameCount + "\n" +
                                            String.format(
                                                    "FPS: %.2f\n",
                                                    fps
                                            ) +
                                            "YUV planes: " +
                                            planes.length +
                                            "\n\n" +
                                            "Status: YUV STREAMING"
                                    )
                            );

                        } catch (Exception e) {

                            System.err.println(
                                    "P4Pilot Step49 FRAME_ERROR"
                            );

                            e.printStackTrace();

                            runOnUiThread(() ->
                                    tv.setText(
                                            "Step48 ERROR:\n" +
                                            e.toString()
                                    ));

                        } finally {

                            if (image != null) {
                                image.close();
                            }
                        }

                    },
                    handler
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
                                                                    handler
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

                                        handler
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
                    handler
            );

        } catch (Exception e) {

            e.printStackTrace();

            tv.setText(
                    "Camera exception:\n" +
                    e.toString()
            );
        }
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

        try {

            if (session != null) {
                session.close();
                session = null;
            }

            if (camera != null) {
                camera.close();
                camera = null;
            }

            if (reader != null) {
                reader.close();
                reader = null;
            }

        } catch (Exception ignored) {
        }
    }
}
