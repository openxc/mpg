package com.nickhs.testopenxc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Timestamp;

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
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.openxc.VehicleService;
import com.openxc.VehicleService.VehicleServiceBinder;
import com.openxc.measurements.FineOdometer;
import com.openxc.measurements.FuelConsumed;
import com.openxc.measurements.IgnitionStatus;
import com.openxc.measurements.IgnitionStatus.IgnitionPosition;
import com.openxc.measurements.UnrecognizedMeasurementTypeException;
import com.openxc.measurements.VehicleMeasurement;
import com.openxc.measurements.VehicleSpeed;
import com.openxc.remote.NoValueException;
import com.openxc.remote.RemoteVehicleServiceException;
import com.openxc.remote.sources.trace.TraceVehicleDataSource;
import com.openxc.remote.sources.usb.UsbVehicleDataSource;

/* TODO: Send the range into a sharedpreferences.
 * Check on how many points before we die
 * Broadcast filter for ignition on
 * Fix getLastData
 */

public class OpenXCTestActivity extends Activity {
	VehicleService vehicleService;
	DbHelper dbHelper;

	private boolean isBound = false;	
	private boolean isRunning = false;
	private boolean scrollGraph = true;

	private static int OPTIMAL_SPEED = 97;

	private long START_TIME = -1;
	private int POLL_FREQUENCY = -1;
	private double lastUsageCount = 0;
	private int CAN_TIMEOUT = 30;

	private XYMultipleSeriesRenderer mSpeedRenderer = new XYMultipleSeriesRenderer();
	private XYMultipleSeriesRenderer mGasRenderer = new XYMultipleSeriesRenderer();
	private XYSeries speedSeries = new XYSeries("Speed");
	private XYSeries gasSeries = new XYSeries("Gas Consumed"); // FIXME strings should be hardcoded
	private GraphicalView mSpeedChartView;
	private GraphicalView mGasChartView;

	final static String TAG = "XCTest";

	private TextView speed;
	private TextView mpg;
	private ToggleButton scroll;
	private SharedPreferences sharedPrefs;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		Log.i(TAG, "onCreated");

		speed = (TextView) findViewById(R.id.textSpeed);
		mpg = (TextView) findViewById(R.id.textMPG);
		scroll = (ToggleButton) findViewById(R.id.toggleButton1);
		scroll.setChecked(scrollGraph);
		scroll.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				scrollGraph = !scrollGraph;
				scroll.setChecked(scrollGraph);
				mSpeedRenderer.setYAxisMin(0);
				mGasRenderer.setYAxisMin(0);
			}
		});

		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener);

		Intent intent = new Intent(this, VehicleService.class);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

		dbHelper = new DbHelper(this);

		XYMultipleSeriesDataset gDataset = initGraph(mGasRenderer, gasSeries);
		XYMultipleSeriesDataset sDataset = initGraph(mSpeedRenderer, speedSeries);

		START_TIME = getTime();

		if (savedInstanceState != null) {
			double[] speedX = savedInstanceState.getDoubleArray("speedX");
			double[] speedY = savedInstanceState.getDoubleArray("speedY");
			for (int i = 0; i < speedX.length; i++) {
				speedSeries.add(speedX[i], speedY[i]);
			}

			double[] gasX = savedInstanceState.getDoubleArray("gasX");
			double[] gasY = savedInstanceState.getDoubleArray("gasY");
			for (int i = 0; i < gasX.length; i++) {
				gasSeries.add(gasX[i], gasY[i]);
			}

			Log.i(TAG, "Recreated graph");

			START_TIME = savedInstanceState.getLong("time");
		}

		mSpeedRenderer.setXTitle("Time (ms)");
		mSpeedRenderer.setYTitle("Speed (km/h)");

		mGasRenderer.setXTitle("Time (ms)");
		mGasRenderer.setYTitle("Fuel Usage (litres)");

		XYSeries optimalSpeed = new XYSeries("Optimal Speed"); //TODO String should be referenced from strings.xml
		optimalSpeed.add(0, OPTIMAL_SPEED); optimalSpeed.add(Integer.MAX_VALUE, OPTIMAL_SPEED);
		sDataset.addSeries(optimalSpeed);
		mSpeedRenderer.addSeriesRenderer(1, new XYSeriesRenderer());

		mSpeedRenderer.setRange(new double[] {0, 50000, 0, 100}); // FIXME
		mGasRenderer.setRange(new double[] {0, 50000, 0, 0.03});

		FrameLayout topLayout = (FrameLayout) findViewById(R.id.topChart);
		FrameLayout botLayout = (FrameLayout) findViewById(R.id.botChart);

		mSpeedChartView = ChartFactory.getTimeChartView(this, sDataset, mSpeedRenderer, null);
		mSpeedChartView.addPanListener(panListener);
		topLayout.addView(mSpeedChartView, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));

		mGasChartView = ChartFactory.getTimeChartView(this, gDataset, mGasRenderer, null);
		mGasChartView.addPanListener(panListener);
		botLayout.addView(mGasChartView, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Log.i(TAG, "onSaveInstanceState");
		outState.putInt("count", speedSeries.getItemCount());
		double[] speedX = convertToArray(speedSeries, "x");
		double[] speedY = convertToArray(speedSeries, "y");
		double[] gasX = convertToArray(gasSeries, "x");
		double[] gasY = convertToArray(gasSeries, "y");

		outState.putDoubleArray("speedX", speedX);
		outState.putDoubleArray("speedY", speedY);
		outState.putDoubleArray("gasX", gasX);
		outState.putDoubleArray("gasY", gasY);
		outState.putLong("time", START_TIME);
	}

	private double[] convertToArray(XYSeries series, String type) {
		int count = series.getItemCount();
		double[] array = new double[count];
		for (int i=0; i < count; i++) {
			if (type.equalsIgnoreCase("x")) {
				array[i] = series.getX(i);
			}

			else if (type.equalsIgnoreCase("y")) {
				array[i] = series.getY(i);
			}

			else {
				Log.e(TAG, "Invalid call to convertToArray");
				Log.e(TAG, "Type is invalid: "+type);
				break;
			}
		}
		return array;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy called");
		POLL_FREQUENCY = -1;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Log.i(TAG, "Option Selected "+item.getItemId());
		switch (item.getItemId()) {
		case R.id.settings:
			startActivity(new Intent(this, ShowSettingsActivity.class));
			break;
		case R.id.close:
			System.exit(0);
			break;
		case R.id.stopRecording:
			stopRecording();
			break;
		case R.id.pauseRecording:
			if (isRunning) {
				POLL_FREQUENCY = -1;
				item.setIcon(android.R.drawable.ic_media_play);
			}
			else {
				pollManager();
				item.setIcon(android.R.drawable.ic_media_pause);
			}
			break;
		case R.id.viewOverview:
			startActivity(new Intent(this, MileageActivity.class));
			break;
		case R.id.createData:
			dbHelper.createTestData(1);
			break;
		case R.id.viewGraphs:
			startActivity(new Intent(this, OverviewActivity.class));
		}
		return super.onOptionsItemSelected(item);
	}

	public ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceDisconnected(ComponentName name) {
			vehicleService = null;
			Log.i(TAG, "Service unbound");
			isBound = false;
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			VehicleServiceBinder binder = (VehicleServiceBinder) service;
			vehicleService = binder.getService();
			Log.i(TAG, "Remote Vehicle Service bound");

			Log.i(TAG, "Trace file is: "+sharedPrefs.getBoolean("use_trace_file", true));
			if (sharedPrefs.getBoolean("use_trace_file", false)) {
				Log.i(TAG, "Using trace file");
				try {
					vehicleService.setDataSource(TraceVehicleDataSource.class.getName(), "file:///sdcard/drivingnew");
				} catch (RemoteVehicleServiceException e) {
					Log.e(TAG, e.getMessage());
					e.printStackTrace();
				}
			}

			else {
				try {
					vehicleService.setDataSource(UsbVehicleDataSource.class.getName(), null);
				} catch (RemoteVehicleServiceException e) {
					Log.e(TAG, e.getMessage());
					e.printStackTrace();
				}
			}

			try {
				vehicleService.addListener(IgnitionStatus.class, ignitionListener);
			} catch (RemoteVehicleServiceException e) {
				e.printStackTrace();
			} catch (UnrecognizedMeasurementTypeException e) {
				e.printStackTrace();
			}

			isBound = true;
			pollManager();
		}
	};

	OnSharedPreferenceChangeListener prefListener = new OnSharedPreferenceChangeListener() {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
				String key) {
			Log.i(TAG, "Preference changed: "+key);

			if (key.equalsIgnoreCase("use_trace_file")) {
				Log.i(TAG, "finishing");
				finish();
				startActivity(new Intent(getApplicationContext(), OpenXCTestActivity.class));
			}

			else {
				pollManager();
			}
		}
	};

	PanListener panListener = new PanListener() {
		@Override
		public void panApplied() {
			scrollGraph = false;
			scroll.setChecked(false);
		}
	};

	IgnitionStatus.Listener ignitionListener = new IgnitionStatus.Listener() {
		@Override
		public void receive(VehicleMeasurement arg0) {
			IgnitionPosition ignitionPosition = 
					((IgnitionStatus) arg0).getValue().enumValue();
			Log.i(TAG, "Ignition is "+ignitionPosition.toString());
			if (ignitionPosition == IgnitionPosition.OFF) {
				Log.i(TAG, "Ignition is off. Halting recording");
				stopRecording();
			}
		}
	};

	private void drawGraph(double time, double speed, double gas) {
		speedSeries.add(time, speed);
		gasSeries.add(time, gas);
		if (scrollGraph) {
			if (time > 50000) { // FIXME should be a preference
				double max = speedSeries.getMaxX();
				mSpeedRenderer.setXAxisMax(max+POLL_FREQUENCY);
				mSpeedRenderer.setXAxisMin(max-50000); //FIXME
				mGasRenderer.setXAxisMax(max+POLL_FREQUENCY);
				mGasRenderer.setXAxisMin(max-50000);
			}
		}
		if (mSpeedChartView != null) {
			mSpeedChartView.repaint();
			mGasChartView.repaint();
		}
	}

	private void updateMeasurements() {
		if(isBound) {
			isRunning = true;
			new Thread(new Runnable () {
				@Override
				public void run() {
					while(true) {
						if (checkForCANFresh())	getMeasurements();
						else stopRecording();
						try {
							Thread.sleep(POLL_FREQUENCY);
						} catch (InterruptedException e) {
							Log.e(TAG, "InterruptedException");
						} catch (IllegalArgumentException e) {
							Log.i(TAG, "Breaking out of measurement loop");
							isRunning = false;
							break;
						}
					}
				}
			}).start();
		}

		else {
			Log.e(TAG, "No Service Bound - this should not happen");
		}
	}

	private double getSpeed() {
		VehicleSpeed speed;
		double temp = -1;
		try {
			speed = (VehicleSpeed) vehicleService.get(VehicleSpeed.class);
			temp = speed.getValue().doubleValue();
		} catch (UnrecognizedMeasurementTypeException e) {
			e.printStackTrace();
		} catch (NoValueException e) {
			Log.w(TAG, "Failed to get speed measurement");
		}
		return temp;
	}

	private double getGasConsumed() {
		FuelConsumed fuel;
		double temp = 0;
		try {
			fuel = (FuelConsumed) vehicleService.get(FuelConsumed.class);
			temp = fuel.getValue().doubleValue();
		} catch (UnrecognizedMeasurementTypeException e) {
			e.printStackTrace();
		} catch (NoValueException e) {
			Log.w(TAG, "Failed to get fuel measurement");
		}

		double diff = temp - lastUsageCount;
		lastUsageCount = temp;
		if (diff > 1) { // catch bogus values FIXME
			diff = 0;
		}

		return diff;
	}
	
	private boolean checkForCANFresh() {
		boolean ret = false;
		try {
			VehicleSpeed measurement = (VehicleSpeed) vehicleService.get(VehicleSpeed.class);
			if (measurement.getAge() < CAN_TIMEOUT) ret = true;
		} catch (UnrecognizedMeasurementTypeException e) {
			e.printStackTrace();
		} catch (NoValueException e) {
			Log.e(TAG, "NoValueException thrown, ret is "+ret);
		}
		return ret;
	}

	private void getMeasurements() {
		
		double speedm = getSpeed();
		double gas = getGasConsumed();

		final String temp = Double.toString(speedm);
		speed.post(new Runnable() {
			public void run() {
				speed.setText(temp);
			}
		});

		final double usage = gas;
		mpg.post(new Runnable() {
			public void run() {
				mpg.setText(Double.toString(usage));
			}
		});

		double time = getTime();
		drawGraph((time-START_TIME), speedm, gas);
	}

	private void pollManager() {
		String choice = sharedPrefs.getString("update_interval", "1000");
		POLL_FREQUENCY = Integer.parseInt(choice);
		if (!isRunning) updateMeasurements();
	}

	private long getTime() {
		Time curTime = new Time();
		curTime.setToNow();
		long time = curTime.toMillis(false);
		return time;
	}

	private void makeToast(String say) {
		Context context = getApplicationContext();
		int duration = Toast.LENGTH_SHORT;

		Toast toast = Toast.makeText(context, say, duration);
		toast.show();
	}

	private void stopRecording() {
		FineOdometer oMeas;
		try {
			oMeas = (FineOdometer) vehicleService.get(FineOdometer.class);
			final double distanceTravelled = oMeas.getValue().doubleValue();
			FuelConsumed fMeas = (FuelConsumed) vehicleService.get(FuelConsumed.class);
			final double fuelConsumed = fMeas.getValue().doubleValue();
			final double gasMileage = distanceTravelled/fuelConsumed;
			long endTime = getTime();
			dbHelper.saveResults(distanceTravelled, fuelConsumed, gasMileage, START_TIME, endTime);
			
			Bitmap speedChart = mSpeedChartView.toBitmap();
			Bitmap gasChart = mGasChartView.toBitmap();
			
			File sdCard = Environment.getExternalStorageDirectory();
			File dir = new File(sdCard.getAbsolutePath()+"/mpg/tracegraphs");
			
			if (!dir.exists()) {
				Log.i(TAG, "Creating folders");
				if(!dir.mkdirs()) {
					Log.e(TAG, "Creating folders failed!");
				}
			}
						
			FileOutputStream fout1 = new FileOutputStream(new File(dir, START_TIME+"s.png"));
			speedChart.compress(Bitmap.CompressFormat.PNG, 90, fout1);
			fout1.close();
			
			FileOutputStream fout2 = new FileOutputStream(new File(dir, START_TIME+"g.png"));
			gasChart.compress(Bitmap.CompressFormat.PNG, 90, fout2);
			fout2.close();

			startActivity(new Intent(this, OverviewActivity.class));
			POLL_FREQUENCY = -1;
			
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					makeToast("Distance moved: "+distanceTravelled+". Fuel Consumed is: "+fuelConsumed+" Last trip gas mileage was: "+gasMileage);					
				}
			});
			
			vehicleService.removeListener(IgnitionStatus.class, ignitionListener); // FIXME check what's causing this
			
		} catch (UnrecognizedMeasurementTypeException e) {
			e.printStackTrace();
		} catch (NoValueException e) {
			e.printStackTrace();
		} catch (RemoteVehicleServiceException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
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
		rend.addSeriesRenderer(tempRend);
		return dataset;
	}
}
