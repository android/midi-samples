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

import android.media.midi.MidiInputPort
import android.util.Log
import java.time.LocalDateTime
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.experimental.and
import kotlin.random.Random

// The MIDI 2 API is currently experimental and likely to change.
class MidiCiInitiator {
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

    fun setupMidiCI(midiReceiver: WaitingMidiReceiver, inputPort: MidiInputPort, groupId : Int,
                    deviceManufacturer: ByteArray, useFunctionBlocks: Boolean) : Boolean {
        val discoveryHelper = MidiCIDiscoveryHelper()
        val sysExConverter = MidiUmpSysExConverter()

        discoveryHelper.setSourceMuid(generateSourceMuid())
        if (deviceManufacturer.size != 3) {
            Log.e(TAG, "Device Manufacturer invalid size")
            return false
        }
        discoveryHelper.setSourceDeviceManufacturer(deviceManufacturer)

        if (useFunctionBlocks) {
            discoveryHelper.setMidiCiMessageVersion(MidiCIDiscoveryHelper.MIDI_CI_MESSAGE_VERSION_2)
        } else {
            discoveryHelper.setMidiCiMessageVersion(MidiCIDiscoveryHelper.MIDI_CI_MESSAGE_VERSION_1)
        }

        val discoveryMessage = discoveryHelper.generateDiscoveryMessage()
        val discoveryMessageUmp = sysExConverter.addUmpFramingToSysExMessage(discoveryMessage, groupId)
        inputPort.send(discoveryMessageUmp, 0, discoveryMessageUmp.size)
        logByteArray("discoveryMessage: ", discoveryMessageUmp, 0, discoveryMessageUmp.size)

        var discoveryReplyMessageUmp = waitForSysExMessage(midiReceiver, DISCOVERY_REPLY_TIMEOUT_MILLIS, groupId)
        while (discoveryReplyMessageUmp.isNotEmpty()) {
            val discoveryReplyMessage =
                sysExConverter.removeUmpFramingFromUmpSysExMessage(discoveryReplyMessageUmp)
            logByteArray("discoveryReplyMessage: ", discoveryReplyMessageUmp, 0, discoveryReplyMessageUmp.size)
            if (discoveryHelper.parseDiscoveryReply(discoveryReplyMessage)) {
                break
            }
            // Try again if message is bad
            discoveryReplyMessageUmp = waitForSysExMessage(midiReceiver, DISCOVERY_REPLY_TIMEOUT_MILLIS, groupId)
        }
        if (discoveryReplyMessageUmp.isEmpty()) {
            Log.e(TAG, "No discoveryReplyMessage received")
            return false
        }

        if (useFunctionBlocks) {
            val endpointDiscoveryMessage =
                discoveryHelper.generateEndpointDiscoveryMessage()
            inputPort.send(endpointDiscoveryMessage, 0, endpointDiscoveryMessage.size)
            logByteArray(
                "endpointDiscoveryMessage: ",
                endpointDiscoveryMessage,
                0,
                endpointDiscoveryMessage.size
            )

            var endpointInfoNotificationMessage =
                waitForGrouplessMessage(midiReceiver, DISCOVERY_REPLY_TIMEOUT_MILLIS)
            while (endpointInfoNotificationMessage.isNotEmpty()) {
                logByteArray(
                    "endpointInfoNotificationMessage: ",
                    endpointInfoNotificationMessage,
                    0,
                    endpointInfoNotificationMessage.size
                )
                if (discoveryHelper.parseEndpointInfoNotificationMessage(endpointInfoNotificationMessage)) {
                    break
                }
                // Try again if message is bad
                endpointInfoNotificationMessage =
                    waitForGrouplessMessage(midiReceiver, DISCOVERY_REPLY_TIMEOUT_MILLIS)
            }
            if (endpointInfoNotificationMessage.isEmpty()) {
                Log.e(TAG, "No endpointInfoNotificationMessage received")
                return false
            }

            val streamConfigurationRequestMessage =
                discoveryHelper.generateStreamConfigurationRequestMessage()
            inputPort.send(streamConfigurationRequestMessage, 0, streamConfigurationRequestMessage.size)
            logByteArray(
                "streamConfigurationRequestMessage: ",
                streamConfigurationRequestMessage,
                0,
                streamConfigurationRequestMessage.size
            )

            var streamConfigurationNotificationMessage =
                waitForGrouplessMessage(midiReceiver, DISCOVERY_REPLY_TIMEOUT_MILLIS)
            while (streamConfigurationNotificationMessage.isNotEmpty()) {
                logByteArray(
                    "streamConfigurationNotificationMessage: ",
                    streamConfigurationNotificationMessage,
                    0,
                    streamConfigurationNotificationMessage.size
                )
                if (discoveryHelper.parseStreamConfigurationNotificationMessage
                        (streamConfigurationNotificationMessage)) {
                    break
                }
                // Try again if message is bad
                streamConfigurationNotificationMessage =
                    waitForGrouplessMessage(midiReceiver, DISCOVERY_REPLY_TIMEOUT_MILLIS)
            }
            if (streamConfigurationNotificationMessage.isEmpty()) {
                Log.e(TAG, "No streamConfigurationNotificationMessage received")
                return false
            }
        } else {
            val initiateProtocolMessage =
                discoveryHelper.generateInitiateProtocolNegotiationMessage()
            val initiateProtocolMessageUmp =
                sysExConverter.addUmpFramingToSysExMessage(initiateProtocolMessage, groupId)
            inputPort.send(initiateProtocolMessageUmp, 0, initiateProtocolMessageUmp.size)
            logByteArray(
                "initiateProtocolMessage: ",
                initiateProtocolMessageUmp,
                0,
                initiateProtocolMessageUmp.size
            )

            var initiateReplyMessageUmp =
                waitForSysExMessage(midiReceiver, DISCOVERY_REPLY_TIMEOUT_MILLIS, groupId)
            while (initiateReplyMessageUmp.isNotEmpty()) {
                val initiateReplyMessage =
                    sysExConverter.removeUmpFramingFromUmpSysExMessage(initiateReplyMessageUmp)
                logByteArray(
                    "initiateReplyMessage: ",
                    initiateReplyMessageUmp,
                    0,
                    initiateReplyMessageUmp.size
                )
                if (discoveryHelper.parseInitiateProtocolNegotiationReply(initiateReplyMessage)) {
                    break
                }
                // Try again if message is bad
                initiateReplyMessageUmp =
                    waitForSysExMessage(midiReceiver, DISCOVERY_REPLY_TIMEOUT_MILLIS, groupId)
            }
            if (initiateReplyMessageUmp.isEmpty()) {
                Log.e(TAG, "No initiateReplyMessage received")
                return false
            }

            val setNewProtocolMessage = discoveryHelper.generateSetNewProtocolMessage()
            val setNewProtocolMessageUmp =
                sysExConverter.addUmpFramingToSysExMessage(setNewProtocolMessage, groupId)
            inputPort.send(setNewProtocolMessageUmp, 0, setNewProtocolMessageUmp.size)
            logByteArray(
                "setNewProtocolMessage: ",
                setNewProtocolMessageUmp,
                0,
                setNewProtocolMessageUmp.size
            )
            TimeUnit.MILLISECONDS.sleep(PAUSE_TIME_FOR_SWITCHING_MILLIS)

            val newProtocolInitiatorMessage = discoveryHelper.generateNewProtocolInitiatorMessage()
            val newProtocolInitiatorMessageUmp =
                sysExConverter.addUmpFramingToSysExMessage(newProtocolInitiatorMessage, groupId)
            inputPort.send(newProtocolInitiatorMessageUmp, 0, newProtocolInitiatorMessageUmp.size)
            logByteArray(
                "newProtocolInitiatorMessage: ",
                newProtocolInitiatorMessageUmp,
                0,
                newProtocolInitiatorMessageUmp.size
            )

            var newProtocolReplyMessageUmp =
                waitForSysExMessage(midiReceiver, NEW_PROTOCOL_REPLY_TIMEOUT_MILLIS, groupId)
            while (newProtocolReplyMessageUmp.isNotEmpty()) {
                val newProtocolReplyMessage =
                    sysExConverter.removeUmpFramingFromUmpSysExMessage(newProtocolReplyMessageUmp)
                logByteArray(
                    "newProtocolReplyMessage: ",
                    newProtocolReplyMessageUmp,
                    0,
                    newProtocolReplyMessageUmp.size
                )
                if (discoveryHelper.parseNewProtocolResponderReply(newProtocolReplyMessage)) {
                    break
                }
                // Try again if message is bad
                newProtocolReplyMessageUmp =
                    waitForSysExMessage(midiReceiver, NEW_PROTOCOL_REPLY_TIMEOUT_MILLIS, groupId)
            }
            if (newProtocolReplyMessageUmp.isEmpty()) {
                Log.e(TAG, "No newProtocolReplyMessage received")
                return false
            }

            val confirmationMessage = discoveryHelper.generateConfirmationNewProtocolMessage()
            val confirmationMessageUmp =
                sysExConverter.addUmpFramingToSysExMessage(confirmationMessage, groupId)
            inputPort.send(confirmationMessageUmp, 0, confirmationMessageUmp.size)
        }

        Log.d(TAG, "Done with CI")

        return true
    }

    private fun verifyMessageIsMidiSysExMessage(message: ByteArray, groupId: Int): Boolean {
        if ((message.isEmpty()) || (message.size % UMP_PACKET_MULTIPLE != 0)) {
            return false
        }

        val targetFirstByte = (SYSEX_DATA_MESSAGE_TYPE shl 4) + groupId
        return (message[0] == targetFirstByte.toByte())
    }

    private fun verifyMessageIsGrouplessMessage(message: ByteArray): Boolean {
        if ((message.isEmpty()) || (message.size % UMP_PACKET_MULTIPLE != 0)) {
            return false
        }

        // mt = f, f = 0. Last two bits can be anything.
        val targetFirstByte = (GROUPLESS_MESSAGE_TYPE shl 4)
        return ((message[0] and 0xfc.toByte()) == targetFirstByte.toByte())
    }

    private fun checkMessageStartsWithSysExStart(message: ByteArray): Boolean {
        val curByte = 1
        if (curByte < message.size) {
            if ((message[curByte] and 0xF0.toByte()) == SYSEX_PACKET_START) {
                return true
            }
        }
        return false
    }

    private fun checkMessageContainsSysExEnd(message: ByteArray): Boolean {
        var curByte = 1
        while (curByte < message.size) {
            if ((message[curByte] and 0xF0.toByte()) == SYSEX_PACKET_END) {
                return true
            }
            curByte += 8
        }
        return false
    }

    // Waits for a SysEx message from a certain group. Ignores all other messages.
    private fun waitForSysExMessage(midiReceiver: WaitingMidiReceiver, timeoutMs : Long, groupId: Int)
            : ByteArray {
        val startTime = LocalDateTime.now()
        var currentTime = startTime
        val endTime = startTime.plus(timeoutMs, ChronoField.MILLI_OF_DAY.baseUnit)
        var outputMessage = ByteArray(0)
        while (endTime > currentTime) {
            val remainingDurationMs = ChronoUnit.MILLIS.between(currentTime, endTime)
            midiReceiver.waitForMessages(midiReceiver.readCount + 1,
                    remainingDurationMs.toInt())
            while (midiReceiver.readCount < midiReceiver.messageCount) {
                val currentMessage = midiReceiver.getMessage(midiReceiver.readCount).data
                logByteArray("received: ", currentMessage, 0, currentMessage.size)
                midiReceiver.readCount++
                // Assume that all SysEx messages from the intended group has to be the wanted reply
                if (verifyMessageIsMidiSysExMessage(currentMessage, groupId)) {
                    if (checkMessageStartsWithSysExStart(currentMessage)) {
                        outputMessage = ByteArray(0)
                    }
                    outputMessage += currentMessage
                    if (checkMessageContainsSysExEnd(currentMessage)) {
                        return outputMessage
                    }
                }
            }
            currentTime = LocalDateTime.now()

        }
        return outputMessage
    }

    // Waits for a groupless message. Ignores all other messages.
    private fun waitForGrouplessMessage(midiReceiver: WaitingMidiReceiver, timeoutMs : Long)
            : ByteArray {
        val startTime = LocalDateTime.now()
        var currentTime = startTime
        val endTime = startTime.plus(timeoutMs, ChronoField.MILLI_OF_DAY.baseUnit)
        var outputMessage = ByteArray(0)
        var isGroupless = false
        while (endTime > currentTime) {
            val remainingDurationMs = ChronoUnit.MILLIS.between(currentTime, endTime)
            midiReceiver.waitForMessages(midiReceiver.readCount + 1,
                remainingDurationMs.toInt())
            while (midiReceiver.readCount < midiReceiver.messageCount) {
                val currentMessage = midiReceiver.getMessage(midiReceiver.readCount).data
                logByteArray("received: ", currentMessage, 0, currentMessage.size)
                midiReceiver.readCount++

                // If at least one groupless message has been received, return it
                isGroupless = isGroupless || verifyMessageIsGrouplessMessage(currentMessage)
                if (isGroupless) {
                    outputMessage += currentMessage
                }

                if (outputMessage.size >= GROUPLESS_MESSAGE_SIZE) {
                    return outputMessage
                }
            }
            currentTime = LocalDateTime.now()

        }
        return outputMessage
    }

    private fun generateSourceMuid(): ByteArray {
        // 28 bits in 4 bytes
        val bytes = Random.Default.nextBytes(4)
        bytes[0] = bytes[0] and 0x7F.toByte()
        bytes[1] = bytes[1] and 0x7F.toByte()
        bytes[2] = bytes[2] and 0x7F.toByte()
        bytes[3] = bytes[3] and 0x7F.toByte()
        return bytes
    }

    companion object {
        const val TAG = "MidiCIInitiator"
        const val SYSEX_DATA_MESSAGE_TYPE = 0x3
        const val GROUPLESS_MESSAGE_TYPE = 0xf
        const val GROUPLESS_MESSAGE_SIZE = 16
        const val SYSEX_PACKET_END = 0x30.toByte()
        const val SYSEX_PACKET_START = 0x10.toByte()
        const val UMP_PACKET_MULTIPLE = 8
        const val DISCOVERY_REPLY_TIMEOUT_MILLIS = 3000.toLong()
        const val PAUSE_TIME_FOR_SWITCHING_MILLIS = 100.toLong()
        const val NEW_PROTOCOL_REPLY_TIMEOUT_MILLIS = 300.toLong()
    }
}