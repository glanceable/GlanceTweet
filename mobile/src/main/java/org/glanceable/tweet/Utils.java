package org.glanceable.tweet;

import android.content.SharedPreferences;
import android.util.Log;

import com.google.common.io.CharStreams;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import thewearapps.sharedlibrary.Constants;
import twitter4j.Twitter;
import twitter4j.auth.AccessToken;

/**
 * Created by jiayao on 12/24/14.
 */
public class Utils {

    static AccessToken loadAccessToken(SharedPreferences settings) {
        return new AccessToken(settings.getString(Constants.ACCESS_TOKEN, ""),
                settings.getString(Constants.ACCESS_TOKEN_SECRET, ""));
    }

    static void setConsumerKey(Twitter twitter) {
        try {
            twitter.setOAuthConsumer("JMf41eZxRgexvzpV27x2OtQ4q",
                    "CW2PKBKSLdfXikEpJM1SZ6nvC6Bw0Z8CQtHSKrbIkpnFHO4o3A");
        } catch (IllegalStateException e) {
            // It's ok if consumer is already set
        }
    }

    public static void saveTweetToPocket(String url, String accessToken) throws IOException {
        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
        nameValuePairs.add(new BasicNameValuePair("url", url));
        nameValuePairs.add(new BasicNameValuePair("consumer_key", Constants.POCKET_CONSUMER_KEY));
        nameValuePairs.add(new BasicNameValuePair("access_token", accessToken));
        HttpResponse resp = postData("https://getpocket.com/v3/add", nameValuePairs);
    }

    public static HttpResponse getPocketRequestToken() throws IOException {
        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
        nameValuePairs.add(new BasicNameValuePair("consumer_key", Constants.POCKET_CONSUMER_KEY));
        nameValuePairs.add(new BasicNameValuePair("redirect_uri", Constants.POCKET_REDIRECT_URL));
        return postData("https://getpocket.com/v3/oauth/request", nameValuePairs);
    }

    public static HttpResponse exchangePocketAccessToken(String authToken) throws IOException {
        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
        nameValuePairs.add(new BasicNameValuePair("consumer_key", Constants.POCKET_CONSUMER_KEY));
        nameValuePairs.add(new BasicNameValuePair("code", authToken));
        return postData("https://getpocket.com/v3/oauth/authorize", nameValuePairs);
    }

    public static HttpResponse postData(String url, List<NameValuePair> data) throws IOException {
        // Create a new HttpClient and Post Header
        HttpClient httpclient = new DefaultHttpClient();
        HttpPost httppost = new HttpPost(url);

        try {
            httppost.setEntity(new UrlEncodedFormEntity(data));

            return httpclient.execute(httppost);

        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
        }
        return null;
    }

}
