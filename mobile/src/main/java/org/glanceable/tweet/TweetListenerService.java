package org.glanceable.tweet;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Parcel;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.IOException;

import thewearapps.sharedlibrary.AnalyticsEvent;
import thewearapps.sharedlibrary.Constants;

import static thewearapps.sharedlibrary.Constants.TAG;

public class TweetListenerService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks {

    private Tracker tracker;
    private GoogleApiClient mGoogleApiClient;
    private SharedPreferences settings;
    private boolean listenerAdded;
    private DatabaseHelper mOpenHelper;
    private SQLiteDatabase db;
    private static final String EXTRA_SOURCE_PACKAGE = "source";
    private static final String EXTRA_VERSION = "utilVersion";
    private static final int UTIL_VERSION = 1;
    private static final String[] POCKET_PACKAGE_NAMES = new String[]{
            "com.ideashower.readitlater.pro",
            "com.pocket.cn",
            "com.pocket.ru",
            "com.pocket.corgi"
    };


    @Override
    public void onCreate() {
        super.onCreate();

        GoogleAnalytics analytics = GoogleAnalytics.getInstance(this);
        tracker = analytics.newTracker(Constants.TRACKING_ID);
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        mGoogleApiClient = new GoogleApiClient.Builder(this.getApplicationContext())
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .build();
        mGoogleApiClient.connect();
        mOpenHelper = new DatabaseHelper(this);
        db = mOpenHelper.getReadableDatabase();

    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {

        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                Log.d(Constants.TAG, "onDataChanged " + event.getDataItem().getUri());
                String path = event.getDataItem().getUri().getPath();

                if (path.startsWith(Constants.PATH_POCKET_SAVE)) {
                    DataMapItem mapDataItem = DataMapItem.fromDataItem(event.getDataItem());

                    DataMap data = mapDataItem.getDataMap();
                    String[] urls = data.getStringArray("urls");
                    long tweetId = data.getLong("id");
                    String url = "";
                    if (urls.length > 0) {
                        url = urls[0];
                    }

                    String pocketPackageName = getPocketPackageName(this);
                    if (pocketPackageName == null) {
//                            Log.d(Constants.TAG, "Posting to pocket via API" + url);
//                            Utils.saveTweetToPocket(url, settings.getString(Constants.POCKET_ACCESS_TOKEN, ""));
//                            tracker.send(new HitBuilders.EventBuilder()
//                                    .setCategory("engagement")
//                                    .setAction("Pocket_API")
//                                    .setLabel(url)
//                                    .build());
                    } else {
                        tracker.send(new HitBuilders.EventBuilder()
                                .setCategory("engagement")
                                .setAction("Pocket_Intent")
                                .setLabel(Long.toString(tweetId))
                                .build());
                        Log.d(Constants.TAG, "Posting to pocket " + url);
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setPackage(pocketPackageName);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, url);
                        intent.putExtra("tweetStatusId", tweetId);
                        intent.putExtra(EXTRA_SOURCE_PACKAGE, getPackageName());
                        intent.putExtra(EXTRA_VERSION, UTIL_VERSION);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_FROM_BACKGROUND);
                        startActivity(intent);
                    }

                    //TODO: queue the url if post fails due to network error
                    Wearable.DataApi.deleteDataItems(mGoogleApiClient, event.getDataItem().getUri());
                }
            }
        }
        dataEvents.close();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(Constants.TAG, "onMessageReceived " + messageEvent.getPath() + " " + messageEvent.getRequestId() );
        if (messageEvent.getPath().equals(Constants.WEAR_GA_PATH)) {
            Parcel parcel = Parcel.obtain();
            parcel.unmarshall(messageEvent.getData(), 0, messageEvent.getData().length);
            parcel.setDataPosition(0);

            AnalyticsEvent event = (AnalyticsEvent) AnalyticsEvent.CREATOR.createFromParcel(parcel);
            tracker.send(new HitBuilders.EventBuilder()
                    .setCategory(event.getCategory())
                    .setAction(event.getAction())
                    .setLabel(event.getLabel())
                    .setValue(event.getValue())
                    .build());
            Log.d(Constants.TAG, "sending analytics ");
        } else if (messageEvent.getPath().equals(Constants.PATH_POCKET_SAVE)) {
//            String url = new String(messageEvent.getData());
//            Log.d(Constants.TAG, "Posting to pocket " + url);
//            Utils.saveTweetToPocket(url, settings.getString(Constants.POCKET_ACCESS_TOKEN, ""));
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (!listenerAdded) {
            Log.d(TAG, "onConnected addListener");
            Wearable.MessageApi.addListener(mGoogleApiClient, this);
            listenerAdded = true;
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Listener onDestroy");
        Wearable.MessageApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
    }

    /**
     * Returns true if Pocket is installed on this device.
     */
    protected static boolean isPocketInstalled(Context context) {
        return getPocketPackageName(context) != null;
    }

    /**
     * This looks for all possible Pocket versions and returns the package name of one if it is installed.
     * Otherwise returns null if Pocket is not installed.
     */
    private static String getPocketPackageName(Context context) {
        for (String pname : POCKET_PACKAGE_NAMES) {
            if (isAppInstalled(context, pname)) {
                return pname;
            }
        }
        return null;
    }

    private static boolean isAppInstalled(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        PackageInfo info;
        try {
            info = pm.getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            info = null;
        }

        return info != null;
    }
}
