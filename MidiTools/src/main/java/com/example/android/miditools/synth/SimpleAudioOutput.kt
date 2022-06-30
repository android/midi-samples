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

import android.media.AudioTrack
import kotlin.jvm.Volatile
import android.media.AudioDeviceInfo
import android.annotation.TargetApi
import android.os.Build
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack.PERFORMANCE_MODE_LOW_LATENCY
import android.util.Log

/**
 * Simple base class for implementing audio output for examples.
 * This can be sub-classed for experimentation or to redirect audio output.
 */
class SimpleAudioOutput {
    private var mAudioTrack: AudioTrack? = null
    var frameRate = 0
        private set
    private var mLatencyTuner: AudioLatencyTuner? = null
    private val mLatencyController = MyLatencyController()
    private var previousBeginTime: Long = 0

    @Volatile
    private var filteredCpuInterval: Long = 0

    @Volatile
    private var filteredTotalInterval: Long = 0
    private var mAudioDeviceInfo: AudioDeviceInfo? = null

    internal inner class MyLatencyController : LatencyController() {
        override val isRunning: Boolean
            get() = mAudioTrack != null

        override val bufferSizeInFrames: Int
            get() {
                val track = mAudioTrack
                return track?.bufferSizeInFrames ?: 0
            }
        override val bufferCapacityInFrames: Int
            get() {
                val tuner = mLatencyTuner
                return tuner?.bufferCapacityInFrames ?: 0
            }
        override val underrunCount: Int
            get() {
                val tuner = mLatencyTuner
                return tuner?.underrunCount ?: 0
            }
        override val cpuLoad: Int
            get() {
                var load = 0
                if (filteredTotalInterval > 0) {
                    load = (filteredCpuInterval * 100 / filteredTotalInterval).toInt()
                }
                return load
            }
    }

    /**
     * Create an audio track then call play().
     */
    fun start(framesPerBlock: Int, audioDeviceInfo: AudioDeviceInfo?) {
        stop()
        mAudioDeviceInfo = audioDeviceInfo
        mAudioTrack = createAudioTrack()
        mLatencyTuner = AudioLatencyTuner(mAudioTrack!!, framesPerBlock)
        // Use frame rate chosen by the AudioTrack so that we can get a
        // low latency fast mixer track.
        frameRate = mAudioTrack!!.sampleRate
        // AudioTrack will wait until it has enough data before starting.
        mAudioTrack!!.play()
        previousBeginTime = 0
        filteredCpuInterval = 0
        filteredTotalInterval = 0
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun createAudioTrack(): AudioTrack {
        val attributesBuilder = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)

        val attributes = attributesBuilder.build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val builder = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setPerformanceMode(PERFORMANCE_MODE_LOW_LATENCY)
            .setAudioFormat(format)
        // Start with a bigger buffer because we can lower it later.
        val bufferSizeInFrames = LOW_LATENCY_BUFFER_CAPACITY_IN_FRAMES
        builder.setBufferSizeInBytes(bufferSizeInFrames * BYTES_PER_FRAME)
        val track = builder.build()
        track.preferredDevice = mAudioDeviceInfo
        Log.i(TAG, "Device: " + track.routedDevice.productName)
        return track
    }

    fun write(buffer: FloatArray?, offset: Int, length: Int): Int {
        //Log.i(TAG, "write: " + length);
        endCpuLoadInterval()
        val result = mAudioTrack!!.write(
            buffer!!, offset, length,
            AudioTrack.WRITE_BLOCKING
        )
        // This thread just woke up and will now render some audio.
        beginCpuLoadInterval()
        if (result > 0 && mLatencyController.isAutoSizeEnabled) {
            mLatencyTuner!!.update()
        }
        return result
    }

    private fun endCpuLoadInterval() {
        val now = System.nanoTime()
        if (previousBeginTime > 0) {
            val elapsed = now - previousBeginTime
            // recursive low pass filter
            filteredCpuInterval = (filteredCpuInterval * LOAD_FILTER_SCALER + elapsed
                    shr LOAD_FILTER_SHIFT)
        }
    }

    private fun beginCpuLoadInterval() {
        val now = System.nanoTime()
        if (previousBeginTime > 0) {
            val elapsed = now - previousBeginTime
            // recursive low pass filter
            filteredTotalInterval = (filteredTotalInterval * LOAD_FILTER_SCALER + elapsed
                    shr LOAD_FILTER_SHIFT)
        }
        previousBeginTime = now
    }

    fun stop() {
        if (mAudioTrack != null) {
            mAudioTrack!!.stop()
            mAudioTrack!!.release()
            mAudioTrack = null
        }
    }

    val latencyController: LatencyController
        get() = mLatencyController

    companion object {
        private const val TAG = "SimpleAudioOutput"
        private const val SAMPLES_PER_FRAME = 2
        private const val BYTES_PER_SAMPLE = 4 // float
        const val BYTES_PER_FRAME = SAMPLES_PER_FRAME * BYTES_PER_SAMPLE

        // Arbitrary weighting factor for CPU load filter. Higher number for slower response.
        private const val LOAD_FILTER_SHIFT = 6
        private const val LOAD_FILTER_SCALER = (1 shl LOAD_FILTER_SHIFT) - 1

        // LOW_LATENCY_BUFFER_CAPACITY_IN_FRAMES is only used when we do low latency tuning.
        // The *3 is because some devices have a 1 millisecond period. And at
        // 48000 Hz that is 48, which is 16*3.
        // The 512 is arbitrary. 512*3 gives us a 32 millisecond buffer at 48000 Hz.
        // That is more than we need but not hugely wasteful.
        private const val LOW_LATENCY_BUFFER_CAPACITY_IN_FRAMES = 512 * 3
    }
}