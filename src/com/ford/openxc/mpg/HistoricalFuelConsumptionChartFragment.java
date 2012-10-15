package com.ford.openxc.mpg;

import org.achartengine.model.XYSeries;

import android.graphics.Color;
import android.os.Bundle;

public class HistoricalFuelConsumptionChartFragment extends
        HistoricalChartFragment {
    private final int DEFAULT_COLOR = Color.parseColor("#FFFFFF");
    private final int PER_TRIP_COLOR = Color.parseColor("#F46E8F");
    private final int DAILY_COLOR = Color.parseColor("#F43D6B");
    private final int WEEKLY_COLOR = Color.parseColor("#AE2C4C");
    private final int MONTHLY_COLOR = Color.parseColor("#E9003A");

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
