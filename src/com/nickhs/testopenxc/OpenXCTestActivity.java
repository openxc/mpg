package com.nickhs.testopenxc;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.achartengine.tools.PanListener;
import org.achartengine.tools.ZoomEvent;
import org.achartengine.tools.ZoomListener;

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

/* TODO: Send the range into a sharedpreferences. Instantiate sharedprefs and make it global? Why are there so many
 * global variables? Jesus.
 * Actually implement the system life cycle
 * Actually implement a base application
 * Check on how many points before we die
 * Destroy zoom func?
 * Broadcast filter for ignition on
 */

public class OpenXCTestActivity extends Activity {
	VehicleService vehicleService;
	DbHelper dbHelper;
	private boolean isBound = false;	
	private double START_TIME = -1;
	private boolean scrollGraph = true;
	private int POLL_FREQUENCY = -1;
	private static int OPTIMAL_SPEED = 97;
	private double lastUsageCount = 0;
	
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
				Log.i(TAG, "Scroll Lock clicked");
			}
		});
        
        Intent intent = new Intent(this, VehicleService.class);
       	bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
       	
       	dbHelper = new DbHelper(this);
       	
        XYMultipleSeriesDataset gDataset = initGraph(mGasRenderer, gasSeries);
        XYMultipleSeriesDataset sDataset = initGraph(mSpeedRenderer, speedSeries);
        
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
        mSpeedChartView.addZoomListener(zoomListener, false, true);
        topLayout.addView(mSpeedChartView, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
        
        mGasChartView = ChartFactory.getTimeChartView(this, gDataset, mGasRenderer, null);
        mGasChartView.addPanListener(panListener);
        mGasChartView.addZoomListener(zoomListener, false, true);
        botLayout.addView(mGasChartView, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
                
        START_TIME = getTime();
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
		case R.id.manualSave:
			manualSave();
			break;
		case R.id.viewOverview:
			Log.e(TAG, "viewing overview!");
			startActivity(new Intent(this, MileageActivity.class));
			break;
		case R.id.createData:
			Log.i(TAG, "running create test data");
			dbHelper.createTestData(100);
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
			/* try {
				vehicleService.setDataSource(TraceVehicleDataSource.class.getName(), "resource://"+R.raw.driving2);
				makeToast("Using trace file");
			} catch (RemoteVehicleServiceException e) {
				Log.e(TAG, "RemoteVehicleException occurred");
				Log.e(TAG, e.getMessage());
			}
		/*	try { // FIXME renable listener when ready
				vehicleService.addListener(IgnitionStatus.class, ignitionListener);
			} catch (RemoteVehicleServiceException e) {
				e.printStackTrace();
			} catch (UnrecognizedMeasurementTypeException e) {
				e.printStackTrace();
			} */
			isBound = true;
			pollInit();
		}
	};
	
	OnSharedPreferenceChangeListener prefListener = new OnSharedPreferenceChangeListener() {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
				String key) {
			Log.i(TAG, "Preference changed: "+key);
			pollManager();
		}
	};
	
	PanListener panListener = new PanListener() {
		@Override
		public void panApplied() {
			Log.i(TAG, "Person is panning");
			scrollGraph = false;
			scroll.setChecked(false);
		}
	};
	
	ZoomListener zoomListener = new ZoomListener() {
		@Override
		public void zoomReset() {
			Log.i(TAG, "Zoom Reset");
		}
		
		@Override
		public void zoomApplied(ZoomEvent e) {
			Log.i(TAG, "Zoom Applied: "+e.getZoomRate());
		}
	};
	
	IgnitionStatus.Listener ignitionListener = new IgnitionStatus.Listener() {
		@Override
		public void receive(VehicleMeasurement arg0) {
			IgnitionPosition ignitionPosition = 
                    ((IgnitionStatus) arg0).getValue().enumValue();
			Log.d(TAG, "Ignition Status is: " + ignitionPosition);
			if (ignitionPosition == IgnitionPosition.OFF) {
                Log.i(TAG, "Ignition is " + ignitionPosition + 
                        " -- recording a data point");
				try {
					FineOdometer oMeas = (FineOdometer) vehicleService.get(FineOdometer.class);
					double distanceTravelled = oMeas.getValue().doubleValue();
					FuelConsumed fMeas = (FuelConsumed) vehicleService.get(FuelConsumed.class);
					double fuelConsumed = fMeas.getValue().doubleValue();
					double gasMileage = distanceTravelled/fuelConsumed;
					Log.i(TAG, "Distance moved: "+distanceTravelled+". Fuel Consumed is: "+fuelConsumed);
					Log.i(TAG, "Last trip gas mileage was: "+gasMileage);
					vehicleService.removeListener(IgnitionStatus.class, ignitionListener);
				//	makeToast("Distance moved: "+distanceTravelled+". Fuel Consumed is: "+fuelConsumed+" Last trip gas mileage was: "+gasMileage);
					double endTime = getTime();
					dbHelper.saveResults(distanceTravelled, fuelConsumed, gasMileage, START_TIME, endTime);
					Intent intent = new Intent(getApplicationContext(), MileageActivity.class);
					startActivity(intent);
				} catch (UnrecognizedMeasurementTypeException e) {
					e.printStackTrace();
				} catch (NoValueException e) {
					e.printStackTrace();
				} catch (RemoteVehicleServiceException e) {
					e.printStackTrace();
				}
			}
		}
	};
	
	private void drawGraph(double time, double speed, double gas) {
		 Log.i(TAG, "Time is: "+time+". Speed is: "+speed+". Gas is: "+gas);
         speedSeries.add(time, speed);
         gasSeries.add(time, gas);
         Log.i(TAG, "MaxX Gas is: "+gasSeries.getMaxX());
         Log.i(TAG, "MaxX Speed is: "+gasSeries.getMaxX());
         Log.i(TAG, "MinX: "+gasSeries.getMinX());
         Log.i(TAG, "Scale: "+gasSeries.getScaleNumber());
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
	   		new Thread(new Runnable () {
					@Override
					public void run() {
		        		while(true) {
		        			getMeasurements();
		        			try {
		        				Log.i(TAG, "Sleeping for "+POLL_FREQUENCY);
								Thread.sleep(POLL_FREQUENCY);
							} catch (InterruptedException e) {
								Log.e(TAG, "InterruptedException");
							}
		        		}
					}
	   		}).start();
	   	}
	   	else {
	   		Log.e(TAG, "Service not bound - breaking");
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
		if (diff > 1) { // catch bogus values
			diff = 0;
		}
		
		return diff;
	}
	
	private void getMeasurements() {
		double speedm = -1;
		double gas = -1;
		
		speedm = getSpeed();
		gas = getGasConsumed();
   		
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
		SharedPreferences setting = PreferenceManager.getDefaultSharedPreferences(this);
		String choice = setting.getString("update_interval", "0");
		POLL_FREQUENCY = Integer.parseInt(choice);
	}
	
	private void pollInit() {
		SharedPreferences setting = PreferenceManager.getDefaultSharedPreferences(this);
		setting.registerOnSharedPreferenceChangeListener(prefListener);
		pollManager();
		updateMeasurements();
	}
	
	private double getTime() {
		Time curTime = new Time();
   		curTime.setToNow();
   		double time = curTime.toMillis(false);
   		return time;
	}
	
	private void makeToast(String say) {
		Context context = getApplicationContext();
		int duration = Toast.LENGTH_SHORT;
		Toast toast = Toast.makeText(context, say, duration);
		toast.show();
	}
	
	private void manualSave() {
		FineOdometer oMeas;
		try {
			oMeas = (FineOdometer) vehicleService.get(FineOdometer.class);
			double distanceTravelled = oMeas.getValue().doubleValue();
			FuelConsumed fMeas = (FuelConsumed) vehicleService.get(FuelConsumed.class);
			double fuelConsumed = fMeas.getValue().doubleValue();
			double gasMileage = distanceTravelled/fuelConsumed;
			Log.i(TAG, "Distance moved: "+distanceTravelled+". Fuel Consumed is: "+fuelConsumed);
			Log.i(TAG, "Last trip gas mileage was: "+gasMileage);
		//	pollStop();
			double endTime = getTime();
			dbHelper.saveResults(distanceTravelled, fuelConsumed, gasMileage, START_TIME, endTime);
			makeToast("Distance moved: "+distanceTravelled+". Fuel Consumed is: "+fuelConsumed+" Last trip gas mileage was: "+gasMileage);
		} catch (UnrecognizedMeasurementTypeException e) {
			e.printStackTrace();
		} catch (NoValueException e) {
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
