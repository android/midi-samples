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
package com.example.android.midiumpscope

import android.media.midi.MidiDeviceService
import android.media.midi.MidiReceiver
import kotlin.Throws
import android.media.midi.MidiDeviceStatus
import com.example.android.midiumpscope.MidiPrinter.formatDeviceInfo
import com.example.android.miditools.MidiFramer
import java.io.IOException

/**
 * Virtual MIDI Device that logs messages to a ScopeLogger.
 */
class MidiScope : MidiDeviceService() {
    private val mInputReceiver: MidiReceiver = MyReceiver()

    override fun onGetInputPortReceivers(): Array<MidiReceiver> {
        return arrayOf(mInputReceiver)
    }

    internal inner class MyReceiver : MidiReceiver() {
        @Throws(IOException::class)
        override fun onSend(
            data: ByteArray, offset: Int, count: Int,
            timestamp: Long
        ) {
            if (mScopeLogger != null) {
                // Send raw data to be parsed into discrete messages.
                mDeviceFramer!!.send(data, offset, count, timestamp)
            }
        }
    }

    /**
     * This will get called when clients connect or disconnect.
     * Log device information.
     */
    override fun onDeviceStatusChanged(status: MidiDeviceStatus) {
        if (mScopeLogger != null) {
            if (status.isInputPortOpen(0)) {
                mScopeLogger!!.log("=== connected ===")
                val text = formatDeviceInfo(
                    status.deviceInfo
                )
                mScopeLogger!!.log(text)
            } else {
                mScopeLogger!!.log("--- disconnected ---")
            }
        }
    }

    companion object {
        private var mScopeLogger: ScopeLogger? = null
        private var mDeviceFramer: MidiFramer? = null

        // Receiver that prints the messages.
        var scopeLogger: ScopeLogger?
            get() = mScopeLogger
            set(logger) {
                if (logger != null) {
                    // Receiver that prints the messages.
                    val loggingReceiver = LoggingReceiver(logger)
                    mDeviceFramer = MidiFramer(loggingReceiver)
                }
                mScopeLogger = logger
            }
    }
}