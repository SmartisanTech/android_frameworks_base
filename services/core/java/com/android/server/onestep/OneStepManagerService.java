/**
 * Copyright (c) 2016, The Smartisan Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package com.android.server.onestep;

import android.content.ComponentName;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.RemoteCallbackList;
import android.os.SystemProperties;
import android.util.Slog;
import android.util.SparseIntArray;
import android.view.ViewRootImpl;
import android.view.WindowManagerPolicy;
import android.view.onestep.IOneStepManager;
import android.view.onestep.IOneStepStateObserver;
import android.view.onestep.IOneStep;
import android.view.onestep.OneStepManager;

import com.android.internal.os.SomeArgs;
import com.android.server.FgThread;
import com.android.server.wm.ThumbModeHelper;
import com.android.server.wm.ThumbModeHelper.OnThumbEventsCallbacks;
import com.android.server.wm.WindowManagerService;

public class OneStepManagerService extends IOneStepManager.Stub {

    public static final String TAG = "OneStepManagerService";
    public static final boolean DEBUG = SystemProperties.getInt("ro.debuggable", 0) == 1;

    private Context mContext;
    private WindowManagerService mWindowService;
    private volatile IOneStep mBar;
    private ThumbModeHelper mThumbModeHelper;
    private StateCallbacks mStateCallbacks;
    private int mSideBarMode = OneStepManager.BIT_SIDEBAR_IN_NONE_MODE;
    private int mLastResetMode = OneStepManager.BIT_SIDEBAR_IN_NONE_MODE;

    /**
     * Construct the service, add the status bar view to the window manager
     */
    public OneStepManagerService(Context context, WindowManagerService windowManager) {
        mContext = context;
        mWindowService = windowManager;
        mThumbModeHelper = ThumbModeHelper.getInstance();
        mStateCallbacks = new StateCallbacks(FgThread.get().getLooper());

        mThumbModeHelper.setThumbEventsListener(new OnThumbEventsCallbacks() {
            @Override
            public void onEnterSidebarMode(final int state) {
                Slog.d(TAG, "onEnterSidebarMode" + state);
                if ((state & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_TYPE_L_CORNER_SIDEBAR) != 0) {
                    mSideBarMode = OneStepManager.BIT_SIDEBAR_IN_RIGHT_TOP_MODE;
                } else if ((state & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_TYPE_R_CORNER_SIDEBAR) != 0) {
                    mSideBarMode = OneStepManager.BIT_SIDEBAR_IN_LEFT_TOP_MODE;
                } else {
                    return;
                }
                if (mStateCallbacks != null) {
                    mStateCallbacks.notifyEnterState(mSideBarMode);
                }
            }

            @Override
            public void onExitSidebarMode() {
                Slog.d(TAG, "onExitSidebarMode" + mSideBarMode);
                mSideBarMode = OneStepManager.BIT_SIDEBAR_IN_NONE_MODE;
                if (mStateCallbacks != null) {
                    mStateCallbacks.notifyExitState();
                }
            }
        });

    }

    @Override
    public void bindOneStepUI(IOneStep bar) {
        enforceSidebarService();
        Slog.d(TAG, "[Uid: " + Binder.getCallingUid() + ", Pid: " + Binder.getCallingPid()
                + "] bindOneStepUI bar=" + bar);
        mBar = bar;
    }

    @Override
    public void resetWindow() {
        enforceSidebarService();
        Slog.d(TAG, "[Uid: " + Binder.getCallingUid() + ", Pid: " + Binder.getCallingPid()
                + "] resetWindow");
        if (isInOneStepMode()) {
            if (mThumbModeHelper != null) {
                mThumbModeHelper.resetWindowState();
            }
        } else {
            Slog.w(TAG, "Not in sidebar mode, no need to resetWindow");
        }
    }

    @Override
    public boolean isInOneStepMode() {
        Slog.w(TAG, "isInOneStepMode: " + mSideBarMode);
        return mSideBarMode != OneStepManager.BIT_SIDEBAR_IN_NONE_MODE;
    }

    @Override
    public void requestEnterOneStepMode(int mode) {
        enforceSidebarService();
        Slog.d(TAG, "[Uid: " + Binder.getCallingUid() + ", Pid: " + Binder.getCallingPid()
                + "] requestEnterSidebarMode");

        if (mode != OneStepManager.BIT_SIDEBAR_IN_LEFT_TOP_MODE
                && mode != OneStepManager.BIT_SIDEBAR_IN_RIGHT_TOP_MODE) {
            Slog.w(TAG, "requestEnterSidebarMode request code error: " + mode);
            return;
        }
        if (isInOneStepMode()) {
            Slog.w(TAG, "Already in sidebar mode, do nothing");
        } else {
            if (mThumbModeHelper != null) {
                mThumbModeHelper
                        .requestTraversalToThumbMode(mode == OneStepManager.BIT_SIDEBAR_IN_LEFT_TOP_MODE ? android.view.WindowManagerPolicy.ThumbModeFuncs.ACTION_FROM_TOP_RIGHT_PULL_DOWN_SIDEBAR
                                : android.view.WindowManagerPolicy.ThumbModeFuncs.ACTION_FROM_TOP_LEFT_PULL_DOWN_SIDEBAR);
            }
        }
    }

    @Override
    public void requestExitOneStepMode() {
        enforceSidebarService();
        Slog.d(TAG, "[Uid: " + Binder.getCallingUid() + ", Pid: " + Binder.getCallingPid()
                + "] requestExitSidebarMode");

        if (isInOneStepMode()) {
            Slog.w(TAG, "Not in sidebar mode, no need to do ExitSidebarMode");
        } else {
            mLastResetMode = mSideBarMode;
            if (mThumbModeHelper != null) {
                mThumbModeHelper.resetWindowState();
            }
        }
    }

    @Override
    public void requestEnterLastMode() {
        enforceSidebarService();
        Slog.d(TAG, "[Uid: " + Binder.getCallingUid() + ", Pid: " + Binder.getCallingPid()
                + "] requestEnterLastMode");
        if (isInOneStepMode()) {
            Slog.w(TAG, "Already in sidebar mode, do nothing");
        } else {
            if (mThumbModeHelper != null && mLastResetMode != OneStepManager.BIT_SIDEBAR_IN_NONE_MODE) {
                mThumbModeHelper
                        .requestTraversalToThumbMode(mLastResetMode == OneStepManager.BIT_SIDEBAR_IN_LEFT_TOP_MODE ? android.view.WindowManagerPolicy.ThumbModeFuncs.ACTION_FROM_TOP_RIGHT_PULL_DOWN_SIDEBAR
                                : android.view.WindowManagerPolicy.ThumbModeFuncs.ACTION_FROM_TOP_LEFT_PULL_DOWN_SIDEBAR);
            }
        }
    }

    @Override
    public boolean isFocusedOnOneStep() {
        if (mThumbModeHelper != null) {
            return mThumbModeHelper.isSidebarHasFocus();
        }
        return false;
    }

    @Override
    public int getOneStepModeState() {
        return mSideBarMode;
    }

    private void enforceSidebarService() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.ONE_STEP_SERVICE,
                "SideBarManagerService");
    }

    @Override
    public void resumeOneStep() {
        if (!isInOneStepMode()) {
            return;
        }
        if (mBar != null) {
            try {
                if (DEBUG) {
                    Slog.i(TAG, "resumeSidebar ");
                }
                mBar.resumeOneStep();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void updateOngoing(ComponentName name, int token, int pendingNumbers,
            CharSequence title, int pid) throws RemoteException {
        if (mBar != null) {
            try {
                if (DEBUG) {
                    Slog.i(TAG, "updateOngoing ");
                }
                mBar.updateOngoing(name, token, pendingNumbers, title, pid);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (mBar != null) {
            try {
                if (DEBUG) {
                    Slog.i(TAG, "setEnabled " + enabled);
                }
                mBar.setEnabled(enabled);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void registerStateObserver(IOneStepStateObserver observer) throws RemoteException {
        mStateCallbacks.register(observer);
    }

    @Override
    public void unregisterStateObserver(IOneStepStateObserver observer) throws RemoteException {
        mStateCallbacks.unregister(observer);
    }

    private static class StateCallbacks extends Handler {
        private static final int MSG_ENTER = 1;
        private static final int MSG_EXIT = 2;

        private final RemoteCallbackList<IOneStepStateObserver> mCallbacks = new RemoteCallbackList<IOneStepStateObserver>();

        public StateCallbacks(Looper looper) {
            super(looper);
        }

        public void register(IOneStepStateObserver callback) {
            mCallbacks.register(callback);
        }

        public void unregister(IOneStepStateObserver callback) {
            mCallbacks.unregister(callback);
        }

        @Override
        public void handleMessage(Message msg) {
            final SomeArgs args = (SomeArgs) msg.obj;
            final int n = mCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                final IOneStepStateObserver callback = mCallbacks.getBroadcastItem(i);
                try {
                    invokeCallback(callback, msg.what, args);
                } catch (RemoteException ignored) {
                }
            }
            mCallbacks.finishBroadcast();
            if (args != null) {
                args.recycle();
            }
        }

        private void invokeCallback(IOneStepStateObserver callback, int what, SomeArgs args)
                throws RemoteException {
            switch (what) {
                case MSG_ENTER: {
                    callback.onEnterOneStepMode(args.argi1);
                    break;
                }
                case MSG_EXIT: {
                    callback.onExitOneStepMode();
                    break;
                }
            }
        }

        private void notifyEnterState(int state) {
            final SomeArgs args = SomeArgs.obtain();
            args.argi1 = state;
            obtainMessage(MSG_ENTER, args).sendToTarget();
        }

        private void notifyExitState() {
            obtainMessage(MSG_EXIT).sendToTarget();
        }

    }
}
