package com.ford.openxc.mpg;

import java.sql.Timestamp;

import org.joda.time.DateTime;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

public class DbHelper extends SQLiteOpenHelper {
	
	static final String TAG = "DbHelper";
	static final String DB_NAME = "mileage.db";
	static final int DB_VERSION = 14;
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
		String sql = "create table "+TABLE+" ("+C_ID+" integer primary key autoincrement, "+C_TIME+" timestamp default(current_timestamp), "+C_LENGTH+" int, "
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
		double length = ((end-start)/(1000*60));
		
		ContentValues values = new ContentValues();
		values.put(C_DISTANCE, dist);
		Timestamp time = new Timestamp((long) start);
		String stime = time.toString();
		values.put(C_TIME, stime);
		values.put(C_LENGTH, length);
		values.put(C_FUEL, fuel);
		values.put(C_MILEAGE, mileage);
		SQLiteDatabase db = getWritableDatabase();
		db.insertOrThrow(TABLE, null, values);
		Log.i(TAG, "Insertion complete");
	}
	
	public void createTestData(int num) {
		for (int i=1; i < num; i++) {
			DateTime endDt = new DateTime();
			endDt = endDt.minusHours(6);
			DateTime startDt = endDt.minusWeeks(10);
			long start = (long) (startDt.getMillis() + Math.random() * (endDt.getMillis()-startDt.getMillis()));
			long end = (long) ((60 + Math.random() * (7200-60))*1000+start);
			double dist = 0.1 + Math.random() * (200 - 0.1);
			double mileage = 5+ Math.random() * (15-5);
			double fuel = (1/mileage) * dist;
			Log.i(TAG, "Entering ["+i+"], start = "+start+" end = "+end+" length = "+(end-start)+" dist = "+dist+" mileage = "+mileage);
			saveResults(dist, fuel, mileage, (start), (end));
		}
	}
	
	public Cursor getLastData(String startDate, String endDate, String col) {
		SQLiteDatabase db = getReadableDatabase();
		String[] colToFetch = {col};
		Cursor c = db.query(TABLE, colToFetch, 
				C_TIME+" BETWEEN '"+startDate+"' AND '"+endDate+"'",
				null, null, null, null);
		// Cursor c = db.query(TABLE, colToFetch, C_TIME+" > '"+startDate+"'", null, null, null, null);
		return c;
	}
	
	public Cursor getLastData(int numOfData, String col) {
		String num = Integer.toString(numOfData);
		SQLiteDatabase db = getReadableDatabase();
		String[] colToFetch = {col};
		Cursor c = db.query(TABLE, colToFetch, null, null, null, null, DbHelper.C_TIME+" DESC", num);
		return c;
	}
}
