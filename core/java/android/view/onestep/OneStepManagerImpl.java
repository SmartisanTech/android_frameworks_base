
package android.view.onestep;

import java.util.ArrayList;
import java.util.Iterator;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.os.SomeArgs;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Singleton;
import android.view.Display;

/**
 * @author smartisan
 * @hide
 */
public final class OneStepManagerImpl implements OneStepManager {
    @GuardedBy("mDelegates")
    private final ArrayList<OneStepStateObserverDelegate> mDelegates = new ArrayList<OneStepStateObserverDelegate>();

    public OneStepManagerImpl(Context context) {
    }

    private static final Singleton<IOneStepManager> sInstanse = new Singleton<IOneStepManager>() {
        protected IOneStepManager create() {
            IBinder b = ServiceManager.getService(Context.ONE_STEP_SERVICE);
            IOneStepManager oneStepManager = IOneStepManager.Stub.asInterface(b);
            return oneStepManager;
        }
    };

    /** {@hide} */
    private static class OneStepStateObserverDelegate extends IOneStepStateObserver.Stub implements
            Handler.Callback {
        private static final int MSG_ENTER = 1;
        private static final int MSG_EXIT = 2;

        final OneStepStateObserver mObserver;
        final Handler mHandler;

        public OneStepStateObserverDelegate(OneStepStateObserver observer, Looper looper) {
            mObserver = observer;
            mHandler = new Handler(looper, this);
        }

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ENTER: {
                    final SomeArgs args = (SomeArgs) msg.obj;
                    mObserver.onEnterOneStepMode(args.argi1);
                    args.recycle();
                    return true;
                }
                case MSG_EXIT: {
                    mObserver.onExitOneStepMode();
                    return true;
                }
            }
            return false;
        }

        @Override
        public void onEnterOneStepMode(int state) throws RemoteException {
            final SomeArgs args = SomeArgs.obtain();
            args.argi1 = state;
            mHandler.obtainMessage(MSG_ENTER, args).sendToTarget();
        }

        @Override
        public void onExitOneStepMode() throws RemoteException {
            mHandler.obtainMessage(MSG_EXIT).sendToTarget();
        }
    }

    @Override
    public void registerStateObserver(OneStepStateObserver observer, Handler handler) {
        synchronized (mDelegates) {
            final OneStepStateObserverDelegate delegate = new OneStepStateObserverDelegate(
                    observer, handler.getLooper());
            try {
                sInstanse.get().registerStateObserver(delegate);
            } catch (RemoteException e) {
                throw e.rethrowAsRuntimeException();
            }
            mDelegates.add(delegate);
        }
    }

    @Override
    public void unregisterStateObserver(OneStepStateObserver observer) {
        synchronized (mDelegates) {
            for (Iterator<OneStepStateObserverDelegate> i = mDelegates.iterator(); i.hasNext();) {
                final OneStepStateObserverDelegate delegate = i.next();
                if (delegate.mObserver == observer) {
                    try {
                        sInstanse.get().unregisterStateObserver(delegate);
                    } catch (RemoteException e) {
                        throw e.rethrowAsRuntimeException();
                    }
                    i.remove();
                }
            }
        }
    }

    @Override
    public void bindOneStepUI(IOneStep onestep) {
        try {
            sInstanse.get().bindOneStepUI(onestep);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    @Override
    public void resetWindow() {
        try {
            sInstanse.get().resetWindow();
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    @Override
    public boolean isInOneStepMode() {
        try {
            sInstanse.get().isInOneStepMode();
            ;
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
        return false;
    }

    @Override
    public int getOneStepModeState() {
        try {
            sInstanse.get().getOneStepModeState();
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
        return 0;
    }

    @Override
    public boolean isFocusedOnOneStep() {
        try {
            sInstanse.get().isFocusedOnOneStep();
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
        return false;
    }

    @Override
    public void requestEnterLastMode() {
        try {
            sInstanse.get().requestEnterLastMode();
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    @Override
    public void requestEnterOneStepMode(int mode) {
        try {
            sInstanse.get().requestEnterOneStepMode(mode);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    @Override
    public void requestExitOneStepMode() {
        try {
            sInstanse.get().requestExitOneStepMode();
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    @Override
    public void resumeOneStep() {
        try {
            sInstanse.get().resumeOneStep();
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    @Override
    public void updateOngoing(ComponentName name, int token, int pendingNumbers,
            CharSequence title, int pid) {
        try {
            sInstanse.get().updateOngoing(name, token, pendingNumbers, title, pid);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        try {
            sInstanse.get().setEnabled(enabled);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

}
