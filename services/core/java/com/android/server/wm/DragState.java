/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.server.wm;

import com.android.server.UiThread;
import com.android.server.input.InputApplicationHandle;
import com.android.server.input.InputWindowHandle;
import com.android.server.wm.WindowManagerService.DragInputEventReceiver;
import com.android.server.wm.WindowManagerService.H;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipDescription;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.util.Slog;
import android.view.Display;
import android.view.DragEvent;
import android.view.InputChannel;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewRootImpl;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.ArrayList;

/**
 * Drag/drop state
 */
class DragState {
    final WindowManagerService mService;
    IBinder mToken;
    SurfaceControl mSurfaceControl;
    int mFlags;
    IBinder mLocalWin;
    ClipData mData;
    ClipDescription mDataDescription;
    boolean mDragResult;
    float mCurrentX, mCurrentY;
    float mThumbOffsetX, mThumbOffsetY;
    InputChannel mServerChannel, mClientChannel;
    DragInputEventReceiver mInputEventReceiver;
    InputApplicationHandle mDragApplicationHandle;
    InputWindowHandle mDragWindowHandle;
    WindowState mTargetWindow;
    ArrayList<WindowState> mNotifiedWindows;
    boolean mDragInProgress;
    Display mDisplay;
    private final Region mTmpRegion = new Region();

    float mDelX, mDelY;
    int mShowAnimDelay;

    private final Rect mTmpRect = new Rect();

    DragState(WindowManagerService service, IBinder token, SurfaceControl surface,
            int w, int h, int flags, IBinder localWin, float delX, float delY, int showAnimDelay) {
        mService = service;
        mToken = token;
        mSurfaceControl = surface;
        mFlags = flags;
        mLocalWin = localWin;
        mNotifiedWindows = new ArrayList<WindowState>();
        mDelX = delX;
        mDelY = delY;
        mShowAnimDelay = showAnimDelay;
        mWidth = w;
        mHeight = h;
    }

    void dismissAndReset(){
        UiThread.getHandler().removeCallbacks(mOnDragAnimRun);
        UiThread.getHandler().removeCallbacks(mOnDismissAnimRun);
        UiThread.getHandler().removeCallbacks(mOnDismissAnimAlphaRun);
        if(!UiThread.getHandler().hasCallbacks(mOnDragAnimRun)) {
            if (mDragResult) {
                UiThread.getHandler().post(mOnDismissAnimRun);
            } else {
                UiThread.getHandler().post(mOnDismissAnimAlphaRun);
            }
        }
        mService.mH.removeMessages(H.DRAG_STATE_RESET);
        Message msg = mService.mH.obtainMessage(H.DRAG_STATE_RESET);
        mService.mH.sendMessageDelayed(msg, 300);
    }

    void removeAnimRun(){
        UiThread.getHandler().removeCallbacks(mOnDragAnimRun);
        UiThread.getHandler().removeCallbacks(mOnDismissAnimRun);
        UiThread.getHandler().removeCallbacks(mOnDismissAnimAlphaRun);
    }

    void reset() {
        if (mSurfaceControl != null) {
            mSurfaceControl.destroy();
        }
        mSurfaceControl = null;
        mFlags = 0;
        mLocalWin = null;
        mToken = null;
        mData = null;
        mThumbOffsetX = mThumbOffsetY = 0;
        mWidth = mHeight = 0;
        mTouchX = mTouchY = 0;
        mOffsetMtrX = mOffsetMtrY = 0;
        mNotifiedWindows = null;
    }

    /**
     * @param display The Display that the window being dragged is on.
     */
    void register(Display display) {
        mDisplay = display;
        if (WindowManagerService.DEBUG_DRAG) Slog.d(WindowManagerService.TAG, "registering drag input channel");
        if (mClientChannel != null) {
            Slog.e(WindowManagerService.TAG, "Duplicate register of drag input channel");
        } else {
            InputChannel[] channels = InputChannel.openInputChannelPair("drag");
            mServerChannel = channels[0];
            mClientChannel = channels[1];
            mService.mInputManager.registerInputChannel(mServerChannel, null);
            mInputEventReceiver = mService.new DragInputEventReceiver(mClientChannel,
                    mService.mH.getLooper());

            mDragApplicationHandle = new InputApplicationHandle(null);
            mDragApplicationHandle.name = "drag";
            mDragApplicationHandle.dispatchingTimeoutNanos =
                    WindowManagerService.DEFAULT_INPUT_DISPATCHING_TIMEOUT_NANOS;

            mDragWindowHandle = new InputWindowHandle(mDragApplicationHandle, null,
                    mDisplay.getDisplayId());
            mDragWindowHandle.name = "drag";
            mDragWindowHandle.inputChannel = mServerChannel;
            mDragWindowHandle.layer = getDragLayerLw();
            mDragWindowHandle.layoutParamsFlags = 0;
            mDragWindowHandle.layoutParamsType = WindowManager.LayoutParams.TYPE_DRAG;
            mDragWindowHandle.dispatchingTimeoutNanos =
                    WindowManagerService.DEFAULT_INPUT_DISPATCHING_TIMEOUT_NANOS;
            mDragWindowHandle.visible = true;
            mDragWindowHandle.canReceiveKeys = false;
            mDragWindowHandle.hasFocus = true;
            mDragWindowHandle.hasWallpaper = false;
            mDragWindowHandle.paused = false;
            mDragWindowHandle.ownerPid = Process.myPid();
            mDragWindowHandle.ownerUid = Process.myUid();
            mDragWindowHandle.inputFeatures = 0;
            mDragWindowHandle.scaleFactor = 1.0f;
            mDragWindowHandle.inThumbMode = false;

            // The drag window cannot receive new touches.
            mDragWindowHandle.touchableRegion.setEmpty();

            // The drag window covers the entire display
            mDragWindowHandle.frameLeft = 0;
            mDragWindowHandle.frameTop = 0;
            Point p = new Point();
            mDisplay.getRealSize(p);
            mDragWindowHandle.frameRight = p.x;
            mDragWindowHandle.frameBottom = p.y;

            // Pause rotations before a drag.
            if (WindowManagerService.DEBUG_ORIENTATION) {
                Slog.d(WindowManagerService.TAG, "Pausing rotation during drag");
            }
            mService.pauseRotationLocked();
        }
    }

    void unregister() {
        if (WindowManagerService.DEBUG_DRAG) Slog.d(WindowManagerService.TAG, "unregistering drag input channel");
        if (mClientChannel == null) {
            Slog.e(WindowManagerService.TAG, "Unregister of nonexistent drag input channel");
        } else {
            mService.mInputManager.unregisterInputChannel(mServerChannel);
            mInputEventReceiver.dispose();
            mInputEventReceiver = null;
            mClientChannel.dispose();
            mServerChannel.dispose();
            mClientChannel = null;
            mServerChannel = null;

            mDragWindowHandle = null;
            mDragApplicationHandle = null;

            // Resume rotations after a drag.
            if (WindowManagerService.DEBUG_ORIENTATION) {
                Slog.d(WindowManagerService.TAG, "Resuming rotation after drag");
            }
            mService.resumeRotationLocked();
        }
    }

    int getDragLayerLw() {
        return mService.mPolicy.windowTypeToLayerLw(WindowManager.LayoutParams.TYPE_DRAG)
                * WindowManagerService.TYPE_LAYER_MULTIPLIER
                + WindowManagerService.TYPE_LAYER_OFFSET;
    }

    /* call out to each visible window/session informing it about the drag
     */
    void broadcastDragStartedLw(float touchX, float touchY) {
        touchX += mDelX;
        touchY += mDelY;
        // Cache a base-class instance of the clip metadata so that parceling
        // works correctly in calling out to the apps.
        mDataDescription = (mData != null) ? mData.getDescription() : null;
        mNotifiedWindows.clear();
        mDragInProgress = true;

        if (WindowManagerService.DEBUG_DRAG) {
            Slog.d(WindowManagerService.TAG, "broadcasting DRAG_STARTED at (" + touchX + ", " + touchY + ")");
        }

        final WindowList windows = mService.getWindowListLocked(mDisplay);
        if (windows != null) {
            final int N = windows.size();
            for (int i = 0; i < N; i++) {
                sendDragStartedLw(windows.get(i), touchX, touchY, mDataDescription);
            }
        }
    }

    /* helper - send a caller-provided event, presumed to be DRAG_STARTED, if the
     * designated window is potentially a drop recipient.  There are race situations
     * around DRAG_ENDED broadcast, so we make sure that once we've declared that
     * the drag has ended, we never send out another DRAG_STARTED for this drag action.
     *
     * This method clones the 'event' parameter if it's being delivered to the same
     * process, so it's safe for the caller to call recycle() on the event afterwards.
     */
    private void sendDragStartedLw(WindowState newWin, float touchX, float touchY,
            ClipDescription desc) {
        touchX += mDelX;
        touchY += mDelY;
        // Don't actually send the event if the drag is supposed to be pinned
        // to the originating window but 'newWin' is not that window.
        if ((mFlags & View.DRAG_FLAG_GLOBAL) == 0) {
            final IBinder winBinder = newWin.mClient.asBinder();
            if (winBinder != mLocalWin) {
                if (WindowManagerService.DEBUG_DRAG) {
                    Slog.d(WindowManagerService.TAG, "Not dispatching local DRAG_STARTED to " + newWin);
                }
                return;
            }
        }

        if (mDragInProgress && newWin.isPotentialDragTarget()) {
            DragEvent event = obtainDragEvent(newWin, DragEvent.ACTION_DRAG_STARTED,
                    touchX, touchY, null, desc, null, false);
            try {
                newWin.mClient.dispatchDragEvent(event);
                // track each window that we've notified that the drag is starting
                mNotifiedWindows.add(newWin);
            } catch (RemoteException e) {
                Slog.w(WindowManagerService.TAG, "Unable to drag-start window " + newWin);
            } finally {
                // if the callee was local, the dispatch has already recycled the event
                if (Process.myPid() != newWin.mSession.mPid) {
                    event.recycle();
                }
            }
        }
    }

    /* helper - construct and send a DRAG_STARTED event only if the window has not
     * previously been notified, i.e. it became visible after the drag operation
     * was begun.  This is a rare case.
     */
    void sendDragStartedIfNeededLw(WindowState newWin) {
        if (mDragInProgress) {
            // If we have sent the drag-started, we needn't do so again
            for (WindowState ws : mNotifiedWindows) {
                if (ws == newWin) {
                    return;
                }
            }
            if (WindowManagerService.DEBUG_DRAG) {
                Slog.d(WindowManagerService.TAG, "need to send DRAG_STARTED to new window " + newWin);
            }
            sendDragStartedLw(newWin, mCurrentX, mCurrentY, mDataDescription);
        }
    }

    void broadcastDragEndedLw() {
        if (WindowManagerService.DEBUG_DRAG) {
            Slog.d(WindowManagerService.TAG, "broadcasting DRAG_ENDED");
        }
        DragEvent evt = DragEvent.obtain(DragEvent.ACTION_DRAG_ENDED,
                0, 0, null, null, null, mDragResult);
        for (WindowState ws: mNotifiedWindows) {
            try {
                if (Process.myPid() == ws.mSession.mPid) {
                    ws.mClient.dispatchDragEvent(DragEvent.obtain(evt));
                } else {
                    ws.mClient.dispatchDragEvent(evt);
                }
            } catch (RemoteException e) {
                Slog.w(WindowManagerService.TAG, "Unable to drag-end window " + ws);
            }
        }
        mNotifiedWindows.clear();
        mDragInProgress = false;
        evt.recycle();
    }

    void endDragLw() {
        mService.mDragState.broadcastDragEndedLw();

        // stop intercepting input
        mService.mDragState.unregister();
        mService.mInputMonitor.updateInputWindowsLw(true /*force*/);

        // free our resources and drop all the object references
        mService.mDragState.dismissAndReset();
    }

    void handleReset(){
        if(mService.mDragState != null){
            mService.mDragState.removeAnimRun();
            mService.mDragState.reset();
            mService.mDragState = null;
        }
    }

    void notifyMoveLw(float x, float y) {
        final int myPid = Process.myPid();

        // Move the surface to the given touch
        if (WindowManagerService.SHOW_LIGHT_TRANSACTIONS) Slog.i(
                WindowManagerService.TAG, ">>> OPEN TRANSACTION notifyMoveLw");
        SurfaceControl.openTransaction();
        try {
            setPosition(x, y);
        } finally {
            SurfaceControl.closeTransaction();
            if (WindowManagerService.SHOW_LIGHT_TRANSACTIONS) Slog.i(
                    WindowManagerService.TAG, "<<< CLOSE TRANSACTION notifyMoveLw");
        }

        x += mDelX;
        y += mDelY;

        // Tell the affected window
        WindowState touchedWin = getTouchedWinAtPointLw(x, y);
        if (touchedWin == null) {
            if (WindowManagerService.DEBUG_DRAG) Slog.d(WindowManagerService.TAG, "No touched win at x=" + x + " y=" + y);
            return;
        }
        if ((mFlags & View.DRAG_FLAG_GLOBAL) == 0) {
            final IBinder touchedBinder = touchedWin.mClient.asBinder();
            if (touchedBinder != mLocalWin) {
                // This drag is pinned only to the originating window, but the drag
                // point is outside that window.  Pretend it's over empty space.
                touchedWin = null;
            }
        }
        try {
            // have we dragged over a new window?
            if ((touchedWin != mTargetWindow) && (mTargetWindow != null)) {
                if (WindowManagerService.DEBUG_DRAG) {
                    Slog.d(WindowManagerService.TAG, "sending DRAG_EXITED to " + mTargetWindow);
                }
                // force DRAG_EXITED_EVENT if appropriate
                DragEvent evt = obtainDragEvent(mTargetWindow, DragEvent.ACTION_DRAG_EXITED,
                        x, y, null, null, null, false);
                mTargetWindow.mClient.dispatchDragEvent(evt);
                if (myPid != mTargetWindow.mSession.mPid) {
                    evt.recycle();
                }
            }
            if (touchedWin != null) {
                if (false && WindowManagerService.DEBUG_DRAG) {
                    Slog.d(WindowManagerService.TAG, "sending DRAG_LOCATION to " + touchedWin);
                }
                DragEvent evt = obtainDragEvent(touchedWin, DragEvent.ACTION_DRAG_LOCATION,
                        x, y, null, null, null, false);
                touchedWin.mClient.dispatchDragEvent(evt);
                if (myPid != touchedWin.mSession.mPid) {
                    evt.recycle();
                }
            }
        } catch (RemoteException e) {
            Slog.w(WindowManagerService.TAG, "can't send drag notification to windows");
        }
        mTargetWindow = touchedWin;
    }

    // Tell the drop target about the data.  Returns 'true' if we can immediately
    // dispatch the global drag-ended message, 'false' if we need to wait for a
    // result from the recipient.
    boolean notifyDropLw(float x, float y) {
        if (inWindowBorder(x, y)) {
            Slog.w(WindowManagerService.TAG,
                    "touch point is ("
                            + x
                            + ","
                            + y
                            + "), "
                            + "too border! ignore it, immediately dispatch the global drag-ended message");
            return true;
        }

        setPosition(x, y);
        x += mDelX;
        y += mDelY;
        WindowState touchedWin = getTouchedWinAtPointLw(x, y);
        if (touchedWin == null) {
            // "drop" outside a valid window -- no recipient to apply a
            // timeout to, and we can send the drag-ended message immediately.
            mDragResult = false;
            return true;
        }

        if (WindowManagerService.DEBUG_DRAG) {
            Slog.d(WindowManagerService.TAG, "sending DROP to " + touchedWin);
        }
        final int myPid = Process.myPid();
        final IBinder token = touchedWin.mClient.asBinder();

        DragEvent evt = obtainDragEvent(touchedWin, DragEvent.ACTION_DROP, x, y,
                null, null, mData, false);
        try {
            touchedWin.mClient.dispatchDragEvent(evt);

            // 5 second timeout for this window to respond to the drop
            mService.mH.removeMessages(H.DRAG_END_TIMEOUT, token);
            Message msg = mService.mH.obtainMessage(H.DRAG_END_TIMEOUT, token);
            mService.mH.sendMessageDelayed(msg, 5000);
        } catch (RemoteException e) {
            Slog.w(WindowManagerService.TAG, "can't send drop notification to win " + touchedWin);
            return true;
        } finally {
            if (myPid != touchedWin.mSession.mPid) {
                evt.recycle();
            }
        }
        mToken = token;
        return false;
    }

    // Find the visible, touch-deliverable window under the given point
    private WindowState getTouchedWinAtPointLw(float xf, float yf) {
        WindowState touchedWin = null;
        final int x = (int) xf;
        final int y = (int) yf;

        final WindowList windows = mService.getWindowListLocked(mDisplay);
        if (windows == null) {
            return null;
        }
        final int N = windows.size();
        for (int i = N - 1; i >= 0; i--) {
            WindowState child = windows.get(i);
            final int flags = child.mAttrs.flags;
            if (!child.isVisibleLw()) {
                // not visible == don't tell about drags
                continue;
            }
            if ((flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
                // not touchable == don't tell about drags
                continue;
            }

            child.getStackBounds(mTmpRect);
            if (!mTmpRect.contains(x, y)) {
                // outside of this window's activity stack == don't tell about drags
                continue;
            }

            child.getTouchableRegion(mTmpRegion);

            final int touchFlags = flags &
                    (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
            if (mTmpRegion.contains(x, y) || touchFlags == 0) {
                // Found it
                touchedWin = child;
                break;
            }
        }

        return touchedWin;
    }

    private DragEvent obtainDragEvent(WindowState win, int action,
            float x, float y, Object localState,
            ClipDescription description, ClipData data, boolean result) {
        float winX = x;
        float winY = y;
        if(win.isWinInThumbMode()){
            if((ThumbModeHelper.getInstance().getThumbStates() & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_TYPE_L_CORNER_SIDEBAR) != 0){
                winX = winX / mService.mScaleThumbScaleS;
                winX = winX - win.mLeftBase;
                winY = winY - mService.mScaleThumbYOffsetS;
                winY = winY / mService.mScaleThumbScaleS;
                winY = winY - win.mTopBase;
            }else if((ThumbModeHelper.getInstance().getThumbStates() & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_TYPE_R_CORNER_SIDEBAR) != 0){
                winX = (winX - mService.mScaleThumbXOffsetS) / mService.mScaleThumbScaleS;
                winX = winX - win.mLeftBase;;
                winY = winY - mService.mScaleThumbYOffsetS;
                winY = winY / mService.mScaleThumbScaleS;
                winY = winY - win.mTopBase;
            }
        }else{
            winX = winX - win.mLeftBase;
            winY = winY - win.mTopBase;
            if (win.mEnforceSizeCompat) {
                winX *= win.mGlobalScale;
                winY *= win.mGlobalScale;
            }
        }
        return DragEvent.obtain(action, winX, winY, localState, description, data, result);
    }

    int mWidth, mHeight;
    private float mTouchX, mTouchY;
    float mOffsetMtrX, mOffsetMtrY;
    public static float MAX_SCALE = 1.4f;
    public static float MIN_SCALE = 0.3f;
    public static float SCALE_AX_PER = 0.5f;
    public static float SCALE_AY_PER = 0.5f;

    public void setPosition(float touchX, float touchY) {
        if(mSurfaceControl != null){
            mTouchX = touchX;
            mTouchY = touchY;
            mSurfaceControl.setPosition(mTouchX - mThumbOffsetX + mOffsetMtrX, mTouchY - mThumbOffsetY + mOffsetMtrY);
            if (WindowManagerService.SHOW_TRANSACTIONS) Slog.i(WindowManagerService.TAG, "  DRAG "
                    + mSurfaceControl + ": pos=(" +
                    (int)(mTouchX - mThumbOffsetX + mOffsetMtrX) + ","
                            + (int)(mTouchY - mThumbOffsetY + mOffsetMtrY) + ")");
        }
    }

    public void setMatrix(float dsdx, float dtdx, float dsdy, float dtdy) {
        if(mSurfaceControl != null){
            mSurfaceControl.setMatrix(dsdx, dtdx, dsdy, dtdy);
        }
    }

    public void scaleSurface(float xScale, float yScale, float aXPer, float aYPer) {
        scaleSurface(xScale, yScale, aXPer, aYPer, false);
    }

    public void scaleSurface(float xScale, float yScale, float aXPer, float aYPer, boolean skew) {
        final float[] mTmpFloats = new float[9];
        Matrix matrix = new Matrix();
        if(skew){
            matrix.postRotate(45.f);
            matrix.postSkew(1 - xScale, 1 - yScale, aXPer*mWidth, aYPer*mHeight);
            matrix.postRotate(-45.f);
            matrix.postScale(xScale * 0.7f, yScale, aXPer*mWidth, aYPer*mHeight*(1+yScale));
        }else {
            matrix.postScale(xScale, yScale, aXPer*mWidth, aYPer*mHeight);
        }
        matrix.getValues(mTmpFloats);
        mOffsetMtrX = mTmpFloats[Matrix.MTRANS_X];
        mOffsetMtrY = mTmpFloats[Matrix.MTRANS_Y];
        setPosition(mTouchX, mTouchY);
        setMatrix(mTmpFloats[Matrix.MSCALE_X], mTmpFloats[Matrix.MSKEW_Y],
                    mTmpFloats[Matrix.MSKEW_X], mTmpFloats[Matrix.MSCALE_Y]);
    }

    public void startOnDragAnim() {
        UiThread.getHandler().removeCallbacks(mOnDragAnimRun);
        UiThread.getHandler().postDelayed(mOnDragAnimRun, mShowAnimDelay);
    }

    private Runnable mOnDismissAnimAlphaRun = new Runnable() {
        @Override
        public void run() {
            final ValueAnimator animator = ValueAnimator.ofFloat(1, 0);
            final ValueAnimator.AnimatorListener animEventListener = new ValueAnimator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {}

                @Override
                public void onAnimationRepeat(Animator animation) {}

                @Override
                public void onAnimationEnd(Animator animation) {
                    if(animator != null){
                        animator.removeAllUpdateListeners();
                        animator.removeAllListeners();
                    }
                    handleReset();
                }
                @Override
                public void onAnimationCancel(Animator animation){
                    if(animator != null){
                        animator.removeAllUpdateListeners();
                        animator.removeAllListeners();
                    }
                    handleReset();
                }
            };
            final ValueAnimator.AnimatorUpdateListener updateListener = new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float currentAnimVal = (Float) animation.getAnimatedValue();
                    SurfaceControl.openTransaction();
                    try {
                        if(mSurfaceControl != null){
                            mSurfaceControl.setAlpha(currentAnimVal);
                        }
                    } finally {
                        SurfaceControl.closeTransaction();
                    }
                }
            };
            animator.setDuration(200);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(updateListener);
            animator.addListener(animEventListener);
            animator.start();
        }
    };

    private Runnable mOnDismissAnimRun = new Runnable() {
        @Override
        public void run() {
            final ValueAnimator animator = ValueAnimator.ofFloat(1, 0);
            final ValueAnimator.AnimatorListener animEventListener = new ValueAnimator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {}

                @Override
                public void onAnimationRepeat(Animator animation) {}

                @Override
                public void onAnimationEnd(Animator animation) {
                    if(animator != null){
                        animator.removeAllUpdateListeners();
                        animator.removeAllListeners();
                    }
                    handleReset();
                }
                @Override
                public void onAnimationCancel(Animator animation){
                    if(animator != null){
                        animator.removeAllUpdateListeners();
                        animator.removeAllListeners();
                    }
                    handleReset();
                }
            };
            final ValueAnimator.AnimatorUpdateListener updateListener = new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float currentAnimVal = (Float) animation.getAnimatedValue();
                    float alpha = (currentAnimVal*(1-MIN_SCALE)) + MIN_SCALE;
                    float scaleX = currentAnimVal;
                    float scaleY = currentAnimVal;
                    float scaleApX = (mThumbOffsetX + mDelX) / mWidth;
                    float scaleApY = (mThumbOffsetY + mDelY) / mHeight;
                    SurfaceControl.openTransaction();
                    try {
                        scaleSurface(scaleX, scaleY, scaleApX, scaleApY, true);
                        if(mSurfaceControl != null){
                            mSurfaceControl.setAlpha(alpha);
                        }
                    } finally {
                        SurfaceControl.closeTransaction();
                    }
                }
            };
            animator.setDuration(200);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(updateListener);
            animator.addListener(animEventListener);
            animator.start();
        }
    };

    private Runnable mOnDragAnimRun = new Runnable() {
        @Override
        public void run() {
            final ValueAnimator animator = ValueAnimator.ofFloat(MAX_SCALE, 1);
            final ValueAnimator.AnimatorListener animEventListener = new ValueAnimator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {}

                @Override
                public void onAnimationRepeat(Animator animation) {}

                @Override
                public void onAnimationEnd(Animator animation) {
                    if(animator != null){
                        animator.removeAllUpdateListeners();
                        animator.removeAllListeners();
                    }
                }
                @Override
                public void onAnimationCancel(Animator animation){
                    if(animator != null){
                        animator.removeAllUpdateListeners();
                        animator.removeAllListeners();
                    }
                }
            };
            final ValueAnimator.AnimatorUpdateListener updateListener = new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float currentAnimVal = (Float) animation.getAnimatedValue();
                    float scaleX = currentAnimVal; // ((currentAnimVal - 1) * 0.7f) + 1;
                    float scaleY = currentAnimVal;
                    SurfaceControl.openTransaction();
                    try {
                        scaleSurface(scaleX, scaleY, SCALE_AX_PER, SCALE_AY_PER);
                    } finally {
                        SurfaceControl.closeTransaction();
                    }
                }
            };
            SurfaceControl.openTransaction();
            try {
                if (mSurfaceControl != null) {
                    mSurfaceControl.show();
                }
            } finally {
                SurfaceControl.closeTransaction();
            }
            animator.setDuration(200);
            animator.setInterpolator(new OvershootInterpolator());
            animator.addUpdateListener(updateListener);
            animator.addListener(animEventListener);
            animator.start();
        }
    };

    private boolean inWindowBorder(float x, float y) {
        Point pt = new Point();
        mService.getDefaultDisplayContentLocked().getDisplay().getSize(pt);
        int width = pt.x;
        int height = pt.y;
        float delWidth = width / 100.0f;
        float delHeight = height / 100.0f;
        if (x < delWidth || x > width - delWidth || y < delHeight
                || y > height - delHeight) {
            return true;
        }
        return false;
    }
}