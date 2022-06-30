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

import android.annotation.TargetApi
import android.media.AudioTrack
import android.util.Log

/**
 * Optimize the buffer size for an AudioTrack based on the underrun count.
 * Just call update() after every write() to the AudioTrack.
 *
 * The buffer size determines the latency.
 * Lower the latency until there are glitches.
 * Then raise the latency until the glitches stop.
 *
 *
 *
 * This feature was added in N. So we check for support based on the SDK version.
 */
class AudioLatencyTuner @TargetApi(23) constructor(
    private val mAudioTrack: AudioTrack,
    framesPerBlock: Int
) {
    private val mInitialSize: Int = mAudioTrack.bufferSizeInFrames
    private val mFramesPerBlock: Int = framesPerBlock
    private var mState = STATE_PRIMING
    private var mPreviousUnderrunCount = 0

    /**
     * This only works on N or later versions of Android.
     * @return number of times the audio buffer underflowed and glitched.
     */
    @get:TargetApi(24)
    val underrunCount: Int
        get() = mAudioTrack.underrunCount

    /**
     * This only works on N or later versions of Android.
     * @return allocated size of the buffer
     */
    @get:TargetApi(24)
    val bufferCapacityInFrames: Int
        get() = mAudioTrack.bufferCapacityInFrames

    /**
     * Set the amount of the buffer capacity that we want to use.
     * Lower values will reduce latency but may cause glitches.
     * Note that you may not get the size you asked for.
     *
     * This only works on N or later versions of Android.
     *
     * @return actual size of the buffer
     */
    @TargetApi(24)
    fun setBufferSizeInFrames(thresholdFrames: Int): Int {
        return mAudioTrack.setBufferSizeInFrames(thresholdFrames)
    }

    @get:TargetApi(23)
    val bufferSizeInFrames: Int
        get() = mAudioTrack.bufferSizeInFrames

    /**
     * Reset the internal state machine and set the buffer size back to
     * the original size. The tuning process will then restart.
     */
    private fun reset() {
        mState = STATE_PRIMING
        setBufferSizeInFrames(mInitialSize)
    }

    /**
     * This should be called after every write().
     * It will lower the latency until there are underruns.
     * Then it raises the latency until the underruns stop.
     */
    @TargetApi(3)
    fun update() {
        var nextState = mState
        //val underrunCount: Int
        when (mState) {
            STATE_PRIMING -> if (mAudioTrack.playbackHeadPosition > 8 * mFramesPerBlock) {
                nextState = STATE_LOWERING
                mPreviousUnderrunCount = underrunCount
            }
            STATE_LOWERING -> {
                //underrunCount = underrunCount
                if (underrunCount > mPreviousUnderrunCount) {
                    nextState = STATE_RAISING
                } else {
                    if (incrementThreshold(-1)) {
                        // If we hit bottom then start raising it back up.
                        nextState = STATE_RAISING
                    }
                }
                mPreviousUnderrunCount = underrunCount
            }
            STATE_RAISING -> {
                //underrunCount = underrunCount
                if (underrunCount > mPreviousUnderrunCount) {
                    incrementThreshold(1)
                }
                mPreviousUnderrunCount = underrunCount
            }
        }
        mState = nextState
    }

    /**
     * Raise or lower the buffer size in blocks.
     * @return true if the size did not change
     */
    private fun incrementThreshold(deltaBlocks: Int): Boolean {
        val original = bufferSizeInFrames
        var numBlocks = original / mFramesPerBlock
        numBlocks += deltaBlocks
        val target = numBlocks * mFramesPerBlock
        val actual = setBufferSizeInFrames(target)
        Log.i(TAG, "Buffer size changed from $original to $actual")
        return actual == original
    }

    companion object {
        private const val TAG = "AudioLatencyTuner"
        private const val STATE_PRIMING = 0
        private const val STATE_LOWERING = 1
        private const val STATE_RAISING = 2
    }

    init {
        reset()
    }
}