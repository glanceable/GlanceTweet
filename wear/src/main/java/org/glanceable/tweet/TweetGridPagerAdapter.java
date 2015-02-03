/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.glanceable.tweet;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.util.LruCache;
import android.support.wearable.view.CardFragment;
import android.support.wearable.view.FragmentGridPagerAdapter;
import android.support.wearable.view.GridPagerAdapter;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import thewearapps.sharedlibrary.Constants;
import thewearapps.sharedlibrary.TweetItem;

import static thewearapps.sharedlibrary.Constants.PREFS_NAME;
import static thewearapps.sharedlibrary.Constants.TAG;

public class TweetGridPagerAdapter extends FragmentGridPagerAdapter implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, PocketCardFragment.PocketSaveListener {
    private static final int TRANSITION_DURATION_MILLIS = 100;

    private final Context mContext;
    private final GoogleApiClient mGoogleApiClient;
    private final SharedPreferences settings;
    private final TweetsLoadingListener listener;
    private DatabaseHelper dbHelper;
    private final SQLiteDatabase db;
    private List<Row> mRows;
    private ColorDrawable mDefaultBg;
    private ColorDrawable mClearBg;
    private Handler mHandler = new Handler();
    private List<TweetItem> tweetItems;
    private int readIndex = 0;

    public int getReadIndex() {
        return readIndex;
    }

    public List<TweetItem> getTweetItems() {
        return tweetItems;
    }

    interface TweetsLoadingListener {
        void loaded();
        void tweetRead(TweetItem tweetItem);
    }

    public TweetGridPagerAdapter(SQLiteDatabase db, DatabaseHelper helper, Context ctx, FragmentManager fm, TweetsLoadingListener listener) {
        super(fm);
        mContext = ctx;
        this.db = db;
        this.dbHelper = helper;
        mRows = new ArrayList<Row>();
        mDefaultBg = new ColorDrawable(R.color.dark_grey);
        mClearBg = new ColorDrawable(android.R.color.transparent);

        mGoogleApiClient = new GoogleApiClient.Builder(ctx)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();
        this.listener = listener;
        loadTweets(listener);
        settings = ctx.getSharedPreferences(PREFS_NAME, 0);
    }

    public void setTweets(List<TweetItem> tweets) {
        this.tweetItems = tweets;
        for (TweetItem tweet : tweets) {
            Row row = new Row(convertTweetToCard(tweet).toArray(new Fragment[0]));
            mRows.add(row);
        }
        Log.i(TAG, "set tweets size=" + tweets.size());
        notifyDataSetChanged();
    }

    private List<Fragment> convertTweetToCard(TweetItem tweet) {
        List<Fragment> fragments = new ArrayList<>();
        if (tweet.getAsset() != null) {
            ImageCardFragment  fragment = new ImageCardFragment();
            fragment.setBitmap(assetToBitmap(tweet.getAsset()));
            if (tweet.getRetweetFrom() != null && !tweet.getRetweetFrom().isEmpty()) {
                fragment.setScreenName(tweet.getRetweetFrom());
            } else {
                fragment.setScreenName(tweet.getOwner());
            }
            fragment.setText(tweet.getContent());
            fragments.add(fragment);
        } else {
            fragments.add(cardFragment(tweet.getOwner(), tweet.getContent()));
        }
//        if (settings.getBoolean(Constants.KEY_POCKET_CONNECTED, false)) {
        PocketCardFragment fragment = new PocketCardFragment();
        if (tweet.getUrlEntities().length == 0) {
            if (tweet.getPhotoUrl() != null) {
                fragment.setUrls(new String[]{tweet.getPhotoUrl()});
            } else {
                fragment.setUrls(new String[]{String.format("https://twitter.com/%s/status/%d", tweet.getOwner(), tweet.getId())});
            }
        } else {
            fragment.setUrls(tweet.getUrlEntities());
        }
        fragment.setTweetId(tweet.getId());
        fragment.setSaveListener(this);
        fragments.add(fragment);
//        }
//        else {
//            MessageFragment fragment = new MessageFragment();
//            fragment.setMessage(Constants.POCKET_NOT_CONNECTED);
//            fragments.add(fragment);
//        }
        return fragments;
    }

    private Bitmap assetToBitmap(byte[] blob) {
        if (blob != null) {
            Bitmap bitmap =  BitmapFactory.decodeByteArray(blob, 0, blob.length);
            return bitmap;
        }
        return null;
    }

    LruCache<Integer, Drawable> mRowBackgrounds = new LruCache<Integer, Drawable>(3) {
        @Override
        protected Drawable create(final Integer row) {
            int resid = BG_IMAGES[row % BG_IMAGES.length];
            new DrawableLoadingTask(mContext) {
                @Override
                protected void onPostExecute(Drawable result) {
                    TransitionDrawable background = new TransitionDrawable(new Drawable[] {
                            mDefaultBg,
                            result
                    });
                    mRowBackgrounds.put(row, background);
                    notifyRowBackgroundChanged(row);
                    background.startTransition(TRANSITION_DURATION_MILLIS);
                }
            }.execute(resid);
            return mDefaultBg;
        }
    };

    LruCache<Point, Drawable> mPageBackgrounds = new LruCache<Point, Drawable>(3) {
        @Override
        protected Drawable create(final Point page) {
            // place bugdroid as the background at row 2, column 1
            if (page.y == 2 && page.x == 1) {
                int resid = R.drawable.bugdroid_large;
                new DrawableLoadingTask(mContext) {
                    @Override
                    protected void onPostExecute(Drawable result) {
                        TransitionDrawable background = new TransitionDrawable(new Drawable[] {
                                mClearBg,
                                result
                        });
                        mPageBackgrounds.put(page, background);
                        notifyPageBackgroundChanged(page.y, page.x);
                        background.startTransition(TRANSITION_DURATION_MILLIS);
                    }
                }.execute(resid);
            }
            return GridPagerAdapter.BACKGROUND_NONE;
        }
    };

    private Fragment cardFragment(String title, String content) {
        Resources res = mContext.getResources();
        CardFragment fragment = CardFragment.create(title, content);
        return fragment;
    }

    static final int[] BG_IMAGES = new int[] {
            R.drawable.debug_background_1,
            R.drawable.debug_background_2,
            R.drawable.debug_background_3,
            R.drawable.debug_background_4,
            R.drawable.debug_background_5
    };

    /** A convenient container for a row of fragments. */
    private class Row {
        final List<Fragment> columns = new ArrayList<Fragment>();

        public Row(Fragment... fragments) {
            for (Fragment f : fragments) {
                add(f);
            }
        }

        public void add(Fragment f) {
            columns.add(f);
        }

        Fragment getColumn(int i) {
            return columns.get(i);
        }

        public int getColumnCount() {
            return columns.size();
        }
    }

    @Override
    public Fragment getFragment(int row, int col) {
        Row adapterRow = mRows.get(row);
        listener.tweetRead(tweetItems.get(row));
        return adapterRow.getColumn(col);
    }

    @Override
    public Drawable getBackgroundForRow(final int row) {
        return mRowBackgrounds.get(row);
    }

    @Override
    public Drawable getBackgroundForPage(final int row, final int column) {
        return mPageBackgrounds.get(new Point(column, row));
    }

    @Override
    public int getRowCount() {
        return mRows.size();
    }

    @Override
    public int getColumnCount(int rowNum) {
        return mRows.get(rowNum).getColumnCount();
    }

    class DrawableLoadingTask extends AsyncTask<Integer, Void, Drawable> {
        private static final String TAG = "Loader";
        private Context context;

        DrawableLoadingTask(Context context) {
            this.context = context;
        }

        @Override
        protected Drawable doInBackground(Integer... params) {
            Log.d(TAG, "Loading asset 0x" + Integer.toHexString(params[0]));
            return context.getResources().getDrawable(params[0]);
        }
    }

    private void loadTweets(final TweetsLoadingListener listener) {

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... voids) {
                final List<TweetItem> tweets = new ArrayList<TweetItem>();
                Collections.addAll(tweets,
                        dbHelper.loadTweets(db, Constants.TWEETS_TO_DISPLAY, "read=0"));
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setTweets(tweets);
                        listener.loaded();
                    }
                });


                return null;
            }
        }.execute();
    }

    protected void setCurrentColumnForRow(int row, int currentColumn) {
        super.setCurrentColumnForRow(row, currentColumn);
        if (row > readIndex) {
            readIndex = row;
        }
    }

    @Override
    public void onSave(long tweetId, String[] urls, final Handler.Callback callback) {
        PutDataMapRequest dataMap = PutDataMapRequest.createWithAutoAppendedId(Constants.PATH_POCKET_SAVE);
        dataMap.getDataMap().putStringArray("urls", urls);
        dataMap.getDataMap().putLong("id", tweetId);

        PutDataRequest request = dataMap.asPutDataRequest();
        PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi
                .putDataItem(mGoogleApiClient, request);
        pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(DataApi.DataItemResult dataItemResult) {
                Log.i(TAG, "data send status:" + dataItemResult.getStatus());
                callback.handleMessage(null);
            }
        });
    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }
}
