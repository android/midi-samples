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

import kotlin.jvm.Volatile
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Store SchedulableEvents in a timestamped buffer.
 * Events may be written in any order.
 * Events will be read in sorted order.
 * Events with the same timestamp will be read in the order they were added.
 *
 * Only one Thread can write into the buffer.
 * And only one Thread can read from the buffer.
 */
open class EventScheduler {
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    private val mEventBuffer: SortedMap<Long, FastEventQueue>

    // This does not have to be guarded. It is only set by the writing thread.
    // If the reader sees a null right before being set then that is OK.
    private var mEventPool: FastEventQueue? = null

    // If we keep at least one node in the list then it can be atomic
    // and non-blocking.
    private inner class FastEventQueue(// One thread takes from the beginning of the list.
        @field:Volatile var mFirst: SchedulableEvent?
    ) {
        // A second thread returns events to the end of the list.
        @Volatile
        var mLast: SchedulableEvent?

        @Volatile
        var mEventsAdded: Long

        @Volatile
        var mEventsRemoved: Long
        fun size(): Int {
            return (mEventsAdded - mEventsRemoved).toInt()
        }

        /**
         * Do not call this unless there is more than one event
         * in the list.
         * @return first event in the list
         */
        fun remove(): SchedulableEvent {
            // Take first event.
            mEventsRemoved++
            val event = mFirst
            mFirst = event!!.mNext
            return event
        }

        /**
         * @param event
         */
        fun add(event: SchedulableEvent) {
            event.mNext = null
            mLast!!.mNext = event
            mLast = event
            mEventsAdded++
        }

        init {
            mLast = mFirst
            mEventsAdded = 1 // Always created with one event added. Never empty.
            mEventsRemoved = 0 // None removed yet.
        }
    }

    /**
     * Base class for events that can be stored in the EventScheduler.
     */
    open class SchedulableEvent
    /**
     * @param timestamp
     */(
        /**
         * The timestamp should not be modified when the event is in the
         * scheduling buffer.
         */
        var timestamp: Long
    ) {
        /**
         * @return timestamp
         */
        var mNext: SchedulableEvent? = null
    }

    /**
     * Get an event from the pool.
     * Always leave at least one event in the pool.
     * @return event or null
     */
    fun removeEventFromPool(): SchedulableEvent? {
        var event: SchedulableEvent? = null
        if (mEventPool != null && mEventPool!!.size() > 1) {
            event = mEventPool!!.remove()
        }
        return event
    }

    /**
     * Return events to a pool so they can be reused.
     *
     * @param event
     */
    open fun addEventToPool(event: SchedulableEvent) {
        if (mEventPool == null) {
            mEventPool = FastEventQueue(event) // add event to pool
            // If we already have enough items in the pool then just
            // drop the event. This prevents unbounded memory leaks.
        } else if (mEventPool!!.size() < MAX_POOL_SIZE) {
            mEventPool!!.add(event)
        }
    }

    /**
     * Add an event to the scheduler. Events with the same time will be
     * processed in order.
     *
     * @param event
     */
    fun add(event: SchedulableEvent) {
        lock.withLock {
            var list = mEventBuffer[event.timestamp]
            if (list == null) {
                val lowestTime =
                    if (mEventBuffer.isEmpty()) Long.MAX_VALUE else mEventBuffer.firstKey()
                list = FastEventQueue(event)
                mEventBuffer[event.timestamp] = list
                // If the event we added is earlier than the previous earliest
                // event then notify any threads waiting for the next event.
                if (event.timestamp < lowestTime) {
                    condition.signal()
                }
            } else {
                list.add(event)
            }
        }
    }

    // Caller must synchronize on lock before calling.
    private fun removeNextEventLocked(lowestTime: Long): SchedulableEvent {
        val list = mEventBuffer[lowestTime]
        // Remove list from tree if this is the last node.
        if (list!!.size() == 1) {
            mEventBuffer.remove(lowestTime)
        }
        return list.remove()
    }

    /**
     * Check to see if any scheduled events are ready to be processed.
     *
     * @param time
     * @return next event or null if none ready
     */
    fun getNextEvent(time: Long): SchedulableEvent? {
        var event: SchedulableEvent? = null
        lock.withLock {
            if (!mEventBuffer.isEmpty()) {
                val lowestTime = mEventBuffer.firstKey()
                // Is it time for this list to be processed?
                if (lowestTime <= time) {
                    event = removeNextEventLocked(lowestTime)
                }
            }
        }
        // Log.i(TAG, "getNextEvent: event = " + event);
        return event
    }

    companion object {
        private const val MAX_POOL_SIZE = 200
    }

    init {
        mEventBuffer = TreeMap()
    }
}