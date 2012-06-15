package com.ford.openxc.mpg;

import org.achartengine.model.XYSeries;

import android.graphics.Color;

import android.os.Bundle;

public class HistoricalMpgChartFragment extends HistoricalChartFragment {
    private final int DEFAULT_COLOR = Color.parseColor("#FFFFFF");
    private final int PER_TRIP_COLOR = Color.parseColor("#A0F06C");
    private final int DAILY_COLOR = Color.parseColor("#83F03C");
    private final int WEEKLY_COLOR = Color.parseColor("#5CA82A");
    private final int MONTHLY_COLOR = Color.parseColor("#58E000");

    @Override
    public void onCreate(Bundle savedInstanceState) {
        init("MPG", "Time", "Miles per Gallon");
        super.onCreate(savedInstanceState);
    }

    @Override
    protected int getLineColor() {
        Timeframe timeframe = getTimeframe();
        switch(timeframe) {
            case DAILY:
                return DAILY_COLOR;
            case WEEKLY:
                return WEEKLY_COLOR;
            case MONTHLY:
                return MONTHLY_COLOR;
            case PER_TRIP:
                return PER_TRIP_COLOR;
        }
        return DEFAULT_COLOR;
    }

    @Override
    protected XYSeries getSeries() {
        // TODO we touch WAY too much private state. re-think these classes.
        mSeries = getSeries(DbHelper.C_MILEAGE, "Miles per Gallon", true);
        return mSeries;
    }
}
