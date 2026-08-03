package com.creationreadingassistant.ui.screen.profile

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.material3.AlertDialog
import com.creationreadingassistant.ui.components.GlassAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.creationreadingassistant.R
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 扫码配对屏：CameraX 预览 + ML Kit 条码识别。
 * 识别到首个二维码即回调 [onScanned]，调用方负责配对与退出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrPairingScreen(
    onScanned: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    DisposableEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        hasPermission = granted
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
        onDispose { }
    }

    if (showRationale) {
        GlassAlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("需要相机权限") },
            text = { Text("扫描桌面端同步二维码需要相机权限。请在系统设置中授予后重试。") },
            confirmButton = { TextButton(onClick = { showRationale = false; permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("重试") } },
            dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.back)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫码配对", style = MaterialTheme.typography.headlineLarge) },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (hasPermission) {
                val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
                DisposableEffect(Unit) { onDispose { analyzerExecutor.shutdown() } }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build().also { imageAnalysis ->
                                    imageAnalysis.setAnalyzer(analyzerExecutor) { imageProxy: ImageProxy ->
                                        if (scanned) { imageProxy.close(); return@setAnalyzer }
                                        val image = imageProxy.toInputImageOrNull()
                                        if (image != null) {
                                            BarcodeScanning.getClient().process(image)
                                                .addOnSuccessListener { barcodes: List<Barcode> ->
                                                    val raw = barcodes.firstOrNull()?.rawValue
                                                    if (!raw.isNullOrBlank()) {
                                                        scanned = true
                                                        haptic(HapticFeedbackType.LongPress)
                                                        onScanned(raw)
                                                    }
                                                }
                                                .addOnCompleteListener { imageProxy.close() }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }
                                }
                            runCatching {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis,
                                )
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                )
                // 取景框四角括号，对齐 web .qr-corners
                Box(Modifier.fillMaxSize().animateEnter(reducedMotion = reducedMotion), contentAlignment = Alignment.Center) {
                    QrCorners()
                }
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp).animateEnter(reducedMotion = reducedMotion),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "将桌面端「同步」面板的二维码放入取景框",
                        // 相机预览覆盖层：后置相机预览背景是真实场景画面，不跟随应用主题；
                        // Color.White 在大多数实景下对比度最好，故意脱离主题令牌。
                        // onInverseSurface 浅色下是灰白 (#E8EAEE)，对比度反而差，不适用。
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                        Text("未获得相机权限，无法扫码。", style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = { showRationale = true }, modifier = Modifier.padding(top = 12.dp)) {
                            Text("申请权限")
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
private fun ImageProxy.toInputImageOrNull(): InputImage? {
    val mediaImage = image ?: return null
    return InputImage.fromMediaImage(mediaImage, imageInfo.rotationDegrees)
}

/** 取景框四角括号，对齐 web .qr-corners（四角 L 形亮线）。 */
@Composable
private fun QrCorners() {
    // 同提示文字：相机预览覆盖层，故意脱离主题令牌。
    val lineColor = Color.White
    Canvas(Modifier.size(220.dp)) {
        val w = size.width
        val h = size.height
        val len = 28.dp.toPx()
        val stroke = 3.dp.toPx()
        // 左上
        drawLine(lineColor, Offset(0f, 0f), Offset(len, 0f), strokeWidth = stroke)
        drawLine(lineColor, Offset(0f, 0f), Offset(0f, len), strokeWidth = stroke)
        // 右上
        drawLine(lineColor, Offset(w, 0f), Offset(w - len, 0f), strokeWidth = stroke)
        drawLine(lineColor, Offset(w, 0f), Offset(w, len), strokeWidth = stroke)
        // 左下
        drawLine(lineColor, Offset(0f, h), Offset(len, h), strokeWidth = stroke)
        drawLine(lineColor, Offset(0f, h), Offset(0f, h - len), strokeWidth = stroke)
        // 右下
        drawLine(lineColor, Offset(w, h), Offset(w - len, h), strokeWidth = stroke)
        drawLine(lineColor, Offset(w, h), Offset(w, h - len), strokeWidth = stroke)
    }
}
