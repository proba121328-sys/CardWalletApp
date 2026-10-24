package com.example.cardwallet

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@Composable
fun ScannerScreen(
    onCodeScanned: (code: String, format: Int) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Проверяем, дал ли уже пользователь разрешение на камеру
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Системное диалоговое окно с вопросом "Разрешить приложению доступ к камере?"
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        // Контроллер камеры CameraX
        val cameraController = remember {
            LifecycleCameraController(context).apply {
                setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            }
        }

        // Флаг, чтобы код не считывался по 50 раз в секунду
        var isScanned by remember { mutableStateOf(false) }

        // Настраиваем распознавание штрих-кодов
        val barcodeScanner = remember {
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
            BarcodeScanning.getClient(options)
        }

        val executor = remember { ContextCompat.getMainExecutor(context) }

        cameraController.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(
                listOf(barcodeScanner),
                CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED,
                executor
            ) { result ->
                if (isScanned) return@MlKitAnalyzer

                val barcodeList = result?.getValue(barcodeScanner)
                if (!barcodeList.isNullOrEmpty()) {
                    val firstBarcode = barcodeList.firstOrNull()
                    val value = firstBarcode?.rawValue
                    val format = firstBarcode?.format ?: Barcode.FORMAT_CODE_128

                    if (!value.isNullOrBlank()) {
                        isScanned = true // Блокируем повторное чтение
                        onCodeScanned(value, format)
                    }
                }
            }
        )

        // Связываем камеру с жизненным циклом экрана
        cameraController.bindToLifecycle(lifecycleOwner)

        // Разметка экрана
        Box(modifier = Modifier.fillMaxSize()) {
            // Само превью камеры
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        this.controller = cameraController
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Белая рамка-прицел по центру экрана для карты
            Box(
                modifier = Modifier
                    .size(width = 280.dp, height = 180.dp)
                    .align(Alignment.Center)
                    .border(3.dp, Color.White, RoundedCornerShape(16.dp))
            )

            // Текст-подсказка сверху
            Text(
                text = "Наведите камеру на штрих-код",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Кнопка отмены внизу
            Button(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
            ) {
                Text("Отмена")
            }
        }
    } else {
        // Если пользователь отказался давать разрешение
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Для сканирования карты нужен доступ к камере")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Дать разрешение")
                }
            }
        }
    }
}