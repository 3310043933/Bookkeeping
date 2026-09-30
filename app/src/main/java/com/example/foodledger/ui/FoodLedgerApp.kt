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
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val todayStart = remember { startOfToday() }
    val todayEntries = state.entries.filter { it.createdAt >= todayStart }
    val todayTotal = todayEntries.filter { it.transactionType == "EXPENSE" }.sumOf { it.amount }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(Modifier.fillMaxWidth(.9f)) {
                AppDrawer(
                    state = state,
                    onSelect = vm::selectProvider,
                    onSaveKey = vm::saveProviderKey,
                    onSavePrompt = vm::saveRecognitionPrompt,
                    onClearLogs = vm::clearRequestLogs,
                    onAddCategory = vm::addCategory,
                    onDeletePrimary = vm::deletePrimaryCategory,
                    onDeleteSecondary = vm::deleteSecondaryCategory
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
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.BarChart, null) }, label = { Text("统计") })
                }
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "记一笔") }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                SummaryCard(todayTotal, todayEntries.size)
                val shown = if (tab == 0) todayEntries else state.entries
                if(tab==2) StatisticsView(state.entries, Modifier.weight(1f)) else EntryList(shown, vm::delete, { entry -> editingEntry=entry; vm.setImages(entry.imageUris.map(Uri::parse)); showAdd=true }, Modifier.weight(1f))
            }
        }
    }

    if (showAdd) {
        AddEntrySheet(
            state = state,
            existing = editingEntry,
            onDismiss = { showAdd = false; editingEntry=null; vm.setImages(emptyList()); vm.consumeRecognition() },
            onImages = vm::setImages,
            onRemoveImage = vm::removeImage,
            onRecognize = vm::recognizeImages,
            onSave = { meal, amount, primary, secondary, type, note ->
                vm.saveEntry(editingEntry, meal, amount, primary, secondary, type, note)
                vm.consumeRecognition()
                editingEntry=null
                showAdd = false
            }
        )
    }
}

@Composable
private fun AppDrawer(
    state: LedgerUiState,
    onSelect: (ModelProvider) -> Unit,
    onSaveKey: (ModelProvider, String) -> Unit,
    onSavePrompt: (String) -> Unit,
    onClearLogs: () -> Unit,
    onAddCategory: (String,String) -> Unit,
    onDeletePrimary: (String) -> Unit,
    onDeleteSecondary: (String,String) -> Unit
) {
    var section by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("食记账设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
        NavigationDrawerItem(
            label = { Text("模型 Key 设置") }, selected = section == 0,
            onClick = { section = 0 }, icon = { Icon(Icons.Default.Key, null) }, modifier = Modifier.padding(horizontal = 12.dp)
        )
        NavigationDrawerItem(
            label = { Text("模型请求记录") }, selected = section == 1,
            onClick = { section = 1 }, icon = { Icon(Icons.Default.History, null) }, modifier = Modifier.padding(horizontal = 12.dp)
        )
        NavigationDrawerItem(
            label = { Text("分类管理") }, selected = section == 3,
            onClick = { section = 3 }, icon = { Icon(Icons.Default.Category, null) }, modifier = Modifier.padding(horizontal = 12.dp)
        )
        NavigationDrawerItem(
            label = { Text("识别指令设置") }, selected = section == 2,
            onClick = { section = 2 }, icon = { Icon(Icons.Default.EditNote, null) }, modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Box(Modifier.weight(1f)) {
            when (section) {
                0 -> KeySettingsContent(state, onSelect, onSaveKey)
                1 -> RequestLogsContent(state, onClearLogs)
                2 -> PromptSettingsContent(state.recognitionPrompt, onSavePrompt)
                else -> CategorySettingsContent(state, onAddCategory, onDeletePrimary, onDeleteSecondary)
            }
        }
    }
}

@Composable private fun CategorySettingsContent(state: LedgerUiState,onAdd:(String,String)->Unit,onDeletePrimary:(String)->Unit,onDeleteSecondary:(String,String)->Unit){
 var p by remember{mutableStateOf("")};var s by remember{mutableStateOf("")}
 var pendingPrimary by remember{mutableStateOf<String?>(null)};var pendingSecondary by remember{mutableStateOf<Pair<String,String>?>(null)}
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  item{Text("一级 / 二级分类",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}
  items(state.categories.entries.toList()){e->Card{Column(Modifier.padding(12.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text(e.key,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));IconButton({pendingPrimary=e.key}){Icon(Icons.Default.Delete,"删除一级分类",tint=MaterialTheme.colorScheme.error)}};e.value.forEach{sub->Row(verticalAlignment=Alignment.CenterVertically){Text(sub,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);IconButton({pendingSecondary=e.key to sub},Modifier.size(36.dp)){Icon(Icons.Default.Close,"删除二级分类",Modifier.size(18.dp))}}}}}}
  item{OutlinedTextField(p,{p=it},Modifier.fillMaxWidth(),label={Text("一级类别")});OutlinedTextField(s,{s=it},Modifier.fillMaxWidth(),label={Text("二级类别")});Button({onAdd(p,s);s=""},Modifier.fillMaxWidth()){Text("添加分类")}}
 }
 pendingPrimary?.let{name->AlertDialog(onDismissRequest={pendingPrimary=null},title={Text("删除一级分类？")},text={Text("“$name”及其全部二级分类都会被删除，已有账目不会被删除。")},confirmButton={TextButton({onDeletePrimary(name);pendingPrimary=null}){Text("删除",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton({pendingPrimary=null}){Text("取消")}})}
 pendingSecondary?.let{pair->AlertDialog(onDismissRequest={pendingSecondary=null},title={Text("删除二级分类？")},text={Text("确定删除“${pair.second}”吗？已有账目不会被删除。")},confirmButton={TextButton({onDeleteSecondary(pair.first,pair.second);pendingSecondary=null}){Text("删除",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton({pendingSecondary=null}){Text("取消")}})}
}

@Composable private fun StatisticsView(entries:List<LedgerEntry>,modifier:Modifier=Modifier){
 var days by remember{mutableIntStateOf(7)};val since=System.currentTimeMillis()-days*86400000L;val list=entries.filter{it.createdAt>=since};val expense=list.filter{it.transactionType=="EXPENSE"}.sumOf{it.amount};val income=list.filter{it.transactionType=="INCOME"}.sumOf{it.amount};
 LazyColumn(modifier,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(days==7,{days=7},{Text("本周")});FilterChip(days==30,{days=30},{Text("本月")});FilterChip(days==365,{days=365},{Text("本年")})}}
  item{Card{Row(Modifier.fillMaxWidth().padding(20.dp),horizontalArrangement=Arrangement.SpaceBetween){Column{Text("收入");Text("+¥%.2f".format(income),color=Color(0xFF2E7D32),fontWeight=FontWeight.Bold)};Column{Text("消费");Text("-¥%.2f".format(expense),color=MaterialTheme.colorScheme.error,fontWeight=FontWeight.Bold)};Column{Text("结余");Text("¥%.2f".format(income-expense),fontWeight=FontWeight.Bold)}}}}
  items(list.filter{it.transactionType=="EXPENSE"}.groupBy{it.primaryCategory}.mapValues{it.value.sumOf{e->e.amount}}.entries.sortedByDescending{it.value}){e->ListItem(headlineContent={Text(e.key)},trailingContent={Text("¥%.2f".format(e.value))})}
 }
}

@Composable
private fun KeySettingsContent(
    state: LedgerUiState,
    onSelect: (ModelProvider) -> Unit,
    onSaveKey: (ModelProvider, String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var visibleKeys by remember { mutableStateOf(emptySet<ModelProvider>()) }
    Column(Modifier.fillMaxSize()) {
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
private fun RequestLogsContent(state: LedgerUiState, onClearLogs: () -> Unit) {
    var expandedId by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("最近 50 条请求", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onClearLogs, enabled = state.requestLogs.isNotEmpty()) { Text("清空") }
        }
        if (state.requestLogs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无请求记录") }
        } else LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.requestLogs, key = { it.id }) { log ->
                Card(onClick = { expandedId = if (expandedId == log.id) null else log.id }) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(log.provider, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(if (log.error.isBlank()) "成功" else "失败", color = if (log.error.isBlank()) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
                        }
                        Text("${formatTime(log.timestamp)} · ${log.imageCount} 张 · ${log.durationMs} ms", style = MaterialTheme.typography.bodySmall)
                        if (expandedId == log.id) {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Text("模型：${log.model}", style = MaterialTheme.typography.bodySmall)
                            Text("请求指令：\n${log.prompt}", style = MaterialTheme.typography.bodySmall)
                            Text(if (log.error.isBlank()) "原始响应：\n${log.response}" else "错误：\n${log.error}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptSettingsContent(prompt: String, onSavePrompt: (String) -> Unit) {
    var draft by remember(prompt) { mutableStateOf(prompt) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("让模型做什么", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("JSON 字段格式由应用固定附加，这里只需描述识别规则。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth().weight(1f), label = { Text("自定义识别指令") }, minLines = 8)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onSavePrompt(draft) }, modifier = Modifier.fillMaxWidth()) { Text("保存识别指令") }
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
private fun EntryList(entries: List<LedgerEntry>, onDelete: (Long) -> Unit, onEdit: (LedgerEntry) -> Unit, modifier: Modifier = Modifier) {
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
        items(entries, key = { it.id }) { entry -> EntryCard(entry, onDelete, onEdit) }
    }
}

@Composable
private fun EntryCard(entry: LedgerEntry, onDelete: (Long) -> Unit, onEdit: (LedgerEntry) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.RestaurantMenu, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.meal, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${entry.primaryCategory} / ${entry.secondaryCategory} · ${formatTime(entry.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                if (entry.imageUris.isNotEmpty()) Text("${entry.imageUris.size} 张图片", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text("${if(entry.transactionType=="INCOME") "+" else "-"}¥ %.2f".format(entry.amount), fontWeight = FontWeight.Bold, color=if(entry.transactionType=="INCOME") Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("修改") }, onClick = { menu=false; onEdit(entry) }, leadingIcon = { Icon(Icons.Default.Edit, null) })
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
    existing: LedgerEntry?,
    onDismiss: () -> Unit,
    onImages: (List<Uri>) -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onRecognize: () -> Unit,
    onSave: (String, Double, String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    var meal by remember(existing) { mutableStateOf(existing?.meal.orEmpty()) }
    var amount by remember(existing) { mutableStateOf(existing?.amount?.toString().orEmpty()) }
    var primary by remember(existing) { mutableStateOf(existing?.primaryCategory ?: state.categories.keys.firstOrNull().orEmpty()) }
    var secondary by remember(existing, primary) { mutableStateOf(existing?.secondaryCategory ?: state.categories[primary]?.firstOrNull().orEmpty()) }
    var type by remember(existing) { mutableStateOf(existing?.transactionType ?: "EXPENSE") }
    var note by remember(existing) { mutableStateOf(existing?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.recognizedMeal) {
        if (state.recognizedMeal.isNotBlank()) {
            meal = state.recognizedMeal
            secondary = state.recognizedCategory
            if (state.recognizedAmount.isNotBlank()) amount = state.recognizedAmount
            note = state.recognizedNote
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
            item { Text(if(existing==null) "记一笔" else "修改记录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { FilterChip(type=="EXPENSE",{type="EXPENSE"},{Text("消费 -")}); FilterChip(type=="INCOME",{type="INCOME"},{Text("收入 +")}) } }
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
                Text("一级分类", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.categories.keys.toList()) { item -> FilterChip(primary == item, { primary=item; secondary=state.categories[item]?.firstOrNull().orEmpty() }, { Text(item) }) }
                }
                Text("二级分类", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(state.categories[primary].orEmpty()) { item -> FilterChip(secondary==item,{secondary=item},{Text(item)}) } }
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
                            else -> onSave(meal, value, primary, secondary, type, note)
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
