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

package com.google.firebase.ml.md

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Entry activity to select the detection mode.  */
class MainActivity : AppCompatActivity() {

    private enum class DetectionMode private constructor(val titleResId: Int, val subtitleResId: Int) {
        ODT_LIVE(R.string.mode_odt_live_title, R.string.mode_odt_live_subtitle),
        ODT_STATIC(R.string.mode_odt_static_title, R.string.mode_odt_static_subtitle),
        BARCODE_LIVE(R.string.mode_barcode_live_title, R.string.mode_barcode_live_subtitle)
    }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        setContentView(R.layout.activity_main)

        val modeRecyclerView = findViewById<RecyclerView>(R.id.mode_recycler_view)
        modeRecyclerView.setHasFixedSize(true)
        modeRecyclerView.layoutManager = LinearLayoutManager(this)
        modeRecyclerView.adapter = ModeItemAdapter(DetectionMode.values())
    }

    override fun onResume() {
        super.onResume()
        if (!Utils.allPermissionsGranted(this)) {
            Utils.requestRuntimePermissions(this)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == Utils.REQUEST_CODE_PHOTO_LIBRARY
                && resultCode == Activity.RESULT_OK
                && data != null) {
            val intent = Intent(this, StaticObjectDetectionActivity::class.java)
            intent.data = data.data
            startActivity(intent)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private inner class ModeItemAdapter internal constructor(private val detectionModes: Array<DetectionMode>) : RecyclerView.Adapter<ModeItemAdapter.ModeItemViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModeItemViewHolder {
            return ModeItemViewHolder(
                    LayoutInflater.from(parent.context)
                            .inflate(R.layout.detection_mode_item, parent, false))
        }

        override fun onBindViewHolder(modeItemViewHolder: ModeItemViewHolder, position: Int) {
            modeItemViewHolder.bindDetectionMode(detectionModes[position])
        }

        override fun getItemCount(): Int {
            return detectionModes.size
        }

        private inner class ModeItemViewHolder internal constructor(view: View) : RecyclerView.ViewHolder(view) {

            private val titleView: TextView
            private val subtitleView: TextView

            init {
                titleView = view.findViewById(R.id.mode_title)
                subtitleView = view.findViewById(R.id.mode_subtitle)
            }

            internal fun bindDetectionMode(detectionMode: DetectionMode) {
                titleView.setText(detectionMode.titleResId)
                subtitleView.setText(detectionMode.subtitleResId)
                itemView.setOnClickListener { view ->
                    val activity = this@MainActivity
                    when (detectionMode) {
                        MainActivity.DetectionMode.ODT_LIVE -> activity.startActivity(Intent(activity, LiveObjectDetectionActivity::class.java))
                        MainActivity.DetectionMode.ODT_STATIC -> Utils.openImagePicker(activity)
                        MainActivity.DetectionMode.BARCODE_LIVE -> activity.startActivity(Intent(activity, LiveBarcodeScanningActivity::class.java))
                    }
                }
            }
        }
    }
}
