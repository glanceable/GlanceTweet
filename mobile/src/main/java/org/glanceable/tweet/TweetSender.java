package org.glanceable.tweet;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import thewearapps.sharedlibrary.TweetItem;
import static thewearapps.sharedlibrary.Constants.TAG;

public class TweetSender {

    private static final boolean DEBUG = false;
    private final GoogleApiClient mGoogleApiClient;

    public TweetSender(GoogleApiClient apiClient) {
        mGoogleApiClient = apiClient;
    }

    private static Asset createAssetFromBitmap(Bitmap bitmap) {
        Bitmap scaledBm = Bitmap.createScaledBitmap(bitmap, 320, (int)(bitmap.getHeight() * 320.0/bitmap.getWidth()), false);
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        scaledBm.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        return Asset.createFromBytes(byteStream.toByteArray());
    }

    public void send(List<TweetItem> tweetItems) {

        Log.i(TAG, "sending tweets");
        Gson gson = new GsonBuilder().excludeFieldsWithModifiers(Modifier.TRANSIENT).create();
        String json = gson.toJson(tweetItems);
        String path = "/new_tweets";


        PutDataMapRequest dataMap = PutDataMapRequest.create(path);
        dataMap.getDataMap().putString("tweets", json);
        if (DEBUG) {
            Random random = new Random();
            dataMap.getDataMap().putInt("cache_buster", random.nextInt());
        }

        for (TweetItem item : tweetItems) {
            if (item.getBitmap() != null) {
                Log.i(TAG, "putting asset " + item.getId());
                dataMap.getDataMap().putAsset(Long.toString(item.getId()), createAssetFromBitmap(item.getBitmap()));
            }
        }


        PutDataRequest request = dataMap.asPutDataRequest();
        PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi
                .putDataItem(mGoogleApiClient, request);
        pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(DataApi.DataItemResult dataItemResult) {
                Log.i(TAG, "tweets delivered");
            }

        });
    }

}
