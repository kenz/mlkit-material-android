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

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.ml.md.R
import com.google.firebase.ml.md.camera.WorkflowModel
import com.google.firebase.ml.md.camera.WorkflowModel.WorkflowState
import java.util.ArrayList

/** Displays the bottom sheet to present barcode fields contained in the detected barcode.  */
class BarcodeResultFragment : BottomSheetDialogFragment() {

    override fun onCreateView(
            layoutInflater: LayoutInflater,
            viewGroup: ViewGroup?,
            bundle: Bundle?): View? {
        val view = layoutInflater.inflate(R.layout.barcode_bottom_sheet, viewGroup)
        val barcodeFieldList: ArrayList<BarcodeField>?
        val arguments = arguments
        if (arguments != null && arguments.containsKey(ARG_BARCODE_FIELD_LIST)) {
            barcodeFieldList = arguments.getParcelableArrayList(ARG_BARCODE_FIELD_LIST)
        } else {
            Log.e(TAG, "No barcode field list passed in!")
            barcodeFieldList = ArrayList()
        }

        val fieldRecyclerView = view.findViewById<RecyclerView>(R.id.barcode_field_recycler_view)
        fieldRecyclerView.setHasFixedSize(true)
        fieldRecyclerView.layoutManager = LinearLayoutManager(activity)
        fieldRecyclerView.adapter = BarcodeFieldAdapter(barcodeFieldList)

        return view
    }

    override fun onDismiss(dialogInterface: DialogInterface) {
        if (activity != null) {
            // Back to working state after the bottom sheet is dismissed.
            ViewModelProviders.of(activity!!)
                    .get<WorkflowModel>(WorkflowModel::class.java!!)
                    .setWorkflowState(WorkflowState.DETECTING)
        }
        super.onDismiss(dialogInterface)
    }

    companion object {

        private val TAG = "BarcodeResultFragment"
        private val ARG_BARCODE_FIELD_LIST = "arg_barcode_field_list"

        fun show(
                fragmentManager: FragmentManager, barcodeFieldArrayList: ArrayList<BarcodeField>) {
            val barcodeResultFragment = BarcodeResultFragment()
            val bundle = Bundle()
            bundle.putParcelableArrayList(ARG_BARCODE_FIELD_LIST, barcodeFieldArrayList)
            barcodeResultFragment.arguments = bundle
            barcodeResultFragment.show(fragmentManager, TAG)
        }

        fun dismiss(fragmentManager: FragmentManager) {
            val barcodeResultFragment = fragmentManager.findFragmentByTag(TAG) as BarcodeResultFragment?
            barcodeResultFragment?.dismiss()
        }
    }
}
