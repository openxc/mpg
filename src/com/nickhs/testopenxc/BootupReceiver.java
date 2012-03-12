package com.nickhs.testopenxc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.openxc.VehicleService;

public class BootupReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.i("BootupRec", "Starting watcher on boot");
		Intent intent2 = new Intent(context, VehicleService.class);
		context.startService(intent2);
	}

}
