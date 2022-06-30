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

import android.media.midi.*
import android.media.midi.MidiManager.OnDeviceOpenedListener
import android.util.Log
import android.widget.Spinner
import java.io.IOException

/**
 * Manages a Spinner for selecting a MidiOutputPort.
 */
open class MidiInputOutputPortSelector
/**
 * @param midiManager
 * @param spinner spinner from the layout resource
 */(
    midiManager: MidiManager, spinner: Spinner
) : MidiPortSelector(midiManager, spinner, MidiConstants.MIDI_INPUT_OUTPUT_PORT) {
    private var mInputPort: MidiInputPort? = null
    private var mOutputPort: MidiOutputPort? = null
    private val mDispatcher = MidiDispatcher()
    private var mOpenDevice: MidiDevice? = null
    private var mDeviceOpened = false


    val didOpenComplete: Boolean
        get() = mDeviceOpened

    override fun onPortSelected(wrapper: MidiPortWrapper?) {
        close()
        if (wrapper != null) {
            val info = wrapper.deviceInfo
            if (info != null) {
                try {
                    mMidiManager.openDevice(info, OnDeviceOpenedListener { device ->
                        if (device == null) {
                            Log.e(MidiConstants.TAG, "could not open $info")
                        } else {
                            mOpenDevice = device
                            mInputPort = device.openInputPort(
                                wrapper.portIndex
                            )
                            mOutputPort = device.openOutputPort(wrapper.portIndex)
                            if (mOutputPort == null) {
                                Log.e(
                                    MidiConstants.TAG,
                                    "could not open output port for $info"
                                )
                                return@OnDeviceOpenedListener
                            }
                            mOutputPort!!.connect(mDispatcher)
                            if (mInputPort == null) {
                                Log.e(MidiConstants.TAG, "could not open input port on $info")
                                return@OnDeviceOpenedListener
                            }
                            mDeviceOpened = true
                        }
                    }, null)
                    // Don't run the callback on the UI thread because this might take a while.
                } catch (e: Exception) {
                    Log.e(MidiConstants.TAG, "openDevice failed", e)
                }
            }
        }
    }

    override fun onClose() {
        try {
            if (mOutputPort != null) {
                mOutputPort!!.disconnect(mDispatcher)
                Log.i(MidiConstants.TAG, "MidiInputOutputPortSelector.onClose() - close output port")
                mOutputPort!!.close()
            }
            mOutputPort = null
            if (mInputPort != null) {
                Log.i(MidiConstants.TAG, "MidiInputOutputPortSelector.onClose() - close input port")
                mInputPort!!.close()
            }
            mInputPort = null
            if (mOpenDevice != null) {
                Log.i(MidiConstants.TAG, "MidiInputOutputPortSelector.onClose() - close device")
                mOpenDevice!!.close()
            }
            mOpenDevice = null
            mDeviceOpened = false
        } catch (e: IOException) {
            Log.e(MidiConstants.TAG, "cleanup failed", e)
        }
        super.onClose()
    }

    /**
     * You can connect your MidiReceivers to this sender. The user will then select which output
     * port will send messages through this MidiSender.
     * @return a MidiSender that will send the messages from the selected port.
     */
    val sender: MidiSender
        get() = mDispatcher.sender

    val receiver: MidiReceiver?
        get() = mInputPort

    companion object {
        const val TAG = "MidiInputOutputPortSelector"
    }
}
