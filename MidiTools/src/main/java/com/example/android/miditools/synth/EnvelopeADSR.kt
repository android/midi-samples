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
 * Very simple Attack, Decay, Sustain, Release envelope with linear ramps.
 *
 * Times are in seconds.
 */
class EnvelopeADSR(private val mSampleRate: Int) : SynthUnit() {
    private var mAttackRate = 0f
    private var mReleaseRate = 0f
    private var mSustainLevel = 0f
    private var mDecayRate = 0f
    private var mCurrent = 0f
    private var mState = IDLE
    private fun setAttackTime(inTime: Float) {
        var time = inTime
        if (time < MIN_TIME) time = MIN_TIME
        mAttackRate = 1.0f / (mSampleRate * time)
    }

    private fun setDecayTime(inTime: Float) {
        var time = inTime
        if (time < MIN_TIME) time = MIN_TIME
        mDecayRate = 1.0f / (mSampleRate * time)
    }

    private fun setSustainLevel(inLevel: Float) {
        var level = inLevel
        if (level < 0.0f) level = 0.0f
        mSustainLevel = level
    }

    private fun setReleaseTime(inTime: Float) {
        var time = inTime
        if (time < MIN_TIME) time = MIN_TIME
        mReleaseRate = 1.0f / (mSampleRate * time)
    }

    fun on() {
        mState = ATTACK
    }

    fun off() {
        mState = RELEASE
    }

    override fun render(): Float {
        when (mState) {
            ATTACK -> {
                mCurrent += mAttackRate
                if (mCurrent > 1.0f) {
                    mCurrent = 1.0f
                    mState = DECAY
                }
            }
            DECAY -> {
                mCurrent -= mDecayRate
                if (mCurrent < mSustainLevel) {
                    mCurrent = mSustainLevel
                    mState = SUSTAIN
                }
            }
            RELEASE -> {
                mCurrent -= mReleaseRate
                if (mCurrent < 0.0f) {
                    mCurrent = 0.0f
                    mState = FINISHED
                }
            }
        }
        return mCurrent
    }

    val isDone: Boolean
        get() = mState == FINISHED

    companion object {
        private const val IDLE = 0
        private const val ATTACK = 1
        private const val DECAY = 2
        private const val SUSTAIN = 3
        private const val RELEASE = 4
        private const val FINISHED = 5
        private const val MIN_TIME = 0.001f
    }

    init {
        setAttackTime(0.003f)
        setDecayTime(0.08f)
        setSustainLevel(0.3f)
        setReleaseTime(1.0f)
    }
}