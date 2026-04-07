package com.projectprocessor.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projectprocessor.data.model.*
import com.projectprocessor.ui.components.CheckboxWithLabel
import com.projectprocessor.ui.components.CodeHighlighter
import com.projectprocessor.ui.theme.*
import com.projectprocessor.viewmodel.MainViewModel
import com.projectprocessor.viewmodel.MessageType
import com.projectprocessor.viewmodel.UserMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val inputUri by viewModel.inputUri.collectAsStateWithLifecycle()
    val outputDirUri by viewModel.outputDirUri.collectAsStateWithLifecycle()
    val processState by viewModel.processState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val fileTreeRoot by viewModel.fileTreeRoot.collectAsStateWithLifecycle()
    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()
    val previewContent by viewModel.previewContent.collectAsStateWithLifecycle()
    val isLoadingPreview by viewModel.isLoadingPreview.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isLargeScreen = configuration.screenWidthDp >= 600
    val useSplitLayout = isLandscape && isLargeScreen
    val isSmallScreen = configuration.screenWidthDp < 600

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 自动处理标志
    var pendingStartAfterOutput by remember { mutableStateOf(false) }

    // 监听用户消息并显示 Snackbar
    LaunchedEffect(userMessage) {
        userMessage?.let { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.message,
                actionLabel = if (message.type == MessageType.WARNING) "选择" else "确定",
                duration = when (message.type) {
                    MessageType.SUCCESS -> SnackbarDuration.Short
                    MessageType.ERROR -> SnackbarDuration.Long
                    else -> SnackbarDuration.Short
                }
            )
            if (result == SnackbarResult.ActionPerformed && message.type == MessageType.WARNING) {
                if (message.message.contains("输出目录")) {
                    outputPicker.launch(null)
                } else if (message.message.contains("输入源")) {
                    showInputDialog = true
                }
            }
            viewModel.clearUserMessage()
        }
    }

    // 文件选择器 - ZIP文件
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) { }
            viewModel.updateInputUri(it, false)
            viewModel.loadFileTree(it, false)
        }
    }

    // 文件选择器 - 文件夹
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) { }
            viewModel.updateInputUri(it, true)
            viewModel.loadFileTree(it, true)
        }
    }

    // 输出目录选择器
    val outputPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) { }
            viewModel.updateOutputDirUri(it)
            if (pendingStartAfterOutput) {
                pendingStartAfterOutput = false
                viewModel.startProcess()
            }
        }
    }

    var showInputDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val isProcessing = processState is ProcessState.Processing

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (isLargeScreen) {
                        Text("安卓项目处理器", fontSize = 18.sp)
                    } else {
                        // 小屏幕只显示图标，节省空间
                        Icon(Icons.Default.Storage, contentDescription = "安卓项目处理器", modifier = Modifier.size(24.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    if (inputUri == null || outputDirUri == null) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "未完成配置",
                            tint = WarningOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (isProcessing) {
                        viewModel.stopProcess()
                    } else {
                        val inputSelected = inputUri != null
                        if (!inputSelected) {
                            showInputDialog = true
                            return@ExtendedFloatingActionButton
                        }
                        if (outputDirUri == null) {
                            pendingStartAfterOutput = true
                            outputPicker.launch(null)
                            return@ExtendedFloatingActionButton
                        }
                        viewModel.startProcess()
                    }
                },
                icon = {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PlayArrow, null)
                    }
                },
                text = { Text(if (isProcessing) "取消" else "开始处理") },
                containerColor = if (isProcessing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (useSplitLayout) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.weight(0.4f)) {
                        FileTreePanel(
                            root = fileTreeRoot,
                            selectedFile = selectedFile,
                            onFileClick = { viewModel.selectFile(it) },
                            onFolderToggle = { viewModel.toggleFolder(it) },
                            modifier = Modifier.weight(0.6f)
                        )
                        SettingsPanel(
                            config = config,
                            inputUri = inputUri,
                            outputDirUri = outputDirUri,
                            viewModel = viewModel,
                            showInputDialog = showInputDialog,
                            onShowInputDialogChange = { showInputDialog = it },
                            outputPicker = { outputPicker.launch(null) },
                            isSmallScreen = isSmallScreen,
                            modifier = Modifier.weight(0.4f)
                        )
                    }
                    PreviewPanel(
                        content = previewContent,
                        isLoading = isLoadingPreview,
                        selectedFile = selectedFile,
                        logs = logs,
                        modifier = Modifier.weight(0.6f)
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.weight(0.55f)) {
                        FileTreePanel(
                            root = fileTreeRoot,
                            selectedFile = selectedFile,
                            onFileClick = { viewModel.selectFile(it) },
                            onFolderToggle = { viewModel.toggleFolder(it) },
                            modifier = Modifier.weight(0.65f)
                        )
                        SettingsPanel(
                            config = config,
                            inputUri = inputUri,
                            outputDirUri = outputDirUri,
                            viewModel = viewModel,
                            showInputDialog = showInputDialog,
                            onShowInputDialogChange = { showInputDialog = it },
                            outputPicker = { outputPicker.launch(null) },
                            isSmallScreen = isSmallScreen,
                            modifier = Modifier.weight(0.35f)
                        )
                    }
                    PreviewPanel(
                        content = previewContent,
                        isLoading = isLoadingPreview,
                        selectedFile = selectedFile,
                        logs = logs,
                        modifier = Modifier.weight(0.45f)
                    )
                }
            }
        }
    }

    if (showInputDialog) {
        AlertDialog(
            onDismissRequest = { showInputDialog = false },
            title = { Text("选择输入源") },
            text = {
                Column {
                    Text(
                        "请选择ZIP文件或文件夹作为输入源",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
                            showInputDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Archive, null)
                        Spacer(Modifier.width(8.dp))
                        Text("选择 ZIP 文件")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            folderPicker.launch(null)
                            showInputDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Folder, null)
                        Spacer(Modifier.width(8.dp))
                        Text("选择文件夹")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showInputDialog = false }) { Text("取消") } }
        )
    }

    if (showSettings) {
        SettingsDialog(
            autoProcess = config.autoProcess,
            onAutoProcessChange = { viewModel.updateAutoProcess(it) },
            autoOpenOutputDir = config.autoOpenOutputDir,
            onAutoOpenOutputDirChange = { viewModel.updateAutoOpenOutputDir(it) },
            autoOpenTextFile = config.autoOpenTextFile,
            onAutoOpenTextFileChange = { viewModel.updateAutoOpenTextFile(it) },
            separateXmlOutput = config.separateXmlOutput,
            onSeparateXmlOutputChange = { viewModel.updateSeparateXmlOutput(it) },
            codeExtras = config.codeExtras,
            onCodeExtrasChange = { viewModel.updateCodeExtras(it) },
            xmlSubdirs = config.xmlSubdirs,
            onXmlSubdirsChange = { viewModel.updateXmlSubdirs(it) },
            onReset = { viewModel.resetToDefault() },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
fun FileTreePanel(
    root: FileFolder?,
    selectedFile: FileItem?,
    onFileClick: (FileItem) -> Unit,
    onFolderToggle: (FileFolder) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxSize().padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        if (root == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "请先选择 ZIP 或文件夹",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items(root.children) { node ->
                    FileTreeNodeView(
                        node = node,
                        selectedFile = selectedFile,
                        onFileClick = onFileClick,
                        onFolderToggle = onFolderToggle,
                        level = 0
                    )
                }
            }
        }
    }
}

@Composable
fun FileTreeNodeView(
    node: FileTreeNode,
    selectedFile: FileItem?,
    onFileClick: (FileItem) -> Unit,
    onFolderToggle: (FileFolder) -> Unit,
    level: Int
) {
    val indent = (level * 16).dp
    when (node) {
        is FileFolder -> {
            var expanded by remember { mutableStateOf(node.isExpanded) }
            LaunchedEffect(node.isExpanded) { expanded = node.isExpanded }
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onFolderToggle(node)
                            expanded = !expanded
                        }
                        .padding(start = indent, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (expanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(node.name, fontWeight = if (expanded) FontWeight.Bold else null)
                }
                if (expanded) {
                    node.children.forEach { child ->
                        FileTreeNodeView(child, selectedFile, onFileClick, onFolderToggle, level + 1)
                    }
                }
            }
        }
        is FileItem -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFileClick(node) }
                    .background(
                        if (selectedFile == node) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .padding(start = indent + 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.InsertDriveFile, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    node.name,
                    color = if (node.isProcessed) SuccessGreen else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (node.isProcessed) {
                    Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun PreviewPanel(
    content: String,
    isLoading: Boolean,
    selectedFile: FileItem?,
    logs: List<LogEntry>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxSize().padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selectedFile != null) {
                Text(
                    text = selectedFile.path,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Divider()
            }
            Box(modifier = Modifier.weight(1f).padding(12.dp)) {
                when {
                    isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    selectedFile == null -> Text("选择一个文件以预览", modifier = Modifier.align(Alignment.Center))
                    else -> CodeHighlighter(
                        code = content,
                        fileExtension = selectedFile.name.substringAfterLast('.', ""),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            if (logs.isNotEmpty()) {
                Divider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(Color(0xFF1E1E1E))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    logs.takeLast(20).forEach { entry ->
                        val color = when (entry.type) {
                            LogType.INFO -> Color.White
                            LogType.WARNING -> WarningOrange
                            LogType.ERROR -> ErrorRed
                            LogType.SUCCESS -> SuccessGreen
                        }
                        Text(
                            text = entry.message,
                            color = color,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsPanel(
    config: ProcessConfig,
    inputUri: Uri?,
    outputDirUri: Uri?,
    viewModel: MainViewModel,
    showInputDialog: Boolean,
    onShowInputDialogChange: (Boolean) -> Unit,
    outputPicker: () -> Unit,
    isSmallScreen: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxSize().padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isSmallScreen) 8.dp else 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 6.dp else 8.dp)
        ) {
            // 输入源按钮
            OutlinedButton(
                onClick = { onShowInputDialogChange(true) },
                modifier = Modifier.fillMaxWidth(),
                colors = if (inputUri != null) {
                    ButtonDefaults.outlinedButtonColors(contentColor = SuccessGreen)
                } else {
                    ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                },
                contentPadding = if (isSmallScreen) PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                else PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(
                    if (inputUri != null) Icons.Default.CheckCircle else Icons.Default.FolderOpen,
                    null,
                    modifier = Modifier.size(if (isSmallScreen) 16.dp else 18.dp)
                )
                Spacer(Modifier.width(if (isSmallScreen) 4.dp else 8.dp))
                Text(
                    text = if (inputUri != null) {
                        val lastSegment = inputUri.lastPathSegment ?: "已选"
                        lastSegment.substringAfterLast('/').take(if (isSmallScreen) 15 else 30)
                    } else "选择输入源",
                    fontSize = if (isSmallScreen) 12.sp else 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 输出目录按钮
            OutlinedButton(
                onClick = outputPicker,
                modifier = Modifier.fillMaxWidth(),
                colors = if (outputDirUri != null) {
                    ButtonDefaults.outlinedButtonColors(contentColor = SuccessGreen)
                } else {
                    ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                },
                contentPadding = if (isSmallScreen) PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                else PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(
                    if (outputDirUri != null) Icons.Default.CheckCircle else Icons.Default.SaveAlt,
                    null,
                    modifier = Modifier.size(if (isSmallScreen) 16.dp else 18.dp)
                )
                Spacer(Modifier.width(if (isSmallScreen) 4.dp else 8.dp))
                Text(
                    text = if (outputDirUri != null) {
                        val lastSegment = outputDirUri.lastPathSegment ?: "已选"
                        lastSegment.substringAfterLast('/').take(if (isSmallScreen) 15 else 30)
                    } else "选择输出目录",
                    fontSize = if (isSmallScreen) 12.sp else 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 状态提示
            if (inputUri == null || outputDirUri == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            WarningOrange.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        null,
                        tint = WarningOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (inputUri == null && outputDirUri == null)
                            "请选择输入源和输出目录"
                        else if (inputUri == null)
                            "请选择输入源"
                        else
                            "请选择输出目录",
                        fontSize = 11.sp,
                        color = WarningOrange
                    )
                }
            }

            // 三个复选框一行显示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CheckboxWithLabel(
                    label = "代码",
                    checked = config.processCode,
                    onCheckedChange = { viewModel.updateProcessCode(it) },
                    modifier = Modifier.weight(1f)
                )
                CheckboxWithLabel(
                    label = "XML",
                    checked = config.processXml,
                    onCheckedChange = { viewModel.updateProcessXml(it) },
                    modifier = Modifier.weight(1f)
                )
                CheckboxWithLabel(
                    label = "高级",
                    checked = config.processAdvanced,
                    onCheckedChange = { viewModel.updateProcessAdvanced(it) },
                    modifier = Modifier.weight(1f)
                )
            }

            // 大小显示
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("大小:", fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Text("${config.splitMb}MB", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    autoProcess: Boolean,
    onAutoProcessChange: (Boolean) -> Unit,
    autoOpenOutputDir: Boolean,
    onAutoOpenOutputDirChange: (Boolean) -> Unit,
    autoOpenTextFile: Boolean,
    onAutoOpenTextFileChange: (Boolean) -> Unit,
    separateXmlOutput: Boolean,
    onSeparateXmlOutputChange: (Boolean) -> Unit,
    codeExtras: String,
    onCodeExtrasChange: (String) -> Unit,
    xmlSubdirs: Set<String>,
    onXmlSubdirsChange: (Set<String>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val availableDirs = listOf("layout", "navigation", "xml", "drawable", "mipmap", "values", "anim", "color")
    var localXmlSubdirs by remember { mutableStateOf(xmlSubdirs) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoProcess, onCheckedChange = onAutoProcessChange)
                    Text("自动处理")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoOpenOutputDir, onCheckedChange = onAutoOpenOutputDirChange)
                    Text("自动打开输出目录")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoOpenTextFile, onCheckedChange = onAutoOpenTextFileChange)
                    Text("自动打开文本文件")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = separateXmlOutput, onCheckedChange = onSeparateXmlOutputChange)
                    Text("XML单独输出文件")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("(生成两个文件，XML内容独立)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Divider()
                Text("额外代码扩展名", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = codeExtras,
                    onValueChange = onCodeExtrasChange,
                    placeholder = { Text("例如: .kt,.java") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Divider()
                Text("XML 智能模式子目录", style = MaterialTheme.typography.labelLarge)
                availableDirs.forEach { dir ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = dir in localXmlSubdirs,
                            onCheckedChange = { checked ->
                                localXmlSubdirs = if (checked) localXmlSubdirs + dir else localXmlSubdirs - dir
                                onXmlSubdirsChange(localXmlSubdirs)
                            }
                        )
                        Text(dir)
                    }
                }
                Divider()
                TextButton(onClick = onReset) { Text("重置为默认") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
