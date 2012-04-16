package com.nickhs.testopenxc;

import java.text.DecimalFormat;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.BarChart.Type;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.joda.time.DateMidnight;
import org.joda.time.DateTime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.TextView;

public class OverviewActivity extends Activity {
	final static String TAG = "OverviewActivity";
	final static String pattern = "YYYY-MM-dd";
	DbHelper dbHelper;

	TextView todayGas;
	TextView yesterGas;
	TextView lastMileage;
	TextView previousMileage;
	
	SharedPreferences sharedPrefs;
	
	int TRIPLY = 0;
	int HOURLY = 1;
	int DAILY = 2;
	int WEEKLY = 3;
	int MONTHLY = 4;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);

		setContentView(R.layout.overview);
		dbHelper = new DbHelper(this);

		todayGas = (TextView) findViewById(R.id.currentUsage);
		yesterGas = (TextView) findViewById(R.id.lastUsage);
		lastMileage = (TextView) findViewById(R.id.lastTrip);
		previousMileage = (TextView) findViewById(R.id.previousTrip);
		
		sharedPrefs = getPreferences(MODE_PRIVATE);
		sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener);

		drawTopLeft();
		drawBottomLeft();
		drawTopRight();
		drawBottomRight();
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.graphResolution:
			Log.i(TAG, "Graph Resolution selected");
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Select Graph Frequency");
			builder.setItems(R.array.graphFrequency, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Editor editor = sharedPrefs.edit();
					Log.i(TAG, "Graph frequency changed to "+which);
					editor.putInt("graphFrequency", which);
					editor.commit();
				}
			});
			
			AlertDialog alert = builder.create();
			alert.show();
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.overviewmenu, menu);
		return super.onCreateOptionsMenu(menu);
	}
	
	public OnSharedPreferenceChangeListener prefListener = new OnSharedPreferenceChangeListener() {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
				String key) {
			Log.i(TAG, "Redrawing graphs!");
			drawBottomLeft();
			drawTopLeft();
		}
	};

	private void drawTopLeft() {
		GraphicalView chartPetrol = getChart(DbHelper.C_FUEL, "Gas Used", "litres", false);
		FrameLayout layout = (FrameLayout) findViewById(R.id.topLeft);
		layout.removeAllViews();
		layout.addView(chartPetrol);
	}

	private void drawBottomLeft() {
		GraphicalView chartMileage = getChart(DbHelper.C_MILEAGE, "Average Mileage", "km/l", true);
		FrameLayout layout = (FrameLayout) findViewById(R.id.bottomLeft);
		layout.removeAllViews();
		layout.addView(chartMileage);
	}

	private void drawTopRight() {
		DateMidnight todayStart = new DateMidnight();
		DateMidnight todayEnd = todayStart.plusDays(1);
		Cursor c = dbHelper.getLastData(todayStart.toString(pattern), todayEnd.toString(pattern), DbHelper.C_FUEL);
		double today = calculateData(c, false);
		String stringToday = new DecimalFormat("##").format(today);
		todayGas.setText(stringToday);

		todayStart = todayStart.minusDays(1);
		todayEnd = todayEnd.minusDays(1);
		Cursor d = dbHelper.getLastData(todayStart.toString(pattern), todayEnd.toString(pattern), DbHelper.C_FUEL);
		double yesterday = calculateData(d, false);
		String stringYesterday = new DecimalFormat("##").format(yesterday);
		yesterGas.setText(stringYesterday);
	}

	private void drawBottomRight() {
		DateMidnight todayStart = new DateMidnight();
		DateMidnight todayEnd = todayStart.plusDays(1);
		todayStart = todayStart.minusMonths(1); // this is really, really bad!
		Cursor c = dbHelper.getLastData(todayStart.toString(pattern), todayEnd.toString(pattern), DbHelper.C_MILEAGE);
		if(c.moveToLast()) {
			String sLastTrip = new DecimalFormat("##").format(c.getDouble(0));
			if(c.moveToPrevious()) {
				String sPreviousTrip = new DecimalFormat("##").format(c.getDouble(0));
				lastMileage.setText(sLastTrip);
				previousMileage.setText(sPreviousTrip);
			} else {
				Log.i(TAG, "Could't find any matching rows in the database");
			}
		} else {
			Log.i(TAG, "Could't find any matching rows in the database");
		}
	}

	private GraphicalView getChart(String column, String dataName, String units, boolean average) {
		XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();

		TimeSeries series = getSeries(column, dataName, average);
		dataset.addSeries(series);

		XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();
		XYSeriesRenderer srend = new XYSeriesRenderer();
		srend.setColor(Color.parseColor("#FFBB33")); 
		renderer.addSeriesRenderer(srend);

		renderer.setShowLegend(false);
		renderer.setApplyBackgroundColor(true);
		renderer.setBackgroundColor(Color.argb(1000, 50, 50, 50));
		renderer.setYTitle(dataName+" ("+units+")");
		renderer.setShowGrid(true);
		renderer.setYAxisMin(0);
		renderer.setYAxisMax(series.getMaxY()+(series.getMaxY()*.05));
		renderer.setXAxisMin(series.getMaxX()-4);
		renderer.setXAxisMax(series.getMaxX()+(series.getMaxX()*.05));
		renderer.setBarSpacing(0.05);
		renderer.setPanLimits(new double[] {0, series.getMaxX()+1, 0, 0});
		renderer.setAntialiasing(true);

		GraphicalView chart = ChartFactory.getBarChartView(this, dataset, renderer, Type.DEFAULT);
		return chart;
	}

	private TimeSeries getSeries(String column, String dataName, boolean average) {
		TimeSeries series = new TimeSeries(dataName);
		int pref = sharedPrefs.getInt("graphFrequency", 3);
		Log.i(TAG, "Pref is: "+pref);
		int bars = 12; // FIXME load from settings
		
		if (pref == DAILY) {
			DateMidnight endDate = new DateMidnight();
			DateMidnight startDate = endDate.minusDays(1);
			
			for (int i=0; i < bars; i++) {
				double total = 0;
				Cursor data = dbHelper.getLastData(startDate.toString(pattern), endDate.toString(pattern), column);
				total = calculateData(data, average);

				series.add(startDate.getDayOfMonth(), total);
				
				endDate = endDate.minusDays(1);
				startDate = startDate.minusDays(1);
			}
		}
		
		else if (pref == HOURLY) {
			DateTime endDate = new DateTime();
			endDate = endDate.minusMinutes(endDate.getMinuteOfHour());
			DateTime startDate = endDate.minusHours(1);
			
			for (int i=0; i < bars; i++) {
				double total = 0;
				Cursor data = dbHelper.getLastData(startDate.toString(pattern), endDate.toString(pattern), column);
				total = calculateData(data, average);

				series.add(startDate.getHourOfDay(), total);
				
				endDate = endDate.minusHours(1);
				startDate = startDate.minusHours(1);
			}
		}
		
		else if (pref == WEEKLY) {
			DateMidnight endDate = new DateMidnight();
			endDate = endDate.minusDays(endDate.getDayOfWeek());
			DateMidnight startDate = endDate.minusDays(7);
			
			for (int i=0; i < bars; i++) {
				double total = 0;
				Cursor data = dbHelper.getLastData(startDate.toString(pattern), endDate.toString(pattern), column);
				total = calculateData(data, average);

				series.add(startDate.getWeekOfWeekyear(), total);
				
				endDate = endDate.minusDays(7);
				startDate = startDate.minusDays(7);
			}
		}
		
		else if (pref == MONTHLY) {
			DateMidnight endDate = new DateMidnight();
			endDate = endDate.minusDays(endDate.getDayOfMonth());
			DateMidnight startDate = endDate.minusMonths(1);
			
			for (int i=0; i < bars; i++) {
				double total = 0;
				Cursor data = dbHelper.getLastData(startDate.toString(pattern), endDate.toString(pattern), column);
				total = calculateData(data, average);

				series.add(startDate.getMonthOfYear(), total);
				
				endDate = endDate.minusMonths(1);
				startDate = startDate.minusMonths(1);
			}
		}
		
		else if (pref == TRIPLY) {
			for (int i=0; i < bars; i++) {
			//	Cursor data = dbHelper.getLastData(startDate.toString(pattern), endDate.toString(pattern), column);
				// FIXME
			}
		}
		
		else {
			Log.e(TAG, "Invalid value! "+pref);
		}
		
		return series;
	}

	private double calculateData(Cursor data, boolean average) {
		double total = 0;
		data.moveToFirst();

		for (int x=0; x < data.getCount(); x++) {
			double fuel = data.getDouble(0);
			if (average) {
				double tempAvg = total;
				double tempTotal = tempAvg * x;
				tempTotal += fuel;
				tempAvg = tempTotal/(x+1);
				total = tempAvg;
			}

			else {
				total += fuel;
			}

			data.moveToNext();
		}

		data.close();
		return total;
	}
}
