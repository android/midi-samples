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
import kotlin.Throws
import java.io.IOException

/**
 * Add MIDI Events to an EventScheduler
 */
open class MidiEventScheduler : EventScheduler() {
    /**
     * This MidiReceiver will write date to the scheduling buffer.
     * @return the MidiReceiver
     */
    val receiver: MidiReceiver = SchedulingReceiver()

    private inner class SchedulingReceiver : MidiReceiver() {
        /**
         * Store these bytes in the EventScheduler to be delivered at the specified
         * time.
         */
        @Throws(IOException::class)
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            val event = createScheduledEvent(msg, offset, count, timestamp)
            add(event)
        }
    }

    class MidiEvent : SchedulableEvent {
        @JvmField
        var count = 0
        @JvmField
        var data: ByteArray

        constructor(count: Int) : super(0) {
            data = ByteArray(count)
        }

        constructor(msg: ByteArray, offset: Int, count: Int, timestamp: Long) : super(
            timestamp
        ) {
            data = ByteArray(count)
            System.arraycopy(msg, offset, data, 0, count)
            this.count = count
        }

        override fun toString(): String {
            var text = "Event: "
            for (i in 0 until count) {
                text += data[i].toString() + ", "
            }
            return text
        }
    }

    /**
     * Create an event that contains the message.
     */
    private fun createScheduledEvent(
        msg: ByteArray, offset: Int, count: Int,
        timestamp: Long
    ): MidiEvent {
        var event: MidiEvent?
        if (count > POOL_EVENT_SIZE) {
            event = MidiEvent(msg, offset, count, timestamp)
        } else {
            event = removeEventFromPool() as MidiEvent?
            if (event == null) {
                event = MidiEvent(POOL_EVENT_SIZE)
            }
            System.arraycopy(msg, offset, event.data, 0, count)
            event.count = count
            event.timestamp = timestamp
        }
        return event
    }

    /**
     * Return events to a pool so they can be reused.
     *
     * @param event
     */
    override fun addEventToPool(event: SchedulableEvent) {
        // Make sure the event is suitable for the pool.
        if (event is MidiEvent) {
            if (event.data.size == POOL_EVENT_SIZE) {
                super.addEventToPool(event)
            }
        }
    }

    companion object {
        // Maintain a pool of scheduled events to reduce memory allocation.
        // This pool increases performance by about 14%.
        private const val POOL_EVENT_SIZE = 16
    }
}