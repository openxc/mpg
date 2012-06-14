package com.ford.openxc.mpg;

import android.app.ActionBar;

import android.app.ActionBar.Tab;
import android.app.FragmentTransaction;
import android.os.Bundle;

import android.support.v4.app.FragmentActivity;

import android.util.Log;

public class OverviewActivity extends FragmentActivity {
    private static final String TAG = "OverviewActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Created OverviewActivity");
        setContentView(R.layout.overview);

        final ActionBar bar = getActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        bar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);

        bar.addTab(bar.newTab().setText("Per Trip").setTabListener(mTabListener));
        bar.addTab(bar.newTab().setText("Daily").setTabListener(mTabListener));
        bar.addTab(bar.newTab().setText("Weekly").setTabListener(mTabListener));
        bar.addTab(bar.newTab().setText("Monthly").setTabListener(mTabListener));

        if(savedInstanceState != null) {
            bar.setSelectedNavigationItem(savedInstanceState.getInt("tab", 0));
        }
	}

    private ActionBar.TabListener mTabListener = new ActionBar.TabListener() {
        public void onTabSelected(ActionBar.Tab tab,
                FragmentTransaction ft) {
            // TODO
        }

        @Override
        public void onTabUnselected(Tab tab,
                FragmentTransaction ft) { }

        @Override
        public void onTabReselected(Tab tab,
                FragmentTransaction ft) { }
    };
}
