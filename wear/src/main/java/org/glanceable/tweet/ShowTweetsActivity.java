package org.glanceable.tweet;

import android.app.Activity;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NotificationManagerCompat;
import android.support.wearable.view.GridViewPager;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import thewearapps.sharedlibrary.Constants;
import thewearapps.sharedlibrary.TweetItem;

import static thewearapps.sharedlibrary.Constants.PREFS_NAME;
import static thewearapps.sharedlibrary.Constants.TABLE_NAME;
import static thewearapps.sharedlibrary.Constants.TAG;

// TODO: First time need to show instruction to setup on the phone
// TODO: when there is no tweets left to read need to show a message.
public class ShowTweetsActivity extends Activity {
    private TweetGridPagerAdapter pagerAdapter;
    private SQLiteDatabase db;
    private DatabaseHelper mOpenHelper;
    private AnalyticsReporter reporter;
    private Handler handler = new Handler();
    private long scrollCounter = 0;
    private Runnable autoScrollTask = new Runnable() {
        @Override
        public void run() {
            pager.scrollBy(0, 1);
            if ((++scrollCounter % 10) == 0) {
                pager.computeScroll();
            }
            handler.postDelayed(autoScrollTask, 70);
        }
    };
    private GridViewPager pager;
    private SharedPreferences settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        reporter = new AnalyticsReporter(this);
        mOpenHelper = new DatabaseHelper(this);
        db = mOpenHelper.getReadableDatabase();
        settings = getSharedPreferences(PREFS_NAME, 0);
        if (isDbInitialized()) {
            int numNew = numNewTweets();
            reporter.report("wear", "open", "success", numNew);
            if (numNew > 0) {
//                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                setContentView(R.layout.activity_main);
                final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);

                stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
                    @Override
                    public void onLayoutInflated(WatchViewStub stub) {
                        pagerAdapter = new TweetGridPagerAdapter(db, mOpenHelper,
                                ShowTweetsActivity.this, ShowTweetsActivity.this.getFragmentManager(),
                                new TweetGridPagerAdapter.TweetsLoadingListener() {
                                    @Override
                                    public void loaded() {
                                        pager = (GridViewPager) findViewById(R.id.pager);
                                        pager.setAdapter(pagerAdapter);
//                                        pager.setOffscreenPageCount(20);
//
//                                        if (settings.getBoolean(Constants.AUTO_SCROLL, false)) {
//                                            enableAutoScroll = true;
//                                            autoScrollTask.run();
//                                        }

                                    }

                                    @Override
                                    public void tweetRead(final TweetItem tweetItem) {
                                        tweetItem.setRead(true);
                                        new AsyncTask<Void, Void, Void>() {
                                            @Override
                                            protected Void doInBackground(Void... voids) {
                                                db.update(TABLE_NAME, tweetItem.toContentValues(), "id=" + tweetItem.getId(), null);
                                                return null;
                                            }


                                        }.execute();
                                    }
                                });
                    }
                });
            } else {
                setWelcomeView(R.string.no_tweets);
            }
        } else {
            Log.d(TAG, "Uninitialized");
            reporter.report("wear", "open", "uninitialized");
            setWelcomeView(R.string.welcome);
        }
        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);
        notificationManager.cancel(Constants.NOTIF_SHOW_TWEETS);
    }

    private void setWelcomeView(int text) {
        setContentView(R.layout.activity_welcome);
        ((TextView) findViewById(R.id.welcomeText)).setText(text);
        ((TextView) findViewById(R.id.welcomeText)).setTextColor(Color.parseColor("#FFECB3"));
    }


    private int numNewTweets() {
        Cursor cursor = db.query(TABLE_NAME, new String[]{"id"},
                "read=0", null, null, null, null);

        return cursor.getCount();
    }

    private boolean isDbInitialized() {
        Cursor cursor = db.rawQuery("select DISTINCT tbl_name from sqlite_master where tbl_name = '"+TABLE_NAME+"'", null);
        if(cursor!=null && cursor.getCount() > 0) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (pagerAdapter != null) {
            reporter.report("wear", "read", Integer.toString(pagerAdapter.getReadIndex()));
            if (pagerAdapter.getReadIndex() > 0) {
                for (int i = 0; i < pagerAdapter.getReadIndex()+1; i++) {
                    TweetItem tweet = pagerAdapter.getTweetItems().get(i);
                    // remove all data from the tweet except the id. Avoid deletion because we would see deleted tweets as new.
                    db.update(TABLE_NAME, new TweetItem(
                            tweet.getId(), null, null, null, true, null, null, null).toContentValues(), "id=" + tweet.getId(), null);
                }
            }
        }
        db.close();
        reporter.close();
    }

//    @Override
//    public void onStop() {
//
//        Log.d(TAG, "onStop");
//        if (pagerAdapter != null) {
//            reporter.report("wear", "read", Integer.toString(pagerAdapter.getReadIndex()));
//            if (pagerAdapter.getReadIndex() > 0) {
//                for (int i = 0; i < pagerAdapter.getReadIndex()+1; i++) {
//                    TweetItem tweet = pagerAdapter.getTweetItems().get(i);
//                    tweet.setRead(true);
//                    // remove all data from the tweet except the id. Avoid deletion because we would see deleted tweets as new.
//                    db.update(TABLE_NAME, tweet.toContentValues(), "id=" + tweet.getId(), null);
//                }
//            }
//        }
//    }

}
