package com.ford.openxc.mpg;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.tools.PanListener;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;

import android.view.LayoutInflater;

import android.view.View;
import android.view.ViewGroup;

import android.support.v4.app.Fragment;

import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYSeriesRenderer;

public class ChartFragment extends Fragment {
	private XYSeries mSeries;
	private XYMultipleSeriesRenderer mRenderer;
    private XYMultipleSeriesDataset mDataset;
	private GraphicalView mChartView;
	private SharedPreferences mPreferences;
    private String mName;

    protected void init(String name, String xLabel, String yLabel) {
        mSeries = new XYSeries(mName);

        mRenderer = new XYMultipleSeriesRenderer();
        mRenderer.setXTitle(xLabel);
        mRenderer.setYTitle(yLabel);
		mRenderer.setRange(new double[] {0, 50000, 0, 100});

		mDataset = new XYMultipleSeriesDataset();
		mDataset.addSeries(mSeries);

		initGraph(mRenderer);
    }

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        if(savedInstanceState != null) {
            restoreState(savedInstanceState);
        }

		mPreferences = PreferenceManager.getDefaultSharedPreferences(
                getActivity());
    }

    protected void restoreState(Bundle savedInstanceState) {
        double[] x = savedInstanceState.getDoubleArray("x");
        double[] y = savedInstanceState.getDoubleArray("y");
        for (int i = 0; i < x.length; i++) {
            mSeries.add(x[i], y[i]);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
		mChartView = ChartFactory.getTimeChartView(getActivity(),
                mDataset, mRenderer, null);
		mChartView.addPanListener(panListener);
        return mChartView;
    }

	public void addData(double time, double value) {
        mSeries.add(time, value);
        if(time > 50000) { // FIXME should be a preference
            String choice = mPreferences.getString("update_interval", "1000");
            int pollFrequency = Integer.parseInt(choice);
            double max = mSeries.getMaxX();
            mRenderer.setXAxisMax(max + pollFrequency);
            mRenderer.setXAxisMin(max - 50000); //FIXME
		}
        mChartView.repaint();
    }

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt("count", mSeries.getItemCount());
		outState.putDoubleArray("x", convertToArray(mSeries, "x"));
		outState.putDoubleArray("x", convertToArray(mSeries, "y"));
    }

	private void initGraph(XYMultipleSeriesRenderer rend) {
		rend.setApplyBackgroundColor(true);
		rend.setBackgroundColor(Color.argb(100, 50, 50, 50));
		rend.setAxisTitleTextSize(16);
		rend.setChartTitleTextSize(20);
		rend.setLabelsTextSize(15);
        rend.setShowLegend(false);
		rend.setLegendTextSize(15);
		rend.setShowGrid(true);
		rend.setYAxisMax(100);
		rend.setYAxisMin(0);
		rend.setPanLimits(new double[] {0, Integer.MAX_VALUE, 0, 400});

		XYSeriesRenderer tempRend = new XYSeriesRenderer();
		tempRend.setLineWidth(2);
		tempRend.setColor(Color.parseColor("#FFBB33"));
		rend.addSeriesRenderer(tempRend);
	}

	private double[] convertToArray(XYSeries series, String type) {
		int count = series.getItemCount();
		double[] array = new double[count];
		for (int i=0; i < count; i++) {
			if (type.equalsIgnoreCase("x")) {
				array[i] = series.getX(i);
			} else if (type.equalsIgnoreCase("y")) {
				array[i] = series.getY(i);
			} else {
                break;
            }
		}
		return array;
	}

	private PanListener panListener = new PanListener() {
		@Override
		public void panApplied() { }
	};
}
