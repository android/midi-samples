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

import org.junit.Assert
import org.junit.Test

class TestCIDiscovery {

    private val mCiDiscoveryHelper = MidiCIDiscoveryHelper()

    @Test
    fun testCIDiscoveryVersion1() {
        // Run tests in order because these functions are stateful and rely on previous calls
        mCiDiscoveryHelper.setSourceMuid(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        mCiDiscoveryHelper.setMidiCiMessageVersion(0x01)
        testGenerateDiscoveryMessage(true)
        testParseDiscoveryReply(true)
        testGenerateInitiateProtocolNegotiationMessage()
        testParseInitiateProtocolNegotiationReply()
        testGenerateSetNewProtocolMessage()
        testGenerateNewProtocolInitiatorMessage()
        testParseNewProtocolResponderReply()
        testGenerateConfirmationNewProtocolMessage()
    }

    @Test
    fun testCIDiscoveryVersion2() {
        // Run tests in order because these functions are stateful and rely on previous calls
        mCiDiscoveryHelper.setSourceMuid(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        mCiDiscoveryHelper.setMidiCiMessageVersion(0x02)
        testGenerateDiscoveryMessage(false)
        testParseDiscoveryReply(false)
        testGenerateEndpointDiscoveryMessage()
        testParseEndpointInfoNotificationMessage()
        testGenerateStreamConfigurationRequestMessage()
        testParseStreamConfigurationNotificationMessage()
    }

    private fun testGenerateDiscoveryMessage(isVersion1 : Boolean) {
        val midiCiVersion = (if (isVersion1) 0x01 else 0x02).toByte()
        var expectedOutputBytes = byteArrayOf(
            // Universal System Exclusive
            0x7E.toByte(),
            // Device ID: 7F = to MIDI Port
            0x7F.toByte(),
            // Universal System Exclusive Sub-ID#1: MIDI-CI
            0x0D.toByte(),
            // Universal System Exclusive Sub-ID#2: Discovery
            0x70.toByte(),
            // MIDI-CI Message Version/Format
            midiCiVersion,
            // Source MUID (LSB first)
            0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(),
            //  Destination MUID (LSB first) (to Broadcast MUID)
            0x7F.toByte(), 0x7F.toByte(), 0x7F.toByte(), 0x7F.toByte(),
            // Device Manufacturer (System Exclusive ID Number)
            0x7D.toByte(), 0x00.toByte(), 0x00.toByte(),
            // Device Family (LSB first)
            0x12.toByte(), 0x34.toByte(),
            // Device Family Model Number (LSB first)
            0x66.toByte(), 0x00.toByte(),
            // Software Revision Level (Format is Device specific)
            0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(),
            // Capability Inquiry Category Supported (bitmap)
            0x07.toByte(),
            // Receivable Maximum SysEx Message Size (LSB first)
            0x00.toByte(), 0x20.toByte(), 0x00.toByte(), 0x00.toByte(),
        )
        if (!isVersion1) {
            // Initiator output path id
            expectedOutputBytes += 0x00.toByte()
        }
        val actualOutputBytes = mCiDiscoveryHelper.generateDiscoveryMessage()
        Assert.assertArrayEquals(expectedOutputBytes, actualOutputBytes)
    }

    private fun testParseDiscoveryReply(isVersion1 : Boolean) {
        val midiCiVersion = (if (isVersion1) 0x01 else 0x02).toByte()
        var discoveryReply = byteArrayOf(
            // Universal System Exclusive
            0x7E.toByte(),
            // Device ID: 7F = from MIDI Port
            0x7F.toByte(),
            // Universal System Exclusive Sub-ID#1: MIDI-CI
            0x0D.toByte(),
            // Universal System Exclusive Sub-ID#2: Reply to Discovery
            0x71.toByte(),
            // MIDI-CI Message Version/Format
            midiCiVersion,
            // Source MUID (LSB first)
            0x06.toByte(), 0x78.toByte(), 0x9A.toByte(), 0xBC.toByte(),
            // Destination MUID (LSB first)
            0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(),
            // Device Manufacturer (System Exclusive ID Number)
            0x90.toByte(), 0x00.toByte(), 0x00.toByte(),
            // Device Family (LSB first)
            0x87.toByte(), 0x65.toByte(),
            // Device Family Model Number (LSB first)
            0x77.toByte(), 0x00.toByte(),
            // Software Revision Level (Format is Device specific)
            0x10.toByte(), 0x20.toByte(), 0x30.toByte(), 0x40.toByte(),
            // Capability Inquiry Category Supported (bitmap)
            0x06.toByte(),
            // Receivable Maximum SysEx Message Size (LSB first)
            0x00.toByte(), 0x40.toByte(), 0x00.toByte(), 0x00.toByte(),
        )
        if (!isVersion1) {
            // Initiator output path id
            discoveryReply += 0x00.toByte()
            // Function block
            discoveryReply += 0x10.toByte()
        }
        Assert.assertTrue(mCiDiscoveryHelper.parseDiscoveryReply(discoveryReply))
        Assert.assertEquals(midiCiVersion, mCiDiscoveryHelper.getDestinationCiVersion())
        Assert.assertArrayEquals(byteArrayOf(0x06.toByte(),0x78.toByte(), 0x9A.toByte(),
                0xBC.toByte()), mCiDiscoveryHelper.getDestinationMuid())
        Assert.assertArrayEquals(byteArrayOf(0x90.toByte(), 0x00.toByte(), 0x00.toByte()),
                mCiDiscoveryHelper.getDestinationDeviceManufacturer())
        Assert.assertArrayEquals(byteArrayOf(0x87.toByte(), 0x65.toByte()),
                mCiDiscoveryHelper.getDestinationDeviceFamily())
        Assert.assertArrayEquals(byteArrayOf(0x77.toByte(), 0x00.toByte()),
                mCiDiscoveryHelper.getDestinationModelNumber())
        Assert.assertArrayEquals(byteArrayOf(0x10.toByte(), 0x20.toByte(), 0x30.toByte(),
                0x40.toByte()), mCiDiscoveryHelper.getDestinationSoftwareRevision())
        Assert.assertEquals(0x06.toByte(), mCiDiscoveryHelper.getDestinationCiCategory())
        Assert.assertArrayEquals(byteArrayOf(0x00.toByte(), 0x40.toByte(), 0x00.toByte(),
                0x00.toByte()), mCiDiscoveryHelper.getDestinationMaxSysExSize())
        if (!isVersion1) {
            Assert.assertEquals(0x10.toByte(), mCiDiscoveryHelper.getFunctionBlock())
        }
    }

    private fun testGenerateInitiateProtocolNegotiationMessage() {
        val expectedOutputBytes = byteArrayOf(
            // Universal System Exclusive
            0x7E.toByte(),
            // To/From whole MIDI Port
            0x7F.toByte(),
            // Universal System Exclusive Sub-ID#1: MIDI-CI
            0x0D.toByte(),
            // Universal System Exclusive Sub-ID#2: Initiate Protocol Negotiation
            0x10.toByte(),
            // MIDI-CI Message Version/Format
            0x01.toByte(),
            // Source MUID (LSB first)
            0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(),
            // Destination MUID (LSB first)
            0x06.toByte(), 0x78.toByte(), 0x9A.toByte(), 0xBC.toByte(),
            // Authority Level
            0x6F.toByte(),
            // Number of Supported Protocols (np)
            0x02.toByte(),
            // MIDI 2.0
            0x02.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            // MIDI 1.0
            0x01.toByte(), 0x02.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        )
        val actualOutputBytes = mCiDiscoveryHelper.generateInitiateProtocolNegotiationMessage()
        Assert.assertArrayEquals(expectedOutputBytes, actualOutputBytes)
    }

    private fun testParseInitiateProtocolNegotiationReply() {
        val initiateProtocolNegotiationReply = byteArrayOf(
            // Universal System Exclusive
            0x7E.toByte(),
            // Device ID: 7F = from MIDI Port
            0x7F.toByte(),
            // Universal System Exclusive Sub-ID#1: MIDI-CI
            0x0D.toByte(),
            // Universal System Exclusive Sub-ID#2: Reply to Initiate Protocol Negotiation
            0x11.toByte(),
            // MIDI-CI Message Version/Format
            0x01.toByte(),
            // Source MUID (LSB first)
            0x06.toByte(), 0x78.toByte(), 0x9A.toByte(), 0xBC.toByte(),
            // Destination MUID (LSB first)
            0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(),
            // Authority Level
            0x10.toByte(),
            // Number of Supported Protocols (np)
            0x02.toByte(),
            // MIDI 2.0
            0x02.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            // MIDI 1.0
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        )
        Assert.assertTrue(mCiDiscoveryHelper.parseInitiateProtocolNegotiationReply(
                initiateProtocolNegotiationReply))
        Assert.assertEquals(0x10.toByte(), mCiDiscoveryHelper.getDestinationAuthorityLevel())
    }

    private fun testGenerateSetNewProtocolMessage() {
        val expectedOutputBytes = byteArrayOf(
            // Universal System Exclusive
            0x7E.toByte(),
            // To/From whole MIDI Port
            0x7F.toByte(),
            // Universal System Exclusive Sub-ID#1: MIDI-CI
            0x0D.toByte(),
            // Universal System Exclusive Sub-ID#2: Set New Selected Protocol
            0x12.toByte(),
            // MIDI-CI Message Version/Format
            0x01.toByte(),
            // Source MUID (LSB first)
            0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(),
            // Destination MUID (LSB first)
            0x06.toByte(), 0x78.toByte(), 0x9A.toByte(), 0xBC.toByte(),
            // Authority Level
            0x6F.toByte(),
            // MIDI 2.0
            0x02.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        )
        val actualOutputBytes = mCiDiscoveryHelper.generateSetNewProtocolMessage()
        Assert.assertArrayEquals(expectedOutputBytes, actualOutputBytes)
    }

    private fun testGenerateNewProtocolInitiatorMessage() {
        var expectedOutputBytes = byteArrayOf(
            // Universal System Exclusive
            0x7E.toByte(),
            // To/From whole MIDI Port
            0x7F.toByte(),
            // Universal System Exclusive Sub-ID#1: MIDI-CI
            0x0D.toByte(),
            // Universal System Exclusive Sub-ID#2: Test New Protocol Initiator to Responder
            0x13.toByte(),
            // MIDI-CI Message Version/Format
            0x01.toByte(),
            // Source MUID (LSB first)
            0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(),
            // Destination MUID (LSB first)
            0x06.toByte(), 0x78.toByte(), 0x9A.toByte(), 0xBC.toByte(),
            // Authority Level
            0x6F.toByte(),
        )
        // 48 bytes of 0x00, 0x01, 0x02, ..., 0x2E, 0x2F
        for (i in 0 until MidiCIDiscoveryHelper.TEST_NUMBER_COUNT) {
            expectedOutputBytes += i.toByte()
        }
        val actualOutputBytes = mCiDiscoveryHelper.generateNewProtocolInitiatorMessage()
        Assert.assertArrayEquals(expectedOutputBytes, actualOutputBytes)
    }

    private fun testParseNewProtocolResponderReply() {
        var newProtocolResponderReply = byteArrayOf(
            // Universal System Exclusive
            0x7E.toByte(),
            // Device ID: 7F = from MIDI Port
            0x7F.toByte(),
            // Universal System Exclusive Sub-ID#1: MIDI-CI
            0x0D.toByte(),
            // Universal System Exclusive Sub-ID#2: Test New Protocol Responder to Initiator
            0x14.toByte(),
            // MIDI-CI Message Version/Format
            0x01.toByte(),
            // Source MUID (LSB first)
            0x06.toByte(), 0x78.toByte(), 0x9A.toByte(), 0xBC.toByte(),
            // Destination MUID (LSB first)
            0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(),
            // Authority Level
            0x10.toByte(),
        )
        // 48 bytes of 0x00, 0x01, 0x02, ..., 0x2E, 0x2F
        for (i in 0 until MidiCIDiscoveryHelper.TEST_NUMBER_COUNT) {
            newProtocolResponderReply += i.toByte()
        }
        Assert.assertTrue(mCiDiscoveryHelper.parseNewProtocolResponderReply(
                newProtocolResponderReply))
    }

    private fun testGenerateConfirmationNewProtocolMessage() {
        val expectedOutputBytes = byteArrayOf(
            // Universal System Exclusive
            0x7E.toByte(),
            // To/From whole MIDI Port
            0x7F.toByte(),
            // Universal System Exclusive Sub-ID#1: MIDI-CI
            0x0D.toByte(),
            // Universal System Exclusive Sub-ID#2: Confirmation New Protocol Established
            0x15.toByte(),
            // MIDI-CI Message Version/Format
            0x01.toByte(),
            // Source MUID (LSB first)
            0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(),
            // Destination MUID (LSB first)
            0x06.toByte(), 0x78.toByte(), 0x9A.toByte(), 0xBC.toByte(),
            // Authority Level
            0x6F.toByte(),
        )
        val actualOutputBytes = mCiDiscoveryHelper.generateConfirmationNewProtocolMessage()
        Assert.assertArrayEquals(expectedOutputBytes, actualOutputBytes)
    }

    private fun testGenerateEndpointDiscoveryMessage() {
        var expectedOutputBytes = byteArrayOf(
            // mt = f, f = 0, status = 0x00
            0xF0.toByte(), 0x00.toByte(),
            // UMP Version Major
            0x01.toByte(),
            // UMP Version Minor
            0x01.toByte(),
            // 3 reserved bytes
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            // Filter Bitmap with only stream configuration
            0x01.toByte(),
        )
        // 8 reserved bytes
        expectedOutputBytes += ByteArray(8)
        val actualOutputBytes = mCiDiscoveryHelper.generateEndpointDiscoveryMessage()
        Assert.assertArrayEquals(expectedOutputBytes, actualOutputBytes)
    }

    private fun testParseEndpointInfoNotificationMessage() {
        var newProtocolResponderReply = byteArrayOf(
            // mt = f, f = 0, status = 0x01
            0xF0.toByte(), 0x01.toByte(),
            // UMP Version Major
            0x01.toByte(),
            // UMP Version Minor
            0x01.toByte(),
            // Static function blocks and the number of function blocks
            0xf2.toByte(),
            // Reserved byte
            0x00.toByte(),
            // MIDI 2.0 and MIDI 1.0 support
            0x03.toByte(),
            // Receive and Transmit JR Timestamp Capability
            0x03.toByte(),
        )
        // 8 reserved bytes
        newProtocolResponderReply += ByteArray(8)
        Assert.assertTrue(mCiDiscoveryHelper.parseEndpointInfoNotificationMessage(
            newProtocolResponderReply))
    }

    private fun testGenerateStreamConfigurationRequestMessage() {
        var expectedOutputBytes = byteArrayOf(
            // mt = f, f = 0, status = 0x05
            0xF0.toByte(), 0x05.toByte(),
            // Protocol (MIDI 2.0)
            0x02.toByte(),
            // Receive and Transmit JR Timestamp Capability
            0x00.toByte(),
        )
        // 12 reserved bytes
        expectedOutputBytes += ByteArray(12)
        val actualOutputBytes = mCiDiscoveryHelper.generateStreamConfigurationRequestMessage()
        Assert.assertArrayEquals(expectedOutputBytes, actualOutputBytes)
    }

    private fun testParseStreamConfigurationNotificationMessage() {
        var newProtocolResponderReply = byteArrayOf(
            // mt = f, f = 0, status = 0x06
            0xF0.toByte(), 0x06.toByte(),
            // Protocol (MIDI 2.0)
            0x02.toByte(),
            // Receive and Transmit JR Timestamp Capability
            0x00.toByte(),
        )
        // 12 reserved bytes
        newProtocolResponderReply += ByteArray(12)
        Assert.assertTrue(mCiDiscoveryHelper.parseStreamConfigurationNotificationMessage(
            newProtocolResponderReply))
    }
}
