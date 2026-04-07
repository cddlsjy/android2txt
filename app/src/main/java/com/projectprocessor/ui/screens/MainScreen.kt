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
import com.projectprocessor.ui.components.CodeHighlighter
import com.projectprocessor.ui.theme.*
import com.projectprocessor.viewmodel.MainViewModel
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
    val fileTreeRoot by viewModel.fileTreeRoot.collectAsStateWithLifecycle()
    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()
    val previewContent by viewModel.previewContent.collectAsStateWithLifecycle()
    val isLoadingPreview by viewModel.isLoadingPreview.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isLargeScreen = configuration.screenWidthDp >= 600
    val isSmallScreen = configuration.screenWidthDp < 600
    val useSplitLayout = isLandscape && isLargeScreen

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingStartAfterOutput by remember { mutableStateOf(false) }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            viewModel.updateInputUri(it, false)
            viewModel.loadFileTree(it, false)
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            viewModel.updateInputUri(uri, true)
            viewModel.loadFileTree(uri, true)
        }
    }
    val outputPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            viewModel.updateOutputDirUri(uri)
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
                title = { Text("安卓项目处理器", fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
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
                        when {
                            inputUri == null -> {
                                showInputDialog = true
                            }
                            outputDirUri == null -> {
                                pendingStartAfterOutput = true
                                outputPicker.launch(null)
                            }
                            else -> {
                                viewModel.startProcess()
                            }
                        }
                    }
                },
                icon = {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PlayArrow, null)
                    }
                },
                text = { Text(if (isProcessing) "取消" else "开始处理") }
            )
        }
    ) {
        paddingValues ->
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
                    Button(onClick = { zipPicker.launch(arrayOf("application/zip")); showInputDialog = false }) {
                        Text("ZIP 文件")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { folderPicker.launch(null); showInputDialog = false }) {
                        Text("文件夹")
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
                Text("请先选择 ZIP 或文件夹", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val contentPadding = if (isSmallScreen) 8.dp else 12.dp
    val buttonPadding = if (isSmallScreen) PaddingValues(horizontal = 12.dp, vertical = 8.dp) else PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    
    Card(
        modifier = modifier.fillMaxSize().padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 6.dp else 8.dp)
        ) {
            OutlinedButton(
                onClick = { onShowInputDialogChange(true) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = buttonPadding
            ) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (inputUri != null) {
                        val path = inputUri.lastPathSegment
                        val shortPath = path?.substringAfterLast('/')?.take(15) ?: path
                        "已选: $shortPath"
                    } else "选择输入源",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = if (isSmallScreen) 12.sp else 14.sp
                )
            }
            OutlinedButton(
                onClick = outputPicker,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = buttonPadding
            ) {
                Icon(Icons.Default.SaveAlt, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (outputDirUri != null) {
                        val path = outputDirUri.lastPathSegment
                        val shortPath = path?.substringAfterLast('/')?.take(15) ?: path
                        "输出: $shortPath"
                    } else "选择输出目录",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = if (isSmallScreen) 12.sp else 14.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = config.processCode,
                        onCheckedChange = { viewModel.updateProcessCode(it) },
                        modifier = Modifier.size(18.dp)
                    )
                    Text("代码", fontSize = 11.sp)
                }
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = config.processXml,
                        onCheckedChange = { viewModel.updateProcessXml(it) },
                        modifier = Modifier.size(18.dp)
                    )
                    Text("XML", fontSize = 11.sp)
                }
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = config.processAdvanced,
                        onCheckedChange = { viewModel.updateProcessAdvanced(it) },
                        modifier = Modifier.size(18.dp)
                    )
                    Text("高级", fontSize = 11.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("大小:", fontSize = 11.sp)
                Spacer(Modifier.width(4.dp))
                Text("${config.splitMb}MB", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
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
