
package android.view.onestep;

import android.content.ComponentName;
import android.os.Handler;
import android.view.onestep.IOneStep;

/** {@hide} */
public interface OneStepManager {

    public static final int BIT_SIDEBAR_IN_NONE_MODE = 0;

    public static final int BIT_SIDEBAR_IN_LEFT_TOP_MODE = 1 << 0;

    public static final int BIT_SIDEBAR_IN_RIGHT_TOP_MODE = 1 << 1;

    public static interface OneStepStateObserver {
        public void onEnterOneStepMode(int state);;

        public void onExitOneStepMode();
    }

    public void registerStateObserver(OneStepStateObserver observer, Handler handler);

    public void unregisterStateObserver(OneStepStateObserver observer);

    /**
     * Bind the onestep UI.( packages/apps/OneStep)
     */
    public void bindOneStepUI(IOneStep onestep);

    // Need the OneStep_SERVICE permission
    public void resetWindow();

    // NOT need the OneStep_SERVICE permission
    public boolean isInOneStepMode();

    // NOT need the OneStep_SERVICE permission
    public int getOneStepModeState();

    // NOT need the OneStep_SERVICE permission
    boolean isFocusedOnOneStep();

    // Need the OneStep_SERVICE permission
    public void requestEnterLastMode();

    public void resumeOneStep();

    public void updateOngoing(ComponentName name, int token, int pendingNumbers,
            CharSequence title, int pid);

    // Need the OneStep_SERVICE permission
    public void requestExitOneStepMode();

    // Need the OneStep_SERVICE permission
    public void requestEnterOneStepMode(int mode);

    public void setEnabled(boolean enabled);
}
