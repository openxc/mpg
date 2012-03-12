package com.nickhs.testopenxc;

import java.text.SimpleDateFormat;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

public class DbHelper extends SQLiteOpenHelper {
	
	static final String TAG = "DbHelper";
	static final String DB_NAME = "mileage.db";
	static final int DB_VERSION = 4;
	static final String TABLE = "mileage";
	static final String C_ID = BaseColumns._ID;
	static final String C_TIME = "time";
	static final String C_LENGTH = "length";
	static final String C_DISTANCE = "distance";
	static final String C_FUEL = "fuelConsumed";
	static final String C_MILEAGE = "miles";
//	Context gContext;
	
	public DbHelper(Context context) {
		super(context, DB_NAME, null, DB_VERSION);
//		gContext = context;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.i(TAG, "onCreated");
		String sql = "create table "+TABLE+" ("+C_ID+" int primary key, "+C_TIME+" text, "+C_LENGTH+" int, "
		+C_DISTANCE+" int, "+C_FUEL+" int, "+C_MILEAGE+" int)";
		db.execSQL(sql);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.i(TAG, "onUpgrade called");
		db.execSQL("drop table if exists "+TABLE);
		onCreate(db);
	}
	
	public void saveResults(double dist, double fuel, double mileage, double start, double end) {		
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String startT = formatter.format(start);
		// String endT = formatter.format(end); FIXME needed?
		double length = ((end-start)/(1000*60));
		Log.i(TAG, "Length is: "+length+". Mileage is: "+mileage);
		
		ContentValues values = new ContentValues();
		values.put(C_DISTANCE, dist);
		values.put(C_TIME, startT);
		values.put(C_LENGTH, length);
		values.put(C_FUEL, fuel);
		values.put(C_MILEAGE, mileage);
		SQLiteDatabase db = getWritableDatabase();
		db.insertOrThrow(TABLE, null, values);
		Log.i(TAG, "Insertion complete");
	}

}
