package com.ford.openxc.mpg;

import android.app.ActionBar;

import android.app.ActionBar.Tab;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;

import android.speech.tts.TextToSpeech;
import android.support.v4.app.FragmentActivity;

import android.util.Log;
import android.view.KeyEvent;

public class OverviewActivity extends FragmentActivity {
    private static final String TAG = "OverviewActivity";
    private ActionBar mActionBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Created OverviewActivity");
        setContentView(R.layout.overview);

        mActionBar = getActionBar();
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        mActionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);

        mActionBar.addTab(mActionBar.newTab().setText("Per Trip")
                .setTag(Timeframe.PER_TRIP).setTabListener(mTabListener));
        mActionBar.addTab(mActionBar.newTab().setText("Daily")
                .setTag(Timeframe.DAILY).setTabListener(mTabListener));
        mActionBar.addTab(mActionBar.newTab().setText("Weekly")
                .setTag(Timeframe.WEEKLY).setTabListener(mTabListener));
        mActionBar.addTab(mActionBar.newTab().setText("Monthly")
                .setTag(Timeframe.MONTHLY).setTabListener(mTabListener));

        if(savedInstanceState != null) {
            mActionBar.setSelectedNavigationItem(savedInstanceState.getInt("tab", 0));
        }
    }
    
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
    	if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
            	Log.i(TAG, "Dpad right, key up.");
            	int CurrentSelection = mActionBar.getSelectedNavigationIndex();
            	if((CurrentSelection >= 0) && (CurrentSelection <= 2)) {
	            	mActionBar.setSelectedNavigationItem(CurrentSelection + 1);
            	}
                return true;
            }
    	} else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
            	Log.i(TAG, "Dpad left, key up.");
            	int CurrentSelection = mActionBar.getSelectedNavigationIndex();
            	if((CurrentSelection >= 1) && (CurrentSelection <= 3)) {
	            	mActionBar.setSelectedNavigationItem(CurrentSelection - 1);
            	} else if (CurrentSelection == 0) {
            		finish();
            	}
                return true;
            }
    	} 
    	return super.dispatchKeyEvent(event);
    }

    private ActionBar.TabListener mTabListener = new ActionBar.TabListener() {
        public void onTabSelected(ActionBar.Tab tab,
                FragmentTransaction ft) {
            HistoricalChartFragment fragment = (HistoricalChartFragment)
                getSupportFragmentManager().findFragmentById(
                        R.id.historical_mpg_chart_fragment);
            fragment.setTimeframe((Timeframe) tab.getTag());

            fragment = (HistoricalChartFragment)
                getSupportFragmentManager().findFragmentById(
                        R.id.historical_fuel_chart_fragment);
            fragment.setTimeframe((Timeframe) tab.getTag());
        }

        @Override
        public void onTabUnselected(Tab tab,
                FragmentTransaction ft) { }

        @Override
        public void onTabReselected(Tab tab,
                FragmentTransaction ft) { }
    };
}
