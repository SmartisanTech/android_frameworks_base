/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.policy;

import android.content.Context;
import android.util.Slog;
import android.view.MotionEvent;
import android.view.ViewRootImpl;
import android.view.WindowManagerPolicy.PointerEventListener;

/*
 * Listens for system-wide input gestures, firing callbacks when detected.
 * @hide
 */
public class SystemGesturesPointerEventListenerThumb implements PointerEventListener {
    private static final String TAG = "SystemGesturesOneHanded";
    private static final boolean DEBUG = false;
    private static final long SWIPE_TIMEOUT_MS_SIDEBAR = 500;

    private static final int SWIPE_NONE = 0;
    private static final int SWIPE_FROM_BOTTOM = 2;
    private static final int SWIPE_FROM_TOP_RIGHT_SIDEBAR = 3;
    private static final int SWIPE_FROM_TOP_LEFT_SIDEBAR = 4;
    private static final int SWIPE_FROM_TOP_STATUS_BAR = 5;
    private static final int PULL_STATUS_BAR_OFFSET = 50;

    private final Callbacks mCallbacks;
    private float mDownX;
    private float mDownY;
    private long mDownTime;

    private boolean mSwipeFireable;

    private boolean mSystemBooted;
    private int mThumbState;
    private float mInterceptSize;
    private int mThumbOffsetScaleHoriS = 0;
    private int mThumbOffsetScaleVertiS = 0;
    private int mScreenWidth = 0;
    private int mScreenHeight = 0;
    private Context mContext;

    private static final int GESTURE_INT_NEED_BIG_SIZE = 0;
    private static final int GESTURE_INT_TO_L_CORNER = 1;
    private static final int GESTURE_INT_TO_R_CORNER = 2;
    private static final int GESTURE_INT_TO_EXP_STATUSBAR = 3;
    private static final int GESTURE_INT_TO_RST_FROM_L_CORNER = 4;
    private static final int GESTURE_INT_TO_RST_FROM_R_CORNER = 5;

    private int mGestureIntent = GESTURE_INT_NEED_BIG_SIZE;
    private static int DETECT_GEST_L = 80;
    private static int DETECT_GEST_W = 70;
    private static int DETECT_GEST_W_H = 50;
    private static int FORCE_NOT_TOUCH_WIDTH = 160;
    private static final int SWIPE_TO_SIDEBAR_MODE_OFFSET = 80 * 80 * 2;

    private int getGestureIntent(float downX, float downY){
        if((mThumbState & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR) == 0){
            if((downX <= DETECT_GEST_L && downY <= DETECT_GEST_W) || (downX <= DETECT_GEST_W_H && downY <= DETECT_GEST_L+DETECT_GEST_W)){
                return GESTURE_INT_TO_R_CORNER;
            }else if((downX >= mScreenWidth - DETECT_GEST_L && downY <= DETECT_GEST_W)
                    || (downX >= mScreenWidth - DETECT_GEST_W_H && downY <= DETECT_GEST_L+DETECT_GEST_W)){
                return GESTURE_INT_TO_L_CORNER;
            }
        }else if((mThumbState & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_TYPE_L_CORNER_SIDEBAR) != 0){
            if(downX < mScreenWidth - mThumbOffsetScaleHoriS){
                if(downY < mThumbOffsetScaleVertiS){
                    return GESTURE_INT_TO_EXP_STATUSBAR;
                }else {
                    return GESTURE_INT_TO_RST_FROM_L_CORNER;
                }
            }
        }else if( (mThumbState & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_TYPE_R_CORNER_SIDEBAR) != 0 ){
            if(downX > mThumbOffsetScaleHoriS){
                if(downY < mThumbOffsetScaleVertiS){
                    return GESTURE_INT_TO_EXP_STATUSBAR;
                }else {
                    return GESTURE_INT_TO_RST_FROM_R_CORNER;
                }
            }
        }
        return GESTURE_INT_NEED_BIG_SIZE;
    }

    public SystemGesturesPointerEventListenerThumb(Context context, Callbacks callbacks) {
        mContext = context;
        mCallbacks = checkNull("callbacks", callbacks);
    }

    private static <T> T checkNull(String name, T arg) {
        if (arg == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return arg;
    }

    @Override
    public void onPointerEvent(MotionEvent event) {
        if(!mSystemBooted) return;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mSwipeFireable = true;
                captureDown(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mSwipeFireable) {
                    final int swipe = (event.getPointerCount() > 1) ? SWIPE_NONE : detectSwipe(event);
                    mSwipeFireable = swipe == SWIPE_NONE;
                    if (swipe == SWIPE_FROM_BOTTOM &&  (mThumbState & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR) != 0) {
                        if (DEBUG) Slog.d(TAG, "Firing onSwipeFromBottom");
                        mCallbacks.onSwipeFromBottom();
                    }else if (swipe == SWIPE_FROM_TOP_STATUS_BAR 
                            && (mThumbState & ViewRootImpl.BIT_THUMB_MODE_ENABLE_SIDEBAR) != 0
                            && (mThumbState & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR) != 0) {
                        if (DEBUG) Slog.d(TAG, "Firing onSwipeFromTopStatusBar");
                        mCallbacks.onSwipeFromTopStatusBar();
                    } else if (swipe == SWIPE_FROM_TOP_LEFT_SIDEBAR
                            && (mThumbState & ViewRootImpl.BIT_THUMB_MODE_ENABLE_SIDEBAR) != 0
                            && (mThumbState & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR) == 0) {
                        if (DEBUG) Slog.d(TAG, "Firing onSwipeFromTopLeft");
                        mCallbacks.onSwipeFromTopLeftSidebar();
                    } else if (swipe == SWIPE_FROM_TOP_RIGHT_SIDEBAR
                            && (mThumbState & ViewRootImpl.BIT_THUMB_MODE_ENABLE_SIDEBAR) != 0
                            && (mThumbState & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR) == 0) {
                        if (DEBUG) Slog.d(TAG, "Firing onSwipeFromTopRight");
                        mCallbacks.onSwipeFromTopRightSidebar();
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mSwipeFireable = false;
                mGestureIntent = GESTURE_INT_NEED_BIG_SIZE;
                break;
            default:
                if (DEBUG) Slog.d(TAG, "Ignoring " + event);
        }
    }

    private void captureDown(MotionEvent event) {
        mDownX = event.getX();
        mDownY = event.getY();
        mDownTime = event.getEventTime();
        mGestureIntent = getGestureIntent(mDownX, mDownY);
        if (DEBUG) Slog.d(TAG, "pointer " +
                " down x=" + mDownX + " y=" + mDownY + " Gesture intent " + mGestureIntent);
    }

    private int detectSwipe(MotionEvent move) {
        boolean sizebBig = move.getSize() > mInterceptSize;
        if(sizebBig == false && mGestureIntent == GESTURE_INT_NEED_BIG_SIZE){
            return SWIPE_NONE;
        }
        final int historySize = move.getHistorySize();
        for (int h = 0; h < historySize; h++) {
            final long time = move.getHistoricalEventTime(h);
            final float x = move.getHistoricalX(h);
            final float y = move.getHistoricalY(h);
            final int swipe = detectSwipe(sizebBig, time, x, y);
            if (swipe != SWIPE_NONE) {
                return swipe;
            }
        }
        final int swipe = detectSwipe(sizebBig, move.getEventTime(), move.getX(), move.getY());
        return swipe;
    }

    private int detectSwipe(boolean big, long time, float x, float y) {
        final float fromX = mDownX;
        final float fromY = mDownY;
        final long elapsed = time - mDownTime;
        if (DEBUG) Slog.d(TAG, "pointer "
                + " moved (" + fromX + "->" + x + "," + fromY + "->" + y + ") in " + elapsed);
        {
            if(elapsed < SWIPE_TIMEOUT_MS_SIDEBAR){
                if(mGestureIntent == GESTURE_INT_TO_EXP_STATUSBAR){
                    if((mThumbState & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_TYPE_R_CORNER_SIDEBAR) != 0
                            || (mThumbState & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_TYPE_L_CORNER_SIDEBAR) != 0){
                        if (fromY < mThumbOffsetScaleVertiS
                                && y > (mThumbOffsetScaleVertiS + PULL_STATUS_BAR_OFFSET)){
                            if (DEBUG) Slog.d(TAG, "SWIPE_FROM_TOP_STATUS_BAR");
                            return SWIPE_FROM_TOP_STATUS_BAR;
                        }
                    }
                }
                if(((y-fromY)*(y-fromY) + (x-fromX)*(x-fromX)) > SWIPE_TO_SIDEBAR_MODE_OFFSET){
                    if(mGestureIntent == GESTURE_INT_TO_L_CORNER){
                        if(fromX - x  > FORCE_NOT_TOUCH_WIDTH
                                && y - fromY > FORCE_NOT_TOUCH_WIDTH){
                            if (DEBUG) Slog.d(TAG, "SWIPE_FROM_TOP_LEFT_SIDEBAR");
                            return SWIPE_FROM_TOP_LEFT_SIDEBAR;
                        }
                    }else if(mGestureIntent == GESTURE_INT_TO_R_CORNER){
                        if(x - fromX > FORCE_NOT_TOUCH_WIDTH
                                && y - fromY > FORCE_NOT_TOUCH_WIDTH){
                            if (DEBUG) Slog.d(TAG, "SWIPE_FROM_TOP_RIGHT_SIDEBAR");
                            return SWIPE_FROM_TOP_RIGHT_SIDEBAR;
                        }
                    }else if(mGestureIntent == GESTURE_INT_TO_RST_FROM_L_CORNER){
                        if(x >  mScreenWidth - mThumbOffsetScaleHoriS && y < mThumbOffsetScaleVertiS
                                && x - fromX  > FORCE_NOT_TOUCH_WIDTH
                                && fromY - y  > FORCE_NOT_TOUCH_WIDTH
                                && (mThumbState & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR) != 0){
                            if (DEBUG) Slog.d(TAG, "SWIPE_FROM_BOTTOM");
                            return SWIPE_FROM_BOTTOM;
                        }
                    }else if(mGestureIntent == GESTURE_INT_TO_RST_FROM_R_CORNER){
                        if(x < mThumbOffsetScaleHoriS && y < mThumbOffsetScaleVertiS
                                && fromX - x  > FORCE_NOT_TOUCH_WIDTH
                                && fromY - y  > FORCE_NOT_TOUCH_WIDTH
                                && (mThumbState & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR) != 0){
                            if (DEBUG) Slog.d(TAG, "SWIPE_FROM_BOTTOM");
                            return SWIPE_FROM_BOTTOM;
                        }
                    }
                }
            }
        }

        if (DEBUG) Slog.d(TAG, "SWIPE_NONE");
        return SWIPE_NONE;
    }

    public void setThumbOffset(int scaleModeVertOffsetS, int scaleModeHoriOffsetS,
            int screenWidth, int screenHeight){
        mThumbOffsetScaleVertiS = scaleModeVertOffsetS;
        mThumbOffsetScaleHoriS = scaleModeHoriOffsetS;
        mScreenWidth = screenWidth;
        mScreenHeight = screenHeight;
    }

    interface Callbacks {
        void onSwipeFromBottom();
        void onSwipeFromTopLeftSidebar();
        void onSwipeFromTopRightSidebar();
        void onSwipeFromTopStatusBar();
    }
    public void setSystemBooted() {
        mSystemBooted = true;
    }

    public void setThumbState(int state) {
        mThumbState = state;
    }

    public void setThumbInterSize(float size){
        mInterceptSize = size;
    }

}
