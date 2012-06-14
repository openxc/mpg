package com.ford.openxc.mpg;

import android.os.Bundle;

public class SpeedChartFragment extends ChartFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        init("Speed", "Time", "Speed");
        super.onCreate(savedInstanceState);
    }
}
