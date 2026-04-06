package com.projectprocessor.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.projectprocessor.data.model.FileFolder
import com.projectprocessor.data.model.FileItem
import com.projectprocessor.data.model.FileTreeNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class FileTreeManager(private val context: Context) {

    suspend fun buildTreeFromZip(uri: Uri): FileFolder? = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        val root = FileFolder(name = "root", path = "")
        val pathToNode = mutableMapOf<String, FileFolder>()
        pathToNode[""] = root

        ZipInputStream(inputStream).use { zipStream ->
            var entry: ZipEntry? = zipStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val fullPath = entry.name
                    val parts = fullPath.split("/")
                    val fileName = parts.last()
                    val dirPath = parts.dropLast(1).joinToString("/")

                    var currentPath = ""
                    for (part in parts.dropLast(1)) {
                        val parentPath = currentPath
                        currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
                        var parentNode = pathToNode[currentPath]
                        if (parentNode == null) {
                            val newFolder = FileFolder(name = part, path = currentPath)
                            pathToNode[parentPath]?.children?.add(newFolder)
                            pathToNode[currentPath] = newFolder
                            parentNode = newFolder
                        }
                    }
                    val parentFolder = pathToNode[dirPath] ?: root
                    parentFolder.children.add(
                        FileItem(
                            name = fileName,
                            path = fullPath,
                            zipEntryName = fullPath
                        )
                    )
                }
                entry = zipStream.nextEntry
            }
        }
        inputStream.close()
        return@withContext root
    }

    suspend fun buildTreeFromFolder(uri: Uri): FileFolder? = withContext(Dispatchers.IO) {
        val rootDoc = DocumentFile.fromTreeUri(context, uri) ?: return@withContext null
        fun buildNode(doc: DocumentFile, currentPath: String): FileTreeNode {
            return if (doc.isDirectory) {
                val folder = FileFolder(name = doc.name ?: "unknown", path = currentPath)
                doc.listFiles().forEach { child ->
                    val childPath = if (currentPath.isEmpty()) child.name!! else "$currentPath/${child.name}"
                    folder.children.add(buildNode(child, childPath))
                }
                folder
            } else {
                FileItem(
                    name = doc.name ?: "unknown",
                    path = currentPath,
                    uri = doc.uri
                )
            }
        }
        return@withContext buildNode(rootDoc, "") as? FileFolder
    }

    suspend fun loadContentFromZip(uri: Uri, entryName: String): String? = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        ZipInputStream(inputStream).use { zipStream ->
            var entry: ZipEntry? = zipStream.nextEntry
            while (entry != null) {
                if (entry.name == entryName && !entry.isDirectory) {
                    return@withContext String(zipStream.readBytes(), Charsets.UTF_8)
                }
                entry = zipStream.nextEntry
            }
        }
        return@withContext null
    }

    suspend fun loadContentFromFolder(rootUri: Uri, fileItem: FileItem): String? = withContext(Dispatchers.IO) {
        val uri = fileItem.uri ?: return@withContext null
        return@withContext try {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.readText()
        } catch (e: Exception) {
            null
        }
    }
}
