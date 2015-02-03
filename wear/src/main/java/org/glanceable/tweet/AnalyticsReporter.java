package org.glanceable.tweet;

import android.content.Context;
import android.os.Bundle;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.LinkedList;

import thewearapps.sharedlibrary.AnalyticsEvent;
import thewearapps.sharedlibrary.Constants;

public class AnalyticsReporter implements GoogleApiClient.ConnectionCallbacks {
    private final GoogleApiClient mGoogleApiClient;
    private final LinkedList<AnalyticsEvent> messageQueue;
    private String mPeerId;

    public AnalyticsReporter(Context context) {
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .build();
        mGoogleApiClient.connect();
        messageQueue = new LinkedList<>();
    }

    public void report(String category, String action, String label) {
        report(category, action, label, 0);
    }

    public void report(String category, String action, String label, int value) {
//        Log.d(Constants.TAG, "ga report " + mPeerId + " category=" + category);
        AnalyticsEvent event = new AnalyticsEvent(category, action, label, value);
        if (mPeerId != null) {

            Wearable.MessageApi.sendMessage(mGoogleApiClient, mPeerId, Constants.WEAR_GA_PATH, event.toParcel().marshall());
        } else {
            messageQueue.push(event);
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                for (Node node : getConnectedNodesResult.getNodes()) {
                    mPeerId = node.getId();
                    for (AnalyticsEvent event : messageQueue) {
                        Wearable.MessageApi.sendMessage(mGoogleApiClient, mPeerId, Constants.WEAR_GA_PATH, event.toParcel().marshall());
                    }
                    break;
                }
            }
        });
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    public void close() {
        mGoogleApiClient.disconnect();
    }
}
