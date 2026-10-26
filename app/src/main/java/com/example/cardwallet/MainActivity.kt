package com.example.cardwallet

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.cardwallet.ui.theme.CardWalletTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Защита от скриншотов
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

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
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val cards by cardDao.getAllCards().collectAsState(initial = emptyList())

    val prefs = remember { context.getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE) }
    var favoriteCodes by remember {
        mutableStateOf(prefs.getStringSet("favorites", emptySet()) ?: emptySet())
    }

    fun toggleFavorite(codeValue: String) {
        val updated = if (codeValue in favoriteCodes) {
            favoriteCodes - codeValue
        } else {
            favoriteCodes + codeValue
        }
        favoriteCodes = updated
        prefs.edit().putStringSet("favorites", updated).apply()
    }

    var showScanner by remember { mutableStateOf(false) }
    var showAddChoiceDialog by remember { mutableStateOf(false) }
    var showManualAddDialog by remember { mutableStateOf(false) }

    var scannedCodeData by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var selectedCard by remember { mutableStateOf<DiscountCard?>(null) }

    var storeNameInput by remember { mutableStateOf("") }
    var codeValueInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val cardColors = listOf(
        0xFF2E7D32L, 0xFF1565C0L, 0xFFC62828L, 0xFF6A1B9AL, 0xFFE65100L, 0xFF00838FL, 0xFF37474FL
    )
    var selectedColor by remember { mutableStateOf(cardColors[0]) }

    val filteredCards = remember(cards, searchQuery, favoriteCodes) {
        cards
            .filter { card ->
                searchQuery.isBlank() ||
                        card.storeName.contains(searchQuery, ignoreCase = true) ||
                        card.codeValue.contains(searchQuery, ignoreCase = true)
            }
            .sortedByDescending { it.codeValue in favoriteCodes }
    }

    if (showScanner) {
        ScannerScreen(
            onCodeScanned = { code, format ->
                scannedCodeData = Pair(code, format)
                selectedColor = cardColors.random()
                showScanner = false
            },
            onClose = { showScanner = false }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Мои скидочные карты")
                            if (cards.isNotEmpty()) {
                                Text(
                                    text = "Всего карт: ${cards.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddChoiceDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить карту")
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (cards.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Поиск по названию или номеру...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Поиск")
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Очистить")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                if (cards.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "У вас пока нет карт.\nНажмите +, чтобы добавить первую!",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (filteredCards.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "По запросу «$searchQuery» ничего не найдено",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredCards) { card ->
                            CardItem(
                                card = card,
                                isFavorite = card.codeValue in favoriteCodes,
                                onFavoriteClick = { toggleFavorite(card.codeValue) },
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
    }

    if (showAddChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showAddChoiceDialog = false },
            title = { Text("Добавление карты") },
            text = { Text("Как вы хотите добавить карту?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAddChoiceDialog = false
                        showScanner = true
                    }
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Сканировать")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddChoiceDialog = false
                        storeNameInput = ""
                        codeValueInput = ""
                        selectedColor = cardColors.random()
                        showManualAddDialog = true
                    }
                ) {
                    Icon(Icons.Default.Create, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ввести вручную")
                }
            }
        )
    }

    if (showManualAddDialog) {
        AlertDialog(
            onDismissRequest = { showManualAddDialog = false },
            title = { Text("Ввод данных карты") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = storeNameInput,
                        onValueChange = { storeNameInput = it },
                        label = { Text("Название магазина") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = codeValueInput,
                        onValueChange = { codeValueInput = it },
                        label = { Text("Номер или штрихкод") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Цвет карточки:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        cardColors.forEach { colorHex ->
                            val isSelected = selectedColor == colorHex
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorHex))
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = colorHex }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (storeNameInput.isNotBlank() && codeValueInput.isNotBlank()) {
                            coroutineScope.launch {
                                cardDao.insertCard(
                                    DiscountCard(
                                        storeName = storeNameInput.trim(),
                                        codeValue = codeValueInput.trim(),
                                        codeFormat = 7,
                                        colorHex = selectedColor
                                    )
                                )
                                showManualAddDialog = false
                            }
                        }
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualAddDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    scannedCodeData?.let { (code, format) ->
        AlertDialog(
            onDismissRequest = { scannedCodeData = null },
            title = { Text("Сохранить карту") },
            text = {
                Column {
                    Text("Код: $code")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = storeNameInput,
                        onValueChange = { storeNameInput = it },
                        label = { Text("Название магазина") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Цвет карточки:", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        cardColors.forEach { colorHex ->
                            val isSelected = selectedColor == colorHex
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorHex))
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = colorHex }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (storeNameInput.isNotBlank()) {
                            coroutineScope.launch {
                                cardDao.insertCard(
                                    DiscountCard(
                                        storeName = storeNameInput.trim(),
                                        codeValue = code,
                                        codeFormat = format,
                                        colorHex = selectedColor
                                    )
                                )
                                storeNameInput = ""
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

    selectedCard?.let { card ->
        DisposableEffect(Unit) {
            val window = (context as? Activity)?.window
            val originalBrightness = window?.attributes?.screenBrightness ?: -1f
            window?.attributes?.let { attr ->
                attr.screenBrightness = 1.0f
                window.attributes = attr
            }
            onDispose {
                window?.attributes?.let { attr ->
                    attr.screenBrightness = originalBrightness
                    window.attributes = attr
                }
            }
        }

        Dialog(onDismissRequest = { selectedCard = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = card.storeName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val bitmap: Bitmap? = remember(card) {
                        BarcodeUtils.generateBarcodeBitmap(card.codeValue, card.codeFormat)
                    }

                    if (bitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Штрих-код",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = card.codeValue.chunked(4).joinToString(" "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(card.codeValue))
                            Toast.makeText(context, "Номер скопирован", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Скопировать номер")
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { selectedCard = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
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
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(card.colorHex)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = card.storeName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onFavoriteClick) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "В избранное",
                            tint = if (isFavorite) Color(0xFFFFD54F) else Color.White
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Удалить карту",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = card.codeValue.chunked(4).joinToString(" "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 15.sp
                )

                Surface(
                    color = Color.Black.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable {
                        clipboardManager.setText(AnnotatedString(card.codeValue))
                        Toast.makeText(context, "Номер скопирован", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(
                        text = "Скопировать",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}