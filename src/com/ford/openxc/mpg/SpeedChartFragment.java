package com.ford.openxc.mpg;

import android.graphics.Color;

import android.os.Bundle;

public class SpeedChartFragment extends ChartFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        init("Speed", "Time", "Speed");
        super.onCreate(savedInstanceState);
    }

    @Override
    protected int getLineColor() {
        return Color.parseColor("#FFD040");
    }
}
