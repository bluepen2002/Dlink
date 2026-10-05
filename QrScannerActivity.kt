package com.dlink.app.exchange

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class QrScannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val previewView = remember { PreviewView(this) }
            DisposableEffect(Unit) {
                val future = ProcessCameraProvider.getInstance(this@QrScannerActivity)
                future.addListener({
                    val provider = future.get()
                    val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analysis.setAnalyzer(ContextCompat.getMainExecutor(this@QrScannerActivity)) { proxy ->
                        val media = proxy.image
                        if (media != null) {
                            scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)).addOnSuccessListener { codes ->
                                val value = codes.firstOrNull()?.rawValue
                                if (!value.isNullOrBlank() && value.startsWith("dlink://share?payload=")) {
                                    try { DlinkShareEnvelope.decode(value); setResult(RESULT_OK, intent.putExtra("dlink_payload", value)); finish() } catch (_: Exception) { }
                                }
                            }.addOnCompleteListener { proxy.close() }
                        } else proxy.close()
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(this@QrScannerActivity, CameraSelector.DEFAULT_BACK_CAMERA, Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }, analysis)
                }, ContextCompat.getMainExecutor(this@QrScannerActivity))
                onDispose { }
            }
            AndroidView({ previewView }, Modifier.fillMaxSize())
        }
    }
}
