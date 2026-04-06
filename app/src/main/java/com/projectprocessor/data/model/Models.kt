package com.projectprocessor.data.model

object FileExtensions {
    val DEFAULT_CODE_EXTENSIONS = setOf(
        ".java", ".kt", ".kts", ".ets", ".c", ".h", ".cpp", ".cc", ".cxx", ".hpp", ".hxx",
        ".py", ".js", ".ts", ".go", ".rs", ".swift", ".rb", ".php", ".sql", ".dart"
    )
    val XML_EXTENSIONS = setOf(".xml")
}

enum class XmlMode(val displayName: String, val description: String) {
    SMART("智能", "仅处理用户选择的 res 子目录下的 XML"),
    ALL("全部", "处理项目中所有 .xml 文件")
}

enum class InputType(val displayName: String) {
    ZIP("ZIP 文件"),
    FOLDER("文件夹")
}

data class ProcessConfig(
    val inputType: InputType = InputType.ZIP,
    val inputPath: String = "",
    val outputDir: String = "",
    val splitMb: Int = 10,
    val processCode: Boolean = true,
    val processAdvanced: Boolean = true,
    val processXml: Boolean = true,
    val xmlMode: XmlMode = XmlMode.SMART,
    val xmlSubdirs: Set<String> = setOf("layout", "navigation", "xml"),
    val codeExtras: String = "",
    val customExts: String = "",
    val autoProcess: Boolean = false,
    val autoOpenOutputDir: Boolean = false,
    val autoOpenTextFile: Boolean = false,
    val separateXmlOutput: Boolean = false
)

data class ProcessStats(
    val codeCount: Int = 0,
    val advancedCount: Int = 0,
    val xmlCount: Int = 0,
    val customCount: Int = 0
)

sealed class ProcessState {
    object Idle : ProcessState()
    object Processing : ProcessState()
    data class Success(val outputPath: String, val stats: ProcessStats) : ProcessState()
    data class Error(val message: String) : ProcessState()
}

data class LogEntry(
    val timestamp: Long,
    val message: String,
    val type: LogType = LogType.INFO
)

enum class LogType {
    INFO, WARNING, ERROR, SUCCESS
}

sealed class FileTreeNode {
    abstract val name: String
    abstract val path: String
}

class FileFolder(
    override val name: String,
    override val path: String,
    val children: MutableList<FileTreeNode> = mutableListOf(),
    var isExpanded: Boolean = false
) : FileTreeNode()

class FileItem(
    override val name: String,
    override val path: String,
    val zipEntryName: String? = null,
    val uri: Uri? = null,
    var isProcessed: Boolean = false
) : FileTreeNode()
