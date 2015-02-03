
package org.glanceable.tweet;

import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import thewearapps.sharedlibrary.Constants;
import thewearapps.sharedlibrary.TweetItem;

import static thewearapps.sharedlibrary.Constants.DEBUG;
import static thewearapps.sharedlibrary.Constants.PREFS_NAME;
import static thewearapps.sharedlibrary.Constants.TABLE_NAME;
import static thewearapps.sharedlibrary.Constants.TAG;
import static thewearapps.sharedlibrary.Constants.VIBRATION_DURATION;

public class TweetReceiverService extends WearableListenerService
        implements GoogleApiClient.ConnectionCallbacks {

    private static final long TIMEOUT_MS = 5000;
    private static final long NOTFICIATION_THRESHOLD = 60000 * 15;
    private static final String LAST_NOTIFIED = "last_notified";
    private GoogleApiClient mGoogleApiClient;
    private SQLiteDatabase db;
    private DatabaseHelper mOpenHelper;
    private SharedPreferences settings;

    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this.getApplicationContext())
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .build();
        mGoogleApiClient.connect();
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
        mOpenHelper = new DatabaseHelper(this);
        db = mOpenHelper.getReadableDatabase();
        Log.i(TAG, "service started");
        settings = getSharedPreferences(PREFS_NAME, 0);
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "onDataChanged data path: numEvents:" + dataEvents.getCount());
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                String path = event.getDataItem().getUri().getPath();

                if (path.equals("/new_tweets")) {
                    storeTweets(event.getDataItem());
                } else if (path.equals(Constants.PATH_MARK_READ)) {
                    markAllRead();
                } else if (path.equals(Constants.GENERIC_CONFIG_PATH)) {
                    DataMapItem mapDataItem = DataMapItem.fromDataItem(event.getDataItem());
                    DataMap data = mapDataItem.getDataMap();
                    if (data.containsKey(Constants.KEY_POCKET_CONNECTED)) {
                        Log.d(TAG, "pocket connected");
                        settings.edit().putBoolean(Constants.KEY_POCKET_CONNECTED,
                                data.getBoolean(Constants.KEY_POCKET_CONNECTED)).apply();
                    }
                    if (data.containsKey(Constants.KEY_NOTIFY_FREQUENCY)) {
                        settings.edit().putInt(Constants.KEY_NOTIFY_FREQUENCY, Constants.DEFAULT_NOTIFY_FREQUENCY).apply();
                    }
                }
            }
        }
        dataEvents.close();
    }

    private void storeTweets(DataItem dataItem) {
        Cursor cursor = db.query(TABLE_NAME, new String[]{"id"},
                "read=0", null, null, null, null);
        int prevUnreadCount = cursor.getCount();
        DataMapItem mapDataItem = DataMapItem.fromDataItem(dataItem);
        DataMap data = mapDataItem.getDataMap();
        Gson gson = new Gson();
        TweetItem[] tweets = gson.fromJson(data.getString("tweets"), TweetItem[].class);
        Log.d(TAG, "Processing " + tweets.length + " tweets");
        for (TweetItem item : tweets) {
            Asset asset = data.getAsset(Long.toString(item.getId()));
            if (asset != null) {
                item.setAsset(loadBytesFromAsset(asset));
            }
        }
        mOpenHelper.storeTweets(db, tweets);

        cursor = db.query(TABLE_NAME, new String[]{"id"},
                "read=0", null, null, null, null);
        int newUnreadCount = cursor.getCount() - prevUnreadCount;
        Log.i(TAG, cursor.getCount() + " unread, last notified:"
                + settings.getLong(LAST_NOTIFIED, 0));
        if (DEBUG || (newUnreadCount > settings.getInt(Constants.KEY_NOTIFY_FREQUENCY, Constants.DEFAULT_NOTIFY_FREQUENCY)
                &&
                System.currentTimeMillis() - settings.getLong(LAST_NOTIFIED, 0) > NOTFICIATION_THRESHOLD)) {
            Log.i(TAG, "Notifying user");
            settings.edit().putLong(LAST_NOTIFIED, System.currentTimeMillis()).commit();
            notifyUser(cursor.getCount());
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(VIBRATION_DURATION);
        }
    }

    private byte[] loadBytesFromAsset(Asset asset) {
        if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
        }
        ConnectionResult result =
                mGoogleApiClient.blockingConnect(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (!result.isSuccess()) {
            return null;
        }
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                mGoogleApiClient, asset).await().getInputStream();
        mGoogleApiClient.disconnect();

        if (assetInputStream == null) {
            Log.w(TAG, "Requested an unknown Asset.");
            return null;
        }
        try {
            byte[] targetArray = ByteStreams.toByteArray(assetInputStream);
            return targetArray;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void notifyUser(int unreadCount) {
        Intent viewIntent = new Intent(this, ShowTweetsActivity.class);

        PendingIntent viewPendingIntent =
                PendingIntent.getActivity(this, 0, viewIntent, 0);


//        Intent markReadIntent = new Intent(this, MarkAllReadActivity.class);
//
//        PendingIntent actionPendingIntent =
//                PendingIntent.getActivity(this, 0, markReadIntent, 0);
//        NotificationCompat.Action markReadAction =
//                new NotificationCompat.Action.Builder(R.drawable.confirmation_animation,
//                        getString(R.string.mark_all_read), actionPendingIntent)
//
//                        .build();

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.twitter_on_watch)
                        .setContentTitle(unreadCount + " unread tweets")
                        .setContentText("Swipe left to open")
//                        .addAction(markReadAction)
                        .setContentIntent(viewPendingIntent)
                        ;

        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);

        notificationManager.notify(Constants.NOTIF_SHOW_TWEETS, notificationBuilder.build());
    }

    private void markAllRead() {
        Log.d(TAG, "marking all read");
        ContentValues args = new ContentValues();
        args.put("read", 1);
        db.update(TABLE_NAME, args, "id>0", null);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.twitter_on_watch)
                        .setContentTitle("Marked all tweets as read");


        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);

        notificationManager.notify(Constants.NOTIF_MARK_READ, notificationBuilder.build());
    }



    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "Service Api connected");
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, messageEvent.getPath());
        DataMap config = DataMap.fromByteArray(messageEvent.getData());

        settings.edit().putBoolean(Constants.AUTO_SCROLL, config.getBoolean(Constants.AUTO_SCROLL)).apply();

    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service destroyed");
        super.onDestroy();
        db.close();
    }
}
