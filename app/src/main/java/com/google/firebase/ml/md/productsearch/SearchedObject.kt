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

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.firebase.ml.md.R
import com.google.firebase.ml.md.Utils
import com.google.firebase.ml.md.objectdetection.DetectedObject

/** Hosts the detected object info and its search result.  */
class SearchedObject(resources: Resources, private val `object`: DetectedObject, val productList: List<Product>) {
    private val objectThumbnailCornerRadius: Int

    private var objectThumbnail: Bitmap? = null

    val objectIndex: Int
        get() = `object`.objectIndex

    val boundingBox: Rect
        get() = `object`.boundingBox

    init {
        this.objectThumbnailCornerRadius = resources.getDimensionPixelOffset(R.dimen.bounding_box_corner_radius)
    }

    @Synchronized
    fun getObjectThumbnail(): Bitmap {
        if (objectThumbnail == null) {
            objectThumbnail = Utils.getCornerRoundedBitmap(`object`.bitmap, objectThumbnailCornerRadius)
        }
        return objectThumbnail
    }
}
