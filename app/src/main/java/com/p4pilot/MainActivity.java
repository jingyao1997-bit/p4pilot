package com.p4pilot;

import android.app.Activity;
import android.os.Bundle;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView tv = new TextView(this);
        tv.setTextSize(16);
        tv.setPadding(30, 30, 30, 30);

        StringBuilder result = new StringBuilder();
        result.append("P4Pilot Camera2 Test\n");
        result.append("Pixel 4 XL\n\n");

        try {
            CameraManager manager =
                    (CameraManager) getSystemService(CAMERA_SERVICE);

            String[] cameraIds = manager.getCameraIdList();

            result.append("Camera count: ")
                  .append(cameraIds.length)
                  .append("\n\n");

            for (String id : cameraIds) {
                CameraCharacteristics c =
                        manager.getCameraCharacteristics(id);

                Integer facing =
                        c.get(CameraCharacteristics.LENS_FACING);

                Integer[] fps =
                        c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);

                result.append("Camera ").append(id).append("\n");
                result.append("Facing: ").append(facing).append("\n");

                if (fps != null) {
                    result.append("FPS ranges: ");
                    for (Integer value : fps) {
                        result.append(value).append(" ");
                    }
                    result.append("\n");
                }

                result.append("\n");
            }

        } catch (Exception e) {
            result.append("ERROR:\n");
            result.append(e.toString());
        }

        tv.setText(result.toString());
        setContentView(tv);
    }
}
