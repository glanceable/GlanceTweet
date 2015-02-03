package org.glanceable.tweet;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;

import org.apache.http.Header;
import org.apache.http.HttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;

import thewearapps.sharedlibrary.Constants;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.OAuthAuthorization;
import twitter4j.auth.RequestToken;
import twitter4j.conf.PropertyConfiguration;

import static thewearapps.sharedlibrary.Constants.ACCESS_TOKEN;
import static thewearapps.sharedlibrary.Constants.ACCESS_TOKEN_SECRET;
import static thewearapps.sharedlibrary.Constants.PREFS_NAME;
import static thewearapps.sharedlibrary.Constants.REQUEST_TOKEN;
import static thewearapps.sharedlibrary.Constants.REQUEST_TOKEN_SECRET;
import static thewearapps.sharedlibrary.Constants.SCREEN_NAME;
//import com.google.common.io
//
public class MainActivity extends ActionBarActivity
        implements GoogleApiClient.ConnectionCallbacks, NodeApi.NodeListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "TwitterForWatch";

    private GoogleApiClient mGoogleApiClient;

    Twitter twitter = TwitterFactory.getSingleton();
    private RequestToken requestToken;
    private SharedPreferences settings;
    private boolean mResolvingError = false;

    private Handler mHandler = new Handler();
    private Tracker tracker;


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(Constants.AUTO_SCROLL)) {
//            sendConfigUpdateMessage(key, sharedPreferences.getBoolean(key, false));
        } else if (key.equals(Constants.KEY_NOTIFY_FREQUENCY)) {
            DataMap config = new DataMap();
            config.putInt(key, Integer.parseInt(sharedPreferences.getString(key,
                    Integer.toString(Constants.DEFAULT_NOTIFY_FREQUENCY))));
            sendConfigUpdateMessage(key, config);
        }
    }

    private void sendConfigUpdateMessage(String key, DataMap config) {
        Random rand = new Random();
//        config.putInt("cachebuster", rand.nextInt());
        PutDataMapRequest dataMap = PutDataMapRequest.create(Constants.GENERIC_CONFIG_PATH);
        dataMap.getDataMap().putAll(config);
        PutDataRequest request = dataMap.asPutDataRequest();
        PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi
                .putDataItem(mGoogleApiClient, request);
        pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(DataApi.DataItemResult dataItemResult) {

            }
        });
        Log.d(TAG, "Sending config message: " + key);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        setContentView(R.layout.activity_connected);
        GoogleAnalytics analytics = GoogleAnalytics.getInstance(this);
        tracker = analytics.newTracker(Constants.TRACKING_ID);
        AccessToken accessToken;

        Uri data = this.getIntent().getData();
        if (data != null) {
            Log.d(TAG, data.getScheme());
            if (data.getScheme().equals(Constants.POCKET_REDIRECT_SCHEME)) {
                processPocketAuthResult(data);

            } else {
                processAuthResult(data);
            }
            SetupUi();
        } else {
            accessToken = Utils.loadAccessToken(settings);
            if (accessToken.getToken().isEmpty()) {
                Log.d(TAG, "not logged in");
                setContentView(R.layout.activity_main);
                (findViewById(R.id.connect_twitter_button)).setOnClickListener(
                        new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        connectTwitter();
                    }
                });

            } else {
                Log.d(TAG, "Already logged in");
                SetupUi();
            }
        }

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .build();

        settings.registerOnSharedPreferenceChangeListener(this);

        // for backward compatibility
        if (settings.getString(Constants.POCKET_ACCESS_TOKEN, "").isEmpty() &&
                !settings.getBoolean(Constants.KEY_POCKET_CONNECTED, false)
                ) {
            DataMap config = new DataMap();
            config.putBoolean(Constants.KEY_POCKET_CONNECTED, true);
            sendConfigUpdateMessage(Constants.KEY_POCKET_CONNECTED, config);
            settings.edit().putBoolean(Constants.KEY_POCKET_CONNECTED, true).apply();
        }
    }

    private void processPocketAuthResult(final Uri data) {
        Log.d(TAG, "processPocketAuth");
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... voids) {

                try {
                    HttpResponse response = Utils.exchangePocketAccessToken(settings.getString(Constants.POCKET_REQ_TOKEN, ""));

                    String content = null;
                    content = CharStreams.toString(new InputStreamReader(response.getEntity().getContent()));

                    Uri uri=Uri.parse("http://example.com/?" + content);
                    String access_token = uri.getQueryParameter("access_token");
//                    Log.d(Constants.TAG, "access token: " + access_token);
                    settings.edit().putString(Constants.POCKET_ACCESS_TOKEN, access_token).apply();
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Pocket connected.", Toast.LENGTH_LONG).show();
                        }
                    });
                    DataMap config = new DataMap();
                    config.putBoolean(Constants.KEY_POCKET_CONNECTED, true);
                    sendConfigUpdateMessage(Constants.KEY_POCKET_CONNECTED, config);
                    tracker.send(new HitBuilders.EventBuilder().setCategory("account").setAction("connect_pocket").build());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }
        }.execute();

    }

    private void SetupUi() {
        ((TextView) findViewById(R.id.usernameTextView)).setText(
                settings.getString("screen_name", "Unknown"));
        ((TextView) findViewById(R.id.lastFetchedTextView)).setText(
                new Date(settings.getLong("fetch_ts", 0)).toString());
    }

    private void connectTwitter() {
        tracker.send(new HitBuilders.EventBuilder().setCategory("account").setAction("connect").build());
        new AsyncTask<Void, Void, Void>() {
            RequestToken getRequestToken() throws TwitterException {
                OAuthAuthorization oAuthAuthorization = new OAuthAuthorization(
                        new PropertyConfiguration(getResources().openRawResource(R.raw.twitter4j)));
                requestToken = oAuthAuthorization.getOAuthRequestToken("wearapps://twitter.auth");
                // temporary data, used again when swapping the oauth token with access token
                settings.edit().putString(REQUEST_TOKEN, requestToken.getToken()).commit();
                settings.edit().putString(REQUEST_TOKEN_SECRET, requestToken.getTokenSecret()).commit();
                return requestToken;
            }
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    requestToken = getRequestToken();
                } catch (TwitterException e) {
                    e.printStackTrace();
                    }

                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(requestToken.getAuthorizationURL()));
                startActivity(intent);
                finish();
                return null;
            }
        }.execute();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_logout) {
            tracker.send(new HitBuilders.EventBuilder().setCategory("account").setAction("logout").build());
            settings.edit().remove(ACCESS_TOKEN).commit();
            settings.edit().remove(ACCESS_TOKEN_SECRET).commit();
            settings.edit().remove(SCREEN_NAME).commit();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_about) {
            tracker.send(new HitBuilders.EventBuilder().setCategory("misc").setAction("about").build());
            AboutDialogFragment dialog = new AboutDialogFragment();
            dialog.show(this.getFragmentManager(), null);
         //   ((TextView) dialog.getDialog().findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
            return true;
        } else if (id == R.id.action_fetch) {
            tracker.send(new HitBuilders.EventBuilder().setCategory("action").setAction("fetch_now").build());
            startTwitterService();
        } else if (id == R.id.action_mark_all_read) {
            tracker.send(new HitBuilders.EventBuilder().setCategory("action").setAction("mark_all_read").build());
            markAllRead();
        }
//        else if (id == R.id.action_connect_pocket) {
//            new AsyncTask<Void, Void, Void>() {
//
//                @Override
//                protected Void doInBackground(Void... voids) {
//
//                    try {
//                        HttpResponse resp = Utils.getPocketRequestToken();
//                        InputStream stream = null;
//                        stream = resp.getEntity().getContent();
//                        String content = CharStreams.toString(new InputStreamReader(stream));
//                        String reqToken = content.split("=")[1];
//                        settings.edit().putString(Constants.POCKET_REQ_TOKEN, reqToken).apply();
//                        Intent intent = new Intent(Intent.ACTION_VIEW);
//                        String authUrl = String.format("https://getpocket.com/auth/authorize?" +
//                                        "request_token=%s&redirect_uri=%s", reqToken,
//                                URLEncoder.encode(Constants.POCKET_REDIRECT_URL, "UTF-8"));
//                        Log.d(Constants.TAG, "browse " + authUrl);
//                        intent.setData(Uri.parse(authUrl));
//                        startActivity(intent);
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//
//                    return null;
//                }
//            }.execute();
//        }

        return super.onOptionsItemSelected(item);
    }

    private void markAllRead() {
        PutDataMapRequest dataMap = PutDataMapRequest.create(Constants.PATH_MARK_READ);

        PutDataRequest request = dataMap.asPutDataRequest();
        PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi
                .putDataItem(mGoogleApiClient, request);
        pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(DataApi.DataItemResult dataItemResult) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Done", Toast.LENGTH_SHORT);
                    }
                });
            }

        });
    }

    private void startTwitterService() {
        Intent intent = new Intent(this, TwitterFetchService.class);
        startService(intent);
    }


    @Override
    public void onConnected(Bundle bundle) {
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mResolvingError) {
            mGoogleApiClient.connect();
            Log.d(TAG, "api connected");
        }
    }

    @Override
    protected void onStop() {
        if (mGoogleApiClient.isConnected()) {
            Wearable.NodeApi.removeListener(mGoogleApiClient, this);
        }
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    private void processAuthResult(Uri data) {
        final String oauthVerifier = data.getQueryParameter("oauth_verifier");
        if (oauthVerifier == null) {
            return;
        }
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                final AccessToken accessToken;
                try {
//                    Log.d(TAG, "request token " + settings.getString(REQUEST_TOKEN, ""));
                    tracker.send(new HitBuilders.EventBuilder().setCategory("event").setAction("auth_success").build());
                    try {
                        //TODO: probably unsafe
                        twitter.setOAuthConsumer("JMf41eZxRgexvzpV27x2OtQ4q", "CW2PKBKSLdfXikEpJM1SZ6nvC6Bw0Z8CQtHSKrbIkpnFHO4o3A");
                    } catch (IllegalStateException e) {

                    }
                    accessToken = twitter.getOAuthAccessToken(
                            new RequestToken(settings.getString(REQUEST_TOKEN, ""),
                                    settings.getString(REQUEST_TOKEN_SECRET, "")), oauthVerifier);
                    twitter.setOAuthAccessToken(accessToken);
                    final String screenName = twitter.getScreenName();

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            settings.edit().putString(ACCESS_TOKEN, accessToken.getToken()).apply();
                            settings.edit().putString(ACCESS_TOKEN_SECRET, accessToken.getTokenSecret()).apply();
                            settings.edit().putString(SCREEN_NAME, screenName).apply();


                    startTwitterService();
                        }
                    });
                } catch (TwitterException e) {
                    Log.w(TAG, e.getMessage());
                    tracker.send(new HitBuilders.EventBuilder().setCategory("event").setAction("auth_failed").setLabel(e.getErrorMessage())
                            .build());
                    e.printStackTrace();
                }
                return null;
            }
        }.execute();
    }

    @Override
    public void onPeerConnected(Node node) {

    }

    @Override
    public void onPeerDisconnected(Node node) {

    }
    @Override
    protected void onResume() {
        super.onResume();

//        PreferenceScreen root = ((PreferenceFragment)getFragmentManager().findFragmentById(R.id.fragment)).getPreferenceScreen();
//        root.getSharedPreferences()
//                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

//        PreferenceScreen root = ((PreferenceFragment)getFragmentManager().findFragmentById(R.id.fragment)).getPreferenceScreen();
//        root.getSharedPreferences()
//                .unregisterOnSharedPreferenceChangeListener(this);
    }
}
