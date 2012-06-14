package com.ford.openxc.mpg;

import java.util.ArrayList;

import android.app.ActionBar;

import android.app.ActionBar.Tab;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;

import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import android.support.v4.view.ViewPager;

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

/* TODO: Send the range into a sharedpreferences.
 * Check on how many points before we die
 * Broadcast filter for ignition on
 * Fix getLastData
 */

public class MpgActivity extends FragmentActivity {
	private final static String TAG = "MpgActivity";
	private final static int CAN_TIMEOUT = 30;

	private boolean mIsRecording = false;

	private long mStartTime = -1;
	private double lastGasCount = 0;
	private double lastOdoCount = 0;

	private SharedPreferences sharedPrefs;
    private IgnitionPosition mLastIgnitionPosition;
	private VehicleManager mVehicle;
	private DbHelper dbHelper;
    private MeasurementUpdater mMeasurementUpdater;
    private ViewPager mViewPager;
    private TabsAdapter mTabsAdapter;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        mViewPager = new ViewPager(this);
        mViewPager.setId(R.id.pager);
        setContentView(mViewPager);

        final ActionBar bar = getActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        bar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);

        mTabsAdapter = new TabsAdapter(this, mViewPager);
        mTabsAdapter.addTab(bar.newTab().setText("Speed")
                .setTabListener(new ActionBar.TabListener() {
                    public void onTabSelected(ActionBar.Tab tab,
                        FragmentTransaction ft) {
                        mViewPager.setCurrentItem(tab.getPosition());
                    }

                    @Override
                    public void onTabUnselected(Tab tab,
                        FragmentTransaction ft) { }

                    @Override
                    public void onTabReselected(Tab tab,
                        FragmentTransaction ft) { }
                }),
                SpeedChartFragment.class, null);
        mTabsAdapter.addTab(bar.newTab().setText("MPG")
                .setTabListener(new ActionBar.TabListener() {
                    public void onTabSelected(ActionBar.Tab tab,
                        FragmentTransaction ft) {
                        mViewPager.setCurrentItem(tab.getPosition());
                    }

                    @Override
                    public void onTabUnselected(Tab tab,
                        FragmentTransaction ft) { }

                    @Override
                    public void onTabReselected(Tab tab,
                        FragmentTransaction ft) { }
                }),
                MpgChartFragment.class, null);

        mViewPager.setOnPageChangeListener(
            new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    getActionBar().setSelectedNavigationItem(position);
                }
            });

        if(savedInstanceState != null) {
            mStartTime = savedInstanceState.getLong("time");
            mIsRecording = savedInstanceState.getBoolean("isRecording");
            bar.setSelectedNavigationItem(savedInstanceState.getInt("tab", 0));
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
        outState.putInt("tab", getActionBar().getSelectedNavigationIndex());
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy called");
        stopMeasurementUpdater();
        try {
            mVehicle.removeListener(IgnitionStatus.class, ignitionListener);
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
		case R.id.checkpoint:
			recordCheckpoint();
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
			mVehicle = null;
			Log.i(TAG, "Service unbound");
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			VehicleBinder binder = (VehicleBinder) service;
			mVehicle = binder.getService();
			Log.i(TAG, "Remote Vehicle Service bound");
			try {
				mVehicle.addListener(IgnitionStatus.class, ignitionListener);
			} catch (VehicleServiceException e) {
				e.printStackTrace();
			} catch (UnrecognizedMeasurementTypeException e) {
				e.printStackTrace();
			}
            startMeasurementUpdater();
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
                    // TODO mark that this is the beginning of a checkpoint
                } else if(ignitionPosition == IgnitionPosition.OFF
                        && mLastIgnitionPosition == IgnitionPosition.RUN) {
                    Log.i(TAG, "Ignition switched off -- checkpointing");
                    recordCheckpoint();
                }
                mLastIgnitionPosition = ignitionPosition;
            }
		}
	};

    public static class TabsAdapter extends FragmentPagerAdapter
            implements ActionBar.TabListener, ViewPager.OnPageChangeListener {
            private final Context mContext;
            private final ActionBar mActionBar;
            private final ViewPager mViewPager;
            private final ArrayList<TabInfo> mTabs = new ArrayList<TabInfo>();

            static final class TabInfo {
                private final Class<?> clss;
                private final Bundle args;

                TabInfo(Class<?> _class, Bundle _args) {
                    clss = _class;
                    args = _args;
                }
            }

            public TabsAdapter(FragmentActivity activity, ViewPager pager) {
                super(activity.getSupportFragmentManager());
                mContext = activity;
                mActionBar = activity.getActionBar();
                mViewPager = pager;
                mViewPager.setAdapter(this);
                mViewPager.setOnPageChangeListener(this);
            }

            public void addTab(ActionBar.Tab tab, Class<?> clss, Bundle args) {
                TabInfo info = new TabInfo(clss, args);
                tab.setTag(info);
                tab.setTabListener(this);
                mTabs.add(info);
                mActionBar.addTab(tab);
                notifyDataSetChanged();
            }

            @Override
            public int getCount() {
                return mTabs.size();
            }

            @Override
            public Fragment getItem(int position) {
                TabInfo info = mTabs.get(position);

                Fragment fragment = Fragment.instantiate(mContext,
                        info.clss.getName(), info.args);
                return fragment;
            }

            @Override
            public void onPageScrolled(int position, float positionOffset,
                    int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                mActionBar.setSelectedNavigationItem(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }

            @Override
            public void onTabSelected(Tab tab, FragmentTransaction ft) {
                Object tag = tab.getTag();
                for (int i=0; i<mTabs.size(); i++) {
                    if (mTabs.get(i) == tag) {
                        mViewPager.setCurrentItem(i);
                    }
                }
            }

            @Override
            public void onTabUnselected(Tab tab, FragmentTransaction ft) {
            }

            @Override
            public void onTabReselected(Tab tab, FragmentTransaction ft) {
            }
    }

    // TODO this is really ugly, we have to copy this private function from the
    // pager adapter to figure out how it registers our fragments
    private String getFragmentTag(int pos){
            return "android:switcher:" + R.id.pager + ":" + pos;
    }

	private void drawGraph(double time, double mpg, double speed) {
        ChartFragment fragment = (ChartFragment) getSupportFragmentManager().
            findFragmentByTag(getFragmentTag(1));
        if(fragment != null) {
            fragment.addData(time, speed);
        } else {
            Log.d(TAG, "Unable to load speed chart fragment");
        }

        fragment = (ChartFragment) getSupportFragmentManager().
            findFragmentByTag(getFragmentTag(0));
        if(fragment != null) {
            fragment.addData(time, mpg);
        } else {
            Log.d(TAG, "Unable to load mpg chart fragment");
        }
	}

    private class MeasurementUpdater extends Thread {
        private boolean mRunning = true;

        public void done() {
            mRunning = false;
        }

        public void run() {
            while(mRunning) {
                if (checkForCANFresh())	{
                    getMeasurements();
                }

                String choice = sharedPrefs.getString("update_interval",
                        "1000");
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
			speed = (VehicleSpeed) mVehicle.get(VehicleSpeed.class);
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
			fineOdo = (FineOdometer) mVehicle.get(FineOdometer.class);
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
			fuel = (FuelConsumed) mVehicle.get(FuelConsumed.class);
			temp = fuel.getValue().doubleValue();
		} catch (UnrecognizedMeasurementTypeException e) {
			e.printStackTrace();
		} catch (NoValueException e) {
			Log.w(TAG, "Failed to get fuel measurement");
		}

		return temp;
	}

	private boolean checkForCANFresh() {
		try {
			VehicleSpeed measurement = (VehicleSpeed) mVehicle.get(
                    VehicleSpeed.class);
			if (measurement.getAge() < CAN_TIMEOUT) {
                return true;
            }
		} catch (UnrecognizedMeasurementTypeException e) {
			e.printStackTrace();
		} catch (NoValueException e) {
			Log.e(TAG, "Unable to check for vehicle measurements", e);
		}
		return false;
	}

	private void getMeasurements() {
		double speedm = getSpeed();
		double fineOdo = getFineOdometer();
		double gas = getGasConsumed();

		speedm *= 0.62137;  //Converting from kph to mph
		fineOdo *= 0.62137; //Converting from km to miles.
		gas *= 0.26417;  //Converting from L to Gal

		double currentGas = gas - lastGasCount;
		lastGasCount = gas;
		double currentDistance = fineOdo - lastOdoCount;
		lastOdoCount = fineOdo;

		if(gas > 0.0) {
			double mpg = currentDistance / currentGas;  //miles per hour
			drawGraph(getTime(), mpg, speedm);
		} else {
			drawGraph(getTime(), 0.0, speedm);
		}
	}

    private void startMeasurementUpdater() {
        stopMeasurementUpdater();
        mMeasurementUpdater = new MeasurementUpdater();
        mMeasurementUpdater.start();
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

	private void recordCheckpoint() {
		try {
			FineOdometer oMeas = (FineOdometer) mVehicle.get(
                    FineOdometer.class);
			final double distanceTravelled = oMeas.getValue().doubleValue();
			FuelConsumed fMeas = (FuelConsumed) mVehicle.get(
                    FuelConsumed.class);
			final double fuelConsumed = fMeas.getValue().doubleValue();
			final double gasMileage = distanceTravelled/fuelConsumed;
			double endTime = getTime();
			dbHelper.saveResults(distanceTravelled, fuelConsumed, gasMileage,
                    mStartTime, endTime);
		} catch (UnrecognizedMeasurementTypeException e) {
			e.printStackTrace();
		} catch (NoValueException e) {
			e.printStackTrace();
		}
	}
}
