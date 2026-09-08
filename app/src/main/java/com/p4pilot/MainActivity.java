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
        tv.setText("P4Pilot Step 40\n正在初始化...");
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

                            image =
                                    imageReader.acquireLatestImage();

                            if (image == null) {
                                return;
                            }

                            frameCount++;

                            long now =
                                    System.currentTimeMillis();

                            if (firstFrameTime == 0) {
                                firstFrameTime = now;
                            }

                            /*
                             * 每 30 帧保存一张 JPEG，
                             * 避免连续写磁盘造成 Camera 阻塞。
                             */
                            if (frameCount % 30 == 1 &&
                                    savedCount < 5) {

                                saveYuvAsJpeg(image);
                            }

                            long elapsed =
                                    now - firstFrameTime;

                            double fps =
                                    elapsed > 0
                                    ? frameCount * 1000.0 / elapsed
                                    : 0;

                            int w = image.getWidth();
                            int h = image.getHeight();

                            runOnUiThread(() ->
                                    tv.setText(
                                            "P4Pilot Step 40\n\n" +
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
                                            "JPEG saved: " +
                                            savedCount + "\n" +
                                            "Output: " +
                                            outputDir.getAbsolutePath() +
                                            "\n\nStatus: RUNNING"
                                    )
                            );

                        } catch (Exception e) {

                            e.printStackTrace();

                            runOnUiThread(() ->
                                    tv.setText(
                                            "ERROR:\n" +
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

    private void saveYuvAsJpeg(Image image) {

        if (savedCount >= 5) {
            return;
        }

        try {

            int width = image.getWidth();
            int height = image.getHeight();

            Image.Plane[] planes = image.getPlanes();

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
