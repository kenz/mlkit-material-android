/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.ml.md.barcodedetection

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Paint.Style
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.content.ContextCompat
import com.google.firebase.ml.md.camera.GraphicOverlay
import com.google.firebase.ml.md.camera.GraphicOverlay.Graphic
import com.google.firebase.ml.md.R
import com.google.firebase.ml.md.settings.PreferenceUtils

internal abstract class BarcodeGraphicBase(overlay: GraphicOverlay) : Graphic(overlay) {

    private val boxPaint: Paint
    private val scrimPaint: Paint
    private val eraserPaint: Paint

    val boxCornerRadius: Int
    val pathPaint: Paint
    val boxRect: RectF

    init {

        boxPaint = Paint()
        boxPaint.color = ContextCompat.getColor(context, R.color.barcode_reticle_stroke)
        boxPaint.style = Paint.Style.STROKE
        boxPaint.strokeWidth = context.resources.getDimensionPixelOffset(R.dimen.barcode_reticle_stroke_width).toFloat()

        scrimPaint = Paint()
        scrimPaint.color = ContextCompat.getColor(context, R.color.barcode_reticle_background)
        eraserPaint = Paint()
        eraserPaint.strokeWidth = boxPaint.strokeWidth
        eraserPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

        boxCornerRadius = context.resources.getDimensionPixelOffset(R.dimen.barcode_reticle_corner_radius)

        pathPaint = Paint()
        pathPaint.color = Color.WHITE
        pathPaint.style = Paint.Style.STROKE
        pathPaint.strokeWidth = boxPaint.strokeWidth
        pathPaint.pathEffect = CornerPathEffect(boxCornerRadius.toFloat())

        boxRect = PreferenceUtils.getBarcodeReticleBox(overlay)
    }

    override fun draw(canvas: Canvas) {
        // Draws the dark background scrim and leaves the box area clear.
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), scrimPaint)
        // As the stroke is always centered, so erase twice with FILL and STROKE respectively to clear
        // all area that the box rect would occupy.
        eraserPaint.style = Style.FILL
        canvas.drawRoundRect(boxRect, boxCornerRadius.toFloat(), boxCornerRadius.toFloat(), eraserPaint)
        eraserPaint.style = Style.STROKE
        canvas.drawRoundRect(boxRect, boxCornerRadius.toFloat(), boxCornerRadius.toFloat(), eraserPaint)

        // Draws the box.
        canvas.drawRoundRect(boxRect, boxCornerRadius.toFloat(), boxCornerRadius.toFloat(), boxPaint)
    }
}
