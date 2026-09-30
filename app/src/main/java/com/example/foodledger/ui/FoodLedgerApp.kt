package com.example.foodledger.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.foodledger.data.LedgerEntry
import com.example.foodledger.recognition.ModelProvider
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val categories = listOf("餐饮", "水果", "零食", "饮品", "买菜", "其他")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodLedgerApp(vm: LedgerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val todayStart = remember { startOfToday() }
    val todayEntries = state.entries.filter { it.createdAt >= todayStart }
    val todayTotal = todayEntries.sumOf { it.amount }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(Modifier.fillMaxWidth(.9f)) {
                ModelSettingsDrawer(
                    state = state,
                    onSelect = vm::selectProvider,
                    onSaveKey = vm::saveProviderKey
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("食记账", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "模型设置")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Home, null) }, label = { Text("今天") })
                    NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.ReceiptLong, null) }, label = { Text("全部") })
                }
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "记一笔") }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                SummaryCard(todayTotal, todayEntries.size)
                val shown = if (tab == 0) todayEntries else state.entries
                EntryList(shown, vm::delete, Modifier.weight(1f))
            }
        }
    }

    if (showAdd) {
        AddEntrySheet(
            state = state,
            onDismiss = { showAdd = false; vm.consumeRecognition() },
            onImages = vm::setImages,
            onRemoveImage = vm::removeImage,
            onRecognize = vm::recognizeImages,
            onSave = { meal, amount, category, note ->
                vm.add(meal, amount, category, note)
                vm.consumeRecognition()
                showAdd = false
            }
        )
    }
}

@Composable
private fun ModelSettingsDrawer(
    state: LedgerUiState,
    onSelect: (ModelProvider) -> Unit,
    onSaveKey: (ModelProvider, String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var visibleKeys by remember { mutableStateOf(emptySet<ModelProvider>()) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("智能识别设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("选择模型并填写 API Key", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
        HorizontalDivider()
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ModelProvider.entries, key = { it.name }) { provider ->
                val selected = state.selectedProvider == provider
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected, { onSelect(provider) })
                            Column(Modifier.weight(1f)) {
                                Text(provider.displayName, fontWeight = FontWeight.SemiBold)
                                Text(provider.modelName, style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { uriHandler.openUri(provider.keyUrl) }) {
                                Text("获取 Key")
                                Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp))
                            }
                        }
                        OutlinedTextField(
                            value = state.providerKeys[provider].orEmpty(),
                            onValueChange = { onSaveKey(provider, it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API Key") },
                            placeholder = { Text("粘贴 ${provider.displayName} Key") },
                            singleLine = true,
                            visualTransformation = if (provider in visibleKeys) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    visibleKeys = if (provider in visibleKeys) visibleKeys - provider else visibleKeys + provider
                                }) {
                                    Icon(if (provider in visibleKeys) Icons.Default.VisibilityOff else Icons.Default.Visibility, "显示或隐藏 Key")
                                }
                            }
                        )
                    }
                }
            }
            item {
                Text(
                    "Key 使用 Android Keystore 加密后仅保存在本机。识别时图片会发送给当前选中的模型厂商。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(total: Double, count: Int) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("今天花了", color = MaterialTheme.colorScheme.secondary)
                Text("¥ %.2f".format(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("饮食记录", color = MaterialTheme.colorScheme.secondary)
                Text("$count 笔", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EntryList(entries: List<LedgerEntry>, onDelete: (Long) -> Unit, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Restaurant, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .45f))
                Spacer(Modifier.height(12.dp))
                Text("还没有记录，点右下角记一笔", color = MaterialTheme.colorScheme.secondary)
            }
        }
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(entries, key = { it.id }) { entry -> EntryCard(entry, onDelete) }
    }
}

@Composable
private fun EntryCard(entry: LedgerEntry, onDelete: (Long) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.RestaurantMenu, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.meal, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${entry.category} · ${formatTime(entry.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                if (entry.imageUris.isNotEmpty()) Text("${entry.imageUris.size} 张图片", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text("¥ %.2f".format(entry.amount), fontWeight = FontWeight.Bold)
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("删除") }, onClick = { menu = false; onDelete(entry.id) }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEntrySheet(
    state: LedgerUiState,
    onDismiss: () -> Unit,
    onImages: (List<Uri>) -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onRecognize: () -> Unit,
    onSave: (String, Double, String, String) -> Unit
) {
    val context = LocalContext.current
    var meal by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("餐饮") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.recognizedMeal) {
        if (state.recognizedMeal.isNotBlank()) {
            meal = state.recognizedMeal
            category = state.recognizedCategory
            if (state.recognizedAmount.isNotBlank()) amount = state.recognizedAmount
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(12)) { uris ->
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        if (uris.isNotEmpty()) onImages((state.selectedImages + uris).distinct().take(12))
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Text("记一笔", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            item {
                OutlinedTextField(meal, { meal = it; error = null }, Modifier.fillMaxWidth(), label = { Text("今天吃了什么") }, singleLine = true)
            }
            item {
                OutlinedTextField(
                    amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' }; error = null },
                    Modifier.fillMaxWidth(), label = { Text("花费金额") }, prefix = { Text("¥ ") }, singleLine = true
                )
            }
            item {
                Text("分类", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { item -> FilterChip(category == item, { category = item }, { Text(item) }) }
                }
            }
            item {
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("备注（可选）") }, minLines = 2)
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, null)
                        Spacer(Modifier.width(6.dp))
                        Text("批量选择图片")
                    }
                    if (state.selectedImages.isNotEmpty()) {
                        Spacer(Modifier.width(10.dp))
                        Text("最多 12 张", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            if (state.selectedImages.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.selectedImages, key = { it.toString() }) { uri ->
                            Box {
                                AsyncImage(uri, null, Modifier.size(86.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                IconButton(
                                    onClick = { onRemoveImage(uri) },
                                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp).background(Color.Black.copy(alpha = .55f), CircleShape)
                                ) { Icon(Icons.Default.Close, "移除", tint = Color.White, modifier = Modifier.size(16.dp)) }
                            }
                        }
                    }
                }
                item {
                    Button(onClick = onRecognize, enabled = !state.recognizing, modifier = Modifier.fillMaxWidth()) {
                        if (state.recognizing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Icon(Icons.Default.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.recognizing) "正在识别…" else "使用 ${state.selectedProvider.displayName} 识别")
                    }
                }
            }
            if (state.recognitionError.isNotBlank()) {
                item { Text(state.recognitionError, color = MaterialTheme.colorScheme.error) }
            }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            item {
                Button(
                    onClick = {
                        val value = amount.toDoubleOrNull()
                        when {
                            meal.isBlank() -> error = "请填写吃了什么"
                            value == null || value < 0 -> error = "请输入正确金额"
                            else -> onSave(meal, value, category, note)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("保存记录") }
            }
        }
    }
}

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun formatTime(time: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(time))
