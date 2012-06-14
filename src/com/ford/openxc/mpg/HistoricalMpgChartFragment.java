package com.ford.openxc.mpg;

import org.achartengine.model.XYSeries;

import android.graphics.Color;

import android.os.Bundle;

public class HistoricalMpgChartFragment extends HistoricalChartFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        init("MPG", "Time", "Miles per Gallon");
        super.onCreate(savedInstanceState);
    }

    @Override
    protected int getLineColor() {
        return Color.parseColor("#FFFFFF");
    }

    @Override
    protected XYSeries getSeries() {
        // TODO we touch WAY too much private state. re-think these classes.
        mSeries = getSeries(DbHelper.C_MILEAGE, "Miles per Gallon", true);
        return mSeries;
    }
}
