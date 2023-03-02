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

import com.example.android.miditools.MidiConstants.getBytesPerMessage
import android.media.midi.MidiReceiver
import kotlin.Throws
import java.io.IOException

/**
 * Convert stream of arbitrary MIDI bytes into discrete messages.
 *
 * Parses the incoming bytes and then posts individual messages to the receiver
 * specified in the constructor. Short messages of 1-3 bytes will be complete.
 * System Exclusive messages may be posted in pieces.
 *
 * Resolves Running Status and interleaved System Real-Time messages.
 */
class MidiFramer(private val mReceiver: MidiReceiver) : MidiReceiver() {
    private val mBuffer = ByteArray(3)
    private var mCount = 0
    private var mRunningStatus: Byte = 0
    private var mNeeded = 0
    private var mInSysEx = false

    /*
     * @see android.midi.MidiReceiver#onSend(byte[], int, int, long)
     */
    @Throws(IOException::class)
    override fun onSend(data: ByteArray, inOffset: Int, count: Int, timestamp: Long) {
        var offset = inOffset
        var sysExStartOffset = if (mInSysEx) offset else -1
        for (i in 0 until count) {
            val currentByte = data[offset]
            val currentInt: Int = currentByte.toInt() and 0xFF
            if (currentInt >= 0x80) { // status byte?
                if (currentInt < 0xF0) { // channel message?
                    mRunningStatus = currentByte
                    mCount = 1
                    mNeeded = getBytesPerMessage(currentByte) - 1
                } else if (currentInt < 0xF8) { // system common?
                    if (currentInt == 0xF0 /* SysEx Start */) {
                        // Log.i(TAG, "SysEx Start");
                        mInSysEx = true
                        sysExStartOffset = offset
                    } else if (currentInt == 0xF7 /* SysEx End */) {
                        // Log.i(TAG, "SysEx End");
                        if (mInSysEx) {
                            mReceiver.send(
                                data, sysExStartOffset,
                                offset - sysExStartOffset + 1, timestamp
                            )
                            mInSysEx = false
                            sysExStartOffset = -1
                        }
                    } else {
                        mBuffer[0] = currentByte
                        mRunningStatus = 0
                        mCount = 1
                        mNeeded = getBytesPerMessage(currentByte) - 1
                    }
                } else { // real-time?
                    // Single byte message interleaved with other data.
                    if (mInSysEx) {
                        mReceiver.send(
                            data, sysExStartOffset,
                            offset - sysExStartOffset, timestamp
                        )
                        sysExStartOffset = offset + 1
                    }
                    mReceiver.send(data, offset, 1, timestamp)
                }
            } else { // data byte
                if (!mInSysEx) {
                    mBuffer[mCount++] = currentByte
                    if (--mNeeded == 0) {
                        if (mRunningStatus.toInt() != 0) {
                            mBuffer[0] = mRunningStatus
                        }
                        mReceiver.send(mBuffer, 0, mCount, timestamp)
                        mNeeded = getBytesPerMessage(mBuffer[0]) - 1
                        mCount = 1
                    }
                }
            }
            ++offset
        }

        // send any accumulatedSysEx data
        if (sysExStartOffset in 0 until offset) {
            mReceiver.send(
                data, sysExStartOffset,
                offset - sysExStartOffset, timestamp
            )
        }
    }
}