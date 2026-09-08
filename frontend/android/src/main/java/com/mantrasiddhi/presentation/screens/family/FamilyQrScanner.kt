package com.mantrasiddhi.presentation.screens.family

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject

class FamilyQrScanner @Inject constructor() {
    suspend fun scan(context: Context): String? = suspendCancellableCoroutine { continuation ->
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom().build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { if (continuation.isActive) continuation.resume(it.rawValue.orEmpty()) }
            .addOnCanceledListener { if (continuation.isActive) continuation.resume(null) }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    }
}
