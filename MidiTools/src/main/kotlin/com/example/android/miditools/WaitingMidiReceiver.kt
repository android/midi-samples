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

import android.media.midi.MidiReceiver
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// Store received messages in an array.
open class WaitingMidiReceiver : MidiReceiver() {
    private var messages: ArrayList<MidiMessage> = ArrayList()

    @get:Synchronized
    var readCount = 0

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    @Synchronized
    override fun onSend(
        data: ByteArray, offset: Int, count: Int,
        timestamp: Long
    ) {
        messages.add(MidiMessage(data, offset, count, timestamp))
        lock.withLock {
            condition.signalAll()
        }
    }

    @get:Synchronized
    val messageCount: Int
        get() = messages.size

    @Synchronized
    fun getMessage(index: Int): MidiMessage {
        return messages[index]
    }

    /**
     * Wait until count messages have arrived. This is a cumulative total.
     *
     * @param count
     * @param timeoutMs
     * @throws InterruptedException
     */
    @Throws(InterruptedException::class)
    fun waitForMessages(count: Int, timeoutMs: Int) {
        val endTimeMs = System.currentTimeMillis() + timeoutMs + 1
        var timeToWait = (timeoutMs + 1).toLong()
        while (messageCount < count
            && timeToWait > 0
        ) {
            lock.withLock {
                condition.await(timeToWait, TimeUnit.MILLISECONDS)
            }
            timeToWait = endTimeMs - System.currentTimeMillis()
        }
    }
}