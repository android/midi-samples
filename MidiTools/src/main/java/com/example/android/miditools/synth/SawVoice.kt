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
 * Sawtooth oscillator with an ADSR.
 */
open class SawVoice(sampleRate: Int) : SynthVoice() {
    private val mOscillator: SawOscillator by lazy { createOscillator() }
    private val mEnvelope: EnvelopeADSR = EnvelopeADSR(sampleRate)
    protected open fun createOscillator(): SawOscillator {
        return SawOscillator()
    }

    override fun noteOn(noteIndex: Int, velocity: Int) {
        super.noteOn(noteIndex, velocity)
        mOscillator.setPitch(noteIndex.toFloat())
        mOscillator.amplitude = amplitude
        mEnvelope.on()
    }

    override fun noteOff() {
        super.noteOff()
        mEnvelope.off()
    }

    override fun setFrequencyScaler(scaler: Float) {
        mOscillator.frequencyScaler = scaler
    }

    override fun render(): Float {
        return mOscillator.render() * mEnvelope.render()
    }

    override fun isDone(): Boolean {
        return mEnvelope.isDone
    }
}