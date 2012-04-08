package com.nickhs.testopenxc;

import java.text.DecimalFormat;

import me.kiip.api.Kiip;
import me.kiip.api.Kiip.RequestListener;
import me.kiip.api.KiipException;
import me.kiip.api.Resource;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.BarChart.Type;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.joda.time.DateMidnight;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

public class OverviewActivity extends Activity {
	final static String TAG = "OverviewActivity";
	final static String pattern = "YYYY-MM-dd";

	private final String KP_APP_KEY =    "46549407b87447d7d9126070dc8abb72";
	private final String KP_APP_SECRET = "5ea6c51ccee47f7939c43ca6d1edfb2a";

	private boolean giveReward;

	DbHelper dbHelper;

	TextView todayGas;
	TextView yesterGas;
	TextView lastMileage;
	TextView previousMileage;

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

		Log.i(TAG, "Init Kiip");
		Kiip.init(this, KP_APP_KEY, KP_APP_SECRET);

		if (savedInstanceState != null) {
			savedInstanceState.getBoolean("giveReward", false);
		}

		drawTopLeft();
		drawBottomLeft();
		drawTopRight();
		drawBottomRight();
	}


	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}


	@Override
	protected void onStart() {
		super.onStart();
		Kiip.getInstance().startSession(this, startSessionListener);
	}

	private void giveRewards() {
		Cursor c = dbHelper.getLastData(2, DbHelper.C_MILEAGE);
		c.moveToFirst();
		double latestMileage = c.getDouble(0);
		c.moveToNext();
		double prevMileage = c.getDouble(0);
		double prevAverage = getLastAverage(DbHelper.C_MILEAGE);

		Log.i(TAG, "Latest Mileage is: "+latestMileage+". "+
				"Previous Mileage was: "+prevMileage+". "+
				"Average Mileage was: "+prevAverage+". ");

		if (latestMileage > prevAverage) {
			makeToast("Congratulations, you beat your average mileage!");
			Log.i(TAG, "Awarding 2");
			Kiip.getInstance().unlockAchievement("2", null);
		}

		else if (latestMileage > prevMileage) {
			makeToast("Congratulations, you beat your last trip's mileage!");
			Log.i(TAG, "Awarding 5");
			Kiip.getInstance().unlockAchievement("5", null);
		}

		else {
			makeToast("Well you are just amazing!");
			Log.i(TAG, "Awarding 1");
			Kiip.getInstance().unlockAchievement("1", null);
		}
	}

	public RequestListener<Resource> startSessionListener = new RequestListener<Resource>() {
		@Override
		public void onFinished(Kiip manager, Resource response) {
			Log.i(TAG, "Session Connected");      

			if (response == null) {
				Log.w(TAG, "Response is null :(");
			}

			manager.showResource(response);
			giveRewards();
		}

		@Override
		public void onError(Kiip manager, KiipException error) {
			Log.e(TAG, "Failed to connect!");
			Log.e(TAG, error.getCause().getMessage());
		}
	};

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onStop() {
		super.onStop();
		Kiip.getInstance().endSession(this, null);
	}

	private void drawTopLeft() {
		GraphicalView chartPetrol = getChart(DbHelper.C_FUEL, "Gas Used", "litres", false);
		FrameLayout layout = (FrameLayout) findViewById(R.id.topLeft);
		layout.addView(chartPetrol);
	}

	private void drawBottomLeft() {
		GraphicalView chartMileage = getChart(DbHelper.C_MILEAGE, "Average Mileage", "km/l", true);
		FrameLayout layout = (FrameLayout) findViewById(R.id.bottomLeft);
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
		renderer.setXTitle("Week Number");
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
		int weeks = 12; // FIXME load from settings

		DateMidnight endDate = new DateMidnight();
		endDate = endDate.minusDays(endDate.getDayOfWeek());
		DateMidnight startDate = endDate.minusDays(7);

		for (int i=0; i < weeks; i++) {
			double calc = 0;
			Cursor data = dbHelper.getLastData(startDate.toString(pattern), endDate.toString(pattern), column);
			calc = calculateData(data, average);

			series.add(startDate.getWeekOfWeekyear(), calc);

			endDate = endDate.minusWeeks(1);
			startDate = startDate.minusWeeks(1);
		}

		return series;
	}

	private double getLastAverage(String column) {
		DateMidnight endDate = new DateMidnight();
		endDate = endDate.minusDays(endDate.getDayOfWeek());
		DateMidnight startDate = endDate.minusDays(7);

		Cursor data = dbHelper.getLastData(startDate.toString(pattern), endDate.toString(pattern), column);
		double calc = calculateData(data, true);

		return calc;
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

	private void makeToast(String say) {
		Context context = getApplicationContext();
		int duration = Toast.LENGTH_LONG;

		Toast toast = Toast.makeText(context, say, duration);
		toast.show();
	}
}
