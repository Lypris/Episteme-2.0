/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader.discovery

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aryan.reader.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BarcodeScanner(onDismiss: () -> Unit, onIsbn: (String) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(topBar = {
                TopAppBar(title = { Text(stringResource(R.string.search_scan)) }, navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.search_close)) }
                })
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.scan_instructions))
                    if (granted) {
                        CameraPreview(Modifier.fillMaxWidth().weight(1f), onIsbn)
                    } else {
                        Text(stringResource(R.string.scan_permission))
                        Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.scan_grant)) }
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.scan_type_isbn)) }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(modifier: Modifier, onIsbn: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(onIsbn)
    var failed by remember { mutableStateOf(false) }
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    DisposableEffect(previewView, lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val disposed = AtomicBoolean(false)
        val delivered = AtomicBoolean(false)
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val decoder = IsbnBarcodeDecoder()
        var lastFrame = 0L
        analysis.setAnalyzer(executor) { frame ->
            try {
                val now = System.nanoTime()
                if (!disposed.get() && !delivered.get() && now - lastFrame > 200_000_000L) {
                    lastFrame = now
                    val plane = frame.planes[0]
                    val buffer = plane.buffer.duplicate()
                    val start = buffer.position()
                    val width = frame.width
                    val height = frame.height
                    val bytes = ByteArray(width * height)
                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            bytes[y * width + x] = buffer.get(start + y * plane.rowStride + x * plane.pixelStride)
                        }
                    }
                    val decoded = decoder.decode(bytes, width, height)
                    if (decoded != null && delivered.compareAndSet(false, true)) {
                        mainExecutor.execute { if (!disposed.get()) callback(decoded) }
                    }
                }
            } catch (_: Exception) {
                // Some camera implementations may supply a truncated frame.
            } finally {
                frame.close()
            }
        }
        future.addListener({
            if (!disposed.get()) {
                try {
                    provider = future.get()
                    val selector = if (provider!!.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else CameraSelector.DEFAULT_FRONT_CAMERA
                    provider!!.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                } catch (error: Exception) {
                    timber.log.Timber.tag("ISBNScanner").w(error, "Camera unavailable")
                    failed = true
                }
            }
        }, mainExecutor)
        onDispose {
            disposed.set(true)
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            executor.shutdown()
        }
    }
    Box(modifier) {
        if (failed) Text(stringResource(R.string.scan_unavailable))
        else AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
    }
}
