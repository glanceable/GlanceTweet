package org.glanceable.tweet;

import android.app.Activity;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import thewearapps.sharedlibrary.Constants;

import static thewearapps.sharedlibrary.Constants.TABLE_NAME;

/**
 * Created by jiayao on 12/28/14.
 */
public class MarkAllReadActivity extends Activity {
    private static final String TAG = "TwitterForWatch";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "MarkAllReadActivity");
        DatabaseHelper mOpenHelper = new DatabaseHelper(this);
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        ContentValues args = new ContentValues();
        args.put("read", 1);
        args.put("photo", new byte[]{});
        db.update(TABLE_NAME, args, "id>0", null);
        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);
        notificationManager.cancel(Constants.NOTIF_SHOW_TWEETS);
        finish();
    }
}
