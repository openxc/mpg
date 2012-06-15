package com.ford.openxc.mpg;

import android.app.ActionBar;

import android.app.ActionBar.Tab;
import android.app.FragmentTransaction;
import android.os.Bundle;

import android.support.v4.app.FragmentActivity;

import android.util.Log;

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
