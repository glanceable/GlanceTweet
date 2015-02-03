package org.glanceable.tweet;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.wearable.view.CardFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class ImageCardFragment extends CardFragment {
    private Bitmap bitmap;
    private String screenName;
    private String text;

    public ImageCardFragment() {
        super();
    }
    @Override
    public View onCreateContentView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup mRootView = (ViewGroup) inflater.inflate(R.layout.fragment_my_card, null);
        ((ImageView) mRootView.findViewById(R.id.imageView)).setImageBitmap(bitmap);
        ((TextView) mRootView.findViewById(R.id.textView)).setText(screenName);
        ((TextView) mRootView.findViewById(R.id.contentView)).setText(text);
        return mRootView;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public void setScreenName(String screenName) {
        this.screenName = screenName;
    }

    public void setText(String text) {
        this.text = text;
    }
}