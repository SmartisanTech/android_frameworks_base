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

import android.view.onestep.IOneStep;
import android.view.onestep.IOneStepStateObserver;
import android.content.ComponentName;

/** @hide */
interface IOneStepManager {

    /**
     * Set a observer to monitor state change.
     */
    void registerStateObserver(IOneStepStateObserver observer);

    /**
     * Clear the observer.
     */
    void unregisterStateObserver(IOneStepStateObserver observer);

    /**
     * Bind the onestep instance.( packages/apps/OneStep).
     */
    void bindOneStepUI(IOneStep oneStep);

    /**
     * Reset the window to normal
     * Can only be called with the ONE_STEP_SERVICE permission
     */
    void resetWindow();

    /**
     * Whether in one step mode or not.
     */
    boolean isInOneStepMode();

    /**
     * Get One Step mode state.
     */
    int getOneStepModeState();

    /**
     * Whether the focus one one step window or not.
     */
    boolean isFocusedOnOneStep();

    /**
     * Request enter last one step mode.
     * Can only be called with the ONE_STEP_SERVICE permission
     */
      void requestEnterLastMode();

    /**
     * Request exit one step mode.
     * Can only be called with the ONE_STEP_SERVICE permission
     */
      void requestExitOneStepMode();

    /**
     * Request enter one step mode.
     * Can only be called with the ONE_STEP_SERVICE permission
     */
     void requestEnterOneStepMode(int mode);

    /**
     * Set one step UI enabled or not.
     * Can only be called with the ONE_STEP permission
     */
    void setEnabled(boolean enabled);

   /**
     * Resume one step UI 
     * Can only be called with the ONE_STEP permission
     */
    void resumeOneStep();

   /**
     * Drag text, image to one. For examle: smartisan notes.
     */
    void updateOngoing(in ComponentName name, int token, int pendingNumbers, CharSequence title, int pid);
}
