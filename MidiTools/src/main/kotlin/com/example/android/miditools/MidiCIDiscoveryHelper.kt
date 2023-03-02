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

// The MIDI 2 API is currently experimental and likely to change.
class MidiCIDiscoveryHelper {
    private var mSourceDeviceManufacturer : ByteArray = NON_COMMERCIAL_SYSEX_ID
    private var mSourceMuid : ByteArray = byteArrayOf(0x01, 0x23, 0x45, 0x67)
    private var mDestinationCiVersion = 0x00.toByte()
    private var mDestinationMuid : ByteArray = byteArrayOf(0x00, 0x00, 0x00, 0x00)
    private var mDestinationDeviceManufacturer = byteArrayOf(0x00, 0x00, 0x00)
    private var mDestinationDeviceFamily = byteArrayOf(0x00, 0x00)
    private var mDestinationModelNumber = byteArrayOf(0x00, 0x00)
    private var mDestinationSoftwareRevision = byteArrayOf(0x00, 0x00, 0x00, 0x00)
    private var mDestinationCiCategory = 0x00.toByte()
    private var mDestinationMaxSysExSize = byteArrayOf(0x00, 0x00, 0x00, 0x00)
    private var mDestinationAuthorityLevel = 0x00.toByte()
    private var mDestinationNumSupportedProtocols = 0x00.toByte()
    private var mDestinationSupportedProtocols = byteArrayOf()

    fun getDestinationCiVersion() : Byte {
        return mDestinationCiVersion
    }

    fun getDestinationMuid() : ByteArray {
        return mDestinationMuid
    }

    fun getDestinationDeviceManufacturer() : ByteArray {
        return mDestinationDeviceManufacturer
    }

    fun getDestinationDeviceFamily() : ByteArray {
        return mDestinationDeviceFamily
    }

    fun getDestinationModelNumber() : ByteArray {
        return mDestinationModelNumber
    }

    fun getDestinationSoftwareRevision() : ByteArray {
        return mDestinationSoftwareRevision
    }

    fun getDestinationCiCategory() : Byte {
        return mDestinationCiCategory
    }

    fun getDestinationMaxSysExSize() : ByteArray {
        return mDestinationMaxSysExSize
    }

    fun getDestinationAuthorityLevel() : Byte {
        return mDestinationAuthorityLevel
    }

    fun setSourceMuid(sourceMuid: ByteArray) {
        mSourceMuid = sourceMuid
    }

    fun setSourceDeviceManufacturer(deviceManufacturer: ByteArray) {
        mSourceDeviceManufacturer = deviceManufacturer
    }

    fun generateDiscoveryMessage() : ByteArray {
        var discoveryMessage = ByteArray(0)
        discoveryMessage += UNIVERSAL_SYSTEM_EXCLUSIVE
        discoveryMessage += FROM_TO_MIDI_PORT_DEVICE_ID
        discoveryMessage += UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_1_MIDI_CI
        discoveryMessage += UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_2_DISCOVERY
        discoveryMessage += MIDI_CI_MESSAGE_VERSION
        discoveryMessage += mSourceMuid
        discoveryMessage += BROADCAST_DESTINATION_MUID
        discoveryMessage += mSourceDeviceManufacturer
        discoveryMessage += DEVICE_FAMILY
        discoveryMessage += MODEL_NUMBER
        discoveryMessage += SOFTWARE_REVISION
        discoveryMessage += CAPABILITY_INQUIRY_CATEGORY_SUPPORTED
        discoveryMessage += RECEIVABLE_MAXIMUM_SYSEX_MESSAGE_SIZE

        return discoveryMessage
    }

    fun parseDiscoveryReply(message : ByteArray) : Boolean {
        var index = 0
        if (message.size != DISCOVERY_REPLY_TARGET_SIZE) {
            Log.d(TAG, "message size (" + message.size + ") not target size ("
                    + DISCOVERY_REPLY_TARGET_SIZE + ")")
            return false
        }
        if (message[index] != UNIVERSAL_SYSTEM_EXCLUSIVE) {
            Log.d(TAG, "Byte (" + message[index] + ") not system exclusive ("
                    + UNIVERSAL_SYSTEM_EXCLUSIVE + ")")
            return false
        }
        index++
        if (message[index] != FROM_TO_MIDI_PORT_DEVICE_ID) {
            Log.d(TAG, "Byte (" + message[index] + ") not from/to port ("
                    + FROM_TO_MIDI_PORT_DEVICE_ID + ")")
            return false
        }
        index++
        if (message[index] != UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_1_MIDI_CI) {
            Log.d(TAG, "Byte (" + message[index] + ") not MIDI-CI ("
                    + UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_1_MIDI_CI + ")")
            return false
        }
        index++
        if (message[index] != UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_2_REPLY_TO_DISCOVERY) {
            Log.d(TAG, "Byte (" + message[index] + ") not reply to discovery ("
                    + UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_2_REPLY_TO_DISCOVERY + ")")
            return false
        }
        index++
        mDestinationCiVersion = message[index]
        index++
        mDestinationMuid = message.copyOfRange(index, index + mDestinationMuid.size)
        index += mDestinationMuid.size
        val sourceMuid = message.copyOfRange(index, index + mSourceMuid.size)
        if (!sourceMuid.contentEquals(mSourceMuid)) {
            Log.d(TAG, "ByteArray (" + String(sourceMuid) + ") not same as set source MUID ("
                    + String(mSourceMuid) + ")")
            return false
        }
        index += sourceMuid.size
        mDestinationDeviceManufacturer = message.copyOfRange(index,
                index + mDestinationDeviceManufacturer.size)
        index += mDestinationDeviceManufacturer.size
        mDestinationDeviceFamily = message.copyOfRange(index,
            index + mDestinationDeviceFamily.size)
        index += mDestinationDeviceFamily.size
        mDestinationModelNumber = message.copyOfRange(index,
            index + mDestinationModelNumber.size)
        index += mDestinationModelNumber.size
        mDestinationSoftwareRevision = message.copyOfRange(index,
            index + mDestinationSoftwareRevision.size)
        index += mDestinationSoftwareRevision.size
        mDestinationCiCategory = message[index]
        index++
        mDestinationMaxSysExSize = message.copyOfRange(index,
            index + mDestinationMaxSysExSize.size)
        index += mDestinationMaxSysExSize.size
        return true
    }

    fun generateInitiateProtocolNegotiationMessage() : ByteArray {
        var negotiationMessage = ByteArray(0)
        negotiationMessage += UNIVERSAL_SYSTEM_EXCLUSIVE
        negotiationMessage += FROM_TO_MIDI_PORT_DEVICE_ID
        negotiationMessage += UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_1_MIDI_CI
        negotiationMessage += UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_2_INITIATE_PROTOCOL_NEGOTIATION
        negotiationMessage += MIDI_CI_MESSAGE_VERSION
        negotiationMessage += mSourceMuid
        negotiationMessage += mDestinationMuid
        negotiationMessage += AUTHORITY_LEVEL
        negotiationMessage += NUM_SUPPORTED_PROTOCOLS
        negotiationMessage += MIDI_2_0_PROTOCOL_BYTES
        negotiationMessage += MIDI_1_0_PROTOCOL_BYTES

        return negotiationMessage
    }

    fun parseInitiateProtocolNegotiationReply(message : ByteArray) : Boolean {
        var index = 0
        if (message.size < INITIALIZE_PROTOCOL_NEGOTIATION_REPLY_MIN_SIZE) {
            Log.d(TAG, "message size (" + message.size + ") smaller than min size ("
                    + INITIALIZE_PROTOCOL_NEGOTIATION_REPLY_MIN_SIZE + ")")
            return false
        }
        if (message[index] != UNIVERSAL_SYSTEM_EXCLUSIVE) {
            Log.d(TAG, "Byte (" + message[index] + ") not system exclusive ("
                    + UNIVERSAL_SYSTEM_EXCLUSIVE + ")")
            return false
        }
        index++
        if (message[index] != FROM_TO_MIDI_PORT_DEVICE_ID) {
            Log.d(TAG, "Byte (" + message[index] + ") not from/to port ("
                    + FROM_TO_MIDI_PORT_DEVICE_ID + ")")
            return false
        }
        index++
        if (message[index] != UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_1_MIDI_CI) {
            Log.d(TAG, "Byte (" + message[index] + ") not MIDI-CI ("
                    + UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_1_MIDI_CI + ")")
            return false
        }
        index++
        if (message[index]
                != UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_2_REPLY_TO_INITIATE_PROTOCOL_NEGOTIATION) {
            Log.d(TAG, "Byte (" + message[index] + ") not reply to initialize protocol "
                    + "negotiation ("
                    + UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_2_REPLY_TO_INITIATE_PROTOCOL_NEGOTIATION
                    + ")")
            return false
        }
        index++
        if (message[index] != mDestinationCiVersion) {
            Log.d(TAG, "Byte (" + message[index] + ") not destination CI version ("
                    + mDestinationCiVersion + ")")
            return false
        }
        index++
        val destinationMuid = message.copyOfRange(index, index + mDestinationMuid.size)
        if (!destinationMuid.contentEquals(mDestinationMuid)) {
            Log.d(TAG, "ByteArray (" + String(destinationMuid) + ") not same as set "
                    + "destination MUID (" + String(mDestinationMuid) + ")")
            return false
        }
        index += mDestinationMuid.size
        val sourceMuid = message.copyOfRange(index, index + mSourceMuid.size)
        if (!sourceMuid.contentEquals(mSourceMuid)) {
            Log.d(TAG, "ByteArray (" + String(sourceMuid) + ") not same as set source MUID ("
                    + String(mSourceMuid) + ")")
            return false
        }
        index += sourceMuid.size
        mDestinationAuthorityLevel = message[index]
        index++
        mDestinationNumSupportedProtocols = message[index]
        index++
        if (mDestinationNumSupportedProtocols * SUPPORTED_PROTOCOL_SIZE + index != message.size) {
            Log.d(TAG, "message size left (" + (message.size - index) + ") smaller than ("
                    + mDestinationNumSupportedProtocols * SUPPORTED_PROTOCOL_SIZE + ")")
            return false
        }
        mDestinationSupportedProtocols = message.copyOfRange(index, message.size)
        index += mDestinationSupportedProtocols.size

        for (i in 0 until mDestinationNumSupportedProtocols) {
            if (mDestinationSupportedProtocols[i * SUPPORTED_PROTOCOL_SIZE]
                    == MIDI_2_0_PROTOCOL_BYTES[0]) {
                return true
            }
        }
        Log.d(TAG, "MIDI 2.0 (" + MIDI_2_0_PROTOCOL_BYTES[0] + ") not supported")
        return false
    }

    fun generateSetNewProtocolMessage() : ByteArray {
        var newProtocolMessage = ByteArray(0)
        newProtocolMessage += UNIVERSAL_SYSTEM_EXCLUSIVE
        newProtocolMessage += FROM_TO_MIDI_PORT_DEVICE_ID
        newProtocolMessage += UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_1_MIDI_CI
        newProtocolMessage += UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_2_SET_NEW_SELECTED_PROTOCOL
        newProtocolMessage += MIDI_CI_MESSAGE_VERSION
        newProtocolMessage += mSourceMuid
        newProtocolMessage += mDestinationMuid
        newProtocolMessage += AUTHORITY_LEVEL
        newProtocolMessage += MIDI_2_0_PROTOCOL_BYTES

        return newProtocolMessage
    }

    fun generateNewProtocolInitiatorMessage() : ByteArray {
        var initiatorMessage = ByteArray(0)
        initiatorMessage += UNIVERSAL_SYSTEM_EXCLUSIVE
        initiatorMessage += FROM_TO_MIDI_PORT_DEVICE_ID
        initiatorMessage += UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_1_MIDI_CI
        initiatorMessage += UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_2_TEST_NEW_PROTOCOL_INITIATOR
        initiatorMessage += MIDI_CI_MESSAGE_VERSION
        initiatorMessage += mSourceMuid
        initiatorMessage += mDestinationMuid
        initiatorMessage += AUTHORITY_LEVEL
        for (i in 0 until TEST_NUMBER_COUNT) {
            initiatorMessage += i.toByte()
        }
        return initiatorMessage
    }

    fun parseNewProtocolResponderReply(message : ByteArray) : Boolean {
        var index = 0
        if (message.size != NEW_PROTOCOL_REPLY_SIZE) {
            Log.d(TAG, "message size (" + message.size + ") not size ("
                    + NEW_PROTOCOL_REPLY_SIZE + ")")
            return false
        }
        if (message[index] != UNIVERSAL_SYSTEM_EXCLUSIVE) {
            Log.d(TAG, "Byte (" + message[index] + ") not system exclusive ("
                    + UNIVERSAL_SYSTEM_EXCLUSIVE + ")")
            return false
        }
        index++
        if (message[index] != FROM_TO_MIDI_PORT_DEVICE_ID) {
            Log.d(TAG, "Byte (" + message[index] + ") not from/to port ("
                    + FROM_TO_MIDI_PORT_DEVICE_ID + ")")
            return false
        }
        index++
        if (message[index] != UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_1_MIDI_CI) {
            Log.d(TAG, "Byte (" + message[index] + ") not MIDI-CI ("
                    + UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_1_MIDI_CI + ")")
            return false
        }
        index++
        if (message[index]
            != UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_2_TEST_NEW_PROTOCOL_RESPONDER) {
            Log.d(TAG, "Byte (" + message[index] + ") not test new protocol responder "
                    + "negotiation ("
                    + UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_2_TEST_NEW_PROTOCOL_RESPONDER
                    + ")")
            return false
        }
        index++
        if (message[index] != mDestinationCiVersion) {
            Log.d(TAG, "Byte (" + message[index] + ") not destination CI version ("
                    + mDestinationCiVersion + ")")
            return false
        }
        index++
        val destinationMuid = message.copyOfRange(index, index + mDestinationMuid.size)
        if (!destinationMuid.contentEquals(mDestinationMuid)) {
            Log.d(TAG, "ByteArray (" + String(destinationMuid) + ") not same as set "
                    + "destination MUID (" + String(mDestinationMuid) + ")")
            return false
        }
        index += mDestinationMuid.size
        val sourceMuid = message.copyOfRange(index, index + mSourceMuid.size)
        if (!sourceMuid.contentEquals(mSourceMuid)) {
            Log.d(TAG, "ByteArray (" + String(sourceMuid) + ") not same as set source MUID ("
                    + String(mSourceMuid) + ")")
            return false
        }
        index += sourceMuid.size
        if (message[index] != mDestinationAuthorityLevel) {
            Log.d(TAG, "Byte (" + message[index] + ") not destination authority level ("
                    + mDestinationAuthorityLevel + ")")
            return false
        }
        index++
        for (i in 0 until TEST_NUMBER_COUNT) {
            if (message[index] != i.toByte()) {
                Log.d(TAG, "Byte (" + message[index] + ") not test number ("
                        + i + ")")
                return false
            }
            index++
        }
        return true
    }

    fun generateConfirmationNewProtocolMessage() : ByteArray {
        var initiatorMessage = ByteArray(0)
        initiatorMessage += UNIVERSAL_SYSTEM_EXCLUSIVE
        initiatorMessage += FROM_TO_MIDI_PORT_DEVICE_ID
        initiatorMessage += UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_1_MIDI_CI
        initiatorMessage += UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_2_CONFIRMATION_NEW_PROTOCOL_ESTABLISHED
        initiatorMessage += MIDI_CI_MESSAGE_VERSION
        initiatorMessage += mSourceMuid
        initiatorMessage += mDestinationMuid
        initiatorMessage += AUTHORITY_LEVEL
        return initiatorMessage
    }

    companion object {
        const val TAG = "MidiCiDiscoveryHelper"

        val NON_COMMERCIAL_SYSEX_ID = byteArrayOf(0x7D, 0x00, 0x00)

        // Change these for your app
        val DEVICE_FAMILY = byteArrayOf(0x12, 0x34)
        val MODEL_NUMBER = byteArrayOf(0x66, 0x00)
        val SOFTWARE_REVISION = byteArrayOf(0x05, 0x06, 0x07, 0x08)

        const val UNIVERSAL_SYSTEM_EXCLUSIVE = 0x7E.toByte()
        const val FROM_TO_MIDI_PORT_DEVICE_ID = 0x7F.toByte()
        const val UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_1_MIDI_CI = 0x0D.toByte()
        const val UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_2_DISCOVERY = 0x70.toByte()
        const val UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_2_REPLY_TO_DISCOVERY = 0x71.toByte()
        const val UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_2_INITIATE_PROTOCOL_NEGOTIATION
                = 0x10.toByte()
        const val UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_2_REPLY_TO_INITIATE_PROTOCOL_NEGOTIATION
                = 0x11.toByte()
        const val UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_2_SET_NEW_SELECTED_PROTOCOL
                = 0x12.toByte()
        const val UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_2_TEST_NEW_PROTOCOL_INITIATOR
                = 0x13.toByte()
        const val UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_2_TEST_NEW_PROTOCOL_RESPONDER
                = 0x14.toByte()
        const val UNIVERSAL_SYSTEM_EXCLUSIVE_SUB_ID_2_CONFIRMATION_NEW_PROTOCOL_ESTABLISHED
                = 0x15.toByte()
        const val MIDI_CI_MESSAGE_VERSION = 0x01.toByte()
        val BROADCAST_DESTINATION_MUID = byteArrayOf(0x7F, 0x7F, 0x7F, 0x7F)
        const val CAPABILITY_INQUIRY_CATEGORY_SUPPORTED = 0x07.toByte()
        val RECEIVABLE_MAXIMUM_SYSEX_MESSAGE_SIZE = byteArrayOf(0x00, 0x20, 0x00, 0x00)

        const val DISCOVERY_REPLY_TARGET_SIZE = 29

        const val AUTHORITY_LEVEL = 0x6F.toByte()
        const val NUM_SUPPORTED_PROTOCOLS = 0x02.toByte()
        val MIDI_2_0_PROTOCOL_BYTES = byteArrayOf(0x02, 0x00, 0x00, 0x00, 0x00)
        val MIDI_1_0_PROTOCOL_BYTES = byteArrayOf(0x01, 0x02, 0x00, 0x00, 0x00)
        const val SUPPORTED_PROTOCOL_SIZE = 5

        const val INITIALIZE_PROTOCOL_NEGOTIATION_REPLY_MIN_SIZE = 15

        const val TEST_NUMBER_COUNT = 48
        const val NEW_PROTOCOL_REPLY_SIZE = 62
    }
}