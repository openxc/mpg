package com.ford.openxc.mpg;

import org.achartengine.model.XYSeries;

import android.graphics.Color;

import android.os.Bundle;

public class HistoricalMpgChartFragment extends HistoricalChartFragment {
    private final int DEFAULT_COLOR = Color.parseColor("#FFBB33");
    private final int PER_TRIP_COLOR = Color.parseColor("#FF0000");
    private final int DAILY_COLOR = Color.parseColor("#00FF00");
    private final int WEEKLY_COLOR = Color.parseColor("#0000FF");
    private final int MONTHLY_COLOR = Color.parseColor("#FF00FF");

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
