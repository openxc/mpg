package com.ford.openxc.mpg;

import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYSeries;

import android.graphics.Color;

import android.os.Bundle;

public class HistoricalFuelConsumptionChartFragment extends
        HistoricalChartFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        init("Fuel Consumption", "Time", "Liters");
        super.onCreate(savedInstanceState);
    }

    @Override
    protected int getLineColor() {
        return Color.parseColor("#FFFFFF");
    }

    @Override
    protected XYSeries getSeries() {
        return getSeries(DbHelper.C_FUEL, "Fuel Consumption (Gallons)", true,
                Timeframe.DAILY);
    }
}
