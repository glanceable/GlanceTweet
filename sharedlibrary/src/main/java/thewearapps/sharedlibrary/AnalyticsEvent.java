package thewearapps.sharedlibrary;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class AnalyticsEvent implements Parcelable {

    private String category;
    private String action;
    private String label;
    private int value;

    public AnalyticsEvent(String category, String action, String label, int value) {
        this.category = category;
        this.action = action;
        this.label = label;
        this.value = value;
    }

    public AnalyticsEvent(Parcel in) {
        category = in.readString();
        action = in.readString();
        label = in.readString();
        value = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flag) {
        dest.writeString(category);
        dest.writeString(action);
        dest.writeString(label);
        dest.writeInt(value);
    }

    public Parcel toParcel() {
        Parcel parcel = Parcel.obtain();
        writeToParcel(parcel, 0);
        return parcel;
    }

    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {
        public AnalyticsEvent createFromParcel(Parcel in) {
            return new AnalyticsEvent(in);
        }

        public AnalyticsEvent[] newArray(int size) {
            return new AnalyticsEvent[size];
        }
    };

    public String getCategory() {
        return category;
    }

    public String getAction() {
        return action;
    }

    public String getLabel() {
        return label;
    }

    public int getValue() {
        return value;
    }
}
