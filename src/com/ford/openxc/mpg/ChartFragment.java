package com.ford.openxc.mpg;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.achartengine.tools.PanListener;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.openxc.VehicleManager;
import com.openxc.VehicleManager.VehicleBinder;
import com.openxc.measurements.FineOdometer;
import com.openxc.measurements.FuelConsumed;
import com.openxc.measurements.IgnitionStatus;
import com.openxc.measurements.IgnitionStatus.IgnitionPosition;
import com.openxc.measurements.UnrecognizedMeasurementTypeException;
import com.openxc.measurements.Measurement;
import com.openxc.measurements.VehicleSpeed;
import com.openxc.NoValueException;
import com.openxc.remote.VehicleServiceException;
import com.openxc.sources.trace.TraceVehicleDataSource;
import com.openxc.sources.usb.UsbVehicleDataSource;
import com.openxc.sources.DataSourceException;
import android.view.View;
import android.view.ViewGroup;

import android.app.FragmentManager;
import android.app.Fragment;
import android.os.Bundle;

import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

public class ChartFragment extends Fragment {
	private XYSeries mSeries;
	private XYMultipleSeriesRenderer mRenderer;
    private XYMultipleSeriesDataset mDataset;
	private GraphicalView mChartView;
	private SharedPreferences mPreferences;
    private String mName;

    protected void init(String name, String xLabel, String yLabel) {
		mDataset = initGraph(mRenderer, mSeries);
        mSeries = new XYSeries(mName);

        mRenderer = new XYMultipleSeriesRenderer();
        mRenderer.setXTitle(xLabel);
        mRenderer.setYTitle(yLabel);
		mRenderer.setRange(new double[] {0, 50000, 0, 100}); // FIXME
    }

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        if(savedInstanceState != null) {
            restoreState(savedInstanceState);
        }

		mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
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
		GraphicalView chartView = ChartFactory.getTimeChartView(this, mDataset,
                mRenderer, null);
		chartView.addPanListener(panListener);
        return chartView;
    }

	private PanListener panListener = new PanListener() {
		@Override
		public void panApplied() { }
	};
	private void addData(double time, double value) {
        mSeries.add(time, value);
        if(time > 50000) { // FIXME should be a preference
            String choice = mPreferences.getString("update_interval", "1000");
            int pollFrequency = Integer.parseInt(choice);
            double max = mSeries.getMaxX();
            mRenderer.setXAxisMax(max + pollFrequency);
            mRenderer.setXAxisMin(max-50000); //FIXME
		}
        mChartView.repaint();
    }

	private XYMultipleSeriesDataset initGraph(XYMultipleSeriesRenderer rend, XYSeries series) {
		rend.setApplyBackgroundColor(true);
		rend.setBackgroundColor(Color.argb(100, 50, 50, 50));
		rend.setAxisTitleTextSize(16);
		rend.setChartTitleTextSize(20);
		rend.setLabelsTextSize(15);
		rend.setLegendTextSize(15);
		rend.setShowGrid(true);
		rend.setYAxisMax(100);
		rend.setYAxisMin(0);
		rend.setPanLimits(new double[] {0, Integer.MAX_VALUE, 0, 400});

		XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
		dataset.addSeries(series);

		XYSeriesRenderer tempRend = new XYSeriesRenderer();
		tempRend.setLineWidth(2);
		tempRend.setColor(Color.parseColor("#FFBB33"));
		rend.addSeriesRenderer(tempRend);
		return dataset;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt("count", mSeries.getItemCount());
		outState.putDoubleArray("x", convertToArray(mSeries, "x"));
		outState.putDoubleArray("x", convertToArray(mSeries, "y"));
    }
}
