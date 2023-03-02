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

import com.example.android.miditools.MidiDeviceMonitor.Companion.getInstance
import android.media.midi.MidiManager
import android.media.midi.MidiManager.DeviceCallback
import android.media.midi.MidiDeviceInfo
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.media.midi.MidiDeviceStatus
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.AdapterView
import android.util.Log
import android.view.View
import java.lang.Integer.min
import java.util.HashSet

/**
 * Base class that uses a Spinner to select available MIDI ports.
 */
abstract class MidiPortSelector(
    protected var mMidiManager: MidiManager, spinner: Spinner, type: Int
) : DeviceCallback() {
    private var mType = MidiConstants.MIDI_INPUT_PORT
    protected var mAdapter: ArrayAdapter<MidiPortWrapper>
    private var mBusyPorts = HashSet<MidiPortWrapper>()
    private val mSpinner: Spinner
    private var mCurrentWrapper: MidiPortWrapper? = null

    /**
     * Set to no port selected.
     */
    private fun clearSelection() {
        mSpinner.setSelection(0)
    }

    private fun getInfoPortCount(info: MidiDeviceInfo): Int {
        return when (mType) {
            MidiConstants.MIDI_INPUT_PORT -> {
                info.inputPortCount
            }
            MidiConstants.MIDI_OUTPUT_PORT -> {
                info.outputPortCount
            }
            else -> {
                min(info.inputPortCount, info.outputPortCount)
            }
        }
    }

    final override fun onDeviceAdded(info: MidiDeviceInfo) {
        val portCount = getInfoPortCount(info)
        for (i in 0 until portCount) {
            val wrapper = MidiPortWrapper(info, mType, i)
            mAdapter.add(wrapper)
            Log.i(MidiConstants.TAG, "$wrapper was added to $this")
            mAdapter.notifyDataSetChanged()
        }
    }

    override fun onDeviceRemoved(info: MidiDeviceInfo) {
        val portCount = getInfoPortCount(info)
        for (i in 0 until portCount) {
            val wrapper = MidiPortWrapper(info, mType, i)
            val currentWrapper = mCurrentWrapper
            mAdapter.remove(wrapper)
            // If the currently selected port was removed then select no port.
            if (wrapper == currentWrapper) {
                clearSelection()
            }
            mAdapter.notifyDataSetChanged()
            Log.i(MidiConstants.TAG, "$wrapper was removed")
        }
    }

    override fun onDeviceStatusChanged(status: MidiDeviceStatus) {
        // If an input port becomes busy then remove it from the menu.
        // If it becomes free then add it back to the menu.
        if ((mType == MidiConstants.MIDI_INPUT_PORT)
                || (mType == MidiConstants.MIDI_INPUT_OUTPUT_PORT)) {
            val info = status.deviceInfo
            Log.i(
                MidiConstants.TAG, "MidiPortSelector.onDeviceStatusChanged status = " + status
                        + ", mType = " + mType
                        + ", info = " + info
            )
            // Look for transitions from free to busy.
            val portCount = info.inputPortCount
            for (i in 0 until portCount) {
                val wrapper = MidiPortWrapper(info, mType, i)
                if (wrapper != mCurrentWrapper) {
                    if (status.isInputPortOpen(i)) { // busy?
                        if (!mBusyPorts.contains(wrapper)) {
                            // was free, now busy
                            mBusyPorts.add(wrapper)
                            mAdapter.remove(wrapper)
                            mAdapter.notifyDataSetChanged()
                        }
                    } else {
                        if (mBusyPorts.remove(wrapper)) {
                            // was busy, now free
                            mAdapter.add(wrapper)
                            mAdapter.notifyDataSetChanged()
                        }
                    }
                }
            }
        }
    }

    /**
     * Implement this method to handle the user selecting a port on a device.
     *
     * @param wrapper
     */
    abstract fun onPortSelected(wrapper: MidiPortWrapper?)

    /**
     * Implement this method to clean up any open resources.
     */
    open fun onClose() {}

    /**
     * Implement this method to clean up any open resources.
     */
    fun onDestroy() {
        getInstance(mMidiManager)!!.unregisterDeviceCallback(this)
    }

    /**
     *
     */
    fun close() {
        onClose()
    }

    init {
        mType = type
        mSpinner = spinner
        mAdapter = ArrayAdapter(
            mSpinner.context,
            android.R.layout.simple_spinner_item
        )
        mAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )
        mAdapter.add(MidiPortWrapper(null, 0, 0))
        mSpinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?,
                pos: Int, id: Long
            ) {
                mCurrentWrapper = mAdapter.getItem(pos)
                onPortSelected(mCurrentWrapper)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                onPortSelected(null)
                mCurrentWrapper = null
            }
        }
        mSpinner.adapter = mAdapter
        val infoSet =
            mMidiManager.getDevicesForTransport(MidiManager.TRANSPORT_UNIVERSAL_MIDI_PACKETS)
        for (info in infoSet) {
            onDeviceAdded(info)
        }
    }
}