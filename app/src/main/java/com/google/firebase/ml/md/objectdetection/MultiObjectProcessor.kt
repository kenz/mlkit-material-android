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

package com.google.firebase.ml.md.objectdetection

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import androidx.annotation.MainThread
import com.google.android.gms.tasks.Task
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.objects.FirebaseVisionObject
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetector
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetectorOptions
import com.google.firebase.ml.md.camera.CameraReticleAnimator
import com.google.firebase.ml.md.camera.GraphicOverlay
import com.google.firebase.ml.md.R
import com.google.firebase.ml.md.camera.WorkflowModel
import com.google.firebase.ml.md.camera.FrameProcessorBase
import com.google.firebase.ml.md.settings.PreferenceUtils
import java.io.IOException
import java.util.ArrayList
import java.util.HashMap
import kotlin.collections.Map.Entry

/** A processor to run object detector in multi-objects mode.  */
class MultiObjectProcessor(graphicOverlay: GraphicOverlay, private val workflowModel: WorkflowModel) : FrameProcessorBase<List<FirebaseVisionObject>>() {
    private val confirmationController: ObjectConfirmationController
    private val cameraReticleAnimator: CameraReticleAnimator
    private val objectSelectionDistanceThreshold: Int
    private val detector: FirebaseVisionObjectDetector
    // Each new tracked object plays appearing animation exactly once.
    private val objectDotAnimatorMap = HashMap<Int, ObjectDotAnimator>()

    init {
        this.confirmationController = ObjectConfirmationController(graphicOverlay)
        this.cameraReticleAnimator = CameraReticleAnimator(graphicOverlay)
        this.objectSelectionDistanceThreshold = graphicOverlay
                .resources
                .getDimensionPixelOffset(R.dimen.object_selection_distance_threshold)

        val optionsBuilder = FirebaseVisionObjectDetectorOptions.Builder()
                .setDetectorMode(FirebaseVisionObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
        if (PreferenceUtils.isClassificationEnabled(graphicOverlay.context)) {
            optionsBuilder.enableClassification()
        }
        this.detector = FirebaseVision.getInstance().getOnDeviceObjectDetector(optionsBuilder.build())
    }

    override fun stop() {
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to close object detector!", e)
        }

    }

    override fun detectInImage(image: FirebaseVisionImage): Task<List<FirebaseVisionObject>> {
        return detector.processImage(image)
    }

    @MainThread
    override fun onSuccess(
            image: FirebaseVisionImage,
            objects: List<FirebaseVisionObject>,
            graphicOverlay: GraphicOverlay) {
        var objects = objects
        if (!workflowModel.isCameraLive) {
            return
        }

        if (PreferenceUtils.isClassificationEnabled(graphicOverlay.context)) {
            val qualifiedObjects = ArrayList<FirebaseVisionObject>()
            for (`object` in objects) {
                if (`object`.classificationCategory != FirebaseVisionObject.CATEGORY_UNKNOWN) {
                    qualifiedObjects.add(`object`)
                }
            }
            objects = qualifiedObjects
        }

        removeAnimatorsFromUntrackedObjects(objects)

        graphicOverlay.clear()

        var selectedObject: DetectedObject? = null
        for (i in objects.indices) {
            val `object` = objects[i]
            if (selectedObject == null && shouldSelectObject(graphicOverlay, `object`)) {
                selectedObject = DetectedObject(`object`, i, image)
                // Starts the object confirmation once an object is regarded as selected.
                confirmationController.confirming(`object`.trackingId)
                graphicOverlay.add(ObjectConfirmationGraphic(graphicOverlay, confirmationController))

                graphicOverlay.add(
                        ObjectGraphicInMultiMode(
                                graphicOverlay, selectedObject, confirmationController))
            } else {
                if (confirmationController.isConfirmed) {
                    // Don't render other objects when an object is in confirmed state.
                    continue
                }

                var objectDotAnimator = objectDotAnimatorMap.get(`object`.trackingId)
                if (objectDotAnimator == null) {
                    objectDotAnimator = ObjectDotAnimator(graphicOverlay)
                    objectDotAnimator.start()
                    objectDotAnimatorMap[`object`.trackingId!!] = objectDotAnimator
                }
                graphicOverlay.add(
                        ObjectDotGraphic(
                                graphicOverlay, DetectedObject(`object`, i, image), objectDotAnimator))
            }
        }

        if (selectedObject == null) {
            confirmationController.reset()
            graphicOverlay.add(ObjectReticleGraphic(graphicOverlay, cameraReticleAnimator))
            cameraReticleAnimator.start()
        } else {
            cameraReticleAnimator.cancel()
        }

        graphicOverlay.invalidate()

        if (selectedObject != null) {
            workflowModel.confirmingObject(selectedObject, confirmationController.progress)
        } else {
            workflowModel.setWorkflowState(
                    if (objects.isEmpty())
                        WorkflowModel.WorkflowState.DETECTING
                    else
                        WorkflowModel.WorkflowState.DETECTED)
        }
    }

    private fun removeAnimatorsFromUntrackedObjects(detectedObjects: List<FirebaseVisionObject>) {
        val trackingIds = ArrayList<Int>()
        for (`object` in detectedObjects) {
            trackingIds.add(`object`.trackingId)
        }
        // Stop and remove animators from the objects that have lost tracking.
        val removedTrackingIds = ArrayList<Int>()
        for ((key, value) in objectDotAnimatorMap) {
            if (!trackingIds.contains(key)) {
                value.cancel()
                removedTrackingIds.add(key)
            }
        }
        objectDotAnimatorMap.keys.removeAll(removedTrackingIds)
    }

    private fun shouldSelectObject(graphicOverlay: GraphicOverlay, `object`: FirebaseVisionObject): Boolean {
        // Considers an object as selected when the camera reticle touches the object dot.
        val box = graphicOverlay.translateRect(`object`.boundingBox)
        val objectCenter = PointF((box.left + box.right) / 2f, (box.top + box.bottom) / 2f)
        val reticleCenter = PointF(graphicOverlay.width / 2f, graphicOverlay.height / 2f)
        val distance = Math.hypot((objectCenter.x - reticleCenter.x).toDouble(), (objectCenter.y - reticleCenter.y).toDouble())
        return distance < objectSelectionDistanceThreshold
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "Object detection failed!", e)
    }

    companion object {

        private val TAG = "MultiObjectProcessor"
    }
}
