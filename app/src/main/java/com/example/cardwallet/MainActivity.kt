package com.example.cardwallet

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.cardwallet.ui.theme.CardWalletTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = AppDatabase.getDatabase(this)
        val cardDao = database.cardDao()

        setContent {
            CardWalletTheme {
                MainWalletApp(cardDao)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainWalletApp(cardDao: CardDao) {
    val coroutineScope = rememberCoroutineScope()
    val cards by cardDao.getAllCards().collectAsState(initial = emptyList())

    // Состояния интерфейса
    var showScanner by remember { mutableStateOf(false) }
    var scannedCodeData by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var selectedCard by remember { mutableStateOf<DiscountCard?>(null) }
    var newStoreName by remember { mutableStateOf("") }

    // Список приятных цветов для фона карточек
    val cardColors = listOf(0xFF2E7D32, 0xFF1565C0, 0xFFC62828, 0xFF6A1B9A, 0xFFE65100)

    if (showScanner) {
        // Экран камеры со сканером
        ScannerScreen(
            onCodeScanned = { code, format ->
                scannedCodeData = Pair(code, format)
                showScanner = false
            },
            onClose = { showScanner = false }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Мои скидочные карты") }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showScanner = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить карту")
                }
            }
        ) { paddingValues ->
            if (cards.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text("У вас пока нет карт.\nНажмите +, чтобы добавить первую!", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(cards) { card ->
                        CardItem(
                            card = card,
                            onClick = { selectedCard = card },
                            onDelete = {
                                coroutineScope.launch {
                                    cardDao.deleteCard(card)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Окно ввода названия после сканирования
    scannedCodeData?.let { (code, format) ->
        AlertDialog(
            onDismissRequest = { scannedCodeData = null },
            title = { Text("Сохранить карту") },
            text = {
                Column {
                    Text("Код: $code")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newStoreName,
                        onValueChange = { newStoreName = it },
                        label = { Text("Название магазина") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newStoreName.isNotBlank()) {
                            val randomColor = cardColors.random()
                            coroutineScope.launch {
                                cardDao.insertCard(
                                    DiscountCard(
                                        storeName = newStoreName,
                                        codeValue = code,
                                        codeFormat = format,
                                        colorHex = randomColor
                                    )
                                )
                                newStoreName = ""
                                scannedCodeData = null
                            }
                        }
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { scannedCodeData = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Окно показа штрих-кода крупно на кассе
    selectedCard?.let { card ->
        Dialog(onDismissRequest = { selectedCard = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = card.storeName,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val bitmap: Bitmap? = remember(card) {
                        BarcodeUtils.generateBarcodeBitmap(card.codeValue, card.codeFormat)
                    }

                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Штрих-код",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = card.codeValue,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = { selectedCard = null }) {
                        Text("Закрыть")
                    }
                }
            }
        }
    }
}

@Composable
fun CardItem(
    card: DiscountCard,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color(card.colorHex)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = card.storeName,
                    style = MaterialTheme.typography.titleLarge,
                    color = androidx.compose.ui.graphics.Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = card.codeValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Удалить карту",
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
        }
    }
}
