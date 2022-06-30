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
 * Sinewave oscillator.
 */
class SineOscillator : SawOscillator() {
    override fun render(): Float {
        // Convert raw sawtooth to sine.
        val phase = incrementWrapPhase()
        return fastSin(phase) * amplitude
    }

    companion object {
        // Factorial constants.
        private const val IF3 = 1.0f / (2 * 3)
        private const val IF5 = IF3 / (4 * 5)
        private const val IF7 = IF5 / (6 * 7)
        private const val IF9 = IF7 / (8 * 9)
        private const val IF11 = IF9 / (10 * 11)

        /**
         * Calculate sine using Taylor expansion. Do not use values outside the range.
         *
         * @param currentPhase in the range of -1.0 to +1.0 for one cycle
         */
        fun fastSin(currentPhase: Float): Float {

            /* Wrap phase back into region where results are more accurate. */
            val yp =
                when {
                    currentPhase > 0.5f -> 1.0f - currentPhase
                    currentPhase < -0.5f -> -1.0f - currentPhase
                    else -> currentPhase
                }
            val x = (yp * Math.PI).toFloat()
            val x2 = x * x
            /* Taylor expansion out to x**11/11! factored into multiply-adds */
            return x * (x2 * (x2 * (x2 * (x2 * (x2 * -IF11 + IF9) - IF7) + IF5) - IF3) + 1)
        }
    }
}