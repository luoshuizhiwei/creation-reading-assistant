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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.creationreadingassistant.R
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * 扫码配对屏：CameraX 预览 + ML Kit 条码识别。
 * 大屏沉浸升级：全屏相机取景框之上悬浮极简墨玉微岛。
 */
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

    val infiniteTransition = rememberInfiniteTransition(label = "qr_pairing_screen")

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

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
            DisposableEffect(Unit) { onDispose { analyzerExecutor.shutdown() } }

            // 1. 全屏相机取景
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

            // 2. 顶底微渐变暗影（强化白光微岛与文字对比度）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.70f)),
                        ),
                    ),
            )

            // 3. 居中扫码取景框（呼吸发光四角 + 扫描微线）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .animateEnter(reducedMotion = reducedMotion),
                contentAlignment = Alignment.Center,
            ) {
                QrCornersAndLaser(reducedMotion = reducedMotion)
            }

            // 4. 顶部悬浮返回胶囊按钮（极简墨玉微岛）
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 12.dp)
                    .animateEnter(reducedMotion = reducedMotion),
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.68f),
                    border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(onClick = onCancel),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White,
                            modifier = Modifier.size(17.dp),
                        )
                        Text(
                            text = "扫码配对",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                            ),
                            color = Color.White,
                        )
                    }
                }
            }

            // 5. 底部配对提示微岛卡片（白底微半透、极细描边、说明文字居中呼吸提示）
            val instructionAlpha by if (reducedMotion) {
                remember { mutableFloatStateOf(0.92f) }
            } else {
                infiniteTransition.animateFloat(
                    initialValue = 0.72f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "instructionAlpha",
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp)
                    .animateEnter(reducedMotion = reducedMotion),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.16f),
                    border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Outlined.QrCodeScanner,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = instructionAlpha),
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "将桌面端「同步」面板的二维码放入取景框",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.5.sp,
                                ),
                                color = Color.White.copy(alpha = instructionAlpha),
                                textAlign = TextAlign.Center,
                            )
                        }
                        Text(
                            text = "局域网高速安全传输 · 密钥与数据不离网",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color.White.copy(alpha = 0.65f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            // 未获权限态（现代微岛居中设计）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(onClick = onCancel),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            modifier = Modifier.size(17.dp),
                        )
                        Text(
                            text = "返回",
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.9f),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateEnter(reducedMotion = reducedMotion),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.QrCodeScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Text(
                            "需要相机权限",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            "扫描桌面端同步二维码需要相机权限。未授权时无法启动实时扫码取景框。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                        )
                        Button(
                            onClick = { showRationale = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                        ) {
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

/** 取景框四角括号与扫描微线动效。 */
@Composable
private fun QrCornersAndLaser(reducedMotion: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "qr_scanner_anim")

    val glowAlpha by if (reducedMotion) {
        remember { mutableFloatStateOf(0.9f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "glowAlpha",
        )
    }

    val scanLineProgress by if (reducedMotion) {
        remember { mutableFloatStateOf(0.5f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0.06f,
            targetValue = 0.94f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "scanLineProgress",
        )
    }

    Canvas(Modifier.size(230.dp)) {
        val w = size.width
        val h = size.height
        val len = 28.dp.toPx()
        val stroke = 3.5.dp.toPx()
        val glowStroke = stroke + 2.5.dp.toPx()

        // 呼吸微发光边角颜色（主色青白光 + 墨玉科技感）
        val primaryGlow = Color(0xFF38BDF8).copy(alpha = glowAlpha * 0.45f)
        val cornerColor = Color.White.copy(alpha = (0.75f + 0.25f * glowAlpha).coerceIn(0f, 1f))

        // 1. 绘制外层呼吸光晕（四角 L 型）
        drawLine(primaryGlow, Offset(0f, 0f), Offset(len, 0f), strokeWidth = glowStroke, cap = StrokeCap.Round)
        drawLine(primaryGlow, Offset(0f, 0f), Offset(0f, len), strokeWidth = glowStroke, cap = StrokeCap.Round)
        drawLine(primaryGlow, Offset(w, 0f), Offset(w - len, 0f), strokeWidth = glowStroke, cap = StrokeCap.Round)
        drawLine(primaryGlow, Offset(w, 0f), Offset(w, len), strokeWidth = glowStroke, cap = StrokeCap.Round)
        drawLine(primaryGlow, Offset(0f, h), Offset(len, h), strokeWidth = glowStroke, cap = StrokeCap.Round)
        drawLine(primaryGlow, Offset(0f, h), Offset(0f, h - len), strokeWidth = glowStroke, cap = StrokeCap.Round)
        drawLine(primaryGlow, Offset(w, h), Offset(w - len, h), strokeWidth = glowStroke, cap = StrokeCap.Round)
        drawLine(primaryGlow, Offset(w, h), Offset(w, h - len), strokeWidth = glowStroke, cap = StrokeCap.Round)

        // 2. 绘制实体高亮四角括号
        drawLine(cornerColor, Offset(0f, 0f), Offset(len, 0f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(0f, 0f), Offset(0f, len), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(w, 0f), Offset(w - len, 0f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(w, 0f), Offset(w, len), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(0f, h), Offset(len, h), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(0f, h), Offset(0f, h - len), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(w, h), Offset(w - len, h), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(w, h), Offset(w, h - len), strokeWidth = stroke, cap = StrokeCap.Round)

        // 3. 扫描微线动效（ReducedMotion 下静止或不绘制）
        if (!reducedMotion) {
            val scanY = h * scanLineProgress
            val laserBrush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF38BDF8).copy(alpha = 0.5f * glowAlpha),
                    Color.White.copy(alpha = 0.95f * glowAlpha),
                    Color(0xFF38BDF8).copy(alpha = 0.5f * glowAlpha),
                    Color.Transparent,
                ),
                startX = 8.dp.toPx(),
                endX = w - 8.dp.toPx(),
            )
            drawLine(
                brush = laserBrush,
                start = Offset(8.dp.toPx(), scanY),
                end = Offset(w - 8.dp.toPx(), scanY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
