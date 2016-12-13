package android.content;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * @hide
 */
public class CopyHistoryItem implements Parcelable, Comparable<CopyHistoryItem> {
    public final String mContent;
    public final long mTimeStamp;

    public CopyHistoryItem(String content, long timestamp) {
        mContent = content;
        mTimeStamp = timestamp;
    }

    public CopyHistoryItem(Parcel source) {
        mContent = source.readString();
        mTimeStamp = source.readLong();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mContent);
        dest.writeLong(mTimeStamp);
    }

    public static final Parcelable.Creator<CopyHistoryItem> CREATOR = new Parcelable.Creator<CopyHistoryItem>() {

        public CopyHistoryItem createFromParcel(Parcel source) {
            return new CopyHistoryItem(source);
        }

        public CopyHistoryItem[] newArray(int size) {
            return new CopyHistoryItem[size];
        }
    };

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof CopyHistoryItem)) {
            return false;
        }
        return compareTo((CopyHistoryItem) o) == 0;
    }

    @Override
    public int compareTo(CopyHistoryItem another) {
        if (mTimeStamp != another.mTimeStamp) {
            if (mTimeStamp > another.mTimeStamp) {
                return -1;
            } else {
                return 1;
            }
        } else {
            int res = mContent.compareTo(another.mContent);
            if (res != 0) {
                return res;
            } else {
                return 0;
            }
        }
    }
}
