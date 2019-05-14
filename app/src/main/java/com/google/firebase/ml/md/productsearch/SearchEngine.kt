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
import com.google.firebase.ml.md.objectdetection.DetectedObject
import kotlinx.coroutines.*

/** A fake search engine to help simulate the complete work flow.  */
class SearchEngine() {


    var deferred:Deferred<List<Product>>? = null
    @FlowPreview
    suspend fun search(detectedObject: DetectedObject, listener: (detectedObject: DetectedObject, productList: List<Product>) -> Unit)
            = GlobalScope.launch(Dispatchers.Main) {
        // Crops the object image out of the full image is expensive, so do it off the UI thread.
        deferred = async(Dispatchers.Default){
            when (val result = createRequest(detectedObject)) {
                is Result.Success -> handleSuccess(result)
                is Result.Failed -> handleFailed(result)
            }

        }
        deferred?.
            await()?.let{
                listener.invoke(detectedObject, it)
            }
        }


    fun shutdown() {
        deferred?.cancel()
    }

    private fun handleSuccess(result: Result.Success):  List<Product>{
        // add process if you need.
        return result.value
    }

    private fun handleFailed(result: Result.Failed): List<Product>{
        // create dummy data.
        return arrayOf(0..7).map{ Product("", "Product title $it", "Product subtitle $it") }
    }

    @Throws(Exception::class)
    fun createRequest(searchingObject: DetectedObject): Result {
        val objectImageData = searchingObject.imageData
                ?: return Result.Failed(Exception("Failed to get object image data!"))

        // Hooks up with your own product search backend here.
        return Result.Failed(Exception("Hooks up with your own product search backend."))
    }


    companion object {
        private const val TAG = "SearchEngine"
    }
}

sealed class Result {
    class Success(val value: List<Product>) : Result()

    class Failed(val exception: java.lang.Exception) : Result()
}
