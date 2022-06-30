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

import android.util.Log
import org.junit.Assert
import org.junit.Test

class TestMidiUmpFramer {

    private fun compareByteArrays(expectedArray: ByteArray, outputArray: ByteArray) {
        Assert.assertEquals(expectedArray.size.toLong(), outputArray.size.toLong())
        for (i in outputArray.indices) {
            Assert.assertEquals(expectedArray[i], outputArray[i])
        }
    }

    @Test
    fun testAddData() {
        val midiConverter = MidiUmpSysExConverter()
        val inputBytes = byteArrayOf(
            0x7E.toByte(), 0x7F.toByte(), 0x0D.toByte(), 0x70.toByte(), 0x01.toByte(),
            0x67.toByte(), 0x45.toByte(), 0x23.toByte(), 0x01.toByte(),
            0x7f.toByte(), 0x7f.toByte(), 0x7f.toByte(), 0x7f.toByte(),
            0x93.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x22.toByte(), 0x33.toByte(),
            0x11.toByte(), 0x00.toByte(),
            0x12.toByte(), 0x34.toByte(), 0x56.toByte(), 0x78.toByte(),
            0x07.toByte(),
            0x00.toByte(), 0x02.toByte(), 0x00.toByte(), 0x00.toByte(),
        )
        val expectedOutputBytes = byteArrayOf(
            0x30.toByte(), 0x16.toByte(), 0x7E.toByte(), 0x7F.toByte(),
            0x0D.toByte(), 0x70.toByte(), 0x01.toByte(), 0x67.toByte(),
            0x30.toByte(), 0x26.toByte(), 0x45.toByte(), 0x23.toByte(),
            0x01.toByte(), 0x7f.toByte(), 0x7f.toByte(), 0x7f.toByte(),
            0x30.toByte(), 0x26.toByte(), 0x7f.toByte(), 0x93.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x22.toByte(), 0x33.toByte(),
            0x30.toByte(), 0x26.toByte(), 0x11.toByte(), 0x00.toByte(),
            0x12.toByte(), 0x34.toByte(), 0x56.toByte(), 0x78.toByte(),
            0x30.toByte(), 0x35.toByte(), 0x07.toByte(), 0x00.toByte(),
            0x02.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        )
        val actualOutputBytes = midiConverter.addUmpFramingToSysExMessage(inputBytes, 0)
        compareByteArrays(expectedOutputBytes, actualOutputBytes)
        val reconvertedInputBytes = midiConverter.removeUmpFramingFromUmpSysExMessage(
                actualOutputBytes)
        logByteArray("Actual: ", reconvertedInputBytes, 0, reconvertedInputBytes.size)
        logByteArray("Expected: ", inputBytes, 0, inputBytes.size)
        compareByteArrays(inputBytes, reconvertedInputBytes)
    }

    private fun logByteArray(prefix: String, value: ByteArray, offset: Int, count: Int) {
        val builder = StringBuilder(prefix)
        for (i in offset until offset + count) {
            builder.append(String.format("0x%02X", value[i]))
            if (i != value.size - 1) {
                builder.append(", ")
            }
        }
        Log.d(TAG, builder.toString())
    }


    companion object {
        private const val TAG = "TestMidiUmpFramer"
    }

}