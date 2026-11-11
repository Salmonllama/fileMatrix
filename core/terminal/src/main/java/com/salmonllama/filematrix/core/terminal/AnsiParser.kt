package com.salmonllama.filematrix.core.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

object AnsiParser {

    private val ANSI_REGEX = Regex("\u001B\\[([0-9;]*)m")

    private val StandardColors = arrayOf(
        Color(0xFF000000), // 0: Black
        Color(0xFFFF5252), // 1: Red
        Color(0xFF00E676), // 2: Green
        Color(0xFFFFD600), // 3: Yellow
        Color(0xFF448AFF), // 4: Blue
        Color(0xFFE040FB), // 5: Magenta
        Color(0xFF00E5FF), // 6: Cyan
        Color(0xFFECEFF1)  // 7: White
    )

    private val BrightColors = arrayOf(
        Color(0xFF616161), // 8: Bright Black (Gray)
        Color(0xFFFF8A80), // 9: Bright Red
        Color(0xFFB9F6CA), // 10: Bright Green
        Color(0xFFFFFF8D), // 11: Bright Yellow
        Color(0xFF82B1FF), // 12: Bright Blue
        Color(0xFFEA80FC), // 13: Bright Magenta
        Color(0xFF84FFFF), // 14: Bright Cyan
        Color(0xFFFFFFFF)  // 15: Bright White
    )

    fun parse(rawText: String, defaultColor: Color = Color(0xFFC9D1D9)): AnnotatedString {
        return buildAnnotatedString {
            var currentIndex = 0
            var currentColor: Color = defaultColor
            var isBold = false

            val matches = ANSI_REGEX.findAll(rawText).toList()
            for (match in matches) {
                val textChunk = rawText.substring(currentIndex, match.range.first)
                if (textChunk.isNotEmpty()) {
                    val style = SpanStyle(
                        color = currentColor,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                    )
                    append(AnnotatedString(textChunk, listOf(AnnotatedString.Range(style, 0, textChunk.length))))
                }

                // Process ANSI codes
                val codes = match.groupValues[1].split(';').mapNotNull { it.toIntOrNull() }
                if (codes.isEmpty() || codes.contains(0)) {
                    currentColor = defaultColor
                    isBold = false
                }
                for (code in codes) {
                    when (code) {
                        0 -> {
                            currentColor = defaultColor
                            isBold = false
                        }
                        1 -> isBold = true
                        in 30..37 -> currentColor = StandardColors[code - 30]
                        39 -> currentColor = defaultColor
                        in 90..97 -> currentColor = BrightColors[code - 90]
                    }
                }

                currentIndex = match.range.last + 1
            }

            if (currentIndex < rawText.length) {
                val remaining = rawText.substring(currentIndex)
                val style = SpanStyle(
                    color = currentColor,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                )
                append(AnnotatedString(remaining, listOf(AnnotatedString.Range(style, 0, remaining.length))))
            }
        }
    }
}
