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

import java.io.IOException

class MidiUmpSysExConverter {

    /**
     * @inputData array of MIDI 1.0 SysEx bytes without the 0xf0 at the beginning and 0xf7 at the
     * end of the message
     * @group the Group ID to assign to the output UMP packets with
     * @return array of bytes containing 7-bit SysEx data UMP packets
     */
    fun addUmpFramingToSysExMessage(inputData: ByteArray, group: Int): ByteArray {
        // Every 6 bytes of input becomes 8 bytes of output
        val numberOfOutputPackets = (inputData.size + NUM_SYSEX_BYTES_PER_PACKET - 1) /
                NUM_SYSEX_BYTES_PER_PACKET
        val outputSize = numberOfOutputPackets * UMP_PACKET_SIZE
        val outputData = ByteArray(outputSize)

        for (i in 0 until numberOfOutputPackets) {
            var packetSize = NUM_SYSEX_BYTES_PER_PACKET
            if (i == numberOfOutputPackets - 1) {
                packetSize = inputData.size - NUM_SYSEX_BYTES_PER_PACKET * i
            }

            var status = STATUS_SYSTEM_EXCLUSIVE_CONTINUE_UMP
            when {
                numberOfOutputPackets == 1 -> {
                    status = STATUS_COMPLETE_SYSTEM_EXCLUSIVE_UMP
                }
                i == 0 -> {
                    status = STATUS_SYSTEM_EXCLUSIVE_START_UMP
                }
                i == numberOfOutputPackets - 1 -> {
                    status = STATUS_SYSTEM_EXCLUSIVE_END_UMP
                }
            }

            outputData[i * UMP_PACKET_SIZE] = (group + (UMP_8_BYTE_DATA_MESSAGE_TYPE shl 4))
                    .toByte()
            outputData[i * UMP_PACKET_SIZE + 1] = ((status shl 4) + packetSize).toByte()
            inputData.copyInto(outputData,
                    i * UMP_PACKET_SIZE + 2,
                    i * NUM_SYSEX_BYTES_PER_PACKET,
                    i * NUM_SYSEX_BYTES_PER_PACKET + packetSize)
        }
        return outputData
    }

    /**
     * @inputData array of bytes containing 7-bit SysEx data UMP packets
     * @return array of MIDI 1.0 SysEx bytes without the 0xf0 at the beginning and 0xf7 at the end
     * of the message
     */
    fun removeUmpFramingFromUmpSysExMessage(inputData: ByteArray): ByteArray {
        // Every 6 bytes of input becomes 8 bytes of output
        val numberOfOutputPackets = inputData.size / UMP_PACKET_SIZE
        var outputData = ByteArray(0)

        for (i in 0 until numberOfOutputPackets) {
            // size of packet is high nibble in the second byte of an UMP packet
            val sysExSizeOfPacket = inputData[i * UMP_PACKET_SIZE + 1].toInt() and 0x0f
            if (sysExSizeOfPacket > NUM_SYSEX_BYTES_PER_PACKET) {
                throw IOException()
            }
            outputData += inputData.copyOfRange(i * UMP_PACKET_SIZE + 2,
                    i * UMP_PACKET_SIZE + 2 + sysExSizeOfPacket)
        }
        return outputData
    }

    companion object {
        const val UMP_8_BYTE_DATA_MESSAGE_TYPE = 0x3
        const val UMP_PACKET_SIZE = 8
        const val NUM_SYSEX_BYTES_PER_PACKET = 6
        const val STATUS_COMPLETE_SYSTEM_EXCLUSIVE_UMP = 0x0
        const val STATUS_SYSTEM_EXCLUSIVE_START_UMP = 0x1
        const val STATUS_SYSTEM_EXCLUSIVE_CONTINUE_UMP = 0x2
        const val STATUS_SYSTEM_EXCLUSIVE_END_UMP = 0x3
    }
}