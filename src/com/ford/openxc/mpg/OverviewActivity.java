package com.ford.openxc.mpg;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.KeyEvent;

import com.actionbarsherlock.app.SherlockFragmentActivity;

public class OverviewActivity extends SherlockFragmentActivity {
    private static final String TAG = "OverviewActivity";
    private ActionBar mActionBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Created OverviewActivity");
        setContentView(R.layout.overview);

        mActionBar = getSupportActionBar();
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
    	//We need to intercept every event that goes with the KEYCODE we're looking for.
    	//Stopping some events and letting others through creates funky behavior.
        int currentSelection = mActionBar.getSelectedNavigationIndex();
        switch(event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            	if (event.getAction() == KeyEvent.ACTION_UP) {
            		Log.i(TAG, "Dpad right, key up.");
            		if((currentSelection >= 0) && (currentSelection <= 2)) {
            			mActionBar.setSelectedNavigationItem(currentSelection + 1);
            		}
            	}
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            	if (event.getAction() == KeyEvent.ACTION_UP) {
            		Log.i(TAG, "Dpad left, key up.");
            		if((currentSelection >= 1) && (currentSelection <= 3)) {
            			mActionBar.setSelectedNavigationItem(currentSelection - 1);
            		} else if (currentSelection == 0) {
            			finish();
            		}
            	}
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            	if (event.getAction() == KeyEvent.ACTION_UP) {
            		startActivity(new Intent(this, MpgActivity.class));
            	}
                return true;
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
