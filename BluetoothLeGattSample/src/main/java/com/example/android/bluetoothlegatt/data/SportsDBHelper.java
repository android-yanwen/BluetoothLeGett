package com.example.android.bluetoothlegatt.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by yanwen on 18/10/24.
 */
public class SportsDBHelper extends SQLiteOpenHelper {
    private final String TAG = "SportsDBHelper";
    public static final String DB_NAME = "sports_db";
    public static final int VERSION = 1;
    private static SportsDBHelper dbHelper;
    public SportsDBHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    public static SportsDBHelper getInstance(Context context, String name) {
        if (dbHelper == null) {
            dbHelper = new SportsDBHelper(context, name, null, VERSION);
        }
        return dbHelper;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "create table sports_table(_id integer primary key autoincrement," +
                "day integer,steps integer,mileage integer,calorie integer,heartbeat integer)";
        Log.i(TAG, "------------------>> create database table");
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        Log.i(TAG, "----------------->> update db");
    }


    public void insert(SportsData data) {
        if (dbHelper==null)return;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("day", data.day);
        cv.put("steps", data.step);
        cv.put("mileage", data.mileage);
        cv.put("calorie", data.calorie);
        cv.put("heartbeat", data.heartbeat);
        long datanum = db.insert("sports_table", null, cv);
        db.close();
    }

    /**
     * 返回数据库中所有的数据
     * */
    public List<SportsData> query() {
        if (dbHelper == null) return null;
        List<SportsData> list = new ArrayList<SportsData>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor;
        cursor = db.query("sports_table", null, null, null, null, null, null);

//        cursor.moveToFirst();
//        Log.d(TAG, "query: " + cursor.getPosition());
        while (cursor.moveToNext()) {
            SportsData data = new SportsData();
            data.day = cursor.getInt(cursor.getColumnIndex("day"));
            data.step = cursor.getInt(cursor.getColumnIndex("steps"));
            data.mileage = cursor.getInt(cursor.getColumnIndex("mileage"));
            data.calorie = cursor.getInt(cursor.getColumnIndex("calorie"));
            data.heartbeat = cursor.getInt(cursor.getColumnIndex("heartbeat"));
            list.add(data);
        }
        db.close();
        return list;
    }

    /**
     * 返回I_DAY日期当天的数据
     * */
    public SportsData query(int i_day) {
        SportsData data = null;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query("sports_table", new String[]{"_id", "day", "steps", "mileage", "calorie", "heartbeat"}, "day=?", new String[]{String.valueOf(i_day)}, null, null, null);
        while (cursor.moveToNext()) {
            data = new SportsData();
            data.day = cursor.getInt(cursor.getColumnIndex("day"));
            data.step = cursor.getInt(cursor.getColumnIndex("steps"));
            data.mileage = cursor.getInt(cursor.getColumnIndex("mileage"));
            data.calorie = cursor.getInt(cursor.getColumnIndex("calorie"));
            data.heartbeat = cursor.getInt(cursor.getColumnIndex("heartbeat"));
        }
        return data;
    }


    public void delete(int i_day) {
        if (dbHelper==null) return;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("sports_table", "day=?", new String[]{String.valueOf(i_day)});
        db.close();
    }

    public void update(SportsData data, int i_day) {
        if (dbHelper == null) return;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("day", data.day);
        cv.put("steps", data.step);
        cv.put("mileage", data.mileage);
        cv.put("calorie", data.calorie);
        cv.put("heartbeat", data.heartbeat);
        db.update("sports_table", cv, "day=?", new String[]{String.valueOf(i_day)});
        db.close();
    }

    //删除数据库
    public boolean deleteDB(Context context) {
        return context.deleteDatabase(SportsDBHelper.DB_NAME);
    }

    public static class SportsData {
        public int day;
        public int step;
        public int mileage;
        public int calorie;
        public int heartbeat;
    }


}
