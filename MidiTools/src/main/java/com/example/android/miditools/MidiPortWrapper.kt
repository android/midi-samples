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

import android.media.midi.MidiDeviceInfo
import java.lang.StringBuilder

class MidiPortWrapper
/**
 * Wrapper for a MIDI device and port description.
 * @param deviceInfo
 * @param mType
 * @param portIndex
 */(val deviceInfo: MidiDeviceInfo?, private val mType: Int, val portIndex: Int) {
    private var mString: String? = null
    private fun updateString() {
        if (deviceInfo == null) {
            mString = "- - - - - -"
        } else {
            val sb = StringBuilder()
            var name = deviceInfo.properties
                .getString(MidiDeviceInfo.PROPERTY_NAME)
            if (name == null) {
                name = (deviceInfo.properties
                    .getString(MidiDeviceInfo.PROPERTY_MANUFACTURER) + ", "
                        + deviceInfo.properties
                    .getString(MidiDeviceInfo.PROPERTY_PRODUCT))
            }
            sb.append("#" + deviceInfo.id)
            sb.append(", ").append(name)
            val portName = findPortName()
            sb.append("[$portIndex]")
            if (portName != "") {
                sb.append(", ").append(portName)
            }
            mString = sb.toString()
        }
    }

    private fun findPortName(): String {
        val ports = deviceInfo!!.ports
        for (portInfo in ports) {
            if (portInfo.portNumber == portIndex
                && portInfo.type == mType
            ) {
                return portInfo.name
            }
        }
        return ""
    }

    override fun toString(): String {
        if (mString == null) {
            updateString()
        }
        return mString!!
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is MidiPortWrapper) return false
        if (portIndex != other.portIndex) return false
        if (mType != other.mType) return false
        return if (deviceInfo == null) other.deviceInfo == null else deviceInfo == other.deviceInfo
    }

    override fun hashCode(): Int {
        var hashCode = 1
        hashCode = 31 * hashCode + portIndex
        hashCode = 31 * hashCode + mType
        hashCode = 31 * hashCode + deviceInfo.hashCode()
        return hashCode
    }
}