package com.p4pilot;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView textView = new TextView(this);
        textView.setText("P4Pilot\nPixel 4 XL\nAndroid 13");
        textView.setTextSize(24);
        textView.setPadding(48, 48, 48, 48);

        setContentView(textView);
    }
}
