package com.rachitgoyal.segmentedprogressbar;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.rachitgoyal.segmented.SegmentedProgressBar;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    SegmentedProgressBar mSegmentedProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSegmentedProgressBar = findViewById(R.id.segmented_pb_1);

        Integer[] enabled = new Integer[]{1, 4, 5, 8, 9};

        mSegmentedProgressBar.setEnabledDivisions(Arrays.asList(enabled));
    }
}
