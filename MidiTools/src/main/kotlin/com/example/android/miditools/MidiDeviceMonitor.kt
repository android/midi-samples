/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.example.android.miditools

import android.media.midi.MidiManager
import android.media.midi.MidiManager.DeviceCallback
import kotlin.jvm.Synchronized
import java.util.concurrent.Executor

/**
 * Manage a list a of DeviceCallbacks that are called when a MIDI Device is
 * plugged in or unplugged.
 *
 * This class is used to workaround a bug in the M release of the Android MIDI API.
 * The MidiManager.unregisterDeviceCallback() method was not working. So if an app
 * was rotated, and the Activity destroyed and recreated, the DeviceCallbacks would
 * accumulate in the MidiServer. This would result in multiple callbacks whenever a
 * device was added. This class allow an app to register and unregister multiple times
 * using a local list of callbacks. It registers a single callback, which stays registered
 * until the app is dead.
 *
 * This code checks to see if the N release is being used. N has a fix for the bug.
 * For N, the register and unregister calls are passed directly to the MidiManager.
 *
 * Note that this code is not thread-safe. It should only be called from the UI thread.
 */
class MidiDeviceMonitor private constructor(private val mMidiManager: MidiManager) {
    fun registerDeviceCallback(
        transportUniversalMidiPackets: Int,
        executor: Executor?,
        midiPortSelector: MidiPortSelector?
    ) {
        mMidiManager.registerDeviceCallback(
            transportUniversalMidiPackets,
            executor!!,
            midiPortSelector!!
        )
    }

    fun unregisterDeviceCallback(callback: DeviceCallback) {
        mMidiManager.unregisterDeviceCallback(callback)
    }

    companion object {
        const val TAG = "MidiDeviceMonitor"
        private var mInstance: MidiDeviceMonitor? = null
        @JvmStatic
        @Synchronized
        fun getInstance(midiManager: MidiManager): MidiDeviceMonitor? {
            if (mInstance == null) {
                mInstance = MidiDeviceMonitor(midiManager)
            }
            return mInstance
        }
    }
}