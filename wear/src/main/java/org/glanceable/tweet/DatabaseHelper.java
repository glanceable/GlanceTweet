package org.glanceable.tweet;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import thewearapps.sharedlibrary.TweetItem;

import static thewearapps.sharedlibrary.Constants.TABLE_NAME;
import static thewearapps.sharedlibrary.Constants.TAG;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "TwitterDB";
    private static final int DATABASE_VERSION = 6;

    DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + "(" +
                "id INTEGER PRIMARY KEY," +
                "screen_name VARCHAR," +
                "content TEXT," +
                "created DATE," +
                "read INTEGER, " +
                "photo BLOB, " +
                "retweetFrom VARCHAR, " +
                "url VARCHAR);");
        db.execSQL("CREATE INDEX creation_idx ON " + TABLE_NAME + "(created);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(TAG, "Upgrading database");
        TweetItem[] tweets = loadTweets(db, 100, "read=0");
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
        storeTweets(db, tweets);
        Log.i(TAG, "done upgrade database");
    }

    public TweetItem[] loadTweets(SQLiteDatabase db, int limit, String condition) {
        final List<TweetItem> tweets = new ArrayList<>();
        try {
            Cursor cursor = db.query(TABLE_NAME, null,
                    condition, null, null, null, "created DESC", Integer.toString(limit));
            while (cursor.moveToNext()) {
                tweets.add(new TweetItem(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        new Date(cursor.getLong(3)),
                        cursor.getInt(4) > 0,
                        cursor.getBlob(5),
                        cursor.getString(6),
                        // TODO: BC
                        (cursor.getColumnCount() > 7 && cursor.getString(7) != null)
                                ? new String[]{cursor.getString(7)}
                                : new String[0]));
            }
            return tweets.toArray(new TweetItem[0]);
        } catch (SQLiteException e) {
            Log.e(TAG, "failed to query table");
        }
        return null;
    }

    public void storeTweets(SQLiteDatabase db, TweetItem[] tweets) {
        for (TweetItem item : tweets) {
            ContentValues contentValues = item.toContentValues();
            db.insertWithOnConflict(TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }
}