package com.projectprocessor.util

import android.content.ContentResolver
import android.net.Uri
import java.io.OutputStreamWriter

class SplitFileWriter(
    private val contentResolver: ContentResolver,
    private val outputFileUri: Uri,
    private val maxBytes: Long,
    private val encoding: String = "UTF-8"
) {
    private var part = 1
    private var currentWriter: OutputStreamWriter? = null
    private var currentSize = 0L
    private var currentUri: Uri = outputFileUri

    init {
        openNewFile()
    }

    private fun openNewFile() {
        close()
        if (part > 1) {
            val base = outputFileUri.toString()
            val newUri = Uri.parse(base.replace(".txt", "_part$part.txt"))
            currentUri = newUri
        }
        currentWriter = OutputStreamWriter(contentResolver.openOutputStream(currentUri)!!, encoding)
        currentSize = 0
    }

    private fun checkRotate(additionalBytes: Int) {
        if (maxBytes > 0 && currentSize + additionalBytes > maxBytes) {
            part++
            openNewFile()
        }
    }

    fun write(data: String) {
        val bytes = data.toByteArray(Charsets.UTF_8).size
        checkRotate(bytes)
        currentWriter?.write(data)
        currentWriter?.flush()
        currentSize += bytes
    }

    fun close() {
        currentWriter?.close()
        currentWriter = null
    }
}