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
 * Abstract control over the audio latency.
 */
abstract class LatencyController {
    /**
     * If true then adjust latency to lowest value that does not produce underruns.
     */
    open var isAutoSizeEnabled = true

    /**
     * The amount of the buffer capacity that is being used.
     * @return
     */
    abstract val bufferSizeInFrames: Int

    /**
     * The allocated size of the buffer.
     * @return
     */
    abstract val bufferCapacityInFrames: Int
    abstract val underrunCount: Int

    /**
     * When the output is running, the LOW_LATENCY flag cannot be set.
     * @return
     */
    abstract val isRunning: Boolean

    /**
     * Calculate the percentage of time that the a CPU is calculating data.
     * @return percent CPU load
     */
    abstract val cpuLoad: Int
}