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
package com.example.android.midiumpscope

import android.hardware.usb.UsbDevice
import android.media.midi.MidiDeviceInfo
import kotlin.experimental.and

/**
 * Format a MIDI message for printing.
 */
object MidiPrinter {
    private val SIZE_PER_MESSAGE_TYPE_BYTES = arrayOf(
        4, 4, 4, 8, 8, 16, 4, 4, 8, 8, 8, 12, 12, 16, 16, 16
    )

    private val MESSAGE_TYPE_NAMES = arrayOf(
        "Utility", "System", "MIDI 1 Voice",
        "64 bit data", "MIDI 2 voice", "128 bit data"
    )

    private const val MESSAGE_TYPE_UTILITY = 0x0
    private const val MESSAGE_TYPE_SYSTEM = 0x1
    private const val MESSAGE_TYPE_MIDI_1_CHANNEL_VOICE = 0x2
    private const val MESSAGE_TYPE_64_BIT_DATA_MESSAGE = 0x3
    private const val MESSAGE_TYPE_MIDI_2_CHANNEL_VOICE = 0x4
    private const val MESSAGE_TYPE_128_BIT_DATA_MESSAGE = 0x5

    private val UTILITY_MESSAGE_NAMES = arrayOf(
        "NOOP", "JitterReductionClock", "JitterReductionTimestamp"
    )
    private val SYSTEM_COMMON_COMMAND_NAMES = arrayOf(
        "F0",  // F0
        "TimeCode",  // F1
        "SongPos",  // F2
        "SongSel",  // F3
        "F4",  // F4
        "F5",  // F5
        "TuneReq",  // F6
        "EndSysEx",  // F7
        "TimingClock",  // F8
        "F9",  // F9
        "Start",  // FA
        "Continue",  // FB
        "Stop",  // FC
        "FD",  // FD
        "ActiveSensing",  // FE
        "Reset" // FF
    )
    private val MIDI_1_0_CHANNEL_COMMAND_NAMES = arrayOf(
        "NoteOff", "NoteOn",
        "PolyTouch", "Control", "Program", "Pressure", "Bend"
    )
    private const val MIDI_1_0_CHANNEL_COMMAND_FIRST_OPCODE = 8
    private val MIDI_2_0_CHANNEL_COMMAND_NAMES = arrayOf(
        "RegisteredPerNoteController", "AssignablePerNoteController",
        "RegisteredController", "AssignableController",
        "RelativeRegisteredController", "RelativeAssignableController",
        "PerNotePitchBend", "0b0111",
        "MIDI2NoteOff", "MIDI2NoteOn", "MIDI2PolyTouch", "MIDI2Control", "MIDI2Program",
        "MIDI2Pressure", "MIDI2Bend", "PerNoteManagement"
    )
    private val SYSTEM_EXCLUSIVE_STATUS_FIELD_NAMES = arrayOf(
        "Complete SysEx", "Start SysEx", "Continue SysEx", "End SysEx"
    )

    private const val USB_DEVICE_STRING = "usb_device"

    private fun formatBytes(data: ByteArray, offset: Int, count: Int): String {
        val sb = StringBuilder()
        for (i in 0 until count) {
            sb.append(String.format(" %02X", data[offset + i]))
        }
        return sb.toString()
    }

    private fun formatUmpMessage(data: ByteArray) : String {
        val sb = StringBuilder()
        val messageType = ((data[0].toInt() and 0xf0).toUInt() shr 4).toInt()
        val groupId = data[0] and 0x0f.toByte()
        if (messageType <= MESSAGE_TYPE_NAMES.size) {
            sb.appendLine("Message Type: " + MESSAGE_TYPE_NAMES[messageType])
        } else {
            sb.appendLine("Message Type: $messageType")
        }
        sb.appendLine("Group Id: " + (groupId + 1)) // Humans use 1-16
        if (messageType == MESSAGE_TYPE_UTILITY) {
            val status = ((data[1].toInt() and 0xf0).toUInt() shr 4).toInt()
            if (status <= UTILITY_MESSAGE_NAMES.size) {
                sb.appendLine("Utility Message Type: " + UTILITY_MESSAGE_NAMES[status])
            } else {
                sb.appendLine("Utility Message Type: $status")
            }
        } else if (messageType == MESSAGE_TYPE_SYSTEM) {
            val statusRightNibble = data[1] and 0x0f.toByte()
            sb.appendLine("System status: "
                    + SYSTEM_COMMON_COMMAND_NAMES[statusRightNibble.toInt()])
        } else if (messageType == MESSAGE_TYPE_MIDI_1_CHANNEL_VOICE) {
            val channel = data[1] and 0x0f.toByte()
            val opcode = ((data[1].toInt() and 0xf0).toUInt() shr 4).toInt()
            // Opcodes start at 8 and are defined until 14
            if ((opcode >= MIDI_1_0_CHANNEL_COMMAND_FIRST_OPCODE)
                    && (opcode < MIDI_1_0_CHANNEL_COMMAND_FIRST_OPCODE
                            + MIDI_1_0_CHANNEL_COMMAND_NAMES.size)) {
                sb.appendLine("Opcode: " + MIDI_1_0_CHANNEL_COMMAND_NAMES[opcode
                        - MIDI_1_0_CHANNEL_COMMAND_FIRST_OPCODE])
            } else {
                sb.appendLine("Opcode: $opcode")
            }
            sb.appendLine("Channel: " + (channel + 1)) // Humans use 1-16
        } else if (messageType == MESSAGE_TYPE_MIDI_2_CHANNEL_VOICE) {
            val channel = data[1] and 0x0f.toByte()
            val opcode = ((data[1].toInt() and 0xf0).toUInt() shr 4).toInt()
            sb.appendLine("Opcode: " + MIDI_2_0_CHANNEL_COMMAND_NAMES[opcode])
            sb.appendLine("Channel: " + (channel + 1)) // Humans use 1-16
        } else if ((messageType == MESSAGE_TYPE_64_BIT_DATA_MESSAGE)
                || (messageType == MESSAGE_TYPE_128_BIT_DATA_MESSAGE)){
            val byteCount = data[1] and 0x0f.toByte()
            val status = ((data[1].toInt() and 0xf0).toUInt() shr 4).toInt()
            if (status <= SYSTEM_EXCLUSIVE_STATUS_FIELD_NAMES.size) {
                sb.appendLine("Status: " + SYSTEM_EXCLUSIVE_STATUS_FIELD_NAMES[status])
                sb.appendLine("Number of bytes: $byteCount")
            } else {
                sb.appendLine("Status: $status")
            }
        }
        sb.appendLine("Raw Bytes: " + formatBytes(data, 0, data.size))
        return sb.toString()
    }

    fun formatUmpSet(data: ByteArray, offset: Int, count: Int) : String {
        var curIndex = offset
        val sb = StringBuilder()
        while (curIndex < offset + count) {
            val messageType = ((data[curIndex] and 0xf0.toByte()).toUInt() shr 4).toInt()
            if (messageType > SIZE_PER_MESSAGE_TYPE_BYTES.size) {
                break
            }
            val byteCount = SIZE_PER_MESSAGE_TYPE_BYTES[messageType]
            if (curIndex + byteCount > offset + count) {
                break
            }
            sb.append(formatUmpMessage(data.copyOfRange(curIndex, curIndex + byteCount)))
            curIndex += byteCount
        }
        sb.appendLine("Not parsed bytes: " + formatBytes(data, curIndex,
                count + offset - curIndex))
        return sb.toString()
    }

    @JvmStatic
    fun formatDeviceInfo(info: MidiDeviceInfo?): String {
        val sb = StringBuilder()
        if (info != null) {
            val properties = info.properties
            for (key in properties.keySet()) {
                if (key == USB_DEVICE_STRING) {
                    val value = properties.getParcelable(key, UsbDevice::class.java)
                    sb.append(key).append(" = ").append(value).append('\n')
                } else {
                    val value = properties.getString(key)
                    sb.append(key).append(" = ").append(value).append('\n')
                }
            }
            for (port in info.ports) {
                sb.append(if (port.type == MidiDeviceInfo.PortInfo.TYPE_INPUT) "input"
                        else "output")
                sb.append("[").append(port.portNumber).append("] = \"").append(
                    """
    ${port.name}"
    
    """.trimIndent()
                )
            }
        }
        return sb.toString()
    }
}