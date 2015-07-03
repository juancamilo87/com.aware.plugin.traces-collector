package com.aware.plugin.tracescollector.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.aware.plugin.tracescollector.model.MyDBPlace;

/**
 * Created by JuanCamilo on 5/7/2015.
 */
public class MySQLiteHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "local_locations.db";
    private static final int DATABASE_VERSION = 5;

    public static final long DAYS_LIMIT_IN_MILLIS = 2592000000L;

    public static final String TABLE_SEARCH_LOCATION = "search_locations";
    public static final String COLUMN_SEARCH_LOCATION_ID = "_id";
    public static final String COLUMN_SEARCH_LOCATION_TIMESTAMP = "timestamp";
    public static final String COLUMN_SEARCH_LOCATION_LATITUDE = "latitude";
    public static final String COLUMN_SEARCH_LOCATION_LONGITUDE = "longitude";


    private static final String CREATE_TABLE_SEARCH_LOCATION = "create table "
            + TABLE_SEARCH_LOCATION + "(" +
            COLUMN_SEARCH_LOCATION_ID + " integer primary key autoincrement, " +
            COLUMN_SEARCH_LOCATION_TIMESTAMP + " real not null, " +
            COLUMN_SEARCH_LOCATION_LATITUDE + " real not null, " +
            COLUMN_SEARCH_LOCATION_LONGITUDE + " real not null);";

    public static final String TABLE_PLACES = "places";
    public static final String COLUMN_PLACES_ID = "_id";
    public static final String COLUMN_PLACES_PLACE_ID = "place_id";
    public static final String COLUMN_PLACES_TIMESTAMP = "timestamp";
    public static final String COLUMN_PLACES_LATITUDE = "latitude";
    public static final String COLUMN_PLACES_LONGITUDE = "longitude";
    public static final String COLUMN_PLACES_NAME = "name";

    private static final String CREATE_TABLE_PLACES = "create table "
            + TABLE_PLACES + "(" +
            COLUMN_PLACES_ID + " integer primary key autoincrement, " +
            COLUMN_PLACES_PLACE_ID + " text not null, " +
            COLUMN_PLACES_TIMESTAMP + " real not null, " +
            COLUMN_PLACES_LATITUDE + " real not null, " +
            COLUMN_PLACES_LONGITUDE + " real not null, " +
            COLUMN_PLACES_NAME + " text, " +
            "UNIQUE (" + COLUMN_PLACES_PLACE_ID + ") ON CONFLICT REPLACE);";

    private static MySQLiteHelper instance;


    public MySQLiteHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static synchronized MySQLiteHelper getHelper(Context context)
    {
        if (instance == null)
            instance = new MySQLiteHelper(context);

        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(CREATE_TABLE_SEARCH_LOCATION);
        sqLiteDatabase.execSQL(CREATE_TABLE_PLACES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i2) {
        // on upgrade drop older tables
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_SEARCH_LOCATION);
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_PLACES);

        // create new tables
        onCreate(sqLiteDatabase);
    }
}
