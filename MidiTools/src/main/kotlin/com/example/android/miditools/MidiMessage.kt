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

// Store complete MIDI message so it can be put in an array.
class MidiMessage(buffer: ByteArray?, offset: Int, length: Int, timestamp: Long) {
    val data: ByteArray = ByteArray(length)
    val timestamp: Long

    init {
        if (buffer != null) {
            System.arraycopy(buffer, offset, data, 0, length)
        }
        this.timestamp = timestamp
    }
}