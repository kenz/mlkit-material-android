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

package com.google.firebase.ml.md.productsearch

import com.google.common.base.Preconditions.checkArgument

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.firebase.ml.md.R

/** Draws the scrim of bottom sheet with object thumbnail highlighted.  */
class BottomSheetScrimView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val scrimPaint: Paint
    private val thumbnailPaint: Paint
    private val boxPaint: Paint
    private val thumbnailHeight: Int
    private val thumbnailMargin: Int
    private val boxCornerRadius: Int

    private var thumbnailBitmap: Bitmap? = null
    private var thumbnailRect: RectF? = null
    private var downPercentInCollapsed: Float = 0.toFloat()

    init {

        val resources = context.resources
        scrimPaint = Paint()
        scrimPaint.color = ContextCompat.getColor(context, R.color.dark)

        thumbnailPaint = Paint()

        boxPaint = Paint()
        boxPaint.style = Paint.Style.STROKE
        boxPaint.strokeWidth = resources.getDimensionPixelOffset(R.dimen.object_thumbnail_stroke_width).toFloat()
        boxPaint.color = Color.WHITE

        thumbnailHeight = resources.getDimensionPixelOffset(R.dimen.object_thumbnail_height)
        thumbnailMargin = resources.getDimensionPixelOffset(R.dimen.object_thumbnail_margin)
        boxCornerRadius = resources.getDimensionPixelOffset(R.dimen.bounding_box_corner_radius)
    }

    /**
     * Translates the object thumbnail up or down along with bottom sheet's sliding movement, with
     * keeping thumbnail size fixed.
     */
    fun updateWithThumbnailTranslate(
            thumbnailBitmap: Bitmap, collapsedStateHeight: Int, slideOffset: Float, bottomSheet: View) {
        this.thumbnailBitmap = thumbnailBitmap

        val currentSheetHeight: Float
        if (slideOffset < 0) {
            downPercentInCollapsed = -slideOffset
            currentSheetHeight = collapsedStateHeight * (1 + slideOffset)
        } else {
            downPercentInCollapsed = 0f
            currentSheetHeight = collapsedStateHeight + (bottomSheet.height - collapsedStateHeight) * slideOffset
        }

        val thumbnailWidth = thumbnailBitmap.width.toFloat() / thumbnailBitmap.height * thumbnailHeight
        thumbnailRect = RectF()
        thumbnailRect!!.left = thumbnailMargin.toFloat()
        thumbnailRect!!.top = height.toFloat() - currentSheetHeight - thumbnailMargin.toFloat() - thumbnailHeight.toFloat()
        thumbnailRect!!.right = thumbnailRect!!.left + thumbnailWidth
        thumbnailRect!!.bottom = thumbnailRect!!.top + thumbnailHeight

        invalidate()
    }

    /**
     * Translates the object thumbnail from original bounding box location to at where the bottom
     * sheet is settled as COLLAPSED state, with its size scales gradually.
     *
     *
     * It's only used by sliding the sheet up from hidden state to collapsed state.
     */
    fun updateWithThumbnailTranslateAndScale(
            thumbnailBitmap: Bitmap, collapsedStateHeight: Int, slideOffset: Float, srcThumbnailRect: RectF) {
        checkArgument(
                slideOffset <= 0,
                "Scale mode works only when the sheet is between hidden and collapsed states.")

        this.thumbnailBitmap = thumbnailBitmap
        this.downPercentInCollapsed = 0f

        val dstX = thumbnailMargin.toFloat()
        val dstY = (height - collapsedStateHeight - thumbnailMargin - thumbnailHeight).toFloat()
        val dstHeight = thumbnailHeight.toFloat()
        val dstWidth = srcThumbnailRect.width() / srcThumbnailRect.height() * dstHeight
        val dstRect = RectF(dstX, dstY, dstX + dstWidth, dstY + dstHeight)

        val progressToCollapsedState = 1 + slideOffset
        thumbnailRect = RectF()
        thumbnailRect!!.left = srcThumbnailRect.left + (dstRect.left - srcThumbnailRect.left) * progressToCollapsedState
        thumbnailRect!!.top = srcThumbnailRect.top + (dstRect.top - srcThumbnailRect.top) * progressToCollapsedState
        thumbnailRect!!.right = srcThumbnailRect.right + (dstRect.right - srcThumbnailRect.right) * progressToCollapsedState
        thumbnailRect!!.bottom = srcThumbnailRect.bottom + (dstRect.bottom - srcThumbnailRect.bottom) * progressToCollapsedState

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draws the dark background.
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        if (thumbnailBitmap != /* src= */ null && downPercentInCollapsed < DOWN_PERCENT_TO_HIDE_THUMBNAIL) {
            val alpha = ((1 - downPercentInCollapsed / DOWN_PERCENT_TO_HIDE_THUMBNAIL) * 255).toInt()

            // Draws the object thumbnail.
            thumbnailPaint.alpha = alpha
            canvas.drawBitmap(thumbnailBitmap!!, null, thumbnailRect!!, thumbnailPaint)

            // Draws the bounding box.
            boxPaint.alpha = alpha
            canvas.drawRoundRect(thumbnailRect!!, boxCornerRadius.toFloat(), boxCornerRadius.toFloat(), boxPaint)
        }
    }

    companion object {

        private val DOWN_PERCENT_TO_HIDE_THUMBNAIL = 0.42f
    }
}
