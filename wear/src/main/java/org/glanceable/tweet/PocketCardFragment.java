package org.glanceable.tweet;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.view.CircledImageView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by jiayao on 12/25/14.
 */
public class PocketCardFragment extends Fragment {

    private PocketSaveListener listener;
    private String[] urls;
    private long tweetId;

    public PocketCardFragment() {
        super();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ViewGroup mRootView = (ViewGroup) inflater.inflate(R.layout.fragment_pocket, null);
        mRootView.setBackgroundResource(android.R.color.transparent);

        final CircledImageView imageView = (CircledImageView) mRootView.findViewById(R.id.imageView);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.onSave(tweetId, urls, new Handler.Callback() {
                    @Override
                    public boolean handleMessage(Message message) {
                        imageView.setImageResource(R.drawable.confirmation);
                        return true;
                    }
                });
            }
        });
        return mRootView;
    }

    public void setSaveListener(PocketSaveListener listener) {
        this.listener = listener;
    }

    public void setUrls(String[] urls) {
        this.urls = urls;
    }

    public void setTweetId(long tweetId) {
        this.tweetId = tweetId;
    }

    public long getTweetId() {
        return tweetId;
    }

    interface PocketSaveListener {
        void onSave(long tweetId, String[] urls, Handler.Callback callback);
    }
}