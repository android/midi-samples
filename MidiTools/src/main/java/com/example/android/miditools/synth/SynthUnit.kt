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

import kotlin.math.pow

abstract class SynthUnit {
    abstract fun render(): Float

    companion object {
        private const val CONCERT_A_PITCH = 69.0
        private const val CONCERT_A_FREQUENCY = 440.0

        /**
         * @param pitch
         * MIDI pitch in semitones
         * @return frequency
         */
        fun pitchToFrequency(pitch: Double): Double {
            val semitones = pitch - CONCERT_A_PITCH
            return CONCERT_A_FREQUENCY * 2.0.pow(semitones / 12.0)
        }
    }
}