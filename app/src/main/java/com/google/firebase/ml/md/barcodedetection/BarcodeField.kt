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

import android.os.Parcel
import android.os.Parcelable

/** Information about a barcode field.  */
class BarcodeField : Parcelable {

    internal val label: String?
    internal val value: String?

    constructor(label: String, value: String) {
        this.label = label
        this.value = value
    }

    private constructor(`in`: Parcel) {
        label = `in`.readString()
        value = `in`.readString()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(label)
        dest.writeString(value)
    }

    companion object {

        val CREATOR: Parcelable.Creator<BarcodeField> = object : Parcelable.Creator<BarcodeField> {
            override fun createFromParcel(`in`: Parcel): BarcodeField {
                return BarcodeField(`in`)
            }

            override fun newArray(size: Int): Array<BarcodeField> {
                return arrayOfNulls(size)
            }
        }
    }
}
