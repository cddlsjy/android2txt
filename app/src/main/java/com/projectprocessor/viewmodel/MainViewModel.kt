package com.projectprocessor.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectprocessor.data.model.*
import com.projectprocessor.data.repository.PreferencesRepository
import com.projectprocessor.util.FileProcessor
import com.projectprocessor.util.FileTreeManager
import com.projectprocessor.util.SplitFileWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

class MainViewModel(private val context: Context) : ViewModel() {

    private val preferencesRepo = PreferencesRepository(context)
    private val fileTreeManager = FileTreeManager(context)

    // 配置
    private val _config = MutableStateFlow(ProcessConfig())
    val config = _config.asStateFlow()

    // 输入/输出
    private val _inputUri = MutableStateFlow<Uri?>(null)
    val inputUri = _inputUri.asStateFlow()

    private val _outputDirUri = MutableStateFlow<Uri?>(null)
    val outputDirUri = _outputDirUri.asStateFlow()

    // 处理状态
    private val _processState = MutableStateFlow<ProcessState>(ProcessState.Idle)
    val processState = _processState.asStateFlow()

    // 文件树
    private val _fileTreeRoot = MutableStateFlow<FileFolder?>(null)
    val fileTreeRoot = _fileTreeRoot.asStateFlow()

    private val _selectedFile = MutableStateFlow<FileItem?>(null)
    val selectedFile = _selectedFile.asStateFlow()

    private val _previewContent = MutableStateFlow("")
    val previewContent = _previewContent.asStateFlow()

    private val _isLoadingPreview = MutableStateFlow(false)
    val isLoadingPreview = _isLoadingPreview.asStateFlow()

    // 日志
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    // 取消标志
    private var isCancelled by mutableStateOf(false)

    init {
        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            preferencesRepo.configFlow.collectLatest {
                _config.value = it
            }
        }
    }

    fun updateInputUri(uri: Uri, isDirectory: Boolean) {
        _inputUri.value = uri
        _config.value = _config.value.copy(
            inputType = if (isDirectory) InputType.FOLDER else InputType.ZIP,
            inputPath = uri.toString()
        )
        saveConfig()
    }

    fun updateOutputDirUri(uri: Uri) {
        _outputDirUri.value = uri
        _config.value = _config.value.copy(outputDir = uri.toString())
        saveConfig()
    }

    fun updateSplitMb(value: Int) {
        _config.value = _config.value.copy(splitMb = value)
        saveConfig()
    }

    fun updateProcessCode(value: Boolean) {
        _config.value = _config.value.copy(processCode = value)
        saveConfig()
    }

    fun updateProcessAdvanced(value: Boolean) {
        _config.value = _config.value.copy(processAdvanced = value)
        saveConfig()
    }

    fun updateProcessXml(value: Boolean) {
        _config.value = _config.value.copy(processXml = value)
        saveConfig()
    }

    fun updateXmlMode(mode: XmlMode) {
        _config.value = _config.value.copy(xmlMode = mode)
        saveConfig()
    }

    fun updateXmlSubdirs(value: Set<String>) {
        _config.value = _config.value.copy(xmlSubdirs = value)
        saveConfig()
    }

    fun updateCodeExtras(value: String) {
        _config.value = _config.value.copy(codeExtras = value)
        saveConfig()
    }

    fun updateCustomExts(value: String) {
        _config.value = _config.value.copy(customExts = value)
        saveConfig()
    }

    fun updateAutoProcess(value: Boolean) {
        _config.value = _config.value.copy(autoProcess = value)
        saveConfig()
    }

    fun updateAutoOpenOutputDir(value: Boolean) {
        _config.value = _config.value.copy(autoOpenOutputDir = value)
        saveConfig()
    }

    fun updateAutoOpenTextFile(value: Boolean) {
        _config.value = _config.value.copy(autoOpenTextFile = value)
        saveConfig()
    }

    fun updateSeparateXmlOutput(value: Boolean) {
        _config.value = _config.value.copy(separateXmlOutput = value)
        saveConfig()
    }

    fun resetToDefault() {
        _config.value = ProcessConfig()
        saveConfig()
    }

    private fun saveConfig() {
        viewModelScope.launch {
            preferencesRepo.saveConfig(_config.value)
        }
    }

    fun loadFileTree(uri: Uri, isDirectory: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val root = if (isDirectory) {
                    fileTreeManager.buildTreeFromFolder(uri)
                } else {
                    fileTreeManager.buildTreeFromZip(uri)
                }
                _fileTreeRoot.value = root
            } catch (e: Exception) {
                addLog("加载文件树失败: ${e.message}", LogType.ERROR)
            }
        }
    }

    fun selectFile(file: FileItem) {
        _selectedFile.value = file
        loadPreviewContent(file)
    }

    fun toggleFolder(folder: FileFolder) {
        folder.isExpanded = !folder.isExpanded
        _fileTreeRoot.value = _fileTreeRoot.value // 触发重组
    }

    private fun loadPreviewContent(file: FileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingPreview.value = true
            try {
                val content = when (_config.value.inputType) {
                    InputType.ZIP -> {
                        _inputUri.value?.let { uri ->
                            file.zipEntryName?.let { entryName ->
                                fileTreeManager.loadContentFromZip(uri, entryName)
                            }
                        }
                    }
                    InputType.FOLDER -> {
                        _inputUri.value?.let { uri ->
                            fileTreeManager.loadContentFromFolder(uri, file)
                        }
                    }
                }
                _previewContent.value = content ?: "[无法读取文件]"
            } catch (e: Exception) {
                _previewContent.value = "[读取错误: ${e.message}]"
            } finally {
                _isLoadingPreview.value = false
            }
        }
    }

    fun startProcess() {
        val input = _inputUri.value ?: return
        val output = _outputDirUri.value ?: return

        isCancelled = false
        _processState.value = ProcessState.Processing

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val processor = FileProcessor(context)
                val result = processor.process(
                    config = _config.value,
                    inputUri = input,
                    outputDirUri = output,
                    callback = object : FileProcessor.ProgressCallback {
                        override fun onLog(message: String, type: LogType) {
                            addLog(message, type)
                        }

                        override fun onProgress(current: Int, total: Int) {
                            // 可以在这里添加进度更新
                        }

                        override fun isCancelled(): Boolean = isCancelled

                        override fun onFileProcessed(path: String) {
                            markFileProcessed(path)
                        }
                    }
                )

                result.onSuccess { outputPath ->
                    _processState.value = ProcessState.Success(
                        outputPath = outputPath,
                        stats = ProcessStats() // 实际应该从处理器获取
                    )

                    // 自动打开输出目录
                    if (_config.value.autoOpenOutputDir) {
                        openOutputFolder()
                    }

                    // 自动打开文本文件
                    if (_config.value.autoOpenTextFile) {
                        openTextFile(outputPath)
                    }
                }.onFailure { e ->
                    _processState.value = ProcessState.Error(e.message ?: "处理失败")
                    addLog("处理失败: ${e.message}", LogType.ERROR)
                }
            } catch (e: Exception) {
                _processState.value = ProcessState.Error(e.message ?: "处理失败")
                addLog("处理异常: ${e.message}", LogType.ERROR)
            }
        }
    }

    fun stopProcess() {
        isCancelled = true
        _processState.value = ProcessState.Idle
    }

    private fun markFileProcessed(path: String) {
        viewModelScope.launch(Dispatchers.Main) {
            fun markRecursive(node: FileTreeNode) {
                when (node) {
                    is FileFolder -> node.children.forEach { markRecursive(it) }
                    is FileItem -> if (node.path == path) node.isProcessed = true
                }
            }
            _fileTreeRoot.value?.let { markRecursive(it) }
            _fileTreeRoot.value = _fileTreeRoot.value // 触发重组
        }
    }

    fun openOutputFolder() {
        _outputDirUri.value?.let { uri ->
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, "打开输出目录"))
        }
    }

    private fun openTextFile(filePath: String) {
        val uri = Uri.parse(filePath)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "text/plain")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "打开文本文件"))
    }

    private fun addLog(message: String, type: LogType = LogType.INFO) {
        viewModelScope.launch(Dispatchers.Main) {
            val entry = LogEntry(
                timestamp = System.currentTimeMillis(),
                message = message,
                type = type
            )
            _logs.value = _logs.value + entry
        }
    }
}
