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
package com.example.android.miditools

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.ArrayList
import java.util.HashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * View that displays a traditional piano style keyboard. Finger presses are reported to a
 * MusicKeyListener. Keys that pressed are highlighted. Running a finger along the top of the
 * keyboard will only hit black keys. Running a finger along the bottom of the keyboard will only
 * hit white keys.
 */
class MusicKeyboardView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    // Preferences
    private var mNumKeys = 0
    private var mNumPortraitKeys = NOTES_PER_OCTAVE + 1
    private var mNumLandscapeKeys = 2 * NOTES_PER_OCTAVE + 1
    private var mNumWhiteKeys = 15

    // Geometry.
    private var mWidth = 0f
    private var mHeight = 0f
    private var mWhiteKeyWidth = 0f
    private var mBlackKeyWidth = 0f

    // Y position of bottom of black keys.
    private var mBlackBottom = 0f
    private lateinit var mBlackKeyRectangles: Array<Rect>

    // Keyboard state
    private val mNotesOnByPitch = BooleanArray(128)

    // Appearance
    private var mShadowPaint: Paint? = null
    private var mBlackOnKeyPaint: Paint? = null
    private var mBlackOffKeyPaint: Paint? = null
    private var mWhiteOnKeyPaint: Paint? = null
    private var mWhiteOffKeyPaint: Paint? = null
    private val mLegato = true
    // Maps each finger to a pair of notes and y location
    private val mFingerMap = HashMap<Int, Pair<Int, Float>>()

    // Note number for the left most key.
    private var mLowestPitch = PITCH_MIDDLE_C - NOTES_PER_OCTAVE
    private val mListeners = ArrayList<MusicKeyListener>()

    /** Implement this to receive keyboard events.  */
    interface MusicKeyListener {
        /** This will be called when a key is pressed.  */
        fun onKeyDown(keyIndex: Int)

        /** This will be called when a key is pressed.  */
        fun onKeyUp(keyIndex: Int)

        /** This will called when the pitch bend changes.
         * pitch bend will be a float between 0 and 1 */
        fun onPitchBend(keyIndex: Int, bend: Float)
    }

    private fun init() {
        mShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mShadowPaint!!.style = Paint.Style.FILL
        mShadowPaint!!.color = -0x8f8f90
        mBlackOnKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mBlackOnKeyPaint!!.style = Paint.Style.FILL
        mBlackOnKeyPaint!!.color = -0xdfdf20
        mBlackOffKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mBlackOffKeyPaint!!.style = Paint.Style.FILL
        mBlackOffKeyPaint!!.color = -0xdfdfe0
        mWhiteOnKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mWhiteOnKeyPaint!!.style = Paint.Style.FILL
        mWhiteOnKeyPaint!!.color = -0x9f9f10
        mWhiteOffKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mWhiteOffKeyPaint!!.style = Paint.Style.FILL
        mWhiteOffKeyPaint!!.color = -0xf0f10
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        mWidth = w.toFloat()
        mHeight = h.toFloat()
        mNumKeys = if (mHeight > mWidth) mNumPortraitKeys else mNumLandscapeKeys
        mNumWhiteKeys = 0
        // Count white keys.
        for (i in 0 until mNumKeys) {
            val pitch = mLowestPitch + i
            if (!isPitchBlack(pitch)) {
                mNumWhiteKeys++
            }
        }
        mWhiteKeyWidth = mWidth / mNumWhiteKeys
        mBlackKeyWidth = mWhiteKeyWidth * BLACK_KEY_WIDTH_FACTOR
        mBlackBottom = mHeight * BLACK_KEY_HEIGHT_FACTOR
        makeBlackRectangles()
    }

    private fun makeBlackRectangles() {
        val top = 0
        val rectangles = ArrayList<Rect>()
        var whiteKeyIndex = 0
        var blackKeyIndex = 0
        for (i in 0 until mNumKeys) {
            val x = mWhiteKeyWidth * whiteKeyIndex
            val pitch = mLowestPitch + i
            val note = pitch % NOTES_PER_OCTAVE
            if (NOTE_IN_OCTAVE_IS_BLACK[note]) {
                val leftComplement = BLACK_KEY_LEFT_COMPLEMENTS[mLowestPitch % 12]
                val offset = (BLACK_KEY_OFFSET_FACTOR
                        * BLACK_KEY_HORIZONTAL_OFFSETS[(blackKeyIndex + leftComplement) % 5])
                var left = x - mBlackKeyWidth * (0.55f - offset)
                left += WHITE_KEY_GAP / 2f
                val right = left + mBlackKeyWidth
                val rect = Rect(left.roundToInt(), top, right.roundToInt(),
                    mBlackBottom.roundToInt())
                rectangles.add(rect)
                blackKeyIndex++
            } else {
                whiteKeyIndex++
            }
        }
        mBlackKeyRectangles = rectangles.toTypedArray()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        var whiteKeyIndex = 0
        canvas.drawRect(0f, 0f, mWidth, mHeight, mShadowPaint!!)
        // Draw white keys first.
        for (i in 0 until mNumKeys) {
            val pitch = mLowestPitch + i
            val note = pitch % NOTES_PER_OCTAVE
            if (!NOTE_IN_OCTAVE_IS_BLACK[note]) {
                val x = mWhiteKeyWidth * whiteKeyIndex + WHITE_KEY_GAP / 2
                val paint = if (mNotesOnByPitch[pitch]) mWhiteOnKeyPaint else mWhiteOffKeyPaint
                canvas.drawRect(
                    x, 0f, x + mWhiteKeyWidth - WHITE_KEY_GAP, mHeight,
                    paint!!
                )
                whiteKeyIndex++
            }
        }
        // Then draw black keys over the white keys.
        var blackKeyIndex = 0
        for (i in 0 until mNumKeys) {
            val pitch = mLowestPitch + i
            val note = pitch % NOTES_PER_OCTAVE
            if (NOTE_IN_OCTAVE_IS_BLACK[note]) {
                val r = mBlackKeyRectangles[blackKeyIndex]
                val paint = if (mNotesOnByPitch[pitch]) mBlackOnKeyPaint else mBlackOffKeyPaint
                canvas.drawRect(r, paint!!)
                blackKeyIndex++
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    override fun onTouchEvent(event: MotionEvent): Boolean {
        super.onTouchEvent(event)
        val action = event.actionMasked
        // Track individual fingers.
        val pointerIndex = event.actionIndex
        var id = event.getPointerId(pointerIndex)
        // Get the pointer's current position
        var x = event.getX(pointerIndex)
        var y = event.getY(pointerIndex)
        // Some devices can return negative x or y, which can cause an array exception.
        x = max(x, 0.0f)
        y = max(y, 0.0f)
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                onFingerDown(id, x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerCount = event.pointerCount
                var i = 0
                while (i < pointerCount) {
                    id = event.getPointerId(i)
                    x = event.getX(i)
                    y = event.getY(i)
                    x = max(x, 0.0f)
                    y = max(y, 0.0f)
                    onFingerMove(id, x, y)
                    i++
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                onFingerUp(id, x, y)
            }
            MotionEvent.ACTION_CANCEL -> {
                onAllFingersUp()
            }
            else -> {
            }
        }

        performClick()
        // Must return true or we do not get the ACTION_MOVE and
        // ACTION_UP events.
        return true
    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun onFingerDown(id: Int, x: Float, y: Float) {
        val pitch = xyToPitch(x, y)
        fireKeyDown(pitch)
        mFingerMap[id] = Pair(pitch, y)
    }

    private fun onFingerMove(id: Int, x: Float, y: Float) {
        val previousPitch = mFingerMap[id]?.first
        if (previousPitch != null) {
            val pitch = if (y < mBlackBottom) {
                // Only hit black keys if above line.
                xyToBlackPitch(x, y)
            } else {
                xToWhitePitch(x)
            }
            // Did we change to a new key.
            if (pitch >= 0 && pitch != previousPitch) {
                if (mLegato) {
                    fireKeyDown(pitch)
                    fireKeyUp(previousPitch)
                } else {
                    fireKeyUp(previousPitch)
                    fireKeyDown(pitch)
                }
                mFingerMap[id] = Pair(pitch, y)
            }
            if (pitch >= 0) {
                val previousY = mFingerMap[id]?.second
                if ((pitch != previousPitch) || (previousY == null) || (PITCH_BEND_HEIGHT_CHANGE_FACTOR * mHeight < abs(y - previousY))) {
                    if (y < mBlackBottom) {
                        firePitchBend(pitch, y / mBlackBottom)
                    } else {
                        firePitchBend(pitch, (y - mBlackBottom) / (mHeight - mBlackBottom))
                    }
                    mFingerMap[id] = Pair(pitch, y)
                }
            }
        }
    }

    private fun onFingerUp(id: Int, x: Float, y: Float) {
        val previousPitch = mFingerMap[id]?.first
        if (previousPitch != null) {
            fireKeyUp(previousPitch)
            mFingerMap.remove(id)
        } else {
            val pitch = xyToPitch(x, y)
            fireKeyUp(pitch)
        }
    }

    private fun onAllFingersUp() {
        // Turn off all notes.
        for (notes in mFingerMap.values) {
            fireKeyUp(notes.first)
        }
        mFingerMap.clear()
    }

    private fun fireKeyDown(pitch: Int) {
        for (listener in mListeners) {
            listener.onKeyDown(pitch)
        }
        mNotesOnByPitch[pitch] = true
        invalidate()
    }

    private fun fireKeyUp(pitch: Int) {
        for (listener in mListeners) {
            listener.onKeyUp(pitch)
        }
        mNotesOnByPitch[pitch] = false
        invalidate()
    }

    private fun firePitchBend(pitch: Int, bend: Float) {
        for (listener in mListeners) {
            listener.onPitchBend(pitch, bend)
        }
    }

    private fun xyToPitch(x: Float, y: Float): Int {
        var pitch = -1
        if (y < mBlackBottom) {
            pitch = xyToBlackPitch(x, y)
        }
        if (pitch < 0) {
            pitch = xToWhitePitch(x)
        }
        return pitch
    }

    private fun isPitchBlack(pitch: Int): Boolean {
        val note = pitch % NOTES_PER_OCTAVE
        return NOTE_IN_OCTAVE_IS_BLACK[note]
    }

    // Convert x to MIDI pitch. Ignores black keys.
    private fun xToWhitePitch(x: Float): Int {
        val leftComplement =
            WHITE_KEY_LEFT_COMPLEMENTS[mLowestPitch % 12]
        val octave2 = mLowestPitch / 12 - 1
        val whiteKeyIndex = (x / mWhiteKeyWidth).toInt() + leftComplement
        val octave =
            whiteKeyIndex / WHITE_KEY_OFFSETS.size
        val indexInOctave =
            whiteKeyIndex - octave * WHITE_KEY_OFFSETS.size
        return 12 * (octave2 + octave + 1) + WHITE_KEY_OFFSETS[indexInOctave]
    }

    // Convert x to MIDI pitch. Ignores white keys.
    private fun xyToBlackPitch(x: Float, y: Float): Int {
        var result = -1
        var blackKeyIndex = 0
        for (i in 0 until mNumKeys) {
            val pitch = mLowestPitch + i
            if (isPitchBlack(pitch)) {
                val rect = mBlackKeyRectangles[blackKeyIndex]
                if (rect.contains(x.toInt(), y.toInt())) {
                    result = pitch
                    break
                }
                blackKeyIndex++
            }
        }
        return result
    }

    fun addMusicKeyListener(musicKeyListener: MusicKeyListener) {
        mListeners.add(musicKeyListener)
    }

    companion object {
        // Adjust proportions of the keys.
        private const val WHITE_KEY_GAP = 10
        private const val PITCH_MIDDLE_C = 60
        private const val NOTES_PER_OCTAVE = 12
        private val WHITE_KEY_OFFSETS = intArrayOf(
            0, 2, 4, 5, 7, 9, 11
        )
        private const val BLACK_KEY_HEIGHT_FACTOR = 0.60f
        private const val BLACK_KEY_WIDTH_FACTOR = 0.6f
        private const val BLACK_KEY_OFFSET_FACTOR = 0.18f
        private const val PITCH_BEND_HEIGHT_CHANGE_FACTOR = 0.05f
        private val BLACK_KEY_HORIZONTAL_OFFSETS = intArrayOf(
            -1, 1, -1, 0, 1
        )
        private val NOTE_IN_OCTAVE_IS_BLACK = booleanArrayOf(
            false, true,
            false, true,
            false, false, true,
            false, true,
            false, true,
            false
        )

        // These COMPLEMENTS are used to fix a problem with the alignment of the keys.
        // If mLowestPitch starts from the beginning of some octave then there is no problem.
        // But if it's not, then the BLACK_KEY_HORIZONTAL_OFFSETS[blackKeyIndex % 5] would give
        // us the wrong result. So, the "left complement" in this case is the number of black keys we
        // should add to the blackKeyIndex to fill the left space down to the beginning of the octave.
        private val WHITE_KEY_LEFT_COMPLEMENTS = intArrayOf(
            0, 1, 1, 2, 2, 3, 4, 4, 5, 5, 6, 6
        )
        private val BLACK_KEY_LEFT_COMPLEMENTS = intArrayOf(
            0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5
        )
    }

    init {
        init()
    }
}