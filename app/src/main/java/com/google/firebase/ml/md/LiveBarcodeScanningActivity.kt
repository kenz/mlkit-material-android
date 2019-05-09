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
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.chip.Chip
import com.google.common.base.Objects
import com.google.firebase.ml.md.camera.GraphicOverlay
import com.google.firebase.ml.md.camera.WorkflowModel
import com.google.firebase.ml.md.camera.WorkflowModel.WorkflowState
import com.google.firebase.ml.md.barcodedetection.BarcodeField
import com.google.firebase.ml.md.barcodedetection.BarcodeProcessor
import com.google.firebase.ml.md.barcodedetection.BarcodeResultFragment
import com.google.firebase.ml.md.camera.CameraSource
import com.google.firebase.ml.md.camera.CameraSourcePreview
import com.google.firebase.ml.md.settings.SettingsActivity
import java.io.IOException
import java.util.ArrayList

/** Demonstrates the barcode scanning workflow using camera preview.  */
class LiveBarcodeScanningActivity : AppCompatActivity(), OnClickListener {

    private var cameraSource: CameraSource? = null
    private var preview: CameraSourcePreview? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var settingsButton: View? = null
    private var flashButton: View? = null
    private var promptChip: Chip? = null
    private var promptChipAnimator: AnimatorSet? = null
    private var workflowModel: WorkflowModel? = null
    private var currentWorkflowState: WorkflowState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_live_barcode)
        preview = findViewById(R.id.camera_preview)
        graphicOverlay = findViewById(R.id.camera_preview_graphic_overlay)
        graphicOverlay!!.setOnClickListener(this)
        cameraSource = CameraSource(graphicOverlay!!)

        promptChip = findViewById(R.id.bottom_prompt_chip)
        promptChipAnimator = AnimatorInflater.loadAnimator(this, R.animator.bottom_prompt_chip_enter) as AnimatorSet
        promptChipAnimator!!.setTarget(promptChip)

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
        currentWorkflowState = WorkflowState.NOT_STARTED
        cameraSource!!.setFrameProcessor(BarcodeProcessor(graphicOverlay, workflowModel))
        workflowModel!!.setWorkflowState(WorkflowState.DETECTING)
    }

    override fun onPostResume() {
        super.onPostResume()
        BarcodeResultFragment.dismiss(supportFragmentManager)
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
    }

    override fun onClick(view: View) {
        val id = view.id
        if (id == R.id.close_button) {
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
                preview!!.start(cameraSource)
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

    private fun setUpWorkflowModel() {
        workflowModel = ViewModelProviders.of(this).get(WorkflowModel::class.java!!)

        // Observes the workflow state changes, if happens, update the overlay view indicators and
        // camera preview state.
        workflowModel!!.workflowState.observe(
                this,
                { workflowState ->
                    if (workflowState == null || Objects.equal(currentWorkflowState, workflowState)) {
                        return@workflowModel.workflowState.observe
                    }

                    currentWorkflowState = workflowState
                    Log.d(TAG, "Current workflow state: " + currentWorkflowState!!.name)

                    val wasPromptChipGone = promptChip!!.visibility == View.GONE

                    when (workflowState) {
                        WorkflowModel.WorkflowState.DETECTING -> {
                            promptChip!!.visibility = View.VISIBLE
                            promptChip!!.setText(R.string.prompt_point_at_a_barcode)
                            startCameraPreview()
                        }
                        WorkflowModel.WorkflowState.CONFIRMING -> {
                            promptChip!!.visibility = View.VISIBLE
                            promptChip!!.setText(R.string.prompt_move_camera_closer)
                            startCameraPreview()
                        }
                        WorkflowModel.WorkflowState.SEARCHING -> {
                            promptChip!!.visibility = View.VISIBLE
                            promptChip!!.setText(R.string.prompt_searching)
                            stopCameraPreview()
                        }
                        WorkflowModel.WorkflowState.DETECTED, WorkflowModel.WorkflowState.SEARCHED -> {
                            promptChip!!.visibility = View.GONE
                            stopCameraPreview()
                        }
                        else -> promptChip!!.visibility = View.GONE
                    }

                    val shouldPlayPromptChipEnteringAnimation = wasPromptChipGone && promptChip!!.visibility == View.VISIBLE
                    if (shouldPlayPromptChipEnteringAnimation && !promptChipAnimator!!.isRunning) {
                        promptChipAnimator!!.start()
                    }
                })

        workflowModel!!.detectedBarcode.observe(
                this,
                { barcode ->
                    if (barcode != null) {
                        val barcodeFieldList = ArrayList<BarcodeField>()
                        barcodeFieldList.add(BarcodeField("Raw Value", barcode!!.getRawValue()))
                        BarcodeResultFragment.show(supportFragmentManager, barcodeFieldList)
                    }
                })
    }

    companion object {

        private val TAG = "LiveBarcodeActivity"
    }
}
