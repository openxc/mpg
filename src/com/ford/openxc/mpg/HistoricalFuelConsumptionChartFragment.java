package com.ford.openxc.mpg;

import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYSeries;

import android.graphics.Color;

import android.os.Bundle;

public class HistoricalFuelConsumptionChartFragment extends
        HistoricalChartFragment {
    private final int DEFAULT_COLOR = Color.parseColor("#FFBB33");
    private final int PER_TRIP_COLOR = Color.parseColor("#FF0000");
    private final int DAILY_COLOR = Color.parseColor("#00FF00");
    private final int WEEKLY_COLOR = Color.parseColor("#0000FF");
    private final int MONTHLY_COLOR = Color.parseColor("#FF00FF");

    @Override
    public void onCreate(Bundle savedInstanceState) {
        init("Fuel Consumption", "Time", "Liters");
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
        mSeries = getSeries(DbHelper.C_FUEL, "Fuel Consumption (Gallons)", true);
        return mSeries;
    }
}
