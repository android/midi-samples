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
import org.junit.Assert
import org.junit.Test
import java.io.IOException
import java.util.ArrayList

/**
 * Unit Tests for the MidiFramer
 */
class TestMidiFramer {
    // Store a complete MIDI message.
    internal class MidiMessage(buffer: ByteArray?, offset: Int, length: Int, timestamp: Long) {
        val data: ByteArray = ByteArray(length)
        val timestamp: Long

        constructor(buffer: ByteArray, timestamp: Long) : this(buffer, 0, buffer.size, timestamp)

        // Check whether these two messages are the same.
        fun check(other: MidiMessage) {
            Assert.assertEquals("data.length", data.size.toLong(), other.data.size.toLong())
            Assert.assertEquals("data.timestamp", timestamp, other.timestamp)
            for (i in data.indices) {
                Assert.assertEquals("data[$i]", data[i], other.data[i])
            }
        }

        init {
            if (buffer != null) {
                System.arraycopy(buffer, offset, data, 0, length)
            }
            this.timestamp = timestamp
        }
    }

    // Store received messages in an array.
    internal inner class MyLoggingReceiver : MidiReceiver() {
        private var messages = ArrayList<MidiMessage>()
        override fun onSend(
            data: ByteArray, offset: Int, count: Int,
            timestamp: Long
        ) {
            messages.add(MidiMessage(data, offset, count, timestamp))
        }

        val messageCount: Int
            get() = messages.size

        fun getMessage(index: Int): MidiMessage {
            return messages[index]
        }
    }

    /**
     * Send the original messages and verify that we receive back the expected
     * messages.
     *
     * @param original
     * @param expected
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun checkSequence(original: Array<MidiMessage?>, expected: Array<MidiMessage?>) {
        val receiver = MyLoggingReceiver()
        val framer = MidiFramer(receiver)
        for (message in original) {
            framer.send(
                message!!.data, 0, message.data.size,
                message.timestamp
            )
        }
        Assert.assertEquals(
            "command count", expected.size.toLong(),
            receiver.messageCount.toLong()
        )
        for (i in expected.indices) {
            expected[i]!!.check(receiver.getMessage(i))
        }
    }

    @Throws(IOException::class)
    private fun checkSequence(
        original: Array<ByteArray>, expected: Array<ByteArray>,
        timestamp: Long
    ) {
        var index = 0
        val originalMessages = arrayOfNulls<MidiMessage>(original.size)
        for (data in original) {
            originalMessages[index++] = MidiMessage(data, timestamp)
        }
        index = 0
        val expectedMessages = arrayOfNulls<MidiMessage>(expected.size)
        for (data in expected) {
            expectedMessages[index++] = MidiMessage(data, timestamp)
        }
        checkSequence(originalMessages, expectedMessages)
    }

    // Send a NoteOn through the MidiFramer
    @Test
    @Throws(IOException::class)
    fun testSimple() {
        val data = byteArrayOf(0x90.toByte(), 0x45, 0x32)
        val receiver = MyLoggingReceiver()
        val framer = MidiFramer(receiver)
        Assert.assertEquals(
            "command byte", 0x90.toByte(), data[0]
        )
        framer.send(data, 0, data.size, 0L)
        Assert.assertEquals("command count", 1, receiver.messageCount.toLong())
        val expected = MidiMessage(data, 0, data.size, 0L)
        val message = receiver.getMessage(0)
        expected.check(message)
    }

    // Test message based testing tool using a NoteOn
    @Test
    @Throws(IOException::class)
    fun testSimpleSequence() {
        val timestamp = 8263518L
        val data = byteArrayOf(0x90.toByte(), 0x45, 0x32)
        val original = arrayOf<MidiMessage?>(MidiMessage(data, timestamp))
        checkSequence(original, original)
    }

    // NoteOn then NoteOff using running status
    @Test
    @Throws(IOException::class)
    fun testRunningArrays() {
        val timestamp = 837518L
        val original = arrayOf(byteArrayOf(0x90.toByte(), 0x45, 0x32, 0x45, 0x00))
        val expected = arrayOf(
            byteArrayOf(0x90.toByte(), 0x45, 0x32), byteArrayOf(
                0x90.toByte(), 0x45, 0x00
            )
        )
        checkSequence(original, expected, timestamp)
    }

    // Start with unresolved running status that should be ignored
    @Test
    @Throws(IOException::class)
    fun testStartMiddle() {
        val timestamp = 837518L
        val original = arrayOf(byteArrayOf(0x23, 0x34, 0x90.toByte(), 0x45, 0x32, 0x45, 0x00))
        val expected = arrayOf(
            byteArrayOf(0x90.toByte(), 0x45, 0x32), byteArrayOf(
                0x90.toByte(), 0x45, 0x00
            )
        )
        checkSequence(original, expected, timestamp)
    }

    @Test
    @Throws(IOException::class)
    fun testTwoOn() {
        val timestamp = 837518L
        val original = arrayOf(byteArrayOf(0x90.toByte(), 0x45, 0x32, 0x47, 0x63))
        val expected = arrayOf(
            byteArrayOf(0x90.toByte(), 0x45, 0x32), byteArrayOf(
                0x90.toByte(), 0x47, 0x63
            )
        )
        checkSequence(original, expected, timestamp)
    }

    @Test
    @Throws(IOException::class)
    fun testThreeOn() {
        val timestamp = 837518L
        val original = arrayOf(byteArrayOf(0x90.toByte(), 0x45, 0x32, 0x47, 0x63, 0x49, 0x23))
        val expected = arrayOf(
            byteArrayOf(0x90.toByte(), 0x45, 0x32), byteArrayOf(
                0x90.toByte(), 0x47, 0x63
            ), byteArrayOf(0x90.toByte(), 0x49, 0x23)
        )
        checkSequence(original, expected, timestamp)
    }

    // A RealTime message before a NoteOn
    @Test
    @Throws(IOException::class)
    fun testRealTimeBefore() {
        val timestamp = 8375918L
        val original = arrayOf(
            byteArrayOf(
                MidiConstants.STATUS_TIMING_CLOCK, 0x90.toByte(),
                0x45, 0x32
            )
        )
        val expected = arrayOf(
            byteArrayOf(MidiConstants.STATUS_TIMING_CLOCK), byteArrayOf(
                0x90.toByte(), 0x45, 0x32
            )
        )
        checkSequence(original, expected, timestamp)
    }

    // A RealTime message in the middle of a NoteOn
    @Test
    @Throws(IOException::class)
    fun testRealTimeMiddle1() {
        val timestamp = 8375918L
        val original = arrayOf(
            byteArrayOf(
                0x90.toByte(), MidiConstants.STATUS_TIMING_CLOCK,
                0x45, 0x32
            )
        )
        val expected = arrayOf(
            byteArrayOf(MidiConstants.STATUS_TIMING_CLOCK), byteArrayOf(
                0x90.toByte(), 0x45, 0x32
            )
        )
        checkSequence(original, expected, timestamp)
    }

    @Test
    @Throws(IOException::class)
    fun testRealTimeMiddle2() {
        val timestamp = 8375918L
        val original = arrayOf(
            byteArrayOf(
                0x90.toByte(), 0x45,
                MidiConstants.STATUS_TIMING_CLOCK, 0x32
            )
        )
        val expected = arrayOf(
            byteArrayOf(0xF8.toByte()), byteArrayOf(
                0x90.toByte(), 0x45, 0x32
            )
        )
        checkSequence(original, expected, timestamp)
    }

    // A RealTime message after a NoteOn
    @Test
    @Throws(IOException::class)
    fun testRealTimeAfter() {
        val timestamp = 8375918L
        val original = arrayOf(
            byteArrayOf(
                0x90.toByte(), 0x45, 0x32,
                MidiConstants.STATUS_TIMING_CLOCK
            )
        )
        val expected = arrayOf(
            byteArrayOf(0x90.toByte(), 0x45, 0x32), byteArrayOf(
                0xF8.toByte()
            )
        )
        checkSequence(original, expected, timestamp)
    }

    // Break up running status across multiple messages
    @Test
    @Throws(IOException::class)
    fun testPieces() {
        val timestamp = 837518L
        val original = arrayOf(
            byteArrayOf(0x90.toByte(), 0x45),
            byteArrayOf(0x32, 0x47),
            byteArrayOf(0x63, 0x49, 0x23)
        )
        val expected = arrayOf(
            byteArrayOf(0x90.toByte(), 0x45, 0x32), byteArrayOf(
                0x90.toByte(), 0x47, 0x63
            ), byteArrayOf(0x90.toByte(), 0x49, 0x23)
        )
        checkSequence(original, expected, timestamp)
    }

    // Break up running status into single byte messages
    @Test
    @Throws(IOException::class)
    fun testByByte() {
        val timestamp = 837518L
        val original = arrayOf(
            byteArrayOf(0x90.toByte()),
            byteArrayOf(0x45),
            byteArrayOf(0x32),
            byteArrayOf(0x47),
            byteArrayOf(0x63),
            byteArrayOf(0x49),
            byteArrayOf(0x23)
        )
        val expected = arrayOf(
            byteArrayOf(0x90.toByte(), 0x45, 0x32), byteArrayOf(
                0x90.toByte(), 0x47, 0x63
            ), byteArrayOf(0x90.toByte(), 0x49, 0x23)
        )
        checkSequence(original, expected, timestamp)
    }

    @Test
    @Throws(IOException::class)
    fun testControlChange() {
        val timestamp = 837518L
        val original = arrayOf(
            byteArrayOf(
                MidiConstants.STATUS_CONTROL_CHANGE, 0x07, 0x52,
                0x0A, 0x63
            )
        )
        val expected = arrayOf(
            byteArrayOf(0xB0.toByte(), 0x07, 0x52), byteArrayOf(
                0xB0.toByte(), 0x0A, 0x63
            )
        )
        checkSequence(original, expected, timestamp)
    }

    @Test
    @Throws(IOException::class)
    fun testProgramChange() {
        val timestamp = 837518L
        val original = arrayOf(byteArrayOf(MidiConstants.STATUS_PROGRAM_CHANGE, 0x05, 0x07))
        val expected = arrayOf(
            byteArrayOf(0xC0.toByte(), 0x05), byteArrayOf(
                0xC0.toByte(), 0x07
            )
        )
        checkSequence(original, expected, timestamp)
    }

    // ProgramChanges, SysEx, ControlChanges
    @Test
    @Throws(IOException::class)
    fun testAck() {
        val timestamp = 837518L
        val original = arrayOf(
            byteArrayOf(
                MidiConstants.STATUS_PROGRAM_CHANGE, 0x05, 0x07,
                MidiConstants.STATUS_SYSTEM_EXCLUSIVE, 0x7E, 0x03, 0x7F, 0x21,
                0xF7.toByte(), MidiConstants.STATUS_CONTROL_CHANGE, 0x07, 0x52,
                0x0A, 0x63
            )
        )
        val expected = arrayOf(
            byteArrayOf(0xC0.toByte(), 0x05), byteArrayOf(
                0xC0.toByte(), 0x07
            ), byteArrayOf(0xF0.toByte(), 0x7E, 0x03, 0x7F, 0x21, 0xF7.toByte()), byteArrayOf(
                0xB0.toByte(), 0x07, 0x52
            ), byteArrayOf(0xB0.toByte(), 0x0A, 0x63)
        )
        checkSequence(original, expected, timestamp)
    }

    // Split a SysEx across 3 messages.
    @Test
    @Throws(IOException::class)
    fun testSplitSysEx() {
        val timestamp = 837518L
        val original = arrayOf(
            byteArrayOf(MidiConstants.STATUS_SYSTEM_EXCLUSIVE, 0x7E),
            byteArrayOf(0x03, 0x7F),
            byteArrayOf(0x21, 0xF7.toByte())
        )
        val expected = arrayOf(
            byteArrayOf(0xF0.toByte(), 0x7E),
            byteArrayOf(0x03, 0x7F),
            byteArrayOf(0x21, 0xF7.toByte())
        )
        checkSequence(original, expected, timestamp)
    }

    // RealTime in the middle of a SysEx
    @Test
    @Throws(IOException::class)
    fun testRealSysEx() {
        val timestamp = 837518L
        val original = arrayOf(
            byteArrayOf(
                MidiConstants.STATUS_SYSTEM_EXCLUSIVE, 0x7E,
                0x03, MidiConstants.STATUS_TIMING_CLOCK, 0x7F, 0x21,
                0xF7.toByte()
            )
        )
        val expected = arrayOf(
            byteArrayOf(0xF0.toByte(), 0x7E, 0x03), byteArrayOf(
                0xF8.toByte()
            ), byteArrayOf(0x7F, 0x21, 0xF7.toByte())
        )
        checkSequence(original, expected, timestamp)
    }
}