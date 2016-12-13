package com.android.server.clipboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.content.CopyHistoryItem;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.BaseColumns;

public class CopyHistoryDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = CopyHistoryDatabaseHelper.class.getName();

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "copy_history.db";

    private static final int MAX_SIZE = 1024;
    private List<CopyHistoryItem> mList = new ArrayList<CopyHistoryItem>();
    private Context mContext;
    private Handler mHandler;
    CopyHistoryDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        mContext = context;
        HandlerThread thread = new HandlerThread(TAG);
        thread.start();
        mHandler = new CopyHandler(thread.getLooper());
        mHandler.obtainMessage(MSG_INIT_LIST).sendToTarget();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE copyhistory ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT," + "content TEXT,"
                + "timestamp TEXT" + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // nothing to do now.
    }

    private void initList() {
        List<CopyHistoryItem> list = new ArrayList<CopyHistoryItem>();
        Cursor cursor = this.getReadableDatabase().query(TABLE_COPYHISTORY,
                null, null, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    do {
                        String content = cursor.getString(cursor.getColumnIndex(CopyHistoryColumns.CONTENT));
                        long timestamp = Long.parseLong(cursor.getString(cursor.getColumnIndex(CopyHistoryColumns.TIMESTAMP)));
                        list.add(new CopyHistoryItem(content, timestamp));
                    } while (cursor.moveToNext());
                }
            } finally {
                cursor.close();
            }
            Collections.sort(list);
            synchronized (mList) {
                mList.addAll(list);
            }
        }
    }

    public List<CopyHistoryItem> getCopyHistory() {
        List<CopyHistoryItem> list = new ArrayList<CopyHistoryItem>();
        synchronized(mList){
            list.addAll(mList);
        }
        return list;
    }

    public void clearCopyHistory(){
        synchronized(mList){
            mList.clear();
        }
        mHandler.obtainMessage(MSG_CLEAR).sendToTarget();
    }

    public void delete(CopyHistoryItem item) {
        if (item != null) {
            synchronized (mList) {
                for (int i = 0; i < mList.size(); ++i) {
                    if (item.equals(mList.get(i))) {
                        mList.remove(i);
                        i--;
                        mHandler.obtainMessage(MSG_DEL, item).sendToTarget();
                        // we assume that only one match
                        break;
                    }
                }
            }
        }
    }

    public void insert(String content) {
        synchronized(mList){
            if (mList.size() > 0) {
                String first = mList.get(0).mContent;
                if (first != null && first.equals(content)) {
                    return;
                }
            }
            CopyHistoryItem item = new CopyHistoryItem(content, System.currentTimeMillis());
            mList.add(0, item);
            if(mList.size() > MAX_SIZE){
                CopyHistoryItem toDel = mList.get(mList.size() - 1);
                mList.remove(mList.size() - 1);
                mHandler.obtainMessage(MSG_DEL, toDel).sendToTarget();
            }
            mHandler.obtainMessage(MSG_INSERT, item).sendToTarget();
        }
    }

    private void insert(CopyHistoryItem item) {
        ContentValues cv = new ContentValues();
        cv.put(CopyHistoryColumns.CONTENT, item.mContent);
        cv.put(CopyHistoryColumns.TIMESTAMP, item.mTimeStamp);
        getWritableDatabase().insert(TABLE_COPYHISTORY, null, cv);
    }

    private void deleteFromDatabase(CopyHistoryItem item) {
        getWritableDatabase().delete(
                TABLE_COPYHISTORY,
                CopyHistoryColumns.CONTENT + " = ?" + " and "
                        + CopyHistoryColumns.TIMESTAMP + " = ?",
                new String[] { item.mContent, item.mTimeStamp + "" });
    }

    private static final String TABLE_COPYHISTORY = "copyhistory";

    static final class CopyHistoryColumns implements BaseColumns {
        static final String CONTENT = "content";
        static final String TIMESTAMP = "timestamp";
    }

    private static final int MSG_INSERT = 0;
    private static final int MSG_DEL = 1;
    private static final int MSG_CLEAR = 2;
    private static final int MSG_INIT_LIST = 3;
    private class CopyHandler extends Handler{
        public CopyHandler(Looper looper) {
            super(looper, null, false);
        }

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what){
            case MSG_INIT_LIST:
                initList();
                break;
            case MSG_INSERT:
                insert((CopyHistoryItem) msg.obj);
                break;
            case MSG_DEL:
                deleteFromDatabase((CopyHistoryItem) msg.obj);
                break;
            case MSG_CLEAR:
                getWritableDatabase().delete(TABLE_COPYHISTORY, null, null);
                break;
            }
        }
    }
}
