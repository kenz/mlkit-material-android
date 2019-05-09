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

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.google.firebase.ml.md.R
import com.google.firebase.ml.md.productsearch.ProductAdapter.ProductViewHolder

/** Presents the list of product items from cloud product search.  */
class ProductAdapter(private val productList: List<Product>) : Adapter<ProductViewHolder>() {

    internal class ProductViewHolder private constructor(view: View) : RecyclerView.ViewHolder(view) {

        private val imageView: ImageView
        private val titleView: TextView
        private val subtitleView: TextView
        private val imageSize: Int

        init {
            imageView = view.findViewById(R.id.product_image)
            titleView = view.findViewById(R.id.product_title)
            subtitleView = view.findViewById(R.id.product_subtitle)
            imageSize = view.resources.getDimensionPixelOffset(R.dimen.product_item_image_size)
        }

        fun bindProduct(product: Product) {
            imageView.setImageDrawable(null)
            if (!TextUtils.isEmpty(product.imageUrl)) {
                ImageDownloadTask(imageView, imageSize).execute(product.imageUrl)
            } else {
                imageView.setImageResource(R.drawable.logo_google_cloud)
            }
            titleView.text = product.title
            subtitleView.text = product.subtitle
        }

        companion object {

            fun create(parent: ViewGroup): ProductViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.product_item, parent, false)
                return ProductViewHolder(view)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        return ProductViewHolder.create(parent)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bindProduct(productList[position])
    }

    override fun getItemCount(): Int {
        return productList.size
    }
}
