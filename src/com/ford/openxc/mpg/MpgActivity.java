package com.ford.openxc.mpg;

import java.net.URI;
import java.util.Locale;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.text.format.Time;
import android.util.Log;
import android.view.KeyEvent;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.openxc.NoValueException;
import com.openxc.VehicleManager;
import com.openxc.VehicleManager.VehicleBinder;
import com.openxc.measurements.FineOdometer;
import com.openxc.measurements.FuelConsumed;
import com.openxc.measurements.IgnitionStatus;
import com.openxc.measurements.IgnitionStatus.IgnitionPosition;
import com.openxc.measurements.Measurement;
import com.openxc.measurements.UnrecognizedMeasurementTypeException;
import com.openxc.measurements.VehicleSpeed;
import com.openxc.remote.VehicleServiceException;
import com.openxc.sources.DataSourceException;
import com.openxc.sources.trace.TraceVehicleDataSource;

/* TODO: Send the range into a sharedpreferences.
 * Check on how many points before we die
 * Broadcast filter for ignition on
 * Fix getLastData
 */

public class MpgActivity extends SherlockFragmentActivity {
    private final static String TAG = "MpgActivity";
    private final static int CAN_TIMEOUT = 30;
    private final static int SPEECH_DATA_REQUEST = 0;

    private boolean mIsRecording = false;

    private long mStartTime = -1;
    private double mLastGasCount = 0;
    private double mLastOdoCount = 0;
    private double mLastMPG = 0;
    private double mLastSpeed = 0;
    private double mLastCheckpointTime = 0;

    private SharedPreferences sharedPrefs;
    private IgnitionPosition mLastIgnitionPosition;
    private VehicleManager mVehicle;
    private DbHelper mDatabase;
    private MeasurementUpdater mMeasurementUpdater;
    private ViewPager mViewPager;
    private TabsAdapter mTabsAdapter;
    private TextToSpeech mTts;
    private boolean TTSReady = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mViewPager = new ViewPager(this);
        mViewPager.setId(R.id.pager);
        setContentView(mViewPager);

        final ActionBar bar = getSupportActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        bar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);

        mTabsAdapter = new TabsAdapter(this, mViewPager);
        mTabsAdapter.addTab(bar.newTab().setText("Speed")
                .setTabListener(mTabListener),
                SpeedChartFragment.class, null);
        mTabsAdapter.addTab(bar.newTab().setText("MPG")
                .setTabListener(mTabListener),
                MpgChartFragment.class, null);

        mViewPager.setOnPageChangeListener(
            new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    getSupportActionBar().setSelectedNavigationItem(position);
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

        mDatabase = new DbHelper(this);
        createTextToSpeech(this);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // We need to intercept every event that goes with the KEYCODE we're
        // looking for.  Stopping some events and letting others through creates
        // funky behavior.
        switch(event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    Log.i(TAG, "Dpad right, key up.");
                    mViewPager.setCurrentItem(1);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    Log.i(TAG, "Dpad left, key up.");
                    mViewPager.setCurrentItem(0);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    startActivity(new Intent(this, OverviewActivity.class));
                }
                return true;
            case KeyEvent.KEYCODE_5:
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    if(TTSReady) {
                        if(mViewPager.getCurrentItem() == 0){
                            long roundedSpeed = Math.round(mLastSpeed);
                            String strMPG = roundedSpeed + "miles per hour";
                            mTts.speak(strMPG, TextToSpeech.QUEUE_FLUSH, null);
                        } else {

                            long roundedMPG = Math.round(mLastMPG);
                            String strMPG = roundedMPG + "miles per gallon";
                            mTts.speak(strMPG, TextToSpeech.QUEUE_FLUSH, null);
                        }
                    } else {
                        Log.e(TAG, "Text to speech called before initialized.");
                    }
                }
                return true;
            }
        return super.dispatchKeyEvent(event);
    }

    private void createTextToSpeech(final Context context) {
        mTts = new TextToSpeech(context, new OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status == TextToSpeech.SUCCESS) {
                    switch (mTts.isLanguageAvailable(Locale.US)) {
                        case TextToSpeech.LANG_AVAILABLE:
                        case TextToSpeech.LANG_COUNTRY_AVAILABLE:
                        case TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE:
                            mTts.setLanguage(Locale.US);
                            //pass the tts back to the main
                            //activity for use
                            break;
                        case TextToSpeech.LANG_MISSING_DATA:
                            Intent installIntent = new Intent();
                            installIntent.setAction(
                                TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                            context.startActivity(installIntent);
                            break;
                    }
                }
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("time", mStartTime);
        outState.putBoolean("isRecording", mIsRecording);
        outState.putInt("tab",
                getSupportActionBar().getSelectedNavigationIndex());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy called");
        mDatabase.close();
        stopMeasurementUpdater();
        try {
            mVehicle.removeListener(IgnitionStatus.class, ignitionListener);
        } catch(VehicleServiceException e) {
            Log.w(TAG, "Unable to remove ignition listener", e);
        }

        if(mTts != null) {
            mTts.shutdown();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
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
            startActivity(new Intent(this, OverviewActivity.class));
            break;
        case R.id.createData:
            mDatabase.createTestData(100);
            break;
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

            if (sharedPrefs.getBoolean("use_trace_file", false)) {
                Log.i(TAG, "Using trace file");
                try {
                    mVehicle.addSource(new TraceVehicleDataSource(
                                MpgActivity.this,
                                new URI("file:///sdcard/com.ford.openxc.mpg/trace.json")));
                } catch (java.net.URISyntaxException e) {
                    Log.e(TAG, "Unable to load trace file", e);
                } catch(DataSourceException e) {
                    Log.e(TAG, "Unable to load trace file", e);
                }
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

    // TODO this is really ugly, we have to copy this private function from the
    // pager adapter to figure out how it registers our fragments
    private String getFragmentTag(int pos){
            return "android:switcher:" + R.id.pager + ":" + pos;
    }

    private void drawGraph(double time, double mpg, double speed) {
        ChartFragment fragment = (ChartFragment) getSupportFragmentManager().
            findFragmentByTag(getFragmentTag(0));
        if(fragment != null) {
            fragment.addData(time, speed);
        } else {
            Log.d(TAG, "Unable to load speed chart fragment");
        }

        fragment = (ChartFragment) getSupportFragmentManager().
            findFragmentByTag(getFragmentTag(1));
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
                if (checkForCANFresh()) {
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
        mLastSpeed = speedm;
        fineOdo *= 0.62137; //Converting from km to miles.
        gas *= 0.26417;  //Converting from L to Gal

        double currentGas = gas - mLastGasCount;
        mLastGasCount = gas;
        double currentDistance = fineOdo - mLastOdoCount;
        mLastOdoCount = fineOdo;

        if(gas > 0.0) {
            mLastMPG = currentDistance / currentGas;  //miles per gallon
            if(mLastMPG > 100){
                // max MPG is 100 MPG to keep the graph sane
                mLastMPG = 100;
            }
            drawGraph(getTime(), mLastMPG, speedm);
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

    private ActionBar.TabListener mTabListener = new ActionBar.TabListener() {
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
    };

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
            if(mLastCheckpointTime == 0) {
                mLastCheckpointTime = endTime;
            }
            mDatabase.saveResults(distanceTravelled, fuelConsumed, gasMileage,
                    mLastCheckpointTime, endTime);
            mLastCheckpointTime = endTime;
        } catch (UnrecognizedMeasurementTypeException e) {
            e.printStackTrace();
        } catch (NoValueException e) {
            e.printStackTrace();
        }
    }
}
