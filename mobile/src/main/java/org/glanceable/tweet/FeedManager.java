package org.glanceable.tweet;

import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.util.LruCache;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import thewearapps.sharedlibrary.TweetItem;
import twitter4j.MediaEntity;
import twitter4j.RateLimitStatus;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.URLEntity;

import static thewearapps.sharedlibrary.Constants.TAG;

public class FeedManager {
    private static final int PAGE_SIZE = 20;
    private Handler mHandler = new Handler();

    private static FeedManager sInstance;
    private static int cacheSize = 64 * 1024 * 1024; // 64MiB
    private static LruCache bitmapCache = new LruCache(cacheSize);

    private ArrayList<TweetItem> tweetItems = new ArrayList<TweetItem>();
    private AtomicInteger mCount = new AtomicInteger();

    private ArrayList<WeakReference<DataSetObserver>> mObservers =
            new ArrayList<WeakReference<DataSetObserver>>();

    private boolean mLoading;
    private int mPageIndex;
    private Twitter twitter = TwitterFactory.getSingleton();
    private LoadingObserver mLoadingObserver;


    private FeedManager() {
    }

    public static FeedManager getInstance() {
        if (sInstance == null) {
            sInstance = new FeedManager();
        }
        return sInstance;
    }


    public void setLoadingObserver(LoadingObserver observer) {
        mLoadingObserver = observer;
    }

    /**
     * Clear all downloaded content
     */
    public void clear() {
        tweetItems.clear();
        mPageIndex = 0;
        notifyObservers();
    }

    /**
     * Add an item to and notify observers of the change.
     * @param item The item to add
     */
    private void add(TweetItem item) {
        tweetItems.add(item);
        notifyObservers();
    }

    /**
     * Gets the item at the specified position
     */
    public TweetItem get(int position) {
        return tweetItems.get(position);
    }

    public void reload() {
        if (mLoading) {
            Log.d(TAG, "reload ignored");
            return;
        }
        clear();
        load();
    }

    private void load() {
        Log.i(TAG, "Loading ...");
        mLoading = true;
        new FetchTweetsTask().execute();
    }

    /**
     * Called when something changes in our data set. Cleans up any weak references that
     * are no longer valid along the way.
     */
    private void notifyObservers() {
        final ArrayList<WeakReference<DataSetObserver>> observers = mObservers;
        final int count = observers.size();

        for (int i = count - 1; i >= 0; i--) {
            WeakReference<DataSetObserver> weak = observers.get(i);
            DataSetObserver obs = weak.get();
            if (obs != null) {
                obs.onChanged();
            } else {
                observers.remove(i);
            }
        }

    }

    private class FetchTweetsTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            try {
                List<twitter4j.Status> statuses = twitter.getHomeTimeline();
                final int count = statuses.size();
                Log.i(TAG, "Got " + count + " tweets");
                for (twitter4j.Status status : statuses) {
                    twitter4j.Status retweetStatus = status.getRetweetedStatus();
                    String text;
                    String retweetFrom = null;
                    if (retweetStatus != null) {
                        text = retweetStatus.getText();
                        retweetFrom = retweetStatus.getUser().getScreenName();
                    } else {
                        text = status.getText();
                    }
                    final TweetItem item = new TweetItem(
                            status.getId(),
                            status.getUser().getScreenName(),
                            status.getUser().getProfileImageURLHttps(),
                            text,
                            convertUrlToStr(status.getURLEntities()),
                            status.getCreatedAt(),
                            retweetFrom);
                    item.setFavourited(status.isFavorited());
                    MediaEntity[] entities = status.getMediaEntities();
                    if (entities != null && entities.length > 0) {
                       if  (entities[0].getType().equals("photo")) {
                           item.setPhotoUrl(entities[0].getMediaURL());
                       }
                    }
                    mCount.incrementAndGet();

                    add(item);
                }

                RateLimitStatus rateLimitStatus = twitter.getRateLimitStatus().get("/statuses/home_timeline");
                Log.i(TAG, "API call remaining " + rateLimitStatus.getRemaining());
                if (rateLimitStatus.getRemaining() <= 1) {
                    Log.w(TAG, "Approaching rate limit, wait " +
                                rateLimitStatus.getSecondsUntilReset() + "s until allowing another fetch");
                    mLoading = true;
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mLoading = false;
                        }
                    }, rateLimitStatus.getSecondsUntilReset() * 1000);
                } else {
                    mLoading = false;
                }
                for (TweetItem item : tweetItems) {
                    if (item.getPhotoUrl() != null) {
                        item.setBitmap(getBitmapFromURL(item.getPhotoUrl()));
                        Log.d(TAG, "Fetched image for " + item.getId());
                    }
                }
                Log.i(TAG, bitmapCache.hitCount() + " cache hits, " +
                        bitmapCache.missCount() + " misses");
                mLoadingObserver.dataLoaded(tweetItems);
            } catch (TwitterException e) {
                Log.e(TAG, e.toString());
                mLoading = false;
            }
            return null;
        }

        private String[] convertUrlToStr(URLEntity[] urlEntities) {
            String[] ret = new String[urlEntities.length];
            for (int i = 0; i< urlEntities.length;i++) {
                ret[i] = urlEntities[i].getURL();
            }
            return ret;
        }
    }

    interface LoadingObserver {
        public void dataLoaded(ArrayList<TweetItem> tweetItems) ;
    }

    static Bitmap getBitmapFromURL(String link) {
        if (bitmapCache.get(link) != null) {
            return (Bitmap) bitmapCache.get(link);
        }
        try {
            URL url = new URL(link);
            HttpURLConnection connection = (HttpURLConnection) url
                    .openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap myBitmap = BitmapFactory.decodeStream(input);
            bitmapCache.put(link, myBitmap);
            return myBitmap;

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage().toString());
            return null;
        }
    }

}
