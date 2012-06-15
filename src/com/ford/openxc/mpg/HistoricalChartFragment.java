package com.ford.openxc.mpg;

import org.achartengine.chart.BarChart.Type;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;

import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;

import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import org.joda.time.DateMidnight;
import org.joda.time.DateTime;

import android.database.Cursor;

import android.graphics.Color;

import android.os.Bundle;

import android.util.Log;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

public class HistoricalChartFragment extends ChartFragment {
    private static final String TAG = "HistoricalChartFragment";
	private final static String pattern = "YYYY-MM-dd";
	private final int DEFAULT_COLOR = Color.parseColor("#FFBB33");
	private final int TRIP_COLOR = Color.parseColor("#FF0000");
	private final int DAILY_COLOR = Color.parseColor("#00FF00");
	private final int WEEKLY_COLOR = Color.parseColor("#0000FF");
	private final int MONTHLY_COLOR = Color.parseColor("#FF00FF");

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
		srend.setColor(DEFAULT_COLOR);
		renderer.addSeriesRenderer(srend);
    }

    protected Timeframe getTimeframe() {
        return mTimeframe;
    }

    protected void setTimeframe(Timeframe newTimeframe) {
        Log.d(TAG, "Changing timeframe to " + newTimeframe);
        mTimeframe = newTimeframe;
        int SeriesCount = mRenderer.getSeriesRendererCount();
        Log.i("HistoricalChartFragment", "Number of Serieses: " + SeriesCount);
        XYSeriesRenderer thisRend = (XYSeriesRenderer) mRenderer.getSeriesRendererAt(0);  //FIXME  Assuming one series per graph.
        if (newTimeframe == Timeframe.DAILY) {
        	thisRend.setColor(DAILY_COLOR);		//FIXME  This change should be reflected in ChartFragment.getLineColor();
        } else if (newTimeframe == Timeframe.WEEKLY) {
        	thisRend.setColor(WEEKLY_COLOR);
        } else if (newTimeframe == Timeframe.MONTHLY) {
        	thisRend.setColor(MONTHLY_COLOR);
        } else if (newTimeframe == Timeframe.PER_TRIP) {
        	thisRend.setColor(TRIP_COLOR);
        }
        repaint();
    }

    private void repaint() {
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
