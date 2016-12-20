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
 
package android.view.onestep;

import android.content.ComponentName;
import android.os.Handler;
import android.view.onestep.IOneStep;

/**
 * The interface that apps use to talk to the one step manager.
 * <p>
 * Use <code>Context.getSystemService(Context.WINDOW_SERVICE)</code> to get one of these.
 *
 * @see android.content.Context#getSystemService
 * @see android.content.Context#WINDOW_SERVICE
 * 
 * @hide
 *  */
public interface OneStepManager {

    /**
     * One Step Mode state normal
     */
    public static final int BIT_SIDEBAR_IN_NONE_MODE = 0;

    /**
     * One Step Mode state in left_top
     */
    public static final int BIT_SIDEBAR_IN_LEFT_TOP_MODE = 1 << 0;

    /**
     * One Step Mode state int right_top
     */
    public static final int BIT_SIDEBAR_IN_RIGHT_TOP_MODE = 1 << 1;

    /**
     * The class to observe one step mode state changed.
     * @author smartisan
     *
     */
    public static interface OneStepStateObserver {
        public void onEnterOneStepMode(int state);;

        public void onExitOneStepMode();
    }

    /**
     * Register one step mode observer
     * @param observer 
     * @param handler the handler to do logic 
     */
    public void registerStateObserver(OneStepStateObserver observer, Handler handler);

    /**
     * Clear the Observer
     * @param observer 
     * @param handler the handler to do logic 
     */
    public void unregisterStateObserver(OneStepStateObserver observer);

    /**
     * Bind the onestep UI.( packages/apps/OneStep).
     * 
     */
    public void bindOneStepUI(IOneStep onestep);

    /**
     * Reset the window to normal
     * Can only be called with the ONE_STEP_SERVICE permission
     */
    public void resetWindow();

    /**
     * Whether in one step mode or not.
     */
    public boolean isInOneStepMode();

    /**
     * Get One Step mode state.
     */
    public int getOneStepModeState();

    /**
     * Whether the focus one one step window or not.
     */
    boolean isFocusedOnOneStep();

    /**
     * Request enter last one step mode.
     * Can only be called with the ONE_STEP_SERVICE permission
     */
    public void requestEnterLastMode();

    /**
     * Request exit one step mode.
     * Can only be called with the ONE_STEP_SERVICE permission
     */
    public void requestExitOneStepMode();

    /**
     * Request enter one step mode.
     * Can only be called with the ONE_STEP_SERVICE permission
     */
    public void requestEnterOneStepMode(int mode);


    /**
     * Resume one step UI .Cancel drag.
     * Can only be called with the ONE_STEP permission
     */
    public void resumeOneStep();

    /**
     * Set one step UI enabled or not.
     * Can only be called with the ONE_STEP permission
     */
    public void setEnabled(boolean enabled);

    /**
     * Drag text, image to one. For examle: smartisan notes.
     */
    public void updateOngoing(ComponentName name, int token, int pendingNumbers,
            CharSequence title, int pid);
}
