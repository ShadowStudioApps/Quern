package com.quern.app.ui.components

import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.quern.app.R
import com.quern.app.data.sync.DeviceLinkClient
import com.quern.app.data.sync.PendingDeviceLink
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LumaRed
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.qString
import java.util.EnumMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Isolated phone-only CameraX + ZXing QR scanner dialog.
 * - Never instantiated or loaded on TV hardware.
 * - Analyses Y-plane luminance frames strictly in RAM (`STRATEGY_KEEP_ONLY_LATEST`), never saving
 *   or transmitting any camera frame.
 * - Deterministically unbinds CameraX and shuts down the analysis executor as soon as a valid QR
 *   is read or the dialog closes.
 */
internal object PhoneQrScannerHost {

    @Composable
    fun Dialog(
        onScanned: (PendingDeviceLink) -> Unit,
        onDismiss: () -> Unit
    ) {
        val context = LocalContext.current
        if (isTvHardware(context)) return

        val palette = LocalQuernPalette.current
        val parentLifecycleOwner = LocalLifecycleOwner.current
        val mainHandler = remember { Handler(Looper.getMainLooper()) }
        val completed = remember { AtomicBoolean(false) }

        var scannerHintError by remember { mutableStateOf<String?>(null) }
        val invalidQrMsg = qString(R.string.link_tv_scan_invalid_qr)
        val cameraErrMsg = qString(R.string.link_tv_scan_camera_error)

        val previewView = remember(context) {
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }

        DisposableEffect(parentLifecycleOwner, previewView) {
            val childOwner = ChildScannerLifecycleOwner(parentLifecycleOwner.lifecycle)
            val analysisExecutor = Executors.newSingleThreadExecutor()
            var boundProvider: ProcessCameraProvider? = null
            var disposed = false

            val reader = MultiFormatReader().apply {
                val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
                hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)
                hints[DecodeHintType.TRY_HARDER] = true
                setHints(hints)
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    if (disposed) return@addListener
                    val provider = runCatching { cameraProviderFuture.get() }.getOrNull()
                    if (provider == null) {
                        scannerHintError = cameraErrMsg
                        return@addListener
                    }
                    boundProvider = provider
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        decodeQrFromYPlane(imageProxy, reader)?.let { rawText ->
                            val parsed = DeviceLinkClient.parseQrOrLink(rawText)
                            if (parsed != null) {
                                if (completed.compareAndSet(false, true)) {
                                    mainHandler.post {
                                        runCatching { provider.unbindAll() }
                                        onScanned(parsed)
                                    }
                                }
                            } else if (!completed.get()) {
                                mainHandler.post { scannerHintError = invalidQrMsg }
                            }
                        }
                    }

                    val selector = when {
                        runCatching { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false) ->
                            CameraSelector.DEFAULT_BACK_CAMERA
                        runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false) ->
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(childOwner, selector, preview, analysis)
                    }.onFailure {
                        scannerHintError = cameraErrMsg
                    }
                },
                ContextCompat.getMainExecutor(context)
            )

            onDispose {
                disposed = true
                childOwner.destroy()
                runCatching { boundProvider?.unbindAll() }
                analysisExecutor.shutdownNow()
            }
        }

        QuernAlertDialog(
            onDismissRequest = onDismiss,
            containerColor = palette.surfaceElevated,
            title = {
                Text(
                    text = qString(R.string.link_tv_scan_title),
                    color = LumaTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = qString(R.string.link_tv_scan_hint),
                        color = LumaTextSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black)
                            .border(BorderStroke(1.2.dp, palette.border), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .size(170.dp)
                                .border(2.dp, palette.focusRing.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                        )
                    }

                    scannerHintError?.let { err ->
                        Text(
                            text = err,
                            color = LumaRed,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(qString(R.string.common_cancel), color = LumaTextSecondary)
                }
            }
        )
    }

    private fun decodeQrFromYPlane(imageProxy: ImageProxy, reader: MultiFormatReader): String? {
        try {
            val plane = imageProxy.planes.firstOrNull() ?: return null
            val width = imageProxy.width
            val height = imageProxy.height
            if (width <= 0 || height <= 0) return null

            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val yBytes = ByteArray(width * height)

            buffer.rewind()
            if (rowStride == width && pixelStride == 1) {
                val available = buffer.remaining().coerceAtMost(yBytes.size)
                buffer.get(yBytes, 0, available)
            } else {
                val rowBuf = ByteArray(rowStride)
                var outOffset = 0
                for (row in 0 until height) {
                    val toRead = if (row == height - 1) {
                        buffer.remaining().coerceAtMost(rowStride)
                    } else {
                        rowStride.coerceAtMost(buffer.remaining())
                    }
                    if (toRead <= 0) break
                    buffer.get(rowBuf, 0, toRead)
                    if (pixelStride == 1) {
                        val copyLen = width.coerceAtMost(toRead)
                        System.arraycopy(rowBuf, 0, yBytes, outOffset, copyLen)
                    } else {
                        for (col in 0 until width) {
                            val idx = col * pixelStride
                            if (idx < toRead) yBytes[outOffset + col] = rowBuf[idx]
                        }
                    }
                    outOffset += width
                }
            }

            val source = PlanarYUVLuminanceSource(
                yBytes,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            return try {
                reader.decodeWithState(bitmap).text
            } catch (_: NotFoundException) {
                null
            } catch (_: Exception) {
                null
            } finally {
                reader.reset()
            }
        } finally {
            imageProxy.close()
        }
    }

    private class ChildScannerLifecycleOwner(
        private val parentLifecycle: Lifecycle
    ) : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        private val observer = LifecycleEventObserver { _, _ -> syncState() }
        private var destroyed = false

        init {
            parentLifecycle.addObserver(observer)
            syncState()
        }

        override val lifecycle: Lifecycle
            get() = registry

        private fun syncState() {
            if (destroyed) return
            val target = parentLifecycle.currentState
            if (target.isAtLeast(Lifecycle.State.CREATED)) {
                registry.currentState = target
            }
        }

        fun destroy() {
            if (destroyed) return
            destroyed = true
            parentLifecycle.removeObserver(observer)
            if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                registry.currentState = Lifecycle.State.DESTROYED
            }
        }
    }
}
