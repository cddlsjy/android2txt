package com.projectprocessor.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CodeHighlighter(
    code: String,
    fileExtension: String,
    modifier: Modifier = Modifier
) {
    val lines = code.lines()
    val annotatedLines = lines.map { highlightLine(it, fileExtension) }

    LazyColumn(modifier = modifier) {
        itemsIndexed(annotatedLines) { index, annotatedLine ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${index + 1}",
                    modifier = Modifier.width(40.dp),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Gray
                )
                Text(
                    text = annotatedLine,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun highlightLine(line: String, ext: String): AnnotatedString {
    return buildAnnotatedString {
        val keywords = setOf(
            "package", "import", "public", "private", "protected", "class", "interface",
            "enum", "fun", "var", "val", "if", "else", "when", "for", "while", "do",
            "return", "break", "continue", "true", "false", "null", "object", "data",
            "sealed", "open", "abstract", "override", "final", "companion", "init",
            "constructor", "super", "this", "throw", "try", "catch", "finally"
        )
        val stringRegex = "\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"".toRegex()
        val commentRegex = "//.*".toRegex()
        val annotationRegex = "@\\w+".toRegex()

        val tokens = mutableListOf<Pair<IntRange, SpanStyle>>()
        commentRegex.findAll(line).forEach { match -> tokens.add(match.range to SpanStyle(color = Color.Green)) }
        stringRegex.findAll(line).forEach { match -> tokens.add(match.range to SpanStyle(color = Color(0xFFCE9178))) }
        annotationRegex.findAll(line).forEach { match -> tokens.add(match.range to SpanStyle(color = Color(0xFFC586C0))) }
        keywords.forEach { keyword ->
            Regex("\\b$keyword\\b").findAll(line).forEach { match -> tokens.add(match.range to SpanStyle(color = Color(0xFF569CD6))) }
        }

        val sorted = tokens.sortedBy { it.first.first }
        var current = 0
        for ((range, style) in sorted) {
            if (range.first > current) append(line.substring(current, range.first))
            withStyle(style) { append(line.substring(range)) }
            current = range.last + 1
        }
        if (current < line.length) append(line.substring(current))
    }
}
