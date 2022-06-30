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

import android.media.midi.MidiReceiver
import android.util.Log
import kotlin.Throws
import java.io.IOException
import java.lang.StringBuilder

/**
 * Convert incoming MIDI messages to a string and write them to a ScopeLogger.
 * Assume that messages have been aligned using a MidiFramer.
 */
class LoggingReceiver(logger: ScopeLogger) : MidiReceiver() {
    private val mStartTime: Long = System.nanoTime()
    private val mLogger: ScopeLogger = logger
    private var mLastTimeStamp: Long = 0

    /*
     * @see android.media.midi.MidiReceiver#onSend(byte[], int, int, long)
     */
    @Throws(IOException::class)
    override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
        val sb = StringBuilder()
        if (timestamp == 0L) {
            sb.append(String.format("-----0----: "))
        } else {
            val monoTime = timestamp - mStartTime
            val delayTimeNanos = timestamp - System.nanoTime()
            val delayTimeMillis = (delayTimeNanos / NANOS_PER_MILLISECOND).toInt()
            val seconds = monoTime.toDouble() / NANOS_PER_SECOND
            // Mark timestamps that are out of order.
            sb.append(if (timestamp < mLastTimeStamp) "*" else " ")
            mLastTimeStamp = timestamp
            sb.append(String.format("%10.3f (%2d): ", seconds, delayTimeMillis))
        }
        sb.append(MidiPrinter.formatUmpSet(data, offset, count))
        val text = sb.toString()
        mLogger.log(text)
        Log.i(TAG, text)
    }

    companion object {
        const val TAG = "MidiUmpScope"
        private const val NANOS_PER_MILLISECOND = 1000000L
        private const val NANOS_PER_SECOND = NANOS_PER_MILLISECOND * 1000L
    }

}