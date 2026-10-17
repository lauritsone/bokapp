package app.shelfmark.ui.scan

import androidx.annotation.StringRes
import app.shelfmark.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import app.shelfmark.ui.icons.Keyboard
import app.shelfmark.ui.icons.PhotoCamera
import app.shelfmark.ui.icons.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.shelfmark.ui.components.rememberRepository
import app.shelfmark.ui.theme.StarDark
import java.util.concurrent.Executors
import kotlin.math.min

private enum class ScanMode(@StringRes val label: Int, val icon: ImageVector, @StringRes val hint: Int) {
    Barcode(R.string.mode_barcode, Icons.Rounded.QrCodeScanner, R.string.hint_barcode),
    Cover(R.string.mode_cover, Icons.Rounded.PhotoCamera, R.string.hint_cover),
}

@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onIsbn: (String) -> Unit,
    onPhoto: (String) -> Unit,
) {
    val context = LocalContext.current
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

    var hasPermission by remember { mutableStateOf(granted()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var showIsbnDialog by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        asked = true
    }
    LaunchedEffect(Unit) {
        if (!hasPermission && !asked) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    // Re-check when returning from system settings.
    LifecycleResumeEffect(Unit) {
        hasPermission = granted()
        onPauseOrDispose { }
    }
    LightStatusBarIcons()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            // The dialog's ISBN and a barcode seen behind it would both open a new book.
            hasPermission -> CameraScanner(onIsbn = onIsbn, onPhoto = onPhoto, paused = showIsbnDialog)
            asked -> PermissionNeeded(
                // After "Don't ask again" Android no longer shows the question: settings is the only way.
                onAllow = permissionLauncher.takeIf {
                    context.activity()?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true
                }?.let { launcher -> { launcher.launch(Manifest.permission.CAMERA) } },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
                onTypeIsbn = { showIsbnDialog = true },
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showIsbnDialog = true }) {
                Icon(Icons.Rounded.Keyboard, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.type_isbn_short), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (showIsbnDialog) {
        IsbnDialog(
            onDismiss = { showIsbnDialog = false },
            onSubmit = { isbn ->
                showIsbnDialog = false
                onIsbn(isbn)
            },
        )
    }
}

@Composable
private fun CameraScanner(onIsbn: (String) -> Unit, onPhoto: (String) -> Unit, paused: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val repo = rememberRepository()
    val currentOnIsbn by rememberUpdatedState(onIsbn)

    var mode by rememberSaveable { mutableStateOf(ScanMode.Barcode) }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }

    val analyzer = remember {
        IsbnAnalyzer { isbn ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnIsbn(isbn)
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
            // Higher than the default analysis size so small barcodes read reliably.
            setImageAnalysisResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                    )
                    .build(),
            )
            // Plenty for a cover, without filling the phone with 12 MP photos.
            setImageCaptureResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1600, 1200), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                    )
                    .build(),
            )
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }

    DisposableEffect(lifecycleOwner) {
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
            analyzer.close()
            executor.shutdown()
        }
    }
    // Frames are only analysed while looking for a barcode; taking a cover photo needs none.
    LaunchedEffect(mode) {
        error = null
        if (mode == ScanMode.Barcode) {
            cameraController.setImageAnalysisAnalyzer(executor, analyzer)
        } else {
            cameraController.clearImageAnalysisAnalyzer()
        }
    }
    LaunchedEffect(mode, paused) { analyzer.active = mode == ScanMode.Barcode && !paused }

    fun takePhoto() {
        capturing = true
        error = null
        val file = repo.newPhotoFile()
        cameraController.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturing = false
                    onPhoto(file.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    error = R.string.photo_failed
                }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    controller = cameraController
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Viewfinder(mode)

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(error ?: mode.hint),
                color = if (error != null) StarDark else Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(20.dp))
            Box(Modifier.height(80.dp), contentAlignment = Alignment.Center) {
                if (mode == ScanMode.Cover) {
                    ShutterButton(capturing = capturing, onClick = ::takePhoto)
                }
            }
            Spacer(Modifier.height(20.dp))
            // Switching while a photo is being saved would leave it to arrive in the wrong mode.
            ModeSwitch(mode, onChange = { if (!capturing) mode = it })
        }
    }
}

/** Dims everything outside the target area; morphs between barcode and cover shapes. */
@Composable
private fun Viewfinder(mode: ScanMode) {
    val morph by animateFloatAsState(
        targetValue = if (mode == ScanMode.Barcode) 0f else 1f,
        animationSpec = tween(380),
        label = "frame",
    )
    val sweep by rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "sweep",
    )
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
    ) {
        fun mix(a: Float, b: Float) = a + (b - a) * morph
        val barcodeW = min(size.width * 0.84f, 340.dp.toPx())
        val coverW = min(size.width * 0.66f, size.height * 0.46f * 2f / 3f)
        val w = mix(barcodeW, coverW)
        val h = mix(barcodeW * 0.5f, coverW * 1.5f)
        val topLeft = Offset((size.width - w) / 2f, size.height * 0.42f - h / 2f)
        val frame = androidx.compose.ui.geometry.Size(w, h)
        val radius = CornerRadius(18.dp.toPx())

        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(Color.Transparent, topLeft, frame, radius, blendMode = BlendMode.Clear)
        drawRoundRect(Color.White, topLeft, frame, radius, style = Stroke(width = 2.5.dp.toPx()))

        if (morph < 1f) {
            val y = topLeft.y + h * (0.18f + 0.64f * sweep)
            drawLine(
                color = StarDark.copy(alpha = 1f - morph),
                start = Offset(topLeft.x + 20.dp.toPx(), y),
                end = Offset(topLeft.x + w - 20.dp.toPx(), y),
                strokeWidth = 2.5.dp.toPx(),
            )
        }
    }
}

@Composable
private fun ShutterButton(capturing: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(76.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(if (capturing) Color.White.copy(alpha = 0.5f) else Color.White)
            .clickable(enabled = !capturing, onClickLabel = stringResource(R.string.take_cover_photo), role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (capturing) {
            CircularProgressIndicator(color = Color.Black, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun ModeSwitch(mode: ScanMode, onChange: (ScanMode) -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ScanMode.entries.forEach { m ->
            val selected = m == mode
            val fg = if (selected) Color.Black else Color.White
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(if (selected) Color.White else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onChange(m) })
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(m.icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(m.label), color = fg, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PermissionNeeded(onAllow: (() -> Unit)?, onOpenSettings: () -> Unit, onTypeIsbn: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.camera_off_title),
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.camera_off_body),
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        Spacer(Modifier.height(28.dp))
        val white = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
        if (onAllow != null) {
            Button(onClick = onAllow, colors = white) {
                Text(stringResource(R.string.allow_camera), style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.open_settings), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Button(onClick = onOpenSettings, colors = white) {
                Text(stringResource(R.string.open_settings), style = MaterialTheme.typography.labelLarge)
            }
        }
        TextButton(onClick = onTypeIsbn) {
            Text(stringResource(R.string.type_isbn_instead), color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The camera is always dark, so force light status-bar icons while this screen is shown. */
@Composable
private fun LightStatusBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = insets?.isAppearanceLightStatusBars
        insets?.isAppearanceLightStatusBars = false
        onDispose {
            if (previous != null) insets?.isAppearanceLightStatusBars = previous
        }
    }
}

private fun Context.activity(): Activity? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
