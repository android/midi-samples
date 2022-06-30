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

open class SawOscillator : SynthUnit() {
    private var mPhase = 0.0f
    private var mPhaseIncrement = 0.01f
    private var mFrequency = 0.0f
    private var mFrequencyScaler = 1.0f
    var amplitude = 1.0f
    fun setPitch(pitch: Float) {
        val freq = pitchToFrequency(pitch.toDouble()).toFloat()
        setFrequency(freq)
    }

    open fun setFrequency(frequency: Float) {
        mFrequency = frequency
        updatePhaseIncrement()
    }

    private fun updatePhaseIncrement() {
        mPhaseIncrement = 2.0f * mFrequency * mFrequencyScaler / 48000.0f
    }

    var frequencyScaler: Float
        get() = mFrequencyScaler
        set(frequencyScaler) {
            mFrequencyScaler = frequencyScaler
            updatePhaseIncrement()
        }

    fun incrementWrapPhase(): Float {
        mPhase += mPhaseIncrement
        while (mPhase > 1.0) {
            mPhase -= 2.0f
        }
        while (mPhase < -1.0) {
            mPhase += 2.0f
        }
        return mPhase
    }

    override fun render(): Float {
        return incrementWrapPhase() * amplitude
    }
}