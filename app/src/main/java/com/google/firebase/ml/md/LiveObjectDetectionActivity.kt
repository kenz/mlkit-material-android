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

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.common.base.Objects
import com.google.common.collect.ImmutableList
import com.google.firebase.ml.md.camera.GraphicOverlay
import com.google.firebase.ml.md.camera.WorkflowModel
import com.google.firebase.ml.md.camera.WorkflowModel.WorkflowState
import com.google.firebase.ml.md.camera.CameraSource
import com.google.firebase.ml.md.camera.CameraSourcePreview
import com.google.firebase.ml.md.objectdetection.MultiObjectProcessor
import com.google.firebase.ml.md.objectdetection.ProminentObjectProcessor
import com.google.firebase.ml.md.productsearch.BottomSheetScrimView
import com.google.firebase.ml.md.productsearch.Product
import com.google.firebase.ml.md.productsearch.ProductAdapter
import com.google.firebase.ml.md.productsearch.SearchEngine
import com.google.firebase.ml.md.productsearch.SearchedObject
import com.google.firebase.ml.md.settings.PreferenceUtils
import com.google.firebase.ml.md.settings.SettingsActivity
import java.io.IOException

/** Demonstrates the object detection and visual search workflow using camera preview.  */
class LiveObjectDetectionActivity : AppCompatActivity(), OnClickListener {

    private var cameraSource: CameraSource? = null
    private var preview: CameraSourcePreview? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var settingsButton: View? = null
    private var flashButton: View? = null
    private var promptChip: Chip? = null
    private var promptChipAnimator: AnimatorSet? = null
    private var searchButton: ExtendedFloatingActionButton? = null
    private var searchButtonAnimator: AnimatorSet? = null
    private var searchProgressBar: ProgressBar? = null
    private var workflowModel: WorkflowModel? = null
    private var currentWorkflowState: WorkflowState? = null
    private var searchEngine: SearchEngine? = null

    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null
    private var bottomSheetScrimView: BottomSheetScrimView? = null
    private var productRecyclerView: RecyclerView? = null
    private var bottomSheetTitleView: TextView? = null
    private var objectThumbnailForBottomSheet: Bitmap? = null
    private var slidingSheetUpFromHiddenState: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        searchEngine = SearchEngine(applicationContext)

        setContentView(R.layout.activity_live_object)
        preview = findViewById(R.id.camera_preview)
        graphicOverlay = findViewById(R.id.camera_preview_graphic_overlay)
        graphicOverlay!!.setOnClickListener(this)
        cameraSource = CameraSource(graphicOverlay!!)

        promptChip = findViewById(R.id.bottom_prompt_chip)
        promptChipAnimator = AnimatorInflater.loadAnimator(this, R.animator.bottom_prompt_chip_enter) as AnimatorSet
        promptChipAnimator!!.setTarget(promptChip)

        searchButton = findViewById(R.id.product_search_button)
        searchButton!!.setOnClickListener(this)
        searchButtonAnimator = AnimatorInflater.loadAnimator(this, R.animator.search_button_enter) as AnimatorSet
        searchButtonAnimator!!.setTarget(searchButton)

        searchProgressBar = findViewById(R.id.search_progress_bar)

        setUpBottomSheet()

        findViewById<View>(R.id.close_button).setOnClickListener(this)
        flashButton = findViewById(R.id.flash_button)
        flashButton!!.setOnClickListener(this)
        settingsButton = findViewById(R.id.settings_button)
        settingsButton!!.setOnClickListener(this)

        setUpWorkflowModel()
    }

    override fun onResume() {
        super.onResume()

        workflowModel!!.markCameraFrozen()
        settingsButton!!.isEnabled = true
        bottomSheetBehavior!!.state = BottomSheetBehavior.STATE_HIDDEN
        currentWorkflowState = WorkflowState.NOT_STARTED
        cameraSource!!.setFrameProcessor(
                if (PreferenceUtils.isMultipleObjectsMode(this))
                    MultiObjectProcessor(graphicOverlay!!, workflowModel!!)
                else
                    ProminentObjectProcessor(graphicOverlay!!, workflowModel!!))
        workflowModel!!.setWorkflowState(WorkflowState.DETECTING)
    }

    override fun onPause() {
        super.onPause()
        currentWorkflowState = WorkflowState.NOT_STARTED
        stopCameraPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (cameraSource != null) {
            cameraSource!!.release()
            cameraSource = null
        }
        searchEngine!!.shutdown()
    }

    override fun onBackPressed() {
        if (bottomSheetBehavior!!.state != BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior!!.setState(BottomSheetBehavior.STATE_HIDDEN)
        } else {
            super.onBackPressed()
        }
    }

    override fun onClick(view: View) {
        val id = view.id
        if (id == R.id.product_search_button) {
            searchButton!!.isEnabled = false
            workflowModel!!.onSearchButtonClicked()

        } else if (id == R.id.bottom_sheet_scrim_view) {
            bottomSheetBehavior!!.setState(BottomSheetBehavior.STATE_HIDDEN)

        } else if (id == R.id.close_button) {
            onBackPressed()

        } else if (id == R.id.flash_button) {
            if (flashButton!!.isSelected) {
                flashButton!!.isSelected = false
                cameraSource!!.updateFlashMode(Camera.Parameters.FLASH_MODE_OFF)
            } else {
                flashButton!!.isSelected = true
                cameraSource!!.updateFlashMode(Camera.Parameters.FLASH_MODE_TORCH)
            }

        } else if (id == R.id.settings_button) {
            // Sets as disabled to prevent the user from clicking on it too fast.
            settingsButton!!.isEnabled = false
            startActivity(Intent(this, SettingsActivity::class.java))

        }
    }

    private fun startCameraPreview() {
        if (!workflowModel!!.isCameraLive && cameraSource != null) {
            try {
                workflowModel!!.markCameraLive()
                preview!!.start(cameraSource!!)

            } catch (e: IOException) {
                Log.e(TAG, "Failed to start camera preview!", e)
                cameraSource!!.release()
                cameraSource = null
            }

        }
    }

    private fun stopCameraPreview() {
        if (workflowModel!!.isCameraLive) {
            workflowModel!!.markCameraFrozen()
            flashButton!!.isSelected = false
            preview!!.stop()
        }
    }

    private fun setUpBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet))
        bottomSheetBehavior!!.setBottomSheetCallback(
                object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        Log.d(TAG, "Bottom sheet new state: $newState")
                        bottomSheetScrimView!!.visibility = if (newState == BottomSheetBehavior.STATE_HIDDEN) View.GONE else View.VISIBLE
                        graphicOverlay!!.clear()

                        when (newState) {
                            BottomSheetBehavior.STATE_HIDDEN -> workflowModel!!.setWorkflowState(WorkflowState.DETECTING)
                            BottomSheetBehavior.STATE_COLLAPSED, BottomSheetBehavior.STATE_EXPANDED, BottomSheetBehavior.STATE_HALF_EXPANDED -> slidingSheetUpFromHiddenState = false
                            BottomSheetBehavior.STATE_DRAGGING, BottomSheetBehavior.STATE_SETTLING -> {
                            }
                            else -> {
                            }
                        }
                    }

                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        val searchedObject = workflowModel!!.searchedObject.value
                        if (searchedObject == null || java.lang.Float.isNaN(slideOffset)) {
                            return
                        }

                        val collapsedStateHeight = Math.min(bottomSheetBehavior!!.peekHeight, bottomSheet.height)
                        if (slidingSheetUpFromHiddenState) {
                            val thumbnailSrcRect = graphicOverlay!!.translateRect(searchedObject.boundingBox)
                            bottomSheetScrimView!!.updateWithThumbnailTranslateAndScale(
                                    objectThumbnailForBottomSheet!!,
                                    collapsedStateHeight,
                                    slideOffset,
                                    thumbnailSrcRect)

                        } else {
                            bottomSheetScrimView!!.updateWithThumbnailTranslate(
                                    objectThumbnailForBottomSheet!!, collapsedStateHeight, slideOffset, bottomSheet)
                        }
                    }
                })

        bottomSheetScrimView = findViewById(R.id.bottom_sheet_scrim_view)
        bottomSheetScrimView!!.setOnClickListener(this)

        bottomSheetTitleView = findViewById(R.id.bottom_sheet_title)
        productRecyclerView = findViewById(R.id.product_recycler_view)
        productRecyclerView!!.setHasFixedSize(true)
        productRecyclerView!!.layoutManager = LinearLayoutManager(this)
        productRecyclerView!!.adapter = ProductAdapter(ImmutableList.of())
    }

    private fun setUpWorkflowModel() {
        workflowModel = ViewModelProviders.of(this).get(WorkflowModel::class.java!!)

        // Observes the workflow state changes, if happens, update the overlay view indicators and
        // camera preview state.
        workflowModel!!.workflowState.observe(
                this,
                Observer { workflowState ->
                    if (workflowState == null || Objects.equal(currentWorkflowState, workflowState)) {
                        return@Observer
                    }

                    currentWorkflowState = workflowState
                    Log.d(TAG, "Current workflow state: " + currentWorkflowState!!.name)

                    if (PreferenceUtils.isAutoSearchEnabled(this)) {
                        stateChangeInAutoSearchMode(workflowState!!)
                    } else {
                        stateChangeInManualSearchMode(workflowState!!)
                    }
                })

        // Observes changes on the object to search, if happens, fire product search request.
        workflowModel!!.objectToSearch.observe(
                this, Observer { `object` -> searchEngine!!.search(`object`) { detectedObject, products -> workflowModel?.onSearchCompleted(detectedObject, products) } })

        // Observes changes on the object that has search completed, if happens, show the bottom sheet
        // to present search result.
        workflowModel!!.searchedObject.observe(
                this,
                Observer { nullableSearchedObject ->
                    val searchedObject = nullableSearchedObject ?: return@Observer
                    val productList = searchedObject.productList
                    objectThumbnailForBottomSheet = searchedObject.getObjectThumbnail()
                    bottomSheetTitleView!!.text = resources
                            .getQuantityString(
                                    R.plurals.bottom_sheet_title, productList.size, productList.size)
                    productRecyclerView!!.adapter = ProductAdapter(productList)
                    slidingSheetUpFromHiddenState = true
                    bottomSheetBehavior!!.peekHeight = preview!!.height / 2
                    bottomSheetBehavior!!.state = BottomSheetBehavior.STATE_COLLAPSED

                })
    }

    private fun stateChangeInAutoSearchMode(workflowState: WorkflowState) {
        val wasPromptChipGone = promptChip!!.visibility == View.GONE

        searchButton!!.visibility = View.GONE
        searchProgressBar!!.visibility = View.GONE
        when (workflowState) {
            WorkflowModel.WorkflowState.DETECTING, WorkflowModel.WorkflowState.DETECTED, WorkflowModel.WorkflowState.CONFIRMING -> {
                promptChip!!.visibility = View.VISIBLE
                promptChip!!.setText(
                        if (workflowState == WorkflowState.CONFIRMING)
                            R.string.prompt_hold_camera_steady
                        else
                            R.string.prompt_point_at_an_object)
                startCameraPreview()
            }
            WorkflowModel.WorkflowState.CONFIRMED -> {
                promptChip!!.visibility = View.VISIBLE
                promptChip!!.setText(R.string.prompt_searching)
                stopCameraPreview()
            }
            WorkflowModel.WorkflowState.SEARCHING -> {
                searchProgressBar!!.visibility = View.VISIBLE
                promptChip!!.visibility = View.VISIBLE
                promptChip!!.setText(R.string.prompt_searching)
                stopCameraPreview()
            }
            WorkflowModel.WorkflowState.SEARCHED -> {
                promptChip!!.visibility = View.GONE
                stopCameraPreview()
            }
            else -> promptChip!!.visibility = View.GONE
        }

        val shouldPlayPromptChipEnteringAnimation = wasPromptChipGone && promptChip!!.visibility == View.VISIBLE
        if (shouldPlayPromptChipEnteringAnimation && !promptChipAnimator!!.isRunning) {
            promptChipAnimator!!.start()
        }
    }

    private fun stateChangeInManualSearchMode(workflowState: WorkflowState) {
        val wasPromptChipGone = promptChip!!.visibility == View.GONE
        val wasSearchButtonGone = searchButton!!.visibility == View.GONE

        searchProgressBar!!.visibility = View.GONE
        when (workflowState) {
            WorkflowModel.WorkflowState.DETECTING, WorkflowModel.WorkflowState.DETECTED, WorkflowModel.WorkflowState.CONFIRMING -> {
                promptChip!!.visibility = View.VISIBLE
                promptChip!!.setText(R.string.prompt_point_at_an_object)
                searchButton!!.visibility = View.GONE
                startCameraPreview()
            }
            WorkflowModel.WorkflowState.CONFIRMED -> {
                promptChip!!.visibility = View.GONE
                searchButton!!.visibility = View.VISIBLE
                searchButton!!.isEnabled = true
                searchButton!!.setBackgroundColor(Color.WHITE)
                startCameraPreview()
            }
            WorkflowModel.WorkflowState.SEARCHING -> {
                promptChip!!.visibility = View.GONE
                searchButton!!.visibility = View.VISIBLE
                searchButton!!.isEnabled = false
                searchButton!!.setBackgroundColor(Color.GRAY)
                searchProgressBar!!.visibility = View.VISIBLE
                stopCameraPreview()
            }
            WorkflowModel.WorkflowState.SEARCHED -> {
                promptChip!!.visibility = View.GONE
                searchButton!!.visibility = View.GONE
                stopCameraPreview()
            }
            else -> {
                promptChip!!.visibility = View.GONE
                searchButton!!.visibility = View.GONE
            }
        }

        val shouldPlayPromptChipEnteringAnimation = wasPromptChipGone && promptChip!!.visibility == View.VISIBLE
        if (shouldPlayPromptChipEnteringAnimation && !promptChipAnimator!!.isRunning) {
            promptChipAnimator!!.start()
        }

        val shouldPlaySearchButtonEnteringAnimation = wasSearchButtonGone && searchButton!!.visibility == View.VISIBLE
        if (shouldPlaySearchButtonEnteringAnimation && !searchButtonAnimator!!.isRunning) {
            searchButtonAnimator!!.start()
        }
    }

    companion object {

        private val TAG = "LiveObjectActivity"
    }
}
