package com.nairo.app.ui.scan

import android.app.Activity
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.nairo.app.ui.theme.Ink900
import com.nairo.app.ui.theme.Sea300
import com.nairo.app.ui.theme.Sea500
import com.nairo.app.ui.theme.Sea600

@Composable
fun ScanBindScreen(
    onResult: (String) -> Unit,
    onClose: () -> Unit,
    onPaste: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    var torchOn by remember { mutableStateOf(false) }
    var scannerView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }
    var handled by remember { mutableStateOf(false) }
    val onResultState = rememberUpdatedState(onResult)

    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }
    DisposableEffect(Unit) {
        onDispose {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    BackHandler(onBack = onClose)

    DisposableEffect(lifecycleOwner, scannerView) {
        val view = scannerView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view?.resume()
                Lifecycle.Event.ON_PAUSE -> view?.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view?.pause()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Ink900)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                DecoratedBarcodeView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    statusView?.visibility = android.view.View.GONE
                    viewFinder?.setLaserVisibility(false)
                    viewFinder?.setMaskColor(0x00000000)
                    barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
                    decodeContinuous(object : BarcodeCallback {
                        override fun barcodeResult(result: BarcodeResult?) {
                            val text = result?.text?.trim().orEmpty()
                            if (text.isEmpty() || handled) return
                            handled = true
                            pause()
                            onResultState.value(text)
                        }
                    })
                    resume()
                    scannerView = this
                }
            },
            update = { view ->
                if (torchOn) view.setTorchOn() else view.setTorchOff()
            },
        )

        ScanOverlay(
            torchOn = torchOn,
            onToggleTorch = { torchOn = !torchOn },
            onClose = onClose,
            onPaste = onPaste,
        )
    }
}

@Composable
private fun ScanOverlay(
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    onClose: () -> Unit,
    onPaste: () -> Unit,
) {
    val infinite = rememberInfiniteTransition(label = "scan")
    val lineProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scanLine",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val frameSize = minOf(maxWidth * 0.72f, 280.dp)
        val density = LocalDensity.current
        val framePx = with(density) { frameSize.toPx() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.42f
            val left = cx - framePx / 2f
            val top = cy - framePx / 2f
            val hole = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(left, top, left + framePx, top + framePx),
                        cornerRadius = CornerRadius(36f, 36f),
                    ),
                )
            }
            val overlay = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
            }
            val mask = Path.combine(PathOperation.Difference, overlay, hole)
            drawPath(mask, Color(0xCC070B14))

            val corner = 42f
            val stroke = 7f
            val color = Sea500.copy(alpha = 0.55f + 0.45f * pulse)
            fun cornerPath(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(color, Offset(x, y), Offset(x + dx, y), stroke, StrokeCap.Round)
                drawLine(color, Offset(x, y), Offset(x, y + dy), stroke, StrokeCap.Round)
            }
            cornerPath(left, top, corner, corner)
            cornerPath(left + framePx, top, -corner, corner)
            cornerPath(left, top + framePx, corner, -corner)
            cornerPath(left + framePx, top + framePx, -corner, -corner)

            val lineY = top + framePx * lineProgress
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Sea300, Sea600, Sea300, Color.Transparent),
                ),
                start = Offset(left + 18f, lineY),
                end = Offset(left + framePx - 18f, lineY),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
            drawRoundRect(
                color = Sea500.copy(alpha = 0.12f),
                topLeft = Offset(left, top),
                size = Size(framePx, framePx),
                cornerRadius = CornerRadius(36f, 36f),
                style = Stroke(width = 1.5f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OverlayChip(text = "关闭", onClick = onClose)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("扫码绑定", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.weight(1f))
                OverlayChip(text = if (torchOn) "关灯" else "闪光灯", onClick = onToggleTorch)
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xE60B1220))
                    .border(1.dp, Sea500.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "将控制台设备二维码放入框内",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "识别后自动完成绑定",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OverlayChip(text = "粘贴绑定内容", onClick = onPaste, filled = true)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun OverlayChip(
    text: String,
    onClick: () -> Unit,
    filled: Boolean = false,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (filled) Sea600 else Color.White.copy(alpha = 0.12f))
            .border(
                width = 1.dp,
                color = if (filled) Sea600 else Color.White.copy(alpha = 0.22f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
