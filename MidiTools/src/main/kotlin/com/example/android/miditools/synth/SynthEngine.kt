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
package com.example.android.miditools.synth

import kotlin.jvm.JvmOverloads
import android.media.midi.MidiReceiver
import kotlin.jvm.Volatile
import com.example.android.miditools.MidiEventScheduler
import com.example.android.miditools.MidiFramer
import android.media.AudioDeviceInfo
import android.util.Log
import kotlin.Throws
import com.example.android.miditools.MidiConstants
import java.io.IOException
import java.lang.Exception
import java.util.*
import kotlin.experimental.and
import kotlin.math.pow

/**
 * Very simple polyphonic, single channel synthesizer. It runs a background
 * thread that processes MIDI events and synthesizes audio.
 */
class SynthEngine @JvmOverloads constructor(val audioOutput: SimpleAudioOutput = SimpleAudioOutput()) :
    MidiReceiver() {
    @Volatile
    private var mThreadEnabled = false
    private var mThread: Thread? = null
    private var mBuffer: FloatArray? = null
    private var mFrequencyScaler = 1.0f
    private val mBendRange = 2.0f // semitones
    private var mProgram = 0
    private val mFreeVoices = ArrayList<SynthVoice>()
    private val mVoices = Hashtable<Int, SynthVoice>()
    private var mEventScheduler: MidiEventScheduler? = null
    private val mFramer: MidiFramer
    private var mReceiver: MidiReceiver = MyReceiver()
    private var mSampleRate = 0
    private var mFramesPerBlock = DEFAULT_FRAMES_PER_BLOCK
    private var midiByteCount = 0
    private var mAudioDeviceInfo: AudioDeviceInfo? = null

    /* This will be called when MIDI data arrives. */
    @Throws(IOException::class)
    override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
        if (mEventScheduler != null) {
            if (!MidiConstants.isAllActiveSensing(data, offset, count)) {
                mEventScheduler!!.receiver.send(
                    data, offset, count,
                    timestamp
                )
            }
        }
        midiByteCount += count
    }

    /**
     * Call this before the engine is started.
     * @param framesPerBlock
     */
    fun setFramesPerBlock(framesPerBlock: Int) {
        mFramesPerBlock = framesPerBlock
    }

    fun setAudioDeviceInfo(audioDeviceInfo: AudioDeviceInfo?) {
        mAudioDeviceInfo = audioDeviceInfo
    }

    private inner class MyReceiver : MidiReceiver() {
        @Throws(IOException::class)
        // Please pass only 64 bit MIDI 2 voice messages to this function.
        // This class handles only handles note off, note on, pitch bend, and program change
        // messages.
        override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
            logMidiMessage(data, offset, count)
            var curIndex = offset
            while (curIndex + 7 < offset + count) {
                val command = (data[1 + curIndex] and MidiConstants.STATUS_COMMAND_MASK)
                val velocity = (((data[4 + curIndex].toUInt() and 0xFFu) shl 8) + (data[5 + curIndex].toUInt() and 0xFFu))
                when (command) {
                    MidiConstants.STATUS_NOTE_OFF -> {
                        noteOff(
                            data[2 + curIndex].toInt(), velocity.toInt()
                        )
                        curIndex += 8
                    }
                    MidiConstants.STATUS_NOTE_ON -> {
                        noteOn(
                            data[2 + curIndex].toInt(), velocity.toInt()
                        )
                        curIndex += 8
                    }
                    MidiConstants.STATUS_PITCH_BEND -> {
                        pitchBend(
                            (data[4].toUInt() shl 24) + (data[5].toUInt() shl 16) + (data[6].toUInt() shl 8) + data[7].toUInt()
                        )
                        curIndex += 8
                    }
                    MidiConstants.STATUS_PROGRAM_CHANGE -> {
                        mProgram = data[4].toInt()
                        mFreeVoices.clear()
                        curIndex += 8
                    }
                    else -> {
                        // Simply skip this set of 8 bytes.
                        curIndex += 8
                    }
                }
            }
        }
    }

    internal inner class MyRunnable : Runnable {
        override fun run() {
            try {
                audioOutput.start(mFramesPerBlock, mAudioDeviceInfo)
                mSampleRate = audioOutput.frameRate // rate is now valid
                if (mBuffer == null) {
                    mBuffer = FloatArray(mFramesPerBlock * SAMPLES_PER_FRAME)
                }
                onLoopStarted()
                // The safest way to exit from a thread is to check a variable.
                while (mThreadEnabled) {
                    processMidiEvents()
                    generateBuffer()
                    val buffer = mBuffer!!
                    audioOutput.write(buffer, 0, buffer.size)
                    onBufferCompleted()
                }
            } catch (e: Exception) {
                Log.e(TAG, "SynthEngine background thread exception.", e)
            } finally {
                onLoopEnded()
                audioOutput.stop()
            }
        }
    }

    /**
     * This is called from the synthesis thread before it starts looping.
     */
    fun onLoopStarted() {}

    /**
     * This is called once at the end of each synthesis loop.
     */
    fun onBufferCompleted() {}

    /**
     * This is called from the synthesis thread when it stops looping.
     */
    fun onLoopEnded() {}

    /**
     * Assume message has been aligned to the start of a MIDI message.
     *
     * @param data
     * @param offset
     * @param count
     */
    fun logMidiMessage(data: ByteArray, offset: Int, count: Int) {
        var text = "Received: "
        for (i in 0 until count) {
            text += String.format("0x%02X, ", data[offset + i])
        }
        Log.i(TAG, text)
    }

    /**
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun processMidiEvents() {
        val now = System.nanoTime() // TODO use audio presentation time
        var event = mEventScheduler!!.getNextEvent(now) as MidiEventScheduler.MidiEvent?
        while (event != null) {
            mReceiver.send(event.data, 0, event.count, event.timestamp)
            mEventScheduler!!.addEventToPool(event)
            event = mEventScheduler!!.getNextEvent(now) as MidiEventScheduler.MidiEvent?
        }
    }

    /**
     * Mix the output of each active voice into a buffer.
     */
    private fun generateBuffer() {
        val buffer = mBuffer
        for (i in buffer!!.indices) {
            buffer[i] = 0.0f
        }
        val iterator = mVoices.values.iterator()
        while (iterator.hasNext()) {
            val voice = iterator.next()
            if (voice.isDone()) {
                iterator.remove()
                //mFreeVoices.add(voice);
            } else {
                //Log.e(TAG, "MIX");
                voice.mix(buffer, SAMPLES_PER_FRAME, 1f)
            }
        }
    }

    fun noteOff(noteIndex: Int, velocity: Int) {
        Log.d(TAG, "NoteOff:$noteIndex velocity: $velocity")
        val voice = mVoices[noteIndex]
        voice?.noteOff()
    }

    /**
     * Create a SynthVoice.
     */
    private fun createVoice(program: Int): SynthVoice {
        // For every odd program number use a sine wave.
        return if (program and 1 == 1) {
            SineVoice(mSampleRate)
        } else {
            SawVoice(mSampleRate)
        }
    }

    /**
     *
     * @param noteIndex
     * @param velocity
     */
    fun noteOn(noteIndex: Int, velocity: Int) {
        if (velocity == 0) {
            noteOff(noteIndex, velocity)
        } else {
            Log.d(TAG, "NoteOn:$noteIndex velocity: $velocity")
            mVoices.remove(noteIndex)
            val voice: SynthVoice = if (mFreeVoices.size > 0) {
                mFreeVoices.removeAt(mFreeVoices.size - 1)
            } else {
                createVoice(mProgram)
            }
            voice.setFrequencyScaler(mFrequencyScaler)
            voice.noteOn(noteIndex, velocity / 256)
            mVoices[noteIndex] = voice
        }
    }

    fun pitchBend(bend: UInt) {
        val semitones = (mBendRange * (bend.toLong() - 0x80000000).toFloat() / 0x80000000)
                .toDouble()
        mFrequencyScaler = 2.0.pow(semitones / 12.0).toFloat()
        val iterator: Iterator<SynthVoice> = mVoices.values.iterator()
        while (iterator.hasNext()) {
            val voice = iterator.next()
            voice.setFrequencyScaler(mFrequencyScaler)
        }
    }

    /**
     * Start the synthesizer.
     */
    fun start() {
        stop()
        mThreadEnabled = true
        mThread = Thread(MyRunnable())
        mEventScheduler = MidiEventScheduler()
        mThread!!.start()
    }

    /**
     * Stop the synthesizer.
     */
    fun stop() {
        mThreadEnabled = false
        if (mThread != null) {
            try {
                mThread!!.interrupt()
                mThread!!.join(500)
            } catch (e: InterruptedException) {
                // OK, just stopping safely.
            }
            mThread = null
            mEventScheduler = null
        }
    }

    companion object {
        private const val TAG = "SynthEngine"

        // 64 is the greatest common divisor of 192 and 128
        private const val DEFAULT_FRAMES_PER_BLOCK = 64
        private const val SAMPLES_PER_FRAME = 2
    }

    init {
        mReceiver = MyReceiver()
        mFramer = MidiFramer(mReceiver)
    }
}