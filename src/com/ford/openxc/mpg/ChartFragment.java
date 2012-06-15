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

import android.util.Log;

import android.view.LayoutInflater;

import android.view.View;
import android.view.ViewGroup;

import android.support.v4.app.Fragment;

import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYSeriesRenderer;

public class ChartFragment extends Fragment {
    protected XYSeries mSeries;
    protected XYMultipleSeriesRenderer mRenderer;
    protected GraphicalView mChartView;
    protected XYMultipleSeriesDataset mDataset;
    private SharedPreferences mPreferences;
    private String mName;

    protected void init(String name, String xLabel, String yLabel) {
        mName = name;

        mRenderer = new XYMultipleSeriesRenderer();
        mRenderer.setXTitle(xLabel);
        mRenderer.setYTitle(yLabel);
        mRenderer.setAxisTitleTextSize(32);

        mRenderer.setRange(new double[] {0, 50000, 0, 100});
    }

    protected XYSeries getSeries() {
        mSeries = new XYSeries(mName);
        return mSeries;

    }

    protected XYMultipleSeriesDataset getDataset() {
        mDataset = new XYMultipleSeriesDataset();
        mDataset.addSeries(getSeries());
        return mDataset;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(savedInstanceState != null) {
            restoreState(savedInstanceState);
        }

        initGraph(mRenderer);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(
                getActivity());
    }

    protected void restoreState(Bundle savedInstanceState) {
        if(mSeries != null) {
            double[] x = savedInstanceState.getDoubleArray("x");
            double[] y = savedInstanceState.getDoubleArray("y");
            for (int i = 0; i < x.length; i++) {
                mSeries.add(x[i], y[i]);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        mChartView = getChartView();
        return mChartView;
    }

    protected GraphicalView getChartView() {
        GraphicalView chartView = ChartFactory.getTimeChartView(getActivity(),
                getDataset(), mRenderer, null);
        chartView.addPanListener(panListener);
        return chartView;
    }

    public void addData(double time, double value) {
        if(mSeries != null && mChartView != null) {
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
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("count", mSeries.getItemCount());
        outState.putDoubleArray("x", convertToArray(mSeries, "x"));
        outState.putDoubleArray("x", convertToArray(mSeries, "y"));
    }

    protected void initGraph(XYMultipleSeriesRenderer renderer) {
        renderer.setApplyBackgroundColor(true);
        renderer.setBackgroundColor(Color.argb(100, 50, 50, 50));
        renderer.setChartTitleTextSize(20);
        renderer.setLabelsTextSize(25);
        renderer.setShowLegend(false);
        renderer.setShowGrid(true);
        renderer.setYAxisMax(100);
        renderer.setYAxisMin(0);
        renderer.setPanLimits(new double[] {0, Integer.MAX_VALUE, 0, 400});
        renderer.setMargins(getMargins());

        XYSeriesRenderer seriesRenderer = new XYSeriesRenderer();
        seriesRenderer.setLineWidth(10);
        seriesRenderer.setColor(getLineColor());
        renderer.addSeriesRenderer(seriesRenderer);
    }

    protected int[] getMargins() {
        return new int[] { 35, 65, 10, 40 };
    }

    protected int getLineColor() {
        return Color.parseColor("#FFBB33");
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
