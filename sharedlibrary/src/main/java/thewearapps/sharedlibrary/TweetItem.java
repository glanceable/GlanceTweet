package thewearapps.sharedlibrary;


import android.content.ContentValues;
import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Date;


/**
 * Holds one item returned from the Panoramio server. This includes
 * the bitmap along with other meta info.
 *
 */
public class TweetItem implements Parcelable {

    private boolean mRead;
    private long mId;
    private String mOwner;
    private String mText;
    private String[] urlEntities;
    private String mProfileImageUrl;
    private Date mCreated;
    private String retweetFrom;
    private String photoUrl;
    transient private Bitmap bitmap;  // exclude from json
    transient private byte[] asset;

    public boolean isFavourited() {
        return favourited;
    }

    public void setFavourited(boolean favourited) {
        this.favourited = favourited;
    }

    private boolean favourited;

    public TweetItem(Parcel in) {
        mId = in.readLong();
        mOwner = in.readString();
        mText = in.readString();
    }
    public TweetItem(long id, String screenName, String text, Date created, boolean read, byte[] asset,
                     String retweetFrom, String[] urls) {
        mId = id;
        mOwner = screenName;
        mText = text;
        mCreated = created;
        this.asset = asset;
        this.retweetFrom = retweetFrom;
        this.mRead = read;
        this.urlEntities = urls;
    }

    public TweetItem(long id, String screenName, String ownerIcon, String text, String[] urlEntities, Date created, String retweetFrom) {
        mId = id;
        mOwner = screenName;
        mProfileImageUrl = ownerIcon;
        mText = text;
        this.urlEntities = urlEntities;
//        for (URLEntity entity : urlEntities) {
//            mText = mText.replace(entity.getURL(), entity.getDisplayURL());
//        }
        mCreated = created;
        this.retweetFrom = retweetFrom;
    }

    public long getId() {
        return mId;
    }



    public String getOwner() {
        return mOwner;
    }

    public static final Creator<TweetItem> CREATOR =
            new Creator<TweetItem>() {
                public TweetItem createFromParcel(Parcel in) {
                    return new TweetItem(in);
                }

                public TweetItem[] newArray(int size) {
                    return new TweetItem[size];
                }
            };

    public String getContent() {
        return mText;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeLong(mId);
        parcel.writeString(mOwner);
        parcel.writeString(mText);
    }

    public String[] getUrlEntities() {
        return urlEntities;
    }

    public String getProfileImageUrl() {
        return mProfileImageUrl;
    }

    public Date getCreated() {
        return mCreated;
    }

    public ContentValues toContentValues() {
        long created = 0;
        if (getCreated() != null) {
            created = getCreated().getTime();
        }
        ContentValues values = new ContentValues();
        values.put("id", getId());
        values.put("screen_name", getOwner());
        values.put("content", getContent());
        values.put("created", created);
        values.put("retweetFrom", getRetweetFrom());
        values.put("read", isRead());
        values.put("photo", getAsset());
        values.put("url", getFirstUrl());

        return values;
    }

    private String getFirstUrl() {
        if (urlEntities == null || urlEntities.length == 0) {
            return photoUrl;
        }
        return urlEntities[0];
    }

    public String getRetweetFrom() {
        return retweetFrom;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setAsset(byte[] asset) {
        this.asset = asset;
    }

    public byte[] getAsset() {
        return asset;
    }

    public boolean isRead() {
        return mRead;
    }

    @Override
    public String toString() {
        return "TweetItem{" +
                "photoUrl='" + photoUrl + '\'' +
                ", mOwner='" + mOwner + '\'' +
                ", mText='" + mText + '\'' +
                ", mCreated=" + mCreated +
                ", retweetFrom='" + retweetFrom + '\'' +
                ", mId=" + mId +
                ", mread=" + mRead +
                ", url=" + getFirstUrl() +
                '}';
    }

    public void setRead(boolean read) {
        this.mRead = read;
    }
}