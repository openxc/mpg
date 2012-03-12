package com.nickhs.testopenxc;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import android.app.Activity;
import android.app.ListActivity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;

public class MileageActivity extends Activity {

	private static final String TAG = "MileageActivity";
	private DbHelper dbHelper;
	
	private XYMultipleSeriesRenderer mRenderer = new XYMultipleSeriesRenderer();
	private GraphicalView chart;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.mileage);
		dbHelper = new DbHelper(this);
		
	/*	mRenderer.setApplyBackgroundColor(true);
        mRenderer.setBackgroundColor(Color.argb(100, 50, 50, 50));
        mRenderer.setAxisTitleTextSize(16);
        mRenderer.setChartTitleTextSize(20);
        mRenderer.setLabelsTextSize(15);
        mRenderer.setLegendTextSize(15);
        mRenderer.setShowGrid(true); */
        
    	XYSeries data = new XYSeries("Data");
        XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
        XYSeries temp = populateSeries(data);
        dataset.addSeries(temp);
        
        XYSeriesRenderer rend = new XYSeriesRenderer();
        rend.setPointStyle(PointStyle.SQUARE);
        mRenderer.addSeriesRenderer(new XYSeriesRenderer());
        chart = ChartFactory.getScatterChartView(this, dataset, mRenderer);
        mRenderer.setXTitle("Distance Travelled");
        mRenderer.setYTitle("Gas Mileage");
        LinearLayout layout = (LinearLayout) findViewById(R.id.chartM);
        layout.addView(chart, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
	}

	private XYSeries populateSeries(XYSeries data) {
		SQLiteDatabase db = dbHelper.getReadableDatabase();
		Cursor c = db.query(DbHelper.TABLE, null, null, null, null, null, null);
		c.moveToFirst();
		for (int x=0; x < c.getCount(); x++) {
			Log.i(TAG, "Inside loop");
			int miles = c.getInt(c.getColumnIndex(DbHelper.C_MILEAGE));
			int length = c.getInt(c.getColumnIndex(DbHelper.C_DISTANCE));
			Log.i(TAG, "Miles is: "+miles);
			Log.i(TAG, "Length is: "+length);
			data.add(length, miles);
			c.moveToNext();
		}
		return data;
	}
}
