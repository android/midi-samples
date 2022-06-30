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

/**
 * Base class for a polyphonic synthesizer voice.
 */
abstract class SynthVoice {
    private var noteIndex: Int
    var amplitude = 0f
    private var mState = STATE_OFF
    open fun noteOn(noteIndex: Int, velocity: Int) {
        mState = STATE_ON
        this.noteIndex = noteIndex
        amplitude = velocity / 128.0f
    }

    open fun noteOff() {
        mState = STATE_OFF
    }

    /**
     * Add the output of this voice to an output buffer.
     *
     * @param outputBuffer
     * @param samplesPerFrame
     * @param level
     */
    fun mix(outputBuffer: FloatArray, samplesPerFrame: Int, level: Float) {
        val numFrames = outputBuffer.size / samplesPerFrame
        for (i in 0 until numFrames) {
            val output = render()
            val offset = i * samplesPerFrame
            for (jf in 0 until samplesPerFrame) {
                outputBuffer[offset + jf] += output * level
            }
        }
    }

    abstract fun render(): Float
    open fun isDone(): Boolean {
        return (mState == STATE_OFF)
    }

    /**
     * @param scaler
     */
    open fun setFrequencyScaler(scaler: Float) {}

    companion object {
        const val STATE_OFF = 0
        const val STATE_ON = 1
    }

    init {
        noteIndex = -1
    }
}