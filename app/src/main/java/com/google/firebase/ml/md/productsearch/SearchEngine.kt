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

import android.content.Context
import android.util.Log
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.tasks.Tasks
import com.google.firebase.ml.md.objectdetection.DetectedObject
import java.util.ArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** A fake search engine to help simulate the complete work flow.  */
class SearchEngine(context: Context) {

    private val searchRequestQueue: RequestQueue
    private val requestCreationExecutor: ExecutorService

    interface SearchResultListener {
        fun onSearchCompleted(`object`: DetectedObject, productList: List<Product>)
    }

    init {
        searchRequestQueue = Volley.newRequestQueue(context)
        requestCreationExecutor = Executors.newSingleThreadExecutor()
    }

    fun search(`object`: DetectedObject, listener: SearchResultListener) {
        // Crops the object image out of the full image is expensive, so do it off the UI thread.
        Tasks.call<JsonObjectRequest>(requestCreationExecutor, { createRequest(`object`) })
                .addOnSuccessListener { productRequest -> searchRequestQueue.add<*>(productRequest.setTag(TAG)) }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to create product search request!", e)
                    // Remove the below dummy code after your own product search backed hooked up.
                    val productList = ArrayList<Product>()
                    for (i in 0..7) {
                        productList.add(
                                Product(/* imageUrl= */"", "Product title $i", "Product subtitle $i"))
                    }
                    listener.onSearchCompleted(`object`, productList)
                }
    }

    fun shutdown() {
        searchRequestQueue.cancelAll(TAG)
        requestCreationExecutor.shutdown()
    }

    companion object {

        private val TAG = "SearchEngine"

        @Throws(Exception::class)
        private fun createRequest(searchingObject: DetectedObject): JsonObjectRequest {
            val objectImageData = searchingObject.imageData
                    ?: throw Exception("Failed to get object image data!")

            // Hooks up with your own product search backend here.
            throw Exception("Hooks up with your own product search backend.")
        }
    }
}
