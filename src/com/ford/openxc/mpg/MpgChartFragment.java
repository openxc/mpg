package com.ford.openxc.mpg;

import android.graphics.Color;

import android.os.Bundle;

public class MpgChartFragment extends ChartFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        init("MPG", "Time", "Miles per Gallon");
        super.onCreate(savedInstanceState);
    }

    @Override
    protected int getLineColor() {
        return Color.parseColor("#3F92D2");
    }
}
