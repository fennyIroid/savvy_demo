@file:Suppress("unused", "UNUSED_PARAMETER")

package com.journeyapps.barcodescanner

import androidx.activity.result.contract.ActivityResultContract

class ScanOptions {
    fun setDesiredBarcodeFormats(vararg formats: String): ScanOptions = this
    fun setBeepEnabled(enabled: Boolean): ScanOptions = this
    fun setOrientationLocked(locked: Boolean): ScanOptions = this
    companion object { const val QR_CODE = "QR_CODE" }
}

class ScanIntentResult { val contents: String? = null }

class ScanContract : ActivityResultContract<ScanOptions, ScanIntentResult>()
