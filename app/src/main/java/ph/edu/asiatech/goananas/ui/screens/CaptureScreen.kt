package ph.edu.asiatech.goananas.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ph.edu.asiatech.goananas.data.PLANT_PARTS
import ph.edu.asiatech.goananas.ui.AnalysisState
import ph.edu.asiatech.goananas.ui.AppViewModel
import ph.edu.asiatech.goananas.ui.theme.AlertRed
import ph.edu.asiatech.goananas.ui.theme.PineGold
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(vm: AppViewModel, onBack: () -> Unit, onAnalyzed: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by vm.analysis.collectAsStateWithLifecycle()
    var part by remember { mutableStateOf(vm.selectedPart) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) { vm.selectedPart = part; vm.analyze(uri) }
    }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    LaunchedEffect(Unit) { if (!hasCamera) cameraPermission.launch(Manifest.permission.CAMERA) }
    LaunchedEffect(state) { if (state is AnalysisState.Done) onAnalyzed() }

    DisposableEffect(hasCamera) {
        var provider: ProcessCameraProvider? = null
        if (hasCamera) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                provider = future.get().also { p ->
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    p.unbindAll()
                    p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose { provider?.unbindAll() }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Scan a pineapple") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        Text("Plant part in the photo", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PLANT_PARTS.forEach { p -> FilterChip(selected = part == p, onClick = { part = p; vm.selectedPart = p }, label = { Text(p) }) }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (hasCamera) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                // guide frame: keep the affected part inside
                Box(Modifier.size(240.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f)))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera permission is needed to take photos.", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                }
            }
            if (state is AnalysisState.Working) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(20.dp)) {
                    CircularProgressIndicator(color = PineGold)
                    Spacer(Modifier.height(8.dp))
                    Text("Analyzing the photo…", color = Color.White)
                }
            }
        }
        ((state as? AnalysisState.Failed)?.message ?: captureError)?.let { Text(it, color = AlertRed, modifier = Modifier.padding(horizontal = 16.dp)) }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.size(6.dp)); Text("Gallery")
            }
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(PineGold),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    enabled = hasCamera && state !is AnalysisState.Working,
                    onClick = {
                        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
                        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
                        imageCapture.takePicture(
                            ImageCapture.OutputFileOptions.Builder(file).build(),
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    captureError = null; vm.selectedPart = part; vm.analyze(Uri.fromFile(file))
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    captureError = "The photo could not be taken. Please try again."
                                }
                            },
                        )
                    },
                    modifier = Modifier.size(72.dp),
                ) { Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White)) }
            }
            Spacer(Modifier.size(96.dp, 1.dp))
        }
    }
}
