package com.ford.openxc.mpg;

import org.achartengine.chart.BarChart.Type;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;

import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYSeries;

import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import org.joda.time.DateMidnight;
import org.joda.time.DateTime;

import android.database.Cursor;

import android.graphics.Color;

import android.os.Bundle;

import android.util.Log;

public class HistoricalChartFragment extends ChartFragment {
    private static final String TAG = "HistoricalChartFragment";
	private DbHelper mDatabase;
	private final static String pattern = "YYYY-MM-dd";

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
		renderer.setYAxisMin(0);
		renderer.setYAxisMax(series.getMaxY()+(series.getMaxY()*.05));
		renderer.setXAxisMin(series.getMaxX()-4);
		renderer.setXAxisMax(series.getMaxX()+(series.getMaxX()*.05));
		renderer.setBarSpacing(0.05);
		renderer.setPanLimits(new double[] {0, series.getMaxX()+1, 0, 0});
		renderer.setAntialiasing(true);

		XYSeriesRenderer srend = new XYSeriesRenderer();
		srend.setColor(Color.parseColor("#FFBB33"));
		renderer.addSeriesRenderer(srend);
    }

    protected enum Timeframe {
        PER_TRIP, DAILY, HOURLY, WEEKLY, MONTHLY
    }

	protected TimeSeries getSeries(String column, String dataName,
            boolean average, Timeframe timeframe) {
		TimeSeries series = new TimeSeries(dataName);
		final int bars = 12;

		if (timeframe == Timeframe.DAILY) {
			DateMidnight endDate = new DateMidnight();
			DateMidnight startDate = endDate.minusDays(1);

			for (int i = 0; i < bars; i++) {
				double total = 0;
				Cursor data = mDatabase.getLastData(startDate.toString(pattern),
                        endDate.toString(pattern), column);
				total = calculateData(data, average);

				series.add(startDate.getDayOfMonth(), total);

				endDate = endDate.minusDays(1);
				startDate = startDate.minusDays(1);
			}
		} else if (timeframe == Timeframe.HOURLY) {
			DateTime endDate = new DateTime();
			endDate = endDate.minusMinutes(endDate.getMinuteOfHour());
			DateTime startDate = endDate.minusHours(1);

			for (int i=0; i < bars; i++) {
				double total = 0;
				Cursor data = mDatabase.getLastData(startDate.toString(pattern),
                        endDate.toString(pattern), column);
				total = calculateData(data, average);

				series.add(startDate.getHourOfDay(), total);

				endDate = endDate.minusHours(1);
				startDate = startDate.minusHours(1);
			}
		} else if (timeframe == Timeframe.WEEKLY) {
			DateMidnight endDate = new DateMidnight();
			endDate = endDate.minusDays(endDate.getDayOfWeek());
			DateMidnight startDate = endDate.minusDays(7);

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
			DateMidnight endDate = new DateMidnight();
			endDate = endDate.minusDays(endDate.getDayOfMonth());
			DateMidnight startDate = endDate.minusMonths(1);

			for (int i=0; i < bars; i++) {
				double total = 0;
				Cursor data = mDatabase.getLastData(startDate.toString(pattern),
                        endDate.toString(pattern), column);
				total = calculateData(data, average);

				series.add(startDate.getMonthOfYear(), total);

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
