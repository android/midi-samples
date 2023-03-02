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

import com.example.android.miditools.EventScheduler.SchedulableEvent
import org.junit.Assert
import org.junit.Test

/**
 * Unit Tests for the EventScheduler
 */
class TestEventScheduler {
    @Test
    fun testEventPool() {
        val scheduler = EventScheduler()
        val time1 = 723L
        val event1 = SchedulableEvent(time1)
        Assert.assertEquals("event time", time1, event1.timestamp)
        Assert.assertEquals("empty event pool", null, scheduler.removeEventFromPool())
        scheduler.addEventToPool(event1)
        Assert.assertEquals("always leave one event in pool", null,
                scheduler.removeEventFromPool())
        val time2 = 9817L
        val event2 = SchedulableEvent(time2)
        scheduler.addEventToPool(event2)
        Assert.assertEquals("first event in pool", event1, scheduler.removeEventFromPool())
        Assert.assertEquals("always leave one event in pool", null,
                scheduler.removeEventFromPool())
        scheduler.addEventToPool(event1)
        Assert.assertEquals("second event in pool", event2, scheduler.removeEventFromPool())
    }

    @Test
    fun testSingleEvent() {
        val scheduler = EventScheduler()
        val time = 723L
        val event = SchedulableEvent(time)
        Assert.assertEquals("event time", time, event.timestamp)
        scheduler.add(event)
        Assert.assertEquals("too soon", null, scheduler.getNextEvent(time - 1))
        Assert.assertEquals("right now", event, scheduler.getNextEvent(time))
    }

    @Test
    fun testTwoEvents() {
        val scheduler = EventScheduler()
        val time1 = 723L
        val event1 = SchedulableEvent(time1)
        val time2 = 9817L
        val event2 = SchedulableEvent(time2)
        scheduler.add(event1)
        scheduler.add(event2)
        Assert.assertEquals("too soon", null, scheduler.getNextEvent(time1 - 1))
        Assert.assertEquals("after 1", event1, scheduler.getNextEvent(time1 + 5))
        Assert.assertEquals("too soon", null, scheduler.getNextEvent(time1 + 5))
        Assert.assertEquals("after 2", event2, scheduler.getNextEvent(time2 + 7))
    }

    @Test
    fun testReverseTwoEvents() {
        val scheduler = EventScheduler()
        val time1 = 723L
        val event1 = SchedulableEvent(time1)
        val time2 = 9817L
        val event2 = SchedulableEvent(time2)
        scheduler.add(event2)
        scheduler.add(event1)
        Assert.assertEquals("too soon", null, scheduler.getNextEvent(time1 - 1))
        Assert.assertEquals("after 1", event1, scheduler.getNextEvent(time1 + 5))
        Assert.assertEquals("too soon", null, scheduler.getNextEvent(time1 + 5))
        Assert.assertEquals("after 2", event2, scheduler.getNextEvent(time2 + 7))
    }
}