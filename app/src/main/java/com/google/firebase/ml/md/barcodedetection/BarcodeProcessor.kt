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

import android.animation.ValueAnimator
import android.graphics.RectF
import android.util.Log
import androidx.annotation.MainThread
import com.google.android.gms.tasks.Task
import com.google.firebase.ml.md.camera.CameraReticleAnimator
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.md.camera.GraphicOverlay
import com.google.firebase.ml.md.camera.WorkflowModel
import com.google.firebase.ml.md.camera.WorkflowModel.WorkflowState
import com.google.firebase.ml.md.camera.FrameProcessorBase
import com.google.firebase.ml.md.settings.PreferenceUtils
import java.io.IOException

/** A processor to run the barcode detector.  */
class BarcodeProcessor(graphicOverlay: GraphicOverlay, private val workflowModel: WorkflowModel) : FrameProcessorBase<List<FirebaseVisionBarcode>>() {

    private val detector = FirebaseVision.getInstance().visionBarcodeDetector
    private val cameraReticleAnimator: CameraReticleAnimator

    init {
        this.cameraReticleAnimator = CameraReticleAnimator(graphicOverlay)
    }

    override fun detectInImage(image: FirebaseVisionImage): Task<List<FirebaseVisionBarcode>> {
        return detector.detectInImage(image)
    }

    @MainThread
    override fun onSuccess(
            image: FirebaseVisionImage,
            results: List<FirebaseVisionBarcode>,
            graphicOverlay: GraphicOverlay) {
        if (!workflowModel.isCameraLive) {
            return
        }

        Log.d(TAG, "Barcode result size: " + results.size)

        // Picks the barcode, if exists, that covers the center of graphic overlay.
        var barcodeInCenter: FirebaseVisionBarcode? = null

        for (barcode in results) {
            val boundingBox = barcode.boundingBox?:continue
            val box = graphicOverlay.translateRect(boundingBox)
            if (box.contains(graphicOverlay.width / 2f, graphicOverlay.height / 2f)) {
                barcodeInCenter = barcode
                break
            }

        }

        graphicOverlay.clear()
        if (barcodeInCenter == null) {
            cameraReticleAnimator.start()
            graphicOverlay.add(BarcodeReticleGraphic(graphicOverlay, cameraReticleAnimator))
            workflowModel.setWorkflowState(WorkflowState.DETECTING)

        } else {
            cameraReticleAnimator.cancel()
            val sizeProgress = PreferenceUtils.getProgressToMeetBarcodeSizeRequirement(graphicOverlay, barcodeInCenter)
            if (sizeProgress < 1) {
                // Barcode in the camera view is too small, so prompt user to move camera closer.
                graphicOverlay.add(BarcodeConfirmingGraphic(graphicOverlay, barcodeInCenter))
                workflowModel.setWorkflowState(WorkflowState.CONFIRMING)

            } else {
                // Barcode size in the camera view is sufficient.
                if (PreferenceUtils.shouldDelayLoadingBarcodeResult(graphicOverlay.context)) {
                    val loadingAnimator = createLoadingAnimator(graphicOverlay, barcodeInCenter)
                    loadingAnimator.start()
                    graphicOverlay.add(BarcodeLoadingGraphic(graphicOverlay, loadingAnimator))
                    workflowModel.setWorkflowState(WorkflowState.SEARCHING)

                } else {
                    workflowModel.setWorkflowState(WorkflowState.DETECTED)
                    workflowModel.detectedBarcode.setValue(barcodeInCenter)
                }
            }
        }
        graphicOverlay.invalidate()
    }

    private fun createLoadingAnimator(
            graphicOverlay: GraphicOverlay, barcode: FirebaseVisionBarcode): ValueAnimator {
        val endProgress = 1.1f
        val loadingAnimator = ValueAnimator.ofFloat(0f, endProgress)
        loadingAnimator.duration = 2000
        loadingAnimator.addUpdateListener { animation ->
            if (java.lang.Float.compare(loadingAnimator.animatedValue as Float, endProgress) >= 0) {
                graphicOverlay.clear()
                workflowModel.setWorkflowState(WorkflowState.SEARCHED)
                workflowModel.detectedBarcode.setValue(barcode)
            } else {
                graphicOverlay.invalidate()
            }
        }
        return loadingAnimator
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "Barcode detection failed!", e)
    }

    override fun stop() {
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to close barcode detector!", e)
        }

    }

    companion object {

        private val TAG = "BarcodeProcessor"
    }
}
