package com.ford.openxc.mpg;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

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

public class MpgActivity extends Activity {
	private final static String TAG = "MpgActivity";
	private final static int CAN_TIMEOUT = 30;

	private boolean mIsRecording = false;

	private long mStartTime = -1;
	private double lastGasCount = 0;
	private double lastOdoCount = 0;

	private SharedPreferences sharedPrefs;
    private IgnitionPosition mLastIgnitionPosition;
	private VehicleManager vehicle;
	private DbHelper dbHelper;
    private MeasurementUpdater mMeasurementUpdater;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

        if(savedInstanceState != null) {
            mStartTime = savedInstanceState.getLong("time");
        }

		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

		Intent intent = new Intent(this, VehicleManager.class);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

		dbHelper = new DbHelper(this);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putLong("time", mStartTime);
		outState.putBoolean("isRecording", mIsRecording);
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
        ChartFragment fragment = (ChartFragment) getFragmentManager()
                .findFragmentById(R.id.speed_chart_fragment);
        fragment.addData(time, speed);

        fragment = (ChartFragment) getFragmentManager()
                .findFragmentById(R.id.mpg_chart_fragment);
        fragment.addData(time, mpg);
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

		double CurrentGas = gas - lastGasCount;
		lastGasCount = gas;
		double CurrentDist = fineOdo - lastOdoCount;
		lastOdoCount = fineOdo;

		if(gas > 0.0) {
			double mpg = CurrentDist / CurrentGas;  //miles per hour
			drawGraph(getTime(), mpg, speedm);
		} else {
			drawGraph(getTime(), 0.0, speedm);
		}
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
			dbHelper.saveResults(distanceTravelled, fuelConsumed, gasMileage,
                    mStartTime, endTime);
            stopMeasurementUpdater();
		} catch (UnrecognizedMeasurementTypeException e) {
			e.printStackTrace();
		} catch (NoValueException e) {
			e.printStackTrace();
		}
        mIsRecording = false;
	}

}
