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
import android.view.Surface;
import android.widget.TextView;

import java.util.Arrays;

public class MainActivity extends Activity {

    private CameraDevice camera;
    private ImageReader reader;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView tv;

    private int frameCount = 0;
    private long firstFrameTime = 0;
    private long lastFrameTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tv = new TextView(this);
        tv.setTextSize(16);
        tv.setPadding(30, 30, 30, 30);
        tv.setText("P4Pilot Camera2\n正在初始化...");
        setContentView(tv);

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
                tv.setText("ERROR: 没有找到后置摄像头");
                return;
            }

            CameraCharacteristics c =
                    manager.getCameraCharacteristics(cameraId);

            android.util.Size[] sizes =
                    c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                            .getOutputSizes(ImageFormat.YUV_420_888);

            android.util.Size selected = sizes[0];

            for (android.util.Size s : sizes) {
                if (s.getWidth() == 1280 && s.getHeight() == 720) {
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

                            if (image != null) {
                                frameCount++;

                                long now = System.currentTimeMillis();

                                if (firstFrameTime == 0) {
                                    firstFrameTime = now;
                                }

                                lastFrameTime = now;

                                final int frames = frameCount;
                                final long elapsed =
                                        now - firstFrameTime;

                                final double fps =
                                        elapsed > 0
                                        ? (frames * 1000.0 / elapsed)
                                        : 0;

                                final int w = image.getWidth();
                                final int h = image.getHeight();

                                runOnUiThread(() -> tv.setText(
                                        "P4Pilot Camera2\n\n" +
                                        "Camera: BACK\n" +
                                        "Format: YUV_420_888\n" +
                                        "Resolution: " + w + " x " + h + "\n" +
                                        "Frames: " + frames + "\n" +
                                        String.format("FPS: %.2f\n", fps) +
                                        "Status: RUNNING"
                                ));
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
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
                        public void onOpened(CameraDevice cameraDevice) {
                            camera = cameraDevice;

                            try {
                                Surface surface = reader.getSurface();

                                CaptureRequest.Builder request =
                                        camera.createCaptureRequest(
                                                CameraDevice.TEMPLATE_PREVIEW);

                                request.addTarget(surface);

                                camera.createCaptureSession(
                                        Arrays.asList(surface),
                                        new CameraCaptureSession.StateCallback() {

                                            @Override
                                            public void onConfigured(
                                                    CameraCaptureSession session) {

                                                try {
                                                    session.setRepeatingRequest(
                                                            request.build(),
                                                            null,
                                                            handler
                                                    );
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                            }

                                            @Override
                                            public void onConfigureFailed(
                                                    CameraCaptureSession session) {
                                                runOnUiThread(() ->
                                                        tv.setText(
                                                                "ERROR: CameraCaptureSession配置失败"
                                                        ));
                                            }
                                        },
                                        handler
                                );

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onDisconnected(CameraDevice cameraDevice) {
                            cameraDevice.close();

                            runOnUiThread(() ->
                                    tv.setText("Camera disconnected"));
                        }

                        @Override
                        public void onError(
                                CameraDevice cameraDevice,
                                int error) {

                            cameraDevice.close();

                            runOnUiThread(() ->
                                    tv.setText(
                                            "Camera ERROR: " + error));
                        }
                    },
                    handler
            );

        } catch (Exception e) {
            tv.setText(
                    "Camera exception:\n" +
                    e.toString()
            );
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (camera != null) {
            camera.close();
            camera = null;
        }

        if (reader != null) {
            reader.close();
            reader = null;
        }
    }
}
