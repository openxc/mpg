package com.ford.openxc.mpg;

import android.os.Bundle;

public class MpgChartFragment extends ChartFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        init("MPG", "Time", "Miles per Gallon");
        super.onCreate(savedInstanceState);
    }
}
