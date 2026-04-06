package com.projectprocessor.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.projectprocessor.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream

class FileProcessor(private val context: Context) {

    interface ProgressCallback {
        fun onLog(message: String, type: LogType = LogType.INFO)
        fun onProgress(current: Int, total: Int)
        fun isCancelled(): Boolean
        fun onFileProcessed(path: String)   // 每个文件处理完成后回调
    }

    suspend fun process(
        config: ProcessConfig,
        inputUri: Uri,
        outputDirUri: Uri,
        callback: ProgressCallback
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            callback.onLog("开始处理...")
            val isZip = config.inputType == InputType.ZIP
            val fileEntries = mutableListOf<Pair<String, ByteArray?>>()

            if (isZip) {
                val inputStream = context.contentResolver.openInputStream(inputUri)
                    ?: return@withContext Result.failure(Exception("无法打开输入文件"))
                ZipInputStream(BufferedInputStream(inputStream)).use { zipStream ->
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) fileEntries.add(entry.name to zipStream.readBytes())
                        entry = zipStream.nextEntry
                    }
                }
                inputStream.close()
            } else {
                fun walk(dir: DocumentFile, basePath: String = "") {
                    dir.listFiles().forEach { file ->
                        if (file.isDirectory) walk(file, if (basePath.isEmpty()) file.name!! else "$basePath/${file.name}")
                        else fileEntries.add((if (basePath.isEmpty()) file.name!! else "$basePath/${file.name}") to null)
                    }
                }
                val root = DocumentFile.fromTreeUri(context, inputUri) ?: return@withContext Result.failure(Exception("无效的文件夹 URI"))
                walk(root)
            }

            if (fileEntries.isEmpty()) {
                callback.onLog("输入为空", LogType.WARNING)
                return@withContext Result.failure(Exception("输入为空"))
            }

            val codeExtrasSet = config.codeExtras.split(",").map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }.map { if (it.startsWith(".")) it else ".$it" }.toSet()
            val codeExtensions = FileExtensions.DEFAULT_CODE_EXTENSIONS + codeExtrasSet

            val advancedExtensions = setOf(".gradle", ".properties", ".pro", ".md")
            val advancedFilenames = setOf(".gitignore", "gradlew", "gradlew.bat", "AndroidManifest.xml", "readme.md", "README.md")
            val customExtsSet = parseCustomExtensions(config.customExts)

            val codeFiles = mutableListOf<Pair<String, ByteArray?>>()
            val advancedFiles = mutableListOf<Pair<String, ByteArray?>>()
            val xmlFiles = mutableListOf<Pair<String, ByteArray?>>()
            val customFiles = mutableListOf<Pair<String, ByteArray?>>()

            for ((path, content) in fileEntries) {
                val name = path.substringAfterLast("/")
                val ext = getExtension(path).lowercase()
                when {
                    config.processCode && ext in codeExtensions -> codeFiles.add(path to content)
                    config.processAdvanced && (ext in advancedExtensions || name in advancedFilenames) -> advancedFiles.add(path to content)
                    config.processXml && ext == ".xml" -> {
                        if (shouldIncludeXml(path, config.xmlMode, config.xmlSubdirs)) xmlFiles.add(path to content)
                    }
                    customExtsSet.contains(ext) -> customFiles.add(path to content)
                }
            }

            val allFilesCount = codeFiles.size + advancedFiles.size + xmlFiles.size + customFiles.size
            if (allFilesCount == 0) {
                callback.onLog("没有符合条件的文件", LogType.WARNING)
                return@withContext Result.failure(Exception("没有符合条件的文件"))
            }

            val stats = ProcessStats(
                codeCount = codeFiles.size,
                advancedCount = advancedFiles.size,
                xmlCount = xmlFiles.size,
                customCount = customFiles.size
            )
            callback.onLog("扫描完成: 代码${stats.codeCount}, 高级${stats.advancedCount}, XML${stats.xmlCount}, 自定义${stats.customCount}")

            val treeStr = buildTree(fileEntries.map { it.first })
            val outputDir = DocumentFile.fromTreeUri(context, outputDirUri)
                ?: return@withContext Result.failure(Exception("输出目录无效"))
            val baseName = getBaseName(inputUri)

            val mainOutputFile = outputDir.createFile("text/plain", "${baseName}-AI.txt")
                ?: return@withContext Result.failure(Exception("无法创建主输出文件"))
            val mainWriter = SplitFileWriter(context.contentResolver, mainOutputFile.uri, config.splitMb * 1024L * 1024L)

            var xmlWriter: SplitFileWriter? = null
            var xmlOutputFile: DocumentFile? = null
            if (config.separateXmlOutput && config.processXml && xmlFiles.isNotEmpty()) {
                xmlOutputFile = outputDir.createFile("text/plain", "${baseName}-AI-xml.txt")
                    ?: return@withContext Result.failure(Exception("无法创建 XML 输出文件"))
                xmlWriter = SplitFileWriter(context.contentResolver, xmlOutputFile.uri, config.splitMb * 1024L * 1024L)
            }

            mainWriter.write("=" .repeat(80) + "\n")
            mainWriter.write("项目目录树\n")
            mainWriter.write("=" .repeat(80) + "\n\n")
            mainWriter.write(treeStr)
            mainWriter.write("\n\n")

            writeSection(mainWriter, "代码文件内容", codeFiles, isZip, inputUri, callback)
            writeSection(mainWriter, "高级模式文件内容", advancedFiles, isZip, inputUri, callback)

            if (xmlFiles.isNotEmpty()) {
                if (config.separateXmlOutput && xmlWriter != null) {
                    writeSection(xmlWriter, "XML文件内容", xmlFiles, isZip, inputUri, callback)
                } else {
                    writeSection(mainWriter, "XML文件内容", xmlFiles, isZip, inputUri, callback)
                }
            }

            writeSection(mainWriter, "自定义文件内容", customFiles, isZip, inputUri, callback)

            mainWriter.close()
            xmlWriter?.close()

            callback.onLog("处理完成！", LogType.SUCCESS)
            if (xmlWriter != null) {
                callback.onLog("主文件: ${mainOutputFile.uri.lastPathSegment}", LogType.INFO)
                callback.onLog("XML文件: ${xmlOutputFile?.uri?.lastPathSegment}", LogType.INFO)
            } else {
                callback.onLog("输出文件: ${mainOutputFile.uri.lastPathSegment}", LogType.SUCCESS)
            }

            Result.success(mainOutputFile.uri.toString())
        } catch (e: Exception) {
            callback.onLog("错误: ${e.message}", LogType.ERROR)
            Result.failure(e)
        }
    }

    private suspend fun writeSection(
        writer: SplitFileWriter,
        title: String,
        files: List<Pair<String, ByteArray?>>,
        isZip: Boolean,
        inputUri: Uri,
        callback: ProgressCallback
    ) {
        if (files.isEmpty()) return
        writer.write("=" .repeat(80) + "\n")
        writer.write("$title\n")
        writer.write("=" .repeat(80) + "\n\n")
        for (i in files.indices) {
            val (path, cachedContent) = files[i]
            if (callback.isCancelled()) break
            callback.onProgress(i + 1, files.size)
            callback.onLog("处理: $path")
            val ext = getExtension(path)
            val lang = if (ext.isNotEmpty()) ext.substring(1) else "text"
            writer.write("## 文件: $path\n")
            writer.write("```$lang\n")
            val content = if (isZip && cachedContent != null) {
                String(cachedContent, Charsets.UTF_8)
            } else if (!isZip) {
                readFileFromDocument(inputUri, path)
            } else {
                "[无法读取]"
            }
            writer.write(content)
            if (!content.endsWith("\n")) writer.write("\n")
            writer.write("```\n\n")

            // 回调，用于 UI 标记已处理
            callback.onFileProcessed(path)
        }
    }

    private fun readFileFromDocument(rootUri: Uri, relativePath: String): String {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return "[无法打开目录]"
        val parts = relativePath.split("/")
        var current = root
        for (part in parts.dropLast(1)) {
            current = current.findFile(part) ?: return "[路径不存在: $part]"
        }
        val file = current.findFile(parts.last()) ?: return "[文件不存在]"
        return try {
            context.contentResolver.openInputStream(file.uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            context.contentResolver.openInputStream(file.uri)!!.bufferedReader(Charsets.ISO_8859_1).use { it.readText() }
        } catch (e: Exception) {
            "[读取错误: ${e.message}]"
        }
    }

    private fun parseCustomExtensions(customExts: String): Set<String> {
        if (customExts.isBlank()) return emptySet()
        return customExts.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .map { if (it.startsWith(".")) it else ".$it" }
            .toSet()
    }

    private fun getExtension(path: String): String {
        val lastDot = path.lastIndexOf('.')
        val lastSep = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
        return if (lastDot > lastSep) path.substring(lastDot) else ""
    }

    private fun shouldIncludeXml(path: String, mode: XmlMode, subdirs: Set<String>): Boolean {
        val pathLower = path.lowercase()
        return when (mode) {
            XmlMode.SMART -> {
                pathLower.endsWith("androidmanifest.xml") ||
                subdirs.any { pathLower.contains("/res/$it/") }
            }
            XmlMode.ALL -> true
        }
    }

    private fun buildTree(paths: List<String>): String {
        if (paths.isEmpty()) return "(empty)"
        val root = mutableMapOf<String, Any>()
        for (path in paths) {
            val parts = path.split("/", "\\")
            var node = root
            for (part in parts) {
                val child = node.getOrPut(part) { mutableMapOf<String, Any>() }
                node = child as MutableMap<String, Any>
            }
        }
        val lines = mutableListOf<String>()
        fun walk(node: Map<String, Any>, indent: String = "") {
            node.keys.sorted().forEachIndexed { i, name ->
                val isLast = i == node.size - 1
                lines.add("$indent${if (isLast) "└── " else "├── "}$name")
                (node[name] as? Map<String, Any>)?.let { walk(it, indent + if (isLast) "    " else "│   ") }
            }
        }
        walk(root)
        return lines.joinToString("\n")
    }

    private fun getBaseName(uri: Uri): String {
        val path = uri.lastPathSegment ?: "project"
        return path.substringAfterLast("/").substringBeforeLast(".").ifEmpty { "project" }
    }
}
