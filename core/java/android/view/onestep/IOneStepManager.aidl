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

    // Need the OneStep_SERVICE permission
    void resetWindow();

    // NOT need the OneStep_SERVICE permission
    boolean isInOneStepMode();

    // NOT need the OneStep_SERVICE permission
    int getOneStepModeState();

    // NOT need the OneStep_SERVICE permission
    boolean isFocusedOnOneStep();

    // Need the OneStep_SERVICE permission
    void requestEnterLastMode();

    void resumeOneStep();

    void updateOngoing(in ComponentName name, int token, int pendingNumbers, CharSequence title, int pid);

    // Need the OneStep_SERVICE permission
    void requestExitOneStepMode();

    // Need the OneStep_SERVICE permission
    void requestEnterOneStepMode(int mode);

    void setEnabled(boolean enabled);
}
