package org.glanceable.tweet;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import thewearapps.sharedlibrary.TweetItem;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import static thewearapps.sharedlibrary.Constants.TAG;
import static thewearapps.sharedlibrary.Constants.PREFS_NAME;

public class TwitterFetchService extends Service
        implements FeedManager.LoadingObserver, GoogleApiClient.ConnectionCallbacks {
    private static final long FETCH_INTERVAL = 1000 * 60 * 5;
    private SharedPreferences settings;
    private Twitter twitter = TwitterFactory.getSingleton();
    Handler mHandler = new Handler();
    private GoogleApiClient mGoogleApiClient;
    private FeedManager feedManager;

    private Runnable twitterFetchTask = new Runnable() {
        @Override
        public void run() {
            feedManager.reload();
            mHandler.postDelayed(twitterFetchTask, FETCH_INTERVAL);
            settings.edit().putLong("fetch_ts", System.currentTimeMillis()).commit();
        }
    };


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "TwitterFetchService start");
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        Utils.setConsumerKey(twitter);
        twitter.setOAuthAccessToken(Utils.loadAccessToken(settings));
        feedManager = FeedManager.getInstance();
        feedManager.setLoadingObserver(this);
        twitterFetchTask.run();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this).build();
        mGoogleApiClient.connect();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        feedManager.reload();
        return START_STICKY;
    }

    @Override
    public void dataLoaded(final ArrayList<TweetItem> tweetItems) {
        TweetSender sender = new TweetSender(mGoogleApiClient);
        List<TweetItem> copy = new ArrayList<>(tweetItems);

        sender.send(copy);
    }

    @Override
    public void onDestroy() {
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "Service onConnected");
    }

    @Override
    public void onConnectionSuspended(int i) {

    }
}
