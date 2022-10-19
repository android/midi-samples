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
package com.example.android.midiumpkeyboard

import android.app.Activity
import android.content.pm.PackageManager
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import com.example.android.miditools.*
import com.example.android.miditools.MusicKeyboardView.MusicKeyListener
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * Main activity for the keyboard app.
 */
class MainActivity : Activity() {
    private var mKeyboardReceiverSelector: MidiInputOutputPortSelector? = null
    private var mKeyboard: MusicKeyboardView? = null
    private var mProgramButton: Button? = null
    private var mMidiManager: MidiManager? = null
    private var mChannel // ranges from 0 to 15
            = 0
    private var mGroup // ranges from 0 to 15
            = 0
    private val mPrograms = IntArray(MidiConstants.MAX_CHANNELS) // ranges from 0 to 127
    private val mByteBuffer = ByteArray(8)
    private var mMidiCiInitiator: MidiCiInitiator? = null
    private var mMidiReceiver: WaitingMidiReceiver? = null
    private var mDoneWithSetup = false
    private var mPitchBendEnabled = true

    inner class ChannelSpinnerActivity : OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?, view: View?,
            pos: Int, id: Long
        ) {
            mChannel = pos and 0x0F
            updateProgramText()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    inner class GroupSpinnerActivity : OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?, view: View?,
            pos: Int, id: Long
        ) {
            mGroup = pos and 0x0F
            updateProgramText()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            setupMidi()
        } else {
            Toast.makeText(this, "MIDI not supported!", Toast.LENGTH_LONG)
                .show()
        }
        mProgramButton = findViewById<View>(R.id.button_program) as Button
        val channelSpinner = findViewById<View>(R.id.spinner_channels) as Spinner
        channelSpinner.onItemSelectedListener = ChannelSpinnerActivity()
        val groupSpinner = findViewById<View>(R.id.spinner_groups) as Spinner
        groupSpinner.onItemSelectedListener = GroupSpinnerActivity()
    }

    private fun setupMidi() {
        mMidiManager = getSystemService(MIDI_SERVICE) as MidiManager
        if (mMidiManager == null) {
            Toast.makeText(this, "MidiManager is null!", Toast.LENGTH_LONG)
                .show()
            return
        }

        mMidiCiInitiator = MidiCiInitiator()
        mMidiReceiver = WaitingMidiReceiver()

        // Setup a menu to select an input source.
        mKeyboardReceiverSelector =
        object : MidiInputOutputPortSelector(
            mMidiManager!!, findViewById<View>(R.id.spinner_receivers) as Spinner
        ) {
            init {
                MidiDeviceMonitor.getInstance(mMidiManager)!!
                    .registerDeviceCallback(
                        MidiManager.TRANSPORT_UNIVERSAL_MIDI_PACKETS, { r: Runnable? ->
                            Handler(Looper.getMainLooper())
                                .post(
                                    r!!
                                )
                        },
                        this
                    )
            }

            override fun onPortSelected(wrapper: MidiPortWrapper?) {
                super.onPortSelected(wrapper)
                if ((wrapper != null) && (wrapper.deviceInfo != null)) {
                    // Wait 2 seconds for device to open
                    var retryCount = 20
                    while (!(mKeyboardReceiverSelector as MidiInputOutputPortSelector)
                            .didOpenComplete) {
                        Thread.sleep(100)
                        retryCount--
                        if (retryCount == 0) {
                            showErrorToast("receiver never ready")
                            Log.e(TAG, "receiver never ready")
                            return
                        }
                    }
                    mDoneWithSetup = false
                    Thread.sleep(100)
                    runBlocking {
                        launch {
                            val midiCISetupSuccess = mMidiCiInitiator?.setupMidiCI(
                                mMidiReceiver!!,
                                (mKeyboardReceiverSelector as MidiInputOutputPortSelector).receiver
                                        as MidiInputPort,
                                mGroup,
                                DEVICE_MANUFACTURER
                            )
                            Log.d(TAG, "Was midi successful: $midiCISetupSuccess")

                            if (midiCISetupSuccess == true) {
                                mDoneWithSetup = true
                            } else {
                                showErrorToast("MIDI-CI failed")
                            }
                        }
                    }
                }
            }
        }
        (mKeyboardReceiverSelector as MidiInputOutputPortSelector).sender.connect(mMidiReceiver)

        mKeyboard = findViewById<View>(R.id.musicKeyboardView) as MusicKeyboardView
        mKeyboard!!.addMusicKeyListener(object : MusicKeyListener {
            override fun onKeyDown(keyIndex: Int) {
                noteOn(mChannel, keyIndex, 255, mGroup)
            }

            override fun onKeyUp(keyIndex: Int) {
                noteOff(mChannel, keyIndex, 0, mGroup)
            }

            override fun onPitchBend(keyIndex: Int, bend: Float) {
                if (mPitchBendEnabled) {
                    pitchBend(mChannel, keyIndex, bend, mGroup)
                }
            }
        })
    }

    fun onProgramSend() {
        if (mDoneWithSetup) {
            midiCommand(0x40 + mGroup, MidiConstants.STATUS_PROGRAM_CHANGE + mChannel,
                    0, 0, mPrograms[mChannel], 0, 0, 0)
        }
    }

    fun onProgramDelta(view: View) {
        val button = view as Button
        val delta = button.text.toString().toInt()
        changeProgram(delta)
    }

    private fun changeProgram(delta: Int) {
        var program = mPrograms[mChannel]
        program += delta
        if (program < 0) {
            program = 0
        } else if (program > 127) {
            program = 127
        }
        if (mDoneWithSetup) {
            midiCommand(0x40 + mGroup, MidiConstants.STATUS_PROGRAM_CHANGE + mChannel,
                    0, 0, program, 0, 0, 0)
        }
        mPrograms[mChannel] = program
        updateProgramText()
    }

    private fun updateProgramText() {
        mProgramButton!!.text = mPrograms[mChannel].toString()
    }

    private fun noteOff(channel: Int, pitch: Int, velocity: Int, group: Int) {
        if (mDoneWithSetup) {
            midiCommand(0x40 + group, MidiConstants.STATUS_NOTE_OFF + channel, pitch,
                    0, velocity, 0, 0, 0)
        }
    }

    private fun noteOn(channel: Int, pitch: Int, velocity: Int, group: Int) {
        if (mDoneWithSetup) {
            midiCommand(0x40 + group, MidiConstants.STATUS_NOTE_ON + channel, pitch,
                    0, velocity, 0, 0, 0)
        }
    }

    private fun pitchBend(channel: Int, pitch: Int, bend: Float, group: Int) {
        val pitchBendULong = clamp32FromFloat(bend)
        // Send MIDI 2.0 per note pitch bend.
        if (mDoneWithSetup) {
            midiCommand((0x40 + group).toByte(),
                (MidiConstants.STATUS_PER_NOTE_PITCH_BEND + channel).toByte(),
                pitch.toByte(),
                0.toByte(), // reserved
                (pitchBendULong shr 24).toByte(),
                (pitchBendULong shr 16).toByte(),
                (pitchBendULong shr 8).toByte(),
                (pitchBendULong).toByte())
        }
    }

    private fun midiCommand(data0: Int, data1: Int, data2: Int, data3: Int, data4: Int, data5: Int,
                            data6: Int, data7: Int) {
        mByteBuffer[0] = data0.toByte()
        mByteBuffer[1] = data1.toByte()
        mByteBuffer[2] = data2.toByte()
        mByteBuffer[3] = data3.toByte()
        mByteBuffer[4] = data4.toByte()
        mByteBuffer[5] = data5.toByte()
        mByteBuffer[6] = data6.toByte()
        mByteBuffer[7] = data7.toByte()
        val now = System.nanoTime()
        midiSend(mByteBuffer, 8, now)
    }

    private fun midiCommand(data0: Byte, data1: Byte, data2: Byte, data3: Byte, data4: Byte,
                            data5: Byte, data6: Byte, data7: Byte) {
        mByteBuffer[0] = data0
        mByteBuffer[1] = data1
        mByteBuffer[2] = data2
        mByteBuffer[3] = data3
        mByteBuffer[4] = data4
        mByteBuffer[5] = data5
        mByteBuffer[6] = data6
        mByteBuffer[7] = data7
        val now = System.nanoTime()
        midiSend(mByteBuffer, 8, now)
    }

    private fun closeSynthResources() {
        if (mKeyboardReceiverSelector != null) {
            mKeyboardReceiverSelector!!.close()
            mKeyboardReceiverSelector!!.onDestroy()
        }
    }

    public override fun onDestroy() {
        closeSynthResources()
        super.onDestroy()
    }

    private fun midiSend(buffer: ByteArray, count: Int, timestamp: Long) {
        logByteArray("sending: ", buffer, 0, buffer.size)
        if (mKeyboardReceiverSelector != null) {
            try {
                // send event immediately
                val receiver = mKeyboardReceiverSelector!!.receiver
                receiver?.send(buffer, 0, count, timestamp)
            } catch (e: IOException) {
                Log.e(TAG, "mKeyboardReceiverSelector.send() failed $e")
            }
        }
    }

    private fun clamp32FromFloat(f : Float) : ULong {
        val scale = (1UL shl 32).toFloat()
        val limitPositive = 1f
        val limitNegative = 0f

        if (f <= limitNegative) {
            return ULong.MIN_VALUE
        } else if (f >= limitPositive) {
            return ULong.MAX_VALUE
        }
        val scaledF = f * scale
        /* integer conversion is through truncation (though int to float is not).
         * ensure that we round to nearest, ties away from 0.
         */
        return if (scaledF.toULong() > ULong.MAX_VALUE / 2u) {
            (scaledF + 0.5).toULong()
        } else {
            (scaledF - 0.5).toULong()
        }
    }

    fun onToggleSetPitchBend(view: View) {
        mPitchBendEnabled = (view as CheckBox).isChecked
    }

    private fun logByteArray(prefix: String, value: ByteArray, offset: Int, count: Int) {
        val builder = StringBuilder(prefix)
        for (i in offset until offset + count) {
            builder.append(String.format("0x%02X", value[i]))
            if (i != value.size - 1) {
                builder.append(", ")
            }
        }
        Log.d(MidiCiInitiator.TAG, builder.toString())
    }

    private fun showErrorToast(message: String) {
        showToast("Error: $message")
    }

    private fun showToast(message: String?) {
        runOnUiThread {
            Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    companion object {
        private const val TAG = "MidiUmpKeyboard"

        // Please request a system exclusive ID with the MIDI Association.
        // See https://midi.org/request-sysex-id
        // Please set these to be specific to your app.
        // The code below uses the special ID 7D.
        // Special ID 7D is reserved for non-commercial use (e.g. schools, research, etc.)
        // and is not to be used on any product released to the public.
        // PLEASE DO NOT SHIP A PRODUCTION APP WITH THESE NUMBERS. SOME USB DEVICES MAY BREAK
        // UNLESS YOU REQUEST YOUR OWN SYSTEM EXCLUSIVE ID.
        val DEVICE_MANUFACTURER = byteArrayOf(0x7D, 0x00, 0x00)
    }
}