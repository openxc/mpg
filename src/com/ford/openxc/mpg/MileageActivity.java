package com.ford.openxc.mpg;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.SeriesSelection;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MileageActivity extends Activity {
	private HashMap<Integer, Integer> idLookup;

	private static final String TAG = "MileageActivity";
	private DbHelper dbHelper;

	private XYMultipleSeriesRenderer mRenderer = new XYMultipleSeriesRenderer();
	private GraphicalView chart;

	final static int INFO_DIALOG = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.mileage);
		dbHelper = new DbHelper(this);

		mRenderer.setApplyBackgroundColor(true);
		mRenderer.setBackgroundColor(Color.argb(100, 50, 50, 50));
		mRenderer.setAxisTitleTextSize(16);
		mRenderer.setChartTitleTextSize(20);
		mRenderer.setLabelsTextSize(15);
		mRenderer.setShowLegend(false);
		mRenderer.setShowGrid(true);

		TimeSeries data = new TimeSeries("Data");
		XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
		TimeSeries temp = populateSeries(data);
		dataset.addSeries(temp);

		XYSeriesRenderer rend = new XYSeriesRenderer();
		rend.setPointStyle(PointStyle.SQUARE);
		rend.setFillPoints(true);
		rend.setLineWidth(Float.MIN_NORMAL);
		mRenderer.addSeriesRenderer(rend);
		chart = ChartFactory.getTimeChartView(this, dataset, mRenderer, null);
		mRenderer.setClickEnabled(true);
		chart.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				SeriesSelection ss = chart.getCurrentSeriesAndPoint();
				if (ss != null) {
					Bundle bundle = new Bundle();
					bundle.putDouble("index", ss.getPointIndex());
					showDialog(INFO_DIALOG, bundle);
				}
			}
		});


		mRenderer.setXTitle("Date Trip Started");
		mRenderer.setYTitle("Gas Mileage");		
		LinearLayout layout = (LinearLayout) findViewById(R.id.chartM);
		layout.addView(chart, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
	}

	@Override
	protected Dialog onCreateDialog(int id, Bundle bundle) {
		switch(id) {
		case INFO_DIALOG: 
			Log.i(TAG, "Creating info dialog");
			Dialog d = new Dialog(this);

			d.setContentView(R.layout.custom_dialog);

			d.setCanceledOnTouchOutside(true);
			d.getWindow().setGravity(Gravity.RIGHT);
			return d;
		default:
			return null;
		}
	}

	@Override
	protected void onPrepareDialog(int id, Dialog d, Bundle bundle) {
		Log.i(TAG, "Preparing info dialog");
		double index = bundle.getDouble("index");
		Log.i(TAG, "Index is: "+index);
		String[] data = getData((int) index);

		d.setTitle("Trip taken on: "+data[1].substring(0, 10));

		TextView dataTime = (TextView) d.findViewById(R.id.dataTime);
		dataTime.setText(data[1]);

		TextView dataLength = (TextView) d.findViewById(R.id.dataLength);
		int temp = (int) Double.parseDouble(data[2]);
		if (temp <= 1) {
			dataLength.setText("less than a minute");
		}
		else if (temp > 300) {
			dataLength.setText(Double.toString(temp/60)+" hours");
		}
		else {
			dataLength.setText(Integer.toString(temp)+" minutes");
		}

		TextView dataGas = (TextView) d.findViewById(R.id.dataGas);
		dataGas.setText(data[4]+" l");

		TextView dataGasMileage = (TextView) d.findViewById(R.id.dataGasMileage);
		dataGasMileage.setText(data[5]+" km/l");

		TextView dataDistance = (TextView) d.findViewById(R.id.dataDistance);
		dataDistance.setText(data[3]+" km");

		super.onPrepareDialog(id, d);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu2, menu);
		EditText startDate = (EditText) menu.findItem(R.id.dateRefine).getActionView();
		startDate.setText("None set");
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.dateRefine:
			Log.i(TAG, "date refine clicked!");

		}
		return super.onOptionsItemSelected(item);
	}

	private TimeSeries populateSeries(TimeSeries data) {
		idLookup = new HashMap<Integer, Integer>();
		SQLiteDatabase db = dbHelper.getReadableDatabase();
		String[] columns = {DbHelper.C_MILEAGE, DbHelper.C_TIME, DbHelper.C_ID};
		Cursor c = db.query(DbHelper.TABLE, columns, null, null, null, null, null);
		c.moveToFirst();
		for (int x=0; x < c.getCount(); x++) {
			double miles = c.getDouble(c.getColumnIndex(DbHelper.C_MILEAGE));
			String stime = c.getString(c.getColumnIndex(DbHelper.C_TIME));
			int pkey = c.getInt(c.getColumnIndex(DbHelper.C_ID));

			idLookup.put(x, pkey);
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			Date time = null;
			try {
				time = sdf.parse(stime);
			} catch (ParseException e) {
				Log.e(TAG, "Malformed time recieved. Crashing!");
			}
			data.add(time, miles);
			c.moveToNext();
		}
		c.close();
		db.close();
		return data;
	}

	private String[] getData(int index) {
		String returnArray[] = new String[6];
		SQLiteDatabase db = dbHelper.getReadableDatabase();
		int tsearch = idLookup.get(index);
		String search = Integer.toString(tsearch);
		Cursor c = db.query(DbHelper.TABLE, null, DbHelper.C_ID+" = "+search, null, null, null, null);
		c.moveToFirst();
		for(int x=0; x < c.getColumnCount(); x++) {
			returnArray[x] = c.getString(x);
		}
		c.close();
		db.close();
		return returnArray;
	}

	private void makeToast(String say) {
		Context context = getApplicationContext();
		int duration = Toast.LENGTH_SHORT;

		Toast toast = Toast.makeText(context, say, duration);
		toast.show();
	}
}
