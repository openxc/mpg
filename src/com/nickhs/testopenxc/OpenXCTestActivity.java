package com.nickhs.testopenxc;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
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
import android.graphics.Paint.Align;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.openxc.VehicleService;
import com.openxc.VehicleService.VehicleServiceBinder;
import com.openxc.measurements.FineOdometer;
import com.openxc.measurements.FuelConsumed;
import com.openxc.measurements.IgnitionStatus;
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
	private final Handler handler = new Handler(); //FIXME is this needed?
	private boolean instantUpdate = false;
	private boolean scrollGraph = true;
	private boolean pollMeasurements = false;
	private int POLL_FREQUENCY = -1;
	private static int OPTIMAL_SPEED = 97;
	private double lastUsageCount = 0;
	
	private XYMultipleSeriesRenderer mRenderer = new XYMultipleSeriesRenderer(2);
	private XYSeries speedSeries = new XYSeries("Speed");
	private XYSeries gasSeries = new XYSeries("Gas Consumed"); // FIXME strings should be hardcoded
	private GraphicalView mChartView;
	
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
				mRenderer.setYAxisMin(0);
				Log.i(TAG, "Scroll Lock clicked");
			}
		});
        
        Intent intent = new Intent(this, VehicleService.class);
       	bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
       	
       	dbHelper = new DbHelper(this);
       	
        mRenderer.setApplyBackgroundColor(true);
        mRenderer.setBackgroundColor(Color.argb(100, 50, 50, 50));
        mRenderer.setAxisTitleTextSize(16);
        mRenderer.setChartTitleTextSize(20);
        mRenderer.setLabelsTextSize(15);
        mRenderer.setLegendTextSize(15);
        mRenderer.setShowGrid(true);
        mRenderer.setYAxisMax(100);
        mRenderer.setYAxisMin(0);
        mRenderer.setPanLimits(new double[] {0, Integer.MAX_VALUE, 0, 400});
        
        LinearLayout layout = (LinearLayout) findViewById(R.id.chart);
        XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
        dataset.addSeries(speedSeries);
        dataset.addSeries(gasSeries);
        XYSeries optimalSpeed = new XYSeries("Optimal Speed"); //TODO String should be referenced from strings.xml
        optimalSpeed.add(0, OPTIMAL_SPEED); optimalSpeed.add(Integer.MAX_VALUE, OPTIMAL_SPEED);
        dataset.addSeries(optimalSpeed);
        XYSeriesRenderer sRenderer = new XYSeriesRenderer();
        XYSeriesRenderer gRenderer = new XYSeriesRenderer();
        mRenderer.addSeriesRenderer(0, sRenderer);
        mRenderer.addSeriesRenderer(1, gRenderer);
        mRenderer.addSeriesRenderer(new XYSeriesRenderer());
        sRenderer.setPointStyle(PointStyle.X);
        sRenderer.setColor(Color.RED);
        gRenderer.setFillBelowLine(true);
        
        mRenderer.setYTitle("Speed (km/s)", 0);
        mRenderer.setYAxisAlign(Align.LEFT, 0);
        mRenderer.setXTitle("Time (ms)");
        mRenderer.setYTitle("Fuel Usage (litres)", 1);
        mRenderer.setYAxisAlign(Align.RIGHT, 1);
       
        
        mRenderer.setRange(new double[] {0, 50000, 0, 100}); // FIXME
        mChartView = ChartFactory.getLineChartView(this, dataset, mRenderer);
        mChartView.addPanListener(panListener);
        mChartView.addZoomListener(zoomListener, false, true);
        layout.addView(mChartView, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
        
        START_TIME = getTime();
    }
    
    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, 0, Menu.NONE, R.string.settingsTitle);
		menu.add(0, 1, Menu.NONE, R.string.exitTitle);
		menu.add(0, 2, Menu.NONE, R.string.saveTitle);
		menu.add(0, 3, Menu.NONE, R.string.mileTitle);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Log.i(TAG, "Option Selected "+item.getItemId());
		switch (item.getItemId()) {
		case 0:
			startActivity(new Intent(this, ShowSettingsActivity.class));
			break;
		case 1:
			System.exit(0);
			break;
		case 2:
			manualSave();
			break;
		case 3:
			startActivity(new Intent(this, MileageActivity.class));
			break;
		}
		return super.onOptionsItemSelected(item);
	}
    
	VehicleSpeed.Listener speedListener = new VehicleSpeed.Listener() {
        public void receive(VehicleMeasurement measurement) {
            if (instantUpdate) {
            	final VehicleSpeed meas = (VehicleSpeed) measurement;
            	final String measure = Double.toString(meas.getValue().doubleValue());
                speed.post(new Runnable() {
    				@Override
    				public void run() {
    		            speed.setText(measure);
    				}
    			});
                double time = getTime();
                drawGraph((time-START_TIME), Double.parseDouble(measure), -1); //because I'm an idiot
            }
            else {
            	Log.w(TAG, "This should not run, you have a bug");
            }
        }
    };
    
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
			// vehicleService.waitUntilBound();
			Log.i(TAG, "Remote Vehicle Service bound");
			try {
				vehicleService.setDataSource(TraceVehicleDataSource.class.getName(), "resource://"+R.raw.drivingc);
			} catch (RemoteVehicleServiceException e) {
				Log.e(TAG, "RemoteVehicleException occurred");
				Log.e(TAG, e.getMessage());
			}
			/* try { // FIXME renable listener when ready
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
			IgnitionStatus status = (IgnitionStatus) arg0;
			String stats = status.getValue().toString();
			Log.d(TAG, "Ignition Status is: "+stats);
			if (status.getValue().toString().equalsIgnoreCase("State{value=OFF}")) {
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
					pollStop();
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
         if (scrollGraph) {
       	  if (time > 50000) { // FIXME should be a preference
           	  double max = speedSeries.getMaxX();
           	  mRenderer.setXAxisMax(max+POLL_FREQUENCY);
           	  mRenderer.setXAxisMin(max-50000); //FIXME
       	  }
         }
         if (mChartView != null) {
       	  mChartView.repaint();
         }
	}
   
	private void updateMeasurements() {
		pollMeasurements = true;
		if(isBound) {
	   		new Thread(new Runnable () {
					@Override
					public void run() {
		        		while(pollMeasurements) {
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
	   		Log.e(TAG, "We're going down!");
	   	}
	}
	
	private double getSpeed() {
		VehicleSpeed speed;
		double temp = -1;
		try {
			speed = (VehicleSpeed) vehicleService.get(VehicleSpeed.class);
			temp = speed.getValue().doubleValue();
		} catch (UnrecognizedMeasurementTypeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoValueException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return temp;
	}
	
	private double getGasConsumed() {
		FuelConsumed fuel;
		double temp = -1;
		try {
			fuel = (FuelConsumed) vehicleService.get(FuelConsumed.class);
			temp = fuel.getValue().doubleValue();
		} catch (UnrecognizedMeasurementTypeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoValueException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		double diff = temp - lastUsageCount;
		lastUsageCount = temp;
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
	
	private double calculateGasUsage(double gas, double speedm) { //FIXME is this right?!
		double distance = speedm*POLL_FREQUENCY;
		double usage = gas-lastUsageCount;
		lastUsageCount = gas;
		Log.i(TAG, "Distance is: "+distance+". Usage is: "+usage);
		return distance/usage;
	}
	
	private void pollManager() {
		SharedPreferences setting = PreferenceManager.getDefaultSharedPreferences(this);
		String choice = setting.getString("update_interval", "0");
		Log.i(TAG, "Choice is: "+choice);
		if (Integer.parseInt(choice) > 0) {
			instantUpdate = false; //may not be needed FIXME
			removeListeners();
			POLL_FREQUENCY = Integer.parseInt(choice);
			if (pollMeasurements == false) {
				updateMeasurements();
			}
		}
		else {
			instantUpdate = true; // may not be needed. FIXME
			try {
				vehicleService.addListener(VehicleSpeed.class, speedListener);
			} catch (RemoteVehicleServiceException e) {
				e.printStackTrace();
			} catch (UnrecognizedMeasurementTypeException e) {
				e.printStackTrace();
			}
			pollMeasurements = false; // Disables polling (kills the for loop)
		}
	}
	
	private void pollInit() {
		SharedPreferences setting = PreferenceManager.getDefaultSharedPreferences(this);
		setting.registerOnSharedPreferenceChangeListener(prefListener);
		pollManager();
	}
	
	private void pollStop() {
		instantUpdate = false;
		removeListeners();
		pollMeasurements = false;
	}
	
	private void removeListeners() {
		try {
			vehicleService.removeListener(VehicleSpeed.class, speedListener);
		} catch (RemoteVehicleServiceException e) {
			e.printStackTrace();
		}
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
}