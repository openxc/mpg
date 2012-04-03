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

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
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
		
		drawTopLeft();
		drawBotomLeft();
		drawTopRight();
		drawBottomRight();
	}
	
	private void drawTopLeft() {
	    GraphicalView chartPetrol = getChart(DbHelper.C_FUEL, "Gas Used", "litres", false);
	    FrameLayout layout = (FrameLayout) findViewById(R.id.topLeft);
	    layout.addView(chartPetrol);
	}
	
	private void drawBotomLeft() {
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
            c.moveToPrevious();
            String sPreviousTrip = new DecimalFormat("##").format(c.getDouble(0));
            lastMileage.setText(sLastTrip);
            previousMileage.setText(sPreviousTrip);
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
	    Log.i(TAG, "Minx is: "+series.getMinX());
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
	    DateMidnight endDate = new DateMidnight();
	    endDate = endDate.minusDays(endDate.getDayOfWeek());
	    DateMidnight startDate = endDate.minusDays(7);
	    int weeks = 12; // FIXME load from settings
	        	
		for (int i=0; i < weeks; i++) {
		    double total = 0;
    		Cursor data = dbHelper.getLastData(startDate.toString(pattern), endDate.toString(pattern), column);
		    total = calculateData(data, average);
    		
		    series.add(startDate.getWeekOfWeekyear(), total);
		    
		    endDate = endDate.minusWeeks(1);
		    startDate = startDate.minusWeeks(1);
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
