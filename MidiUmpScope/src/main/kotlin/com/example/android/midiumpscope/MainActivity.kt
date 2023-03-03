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

import android.app.Activity
import android.media.midi.MidiManager
import android.media.AudioManager
import android.media.midi.MidiReceiver
import android.os.Bundle
import android.media.AudioDeviceInfo
import android.media.midi.MidiInputPort
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.example.android.miditools.*
import com.example.android.miditools.MidiCiInitiator
import com.example.android.miditools.MidiInputOutputPortSelector
import com.example.android.miditools.synth.SynthEngine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.StringBuilder
import java.util.*

/*
 * Print incoming MIDI messages to the screen.
 */
class MainActivity : Activity(), ScopeLogger {
    private var mLog: TextView? = null
    private var mScroller: ScrollView? = null
    private val mLogLines = LinkedList<String>()
    private var mWriteLineCounter = 0
    private var mReadLineCounter = 0
    private var mLogSenderSelector: MidiInputOutputPortSelector? = null
    private var mMidiManager: MidiManager? = null
    private var mAudioManager: AudioManager? = null
    private var mLoggingReceiver: MidiReceiver? = null
    private var mDirectReceiver: MyDirectReceiver? = null
    private var mMidiCiInitiator: MidiCiInitiator? = null
    private var mShowRaw = false
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        mLog = findViewById<View>(R.id.log) as TextView
        mScroller = findViewById<View>(R.id.scroll) as ScrollView

        // Setup MIDI
        mMidiManager = getSystemService(MIDI_SERVICE) as MidiManager
        mAudioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val audioDevices = mAudioManager!!.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (device in audioDevices) {
            if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                val test = mAudioManager!!.setCommunicationDevice(device)
                Log.d(TAG, "Output device set as " + device.id + " " + test)
                mSynthEngine.setAudioDeviceInfo(device)
            }
        }

        // Receiver that prints the messages.
        mLoggingReceiver = LoggingReceiver(this)

        mDirectReceiver = MyDirectReceiver()
        queryOptimalAudioSettings()

        // Tell the virtual device to log its messages here..
        MidiScope.scopeLogger = this

        mMidiCiInitiator = MidiCiInitiator()

        // Setup a menu to select an input source.
        mLogSenderSelector =
        object : MidiInputOutputPortSelector(
            mMidiManager!!, findViewById<View>(R.id.spinner_senders) as Spinner
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
                    log(MidiPrinter.formatDeviceInfo(wrapper.deviceInfo))
                    mDirectReceiver!!.isDoneWithSetup = false
                    // Wait 2 seconds for device to open
                    var retryCount = 20
                    while (!(mLogSenderSelector as MidiInputOutputPortSelector).didOpenComplete) {
                        Thread.sleep(100)
                        retryCount--
                        if (retryCount == 0) {
                            showErrorToast("receiver never ready")
                            Log.e(TAG, "receiver never ready")
                            return
                        }
                    }
                    Thread.sleep(100)
                    runBlocking {
                        launch {
                            val midiCISetupSuccess = mMidiCiInitiator?.setupMidiCI(
                                mDirectReceiver!!,
                                (mLogSenderSelector as MidiInputOutputPortSelector).receiver as MidiInputPort,
                                0,
                                DEVICE_MANUFACTURER
                            )
                            Log.d(TAG, "midiCISetupSuccess: $midiCISetupSuccess")

                            if (midiCISetupSuccess == true) {
                                mDirectReceiver!!.isDoneWithSetup = true
                            } else {
                                showErrorToast("MIDI-CI failed")
                            }
                        }
                    }
                }
            }
        }
        (mLogSenderSelector as MidiInputOutputPortSelector).sender.connect(mDirectReceiver)

        // Instead of updating the scope whenever a message comes in, batch this every 250ms as
        // the UI thread may be too slow otherwise.
        setupScopeUIUpdater();
    }

    private fun queryOptimalAudioSettings() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val text = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        val framesPerBlock = text.toInt()
        mSynthEngine.setFramesPerBlock(framesPerBlock)
        mSynthEngine.start()
    }

    public override fun onDestroy() {
        mLogSenderSelector!!.onClose()
        // The scope will live on as a service so we need to tell it to stop
        // writing log messages to this Activity.
        MidiScope.scopeLogger = null
        mSynthEngine.stop()
        super.onDestroy()
    }

    fun onToggleScreenLock(view: View) {
        val checked = (view as CheckBox).isChecked
        if (checked) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        } else {
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    fun onToggleShowRaw(view: View) {
        mShowRaw = (view as CheckBox).isChecked
    }

    fun onClearLog() {
        synchronized (mLogLines) {
            mLogLines.clear()
        }
        logFromUiThread("")
    }

    internal inner class MyDirectReceiver : WaitingMidiReceiver() {
        var isDoneWithSetup = false
        override fun onSend(
            data: ByteArray, offset: Int, count: Int,
            timestamp: Long
        ) {
            super.onSend(data, offset, count, timestamp)
            log(String.format("0x%08X, ", timestamp))
            if (mShowRaw) {
                logByteArray("Raw Received Bytes: ", data, offset, count)
            }
            log(MidiPrinter.formatUmpSet(data, offset, count))

            if (isDoneWithSetup) {
                if (data.isNotEmpty()) {
                    val messageType = ((data[0 + offset].toInt() and 0xf0).toUInt() shr 4).toInt()
                    // Only send MIDI 2.0 command messages to the synth
                    if (messageType == 4) {
                        mSynthEngine.onSend(data, offset, count, timestamp)
                    }
                }
            }
        }
    }

    /**
     * @param text
     */
    override fun log(text: String?) {
        if (text != null) {
            synchronized (mLogLines) {
                mLogLines.add(text)
                while (mLogLines.size > MAX_LINES) {
                    mLogLines.removeFirst()
                }
                mWriteLineCounter++
            }
        }
    }

    // Log a message to our TextView.
    // Must run on UI thread.
    private fun logFromUiThread(s: String) {
        mLog!!.text = s
        mScroller!!.fullScroll(View.FOCUS_DOWN)
    }

    private fun logByteArray(prefix: String, value: ByteArray, offset: Int, count: Int) {
        val builder = StringBuilder(prefix)
        for (i in 0 until count) {
            builder.append(String.format("0x%02X", value[offset + i]))
            if (i != count - 1) {
                builder.append(", ")
            }
        }
        log(builder.toString())
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

    private fun setupScopeUIUpdater() {
        val mainHandler = Handler(Looper.getMainLooper())

        mainHandler.post(object : Runnable {
            override fun run() {
                // Render line buffer to one String.
                val sb = StringBuilder()
                var shouldUpdateUIThread = false
                synchronized (mLogLines) {
                    if (mReadLineCounter != mWriteLineCounter) {
                        shouldUpdateUIThread = true
                        mReadLineCounter = mWriteLineCounter
                    }
                    if (shouldUpdateUIThread) {
                        for (line in mLogLines) {
                            sb.append(line).append('\n')
                        }
                    }
                }
                if (shouldUpdateUIThread) {
                    runOnUiThread {
                        logFromUiThread(sb.toString())
                    }
                }
                mainHandler.postDelayed(this, TIME_BETWEEN_UI_UPDATES_MS)
            }
        })
    }

    companion object {
        private const val TAG = "MidiUmpScope"
        private const val MAX_LINES = 100
        private const val TIME_BETWEEN_UI_UPDATES_MS = 250L
        private val mSynthEngine = SynthEngine()

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