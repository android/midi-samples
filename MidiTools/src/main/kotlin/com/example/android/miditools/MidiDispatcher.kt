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
import android.media.midi.MidiSender
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.Throws

/**
 * Utility class for dispatching MIDI data to a list of [MidiReceiver]s.
 * This class subclasses [MidiReceiver] and dispatches any data it receives
 * to its receiver list. Any receivers that throw an exception upon receiving data will
 * be automatically removed from the receiver list, but no IOException will be returned
 * from the dispatcher's MidiReceiver.onReceive in that case.
 */
class MidiDispatcher : MidiReceiver() {
    private val mReceivers = CopyOnWriteArrayList<MidiReceiver>()

    /**
     * Returns a [MidiSender] which is used to add and remove
     * [MidiReceiver]s
     * to the dispatcher's receiver list.
     * @return the dispatcher's MidiSender
     */
    val sender: MidiSender = object : MidiSender() {
        /**
         * Called to connect a [MidiReceiver] to the sender
         *
         * @param receiver the receiver to connect
         */
        override fun onConnect(receiver: MidiReceiver) {
            mReceivers.add(receiver)
        }

        /**
         * Called to disconnect a [MidiReceiver] from the sender
         *
         * @param receiver the receiver to disconnect
         */
        override fun onDisconnect(receiver: MidiReceiver) {
            mReceivers.remove(receiver)
        }
    }

    @Throws(IOException::class)
    override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
        for (receiver in mReceivers) {
            try {
                receiver.send(msg, offset, count, timestamp)
            } catch (e: IOException) {
                // if the receiver fails we remove the receiver but do not propagate the exception
                mReceivers.remove(receiver)
            }
        }
    }

    @Throws(IOException::class)
    override fun flush() {
        for (receiver in mReceivers) {
            receiver.flush()
        }
    }
}