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
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.text.format.Time;
import android.util.Log;
import android.view.KeyEvent;
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
import com.openxc.sources.DataSourceException;

import java.net.URI;

/* TODO: Send the range into a sharedpreferences.
 * Check on how many points before we die
 * Broadcast filter for ignition on
 * Fix getLastData
 */

public class MpgActivity extends Activity implements TextToSpeech.OnInitListener{
	private final static String TAG = "MpgActivity";
	private final static int CAN_TIMEOUT = 30;
	//private final static int OPTIMAL_SPEED = 97;

	private boolean mIsRecording = false;

	private boolean scrollGraph = true;

	private long mStartTime = -1;
	private double lastGasCount = 0;
	private double lastOdoCount = 0;
	private double lastMPG = 0;

	private XYMultipleSeriesRenderer mSpeedRenderer = new XYMultipleSeriesRenderer();
	private XYMultipleSeriesRenderer mMPGRenderer = new XYMultipleSeriesRenderer();
	//private XYMultipleSeriesRenderer mGasRenderer = new XYMultipleSeriesRenderer();
	private XYSeries speedSeries = new XYSeries("Speed");
	private XYSeries mpgSeries = new XYSeries("MPG");
	//private XYSeries gasSeries = new XYSeries("Gas Consumed"); // FIXME strings should be hardcoded
	private GraphicalView mSpeedChartView;
	private GraphicalView mMPGChartView;
	//private GraphicalView mGasChartView;

	private TextView distance;
	private TextView fuel;
	private ToggleButton scroll;
	private SharedPreferences sharedPrefs;
    private IgnitionPosition mLastIgnitionPosition;
	private VehicleManager vehicle;
	private DbHelper dbHelper;
    private MeasurementUpdater mMeasurementUpdater;
    private TabHost tabs;
    private boolean TTSReady = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		Log.i(TAG, "onCreated");

//		speed = (TextView) findViewById(R.id.textSpeed);
//		mpg = (TextView) findViewById(R.id.textMPG);
		distance = (TextView) findViewById(R.id.textDistance);
		fuel = (TextView) findViewById(R.id.textFuel);
		scroll = (ToggleButton) findViewById(R.id.toggleButton1);
		scroll.setChecked(scrollGraph);
		scroll.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				scrollGraph = !scrollGraph;
				scroll.setChecked(scrollGraph);
				mSpeedRenderer.setYAxisMin(0);
				mMPGRenderer.setYAxisMin(0);
				//mGasRenderer.setYAxisMin(0);
			}
		});

		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener);

		Intent intent = new Intent(this, VehicleManager.class);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

		dbHelper = new DbHelper(this);

		//XYMultipleSeriesDataset gDataset = initGraph(mGasRenderer, gasSeries);
		XYMultipleSeriesDataset sDataset = initGraph(mSpeedRenderer, speedSeries);
		XYMultipleSeriesDataset mDataset = initGraph(mMPGRenderer, mpgSeries);


		if (savedInstanceState != null) {
			double[] speedX = savedInstanceState.getDoubleArray("speedX");
			double[] speedY = savedInstanceState.getDoubleArray("speedY");
			for (int i = 0; i < speedX.length; i++) {
				speedSeries.add(speedX[i], speedY[i]);
			}
			
			double[] mpgX = savedInstanceState.getDoubleArray("mpgX");
			double[] mpgY = savedInstanceState.getDoubleArray("mpgY");
			for (int i = 0; i < mpgX.length; i++) {
				mpgSeries.add(mpgX[i], mpgY[i]);
			}

//			double[] gasX = savedInstanceState.getDoubleArray("gasX");
//			double[] gasY = savedInstanceState.getDoubleArray("gasY");
//			for (int i = 0; i < gasX.length; i++) {
//				gasSeries.add(gasX[i], gasY[i]);
//			}

			Log.i(TAG, "Recreated graph");

			mStartTime = savedInstanceState.getLong("time");
		}

		mSpeedRenderer.setXTitle("Time");
		mSpeedRenderer.setYTitle("Speed (mph)");

		mMPGRenderer.setXTitle("Time");
		mMPGRenderer.setYTitle("Miles per Gallon");
		
//		mGasRenderer.setXTitle("Time");
//		mGasRenderer.setYTitle("Fuel Usage (litres)");

//		XYSeries optimalSpeed = new XYSeries("Optimal Speed"); //TODO String should be referenced from strings.xml
//		optimalSpeed.add(0, OPTIMAL_SPEED); optimalSpeed.add(Integer.MAX_VALUE, OPTIMAL_SPEED);
//		sDataset.addSeries(optimalSpeed);
//		mSpeedRenderer.addSeriesRenderer(1, new XYSeriesRenderer());

		mSpeedRenderer.setRange(new double[] {0, 50000, 0, 100}); // FIXME
		mMPGRenderer.setRange(new double[] {0, 50000, 0, 100}); // FIXME
		//mGasRenderer.setRange(new double[] {0, 50000, 0, 0.03});

		FrameLayout topLayout = (FrameLayout) findViewById(R.id.topChart);
		FrameLayout botLayout = (FrameLayout) findViewById(R.id.botChart);

		mSpeedChartView = ChartFactory.getTimeChartView(this, sDataset, mSpeedRenderer, null);
		mSpeedChartView.addPanListener(panListener);
		botLayout.addView(mSpeedChartView, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		
		mMPGChartView = ChartFactory.getTimeChartView(this, mDataset, mMPGRenderer, null);
		mMPGChartView.addPanListener(panListener);
		topLayout.addView(mMPGChartView, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));

//		mGasChartView = ChartFactory.getTimeChartView(this, gDataset, mGasRenderer, null);
//		mGasChartView.addPanListener(panListener);
//		botLayout.addView(mGasChartView, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		
		tabs = (TabHost)findViewById(R.id.TabHost01);

        tabs.setup();

        TabHost.TabSpec spec1 = tabs.newTabSpec("tag1");

        spec1.setContent(R.id.topChart);
        spec1.setIndicator("Miles per Gallon");

        tabs.addTab(spec1);

        TabHost.TabSpec spec2 = tabs.newTabSpec("tag2");
        spec2.setContent(R.id.botChart);
        spec2.setIndicator("Speed");

        tabs.addTab(spec2);
        
        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, 1337);

	}
	
	@Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
    	if(ev.getAction() == KeyEvent.ACTION_DOWN){
    		if(ev.getKeyCode() == KeyEvent.KEYCODE_1) {
    			tabs.setCurrentTab(0);
    			return true;
    		} else if(ev.getKeyCode() == KeyEvent.KEYCODE_2) {
    			tabs.setCurrentTab(1);
    			return true;
    		} else if (ev.getKeyCode() == KeyEvent.KEYCODE_5) {
    			String strMPG = lastMPG + "miles per gallon";
    			mTts.speak(strMPG, TextToSpeech.QUEUE_FLUSH, null);
    		}
    	}
    	return super.dispatchKeyEvent(ev);
    }
	
	private TextToSpeech mTts;
	protected void onActivityResult(
	        int requestCode, int resultCode, Intent data) {
		Log.i(TAG, "onActivityResult called.");
	    if (requestCode == 1337) {
	        if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
	            // success, create the TTS instance
	            mTts = new TextToSpeech(this, (OnInitListener) this);
	            Log.i(TAG, "TTS object created!");
	        } else {
	            // missing data, install it
	        	Log.i(TAG, "No TTS data!  Install!");
	            Intent installIntent = new Intent();
	            installIntent.setAction(
	                TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
	            startActivity(installIntent);
	        }
	    }
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Log.i(TAG, "onSaveInstanceState");
		outState.putInt("count", mpgSeries.getItemCount());
		double[] speedX = convertToArray(speedSeries, "x");
		double[] speedY = convertToArray(speedSeries, "y");
		double[] mpgX = convertToArray(mpgSeries, "x");
		double[] mpgY = convertToArray(mpgSeries, "y");
//		double[] gasX = convertToArray(gasSeries, "x");
//		double[] gasY = convertToArray(gasSeries, "y");

		outState.putDoubleArray("speedX", speedX);
		outState.putDoubleArray("speedY", speedY);
		outState.putDoubleArray("mpgX", mpgX);
		outState.putDoubleArray("mpgY", mpgY);

//		outState.putDoubleArray("gasX", gasX);
//		outState.putDoubleArray("gasY", gasY);
		outState.putLong("time", mStartTime);
		outState.putBoolean("isRecording", mIsRecording);
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
        stopMeasurementUpdater();
        try {
            vehicle.removeListener(IgnitionStatus.class, ignitionListener);
        } catch(VehicleServiceException e) {
            Log.w(TAG, "Unable to remove ignition listener", e);
        }
        mTts.shutdown();
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
			if (mIsRecording) {
				stopMeasurementUpdater();
				item.setIcon(android.R.drawable.ic_media_play);
			} else {
                startMeasurementUpdater();
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
			vehicle = null;
			Log.i(TAG, "Service unbound");
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			VehicleBinder binder = (VehicleBinder) service;
			vehicle = binder.getService();
			Log.i(TAG, "Remote Vehicle Service bound");

			Log.i(TAG, "Trace file is: "+sharedPrefs.getBoolean("use_trace_file", true));
			if (sharedPrefs.getBoolean("use_trace_file", false)) {
				Log.i(TAG, "Using trace file");
				try {
					vehicle.addSource(new TraceVehicleDataSource(
                                MpgActivity.this,
                                new URI("file:///sdcard/drivingnew")));
				} catch (java.net.URISyntaxException e) {
					Log.e(TAG, e.getMessage());
					e.printStackTrace();
				} catch(DataSourceException e) {
					Log.e(TAG, e.getMessage());
					e.printStackTrace();
                }
			} else {
				try {
					vehicle.initializeDefaultSources();
				} catch (VehicleServiceException e) {
					Log.e(TAG, e.getMessage());
					e.printStackTrace();
				}
			}

			try {
				vehicle.addListener(IgnitionStatus.class, ignitionListener);
			} catch (VehicleServiceException e) {
				e.printStackTrace();
			} catch (UnrecognizedMeasurementTypeException e) {
				e.printStackTrace();
			}

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
				startActivity(new Intent(getApplicationContext(), MpgActivity.class));
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
		public void receive(Measurement arg0) {
			IgnitionPosition ignitionPosition =
					((IgnitionStatus) arg0).getValue().enumValue();
            if(ignitionPosition == IgnitionPosition.RUN ||
                    ignitionPosition == IgnitionPosition.OFF) {
                Log.d(TAG, "Ignition is " + ignitionPosition +
                        " and last position was " + mLastIgnitionPosition);
                if(ignitionPosition == IgnitionPosition.RUN &&
                        mLastIgnitionPosition == IgnitionPosition.OFF
                        || mLastIgnitionPosition == null) {
                    Log.i(TAG, "Ignition switched on -- starting recording");
                    startRecording();
                } else if(ignitionPosition == IgnitionPosition.OFF
                        && mLastIgnitionPosition == IgnitionPosition.RUN) {
                    Log.i(TAG, "Ignition switched off -- halting recording");
                    stopRecording();
                }
                mLastIgnitionPosition = ignitionPosition;
            }
		}
	};

	private void drawGraph(double time, double mpg, double speed) {
		speedSeries.add(time, speed);
		mpgSeries.add(time, mpg);
//		gasSeries.add(time, gas);
		if (scrollGraph) {
			if (time > 50000) { // FIXME should be a preference
                String choice = sharedPrefs.getString("update_interval", "1000");
                int pollFrequency = Integer.parseInt(choice);
                double max = mpgSeries.getMaxX();
				mSpeedRenderer.setXAxisMax(max + pollFrequency);
				mSpeedRenderer.setXAxisMin(max-50000); //FIXME
                mMPGRenderer.setXAxisMax(max + pollFrequency);
				mMPGRenderer.setXAxisMin(max-50000); //FIXME
//				mGasRenderer.setXAxisMax(max + pollFrequency);
//				mGasRenderer.setXAxisMin(max - 50000);
			}
		}
//		if (mSpeedChartView != null) {
//			mSpeedChartView.repaint();
//			mGasChartView.repaint();
//		}
		
		if (mMPGChartView != null) {
			mMPGChartView.repaint();
			mSpeedChartView.repaint();
		}

	}

    private class MeasurementUpdater extends Thread {
        private boolean mRunning = true;

        public void done() {
            mRunning = false;
        }

        public void run() {
            while(mRunning) {
                if (checkForCANFresh())	getMeasurements();
                else stopRecording();

                String choice = sharedPrefs.getString("update_interval", "1000");
                int pollFrequency = Integer.parseInt(choice);
                try {
                    Thread.sleep(pollFrequency);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Measurement updater was interrupted", e);
                }
            }
            Log.d(TAG, "Measurement polling stopped");
        }
    }

	private double getSpeed() {
		VehicleSpeed speed;
		double temp = -1;
		try {
			speed = (VehicleSpeed) vehicle.get(VehicleSpeed.class);
			temp = speed.getValue().doubleValue();
		} catch (UnrecognizedMeasurementTypeException e) {
			e.printStackTrace();
		} catch (NoValueException e) {
			Log.w(TAG, "Failed to get speed measurement");
		}
		return temp;
	}
    
	private double getFineOdometer() {
		FineOdometer fineOdo;
		double temp = -1;
		try {
			fineOdo = (FineOdometer) vehicle.get(FineOdometer.class);
			temp = fineOdo.getValue().doubleValue();
		} catch (UnrecognizedMeasurementTypeException e) {
			e.printStackTrace();
		} catch (NoValueException e) {
			Log.w(TAG, "Failed to get fine odometer measurement");
		}
		
		return temp;
	}

	private double getGasConsumed() {
		FuelConsumed fuel;
		double temp = 0;
		try {
			fuel = (FuelConsumed) vehicle.get(FuelConsumed.class);
			temp = fuel.getValue().doubleValue();
		} catch (UnrecognizedMeasurementTypeException e) {
			e.printStackTrace();
		} catch (NoValueException e) {
			Log.w(TAG, "Failed to get fuel measurement");
		}

		return temp;
	}

	private boolean checkForCANFresh() {
		boolean ret = false;
		try {
			VehicleSpeed measurement = (VehicleSpeed) vehicle.get(VehicleSpeed.class);
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
		double fineOdo = getFineOdometer();
		double gas = getGasConsumed();
		
		speedm *= 0.62137;  //Converting from kph to mph
		fineOdo *= 0.62137; //Converting from km to miles.
		gas *= 0.26417;  //Converting from L to Gal

//		final String temp = Double.toString(speedm);
//		speed.post(new Runnable() {
//			public void run() {
//				speed.setText(temp);
//			}
//		});
		
//		final double usage = gas;
//		mpg.post(new Runnable() {
//			public void run() {
//				mpg.setText(Double.toString(usage));
//			}
//		});
		
		final String temp = Double.toString(fineOdo);
		distance.post(new Runnable() {
			public void run() {
				distance.setText(temp);
			}
		});

		final double usage = gas;
		fuel.post(new Runnable() {
			public void run() {
				fuel.setText(Double.toString(usage));
			}
		});
		
		double CurrentGas = gas - lastGasCount;
		lastGasCount = gas;
		double CurrentDist = fineOdo - lastOdoCount;
		lastOdoCount = fineOdo;
		
		if(gas > 0.0) {
			lastMPG = CurrentDist / CurrentGas;  //miles per hour
			drawGraph(getTime(), lastMPG, speedm);
		} else {
			drawGraph(getTime(), 0.0, speedm);
		}

//		drawGraph(getTime(), speedm, gas);
	}

    private void startMeasurementUpdater() {
        stopMeasurementUpdater();
        mMeasurementUpdater = new MeasurementUpdater();
        mMeasurementUpdater.start();

        mIsRecording = true;
    }

    private void stopMeasurementUpdater() {
        if(mMeasurementUpdater != null) {
            mMeasurementUpdater.done();
        }

        mMeasurementUpdater = null;
        mIsRecording = false;
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

    private void startRecording() {
        if(mIsRecording) {
            Log.d(TAG, "Stopping recording before starting another one");
            stopRecording();
        }
		mStartTime = getTime();
        startMeasurementUpdater();
        mIsRecording = true;
    }

	private void stopRecording() {
        if(!mIsRecording) {
            Log.d(TAG, "No active recording available to stop");
            return;
        }
        stopMeasurementUpdater();

		try {
			FineOdometer oMeas = (FineOdometer) vehicle.get(FineOdometer.class);
			final double distanceTravelled = oMeas.getValue().doubleValue();
			FuelConsumed fMeas = (FuelConsumed) vehicle.get(FuelConsumed.class);
			final double fuelConsumed = fMeas.getValue().doubleValue();
			final double gasMileage = distanceTravelled/fuelConsumed;
			double endTime = getTime();
			dbHelper.saveResults(distanceTravelled, fuelConsumed, gasMileage, mStartTime, endTime);

			//startActivity(new Intent(this, OverviewActivity.class));
            stopMeasurementUpdater();

			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					makeToast("Distance moved: "+distanceTravelled+". Fuel Consumed is: "+fuelConsumed+" Last trip gas mileage was: "+gasMileage);
				}
			});

		} catch (UnrecognizedMeasurementTypeException e) {
			e.printStackTrace();
		} catch (NoValueException e) {
			e.printStackTrace();
		}
        mIsRecording = false;
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
	public void onInit(int status) {
		Log.i(TAG, "Text to speech finished initializing.");
		TTSReady = true;
	}
}
