package com.ford.openxc.mpg;

import org.achartengine.chart.BarChart.Type;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;

import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYSeries;

import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import org.joda.time.DateMidnight;

import android.database.Cursor;

import android.graphics.Color;

import android.os.Bundle;

import android.util.Log;

import android.view.ViewGroup;

public class HistoricalChartFragment extends ChartFragment {
    private static final String TAG = "HistoricalChartFragment";
    private final static String pattern = "YYYY-MM-dd";

    private DbHelper mDatabase;
    private Timeframe mTimeframe = Timeframe.DAILY;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mDatabase = new DbHelper(getActivity());
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDatabase.close();
    }

    @Override
    protected GraphicalView getChartView() {
        return ChartFactory.getBarChartView(getActivity(), getDataset(),
                mRenderer, Type.DEFAULT);
    }

    @Override
    protected void initGraph(XYMultipleSeriesRenderer renderer) {
        XYSeries series = getSeries();
        renderer.setShowLegend(false);
        renderer.setApplyBackgroundColor(true);
        renderer.setBackgroundColor(Color.argb(1000, 50, 50, 50));
        renderer.setYTitle(series.getTitle());
        renderer.setShowGrid(true);
        renderer.setBarSpacing(0.05);
        renderer.setAntialiasing(true);
        setAxis(renderer);

        XYSeriesRenderer srend = new XYSeriesRenderer();
        srend.setColor(getLineColor());
        renderer.addSeriesRenderer(srend);
    }

    protected Timeframe getTimeframe() {
        return mTimeframe;
    }

    protected void setTimeframe(Timeframe newTimeframe) {
        Log.d(TAG, "Changing timeframe to " + newTimeframe);
        mTimeframe = newTimeframe;
        repaint();
    }

    private void repaint() {
        // TODO  Assuming one series per graph.
        XYSeriesRenderer thisRend = (XYSeriesRenderer)
            mRenderer.getSeriesRendererAt(0);
        thisRend.setColor(getLineColor());

        ViewGroup parent = (ViewGroup) mChartView.getParent();
        parent.removeView(mChartView);

        setAxis(mRenderer);
        mChartView = getChartView();
        parent.addView(mChartView);
    }

    protected void setAxis(XYMultipleSeriesRenderer renderer) {
        XYSeries series = getSeries();
        renderer.setYAxisMin(0);
        renderer.setYAxisMax(series.getMaxY()+(series.getMaxY()*.05));
        renderer.setXAxisMin(series.getMinX() - 3);
        renderer.setXAxisMax(series.getMaxX() + (series.getMaxX()*.05));
        renderer.setPanLimits(new double[] {0, series.getMaxX()+1, 0, 0});
    }

    protected TimeSeries getSeries(String column, String dataName,
            boolean average) {
        TimeSeries series = new TimeSeries(dataName);
        final int bars = 12;

        Timeframe timeframe = getTimeframe();
        if (timeframe == Timeframe.DAILY) {
            DateMidnight endDate = (new DateMidnight()).plusDays(1);
            DateMidnight startDate = endDate.minusDays(2);

            for (int i = 0; i < bars; i++) {
                double total = 0;
                Cursor data = mDatabase.getLastData(startDate.toString(pattern),
                        endDate.toString(pattern), column);
                total = calculateData(data, average);

                series.add(startDate.getDayOfMonth(), total);

                endDate = endDate.minusDays(1);
                startDate = startDate.minusDays(1);
            }
        } else if (timeframe == Timeframe.WEEKLY) {
            DateMidnight endDate = new DateMidnight().plusDays(1);
            DateMidnight startDate = endDate.minusDays(8);

            for (int i=0; i < bars; i++) {
                double total = 0;
                Cursor data = mDatabase.getLastData(startDate.toString(pattern),
                        endDate.toString(pattern), column);
                total = calculateData(data, average);

                series.add(startDate.getWeekOfWeekyear(), total);

                endDate = endDate.minusDays(7);
                startDate = startDate.minusDays(7);
            }
        } else if (timeframe == Timeframe.MONTHLY) {
            DateMidnight endDate = new DateMidnight().plusMonths(1);
            DateMidnight startDate = endDate.minusMonths(2);

            for (int i=0; i < bars; i++) {
                double total = 0;
                Cursor data = mDatabase.getLastData(startDate.toString(pattern),
                        endDate.toString(pattern), column);
                total = calculateData(data, average);

                series.add(startDate.getMonthOfYear() + 1, total);

                endDate = endDate.minusMonths(1);
                startDate = startDate.minusMonths(1);
            }
        } else if (timeframe == Timeframe.PER_TRIP) {
            Cursor data = mDatabase.getLastData(bars, column);
            if(data.moveToLast()) {
                for (int i=1; i < data.getCount(); i++) {
                    series.add(i, data.getDouble(0));
                    data.moveToPrevious();
                }
                data.close();
            }
        } else {
            Log.w(TAG, "Unknown timeframe: " + timeframe);
        }
        return series;
    }

    private double calculateData(Cursor data, boolean average) {
        double total = 0;
        if(data.moveToFirst()) {
            for (int x=0; x < data.getCount(); x++) {
                double fuel = data.getDouble(0);
                if (average) {
                    double tempAvg = total;
                    double tempTotal = tempAvg * x;
                    tempTotal += fuel;
                    tempAvg = tempTotal/(x+1);
                    total = tempAvg;
                } else {
                    total += fuel;
                }
                data.moveToNext();
            }
            data.close();
        }
        return total;
    }
}
