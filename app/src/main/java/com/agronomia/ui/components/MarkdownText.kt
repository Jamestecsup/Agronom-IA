package com.agronomia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Tipos de bloque que sabemos pintar del Markdown que devuelve Gemini.
 */
private enum class MdKind { PARAGRAPH, HEADING, BULLET, NUMBER, RULE }

/**
 * Bloque ya interpretado: tipo, texto sin marcas de bloque y, en listas
 * numeradas, el número original.
 */
private data class MdBlock(
    val kind: MdKind,
    val text: String,
    val marker: String = ""
)

/**
 * Pinta el texto del chat con un subconjunto de Markdown: títulos (#),
 * viñetas (-, *, •), listas numeradas, separadores, negrita (**), cursiva (*)
 * y código (`). Sin esto, el chat mostraría los símbolos en crudo.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified
) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEach { block ->
            when (block.kind) {
                MdKind.HEADING -> Text(
                    text = inlineMarkdown(block.text),
                    style = style.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = style.fontSize * 1.08f
                    ),
                    color = color
                )

                MdKind.BULLET -> Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "•",
                        style = style,
                        color = color,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(text = inlineMarkdown(block.text), style = style, color = color)
                }

                MdKind.NUMBER -> Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "${block.marker}.",
                        style = style.copy(fontWeight = FontWeight.Bold),
                        color = color,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(text = inlineMarkdown(block.text), style = style, color = color)
                }

                MdKind.RULE -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .height(1.dp)
                        .background(color.copy(alpha = 0.25f))
                )

                MdKind.PARAGRAPH -> Text(
                    text = inlineMarkdown(block.text),
                    style = style,
                    color = color
                )
            }
        }
    }
}

/**
 * Convierte el Markdown en bloques. Las líneas seguidas sin marca se unen en
 * un solo párrafo para que el texto fluya al ajustarse al ancho de la burbuja.
 */
private fun parseMarkdown(raw: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks.add(MdBlock(MdKind.PARAGRAPH, paragraph.toString().trim()))
            paragraph.clear()
        }
    }

    raw.lines().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> flushParagraph()

            trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                flushParagraph()
                blocks.add(MdBlock(MdKind.RULE, ""))
            }

            trimmed.startsWith("#") -> {
                flushParagraph()
                blocks.add(MdBlock(MdKind.HEADING, trimmed.trimStart('#').trim()))
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                flushParagraph()
                blocks.add(MdBlock(MdKind.BULLET, trimmed.substring(2).trim()))
            }

            NUMBERED_LINE.matches(trimmed) -> {
                flushParagraph()
                val match = NUMBERED_LINE.find(trimmed)!!
                blocks.add(
                    MdBlock(
                        MdKind.NUMBER,
                        match.groupValues[2].trim(),
                        match.groupValues[1]
                    )
                )
            }

            trimmed.startsWith("> ") -> {
                flushParagraph()
                blocks.add(MdBlock(MdKind.PARAGRAPH, trimmed.substring(2).trim()))
            }

            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
    }
    flushParagraph()
    return blocks
}

private val NUMBERED_LINE = Regex("^(\\d+)[.)]\\s+(.*)$")

/**
 * Aplica negrita, cursiva y código dentro de un bloque, dejando el resto del
 * texto tal cual.
 */
private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", index + 2)
                if (end > index + 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(index + 2, end))
                    }
                    index = end + 2
                } else {
                    append(text[index])
                    index++
                }
            }

            text.startsWith("__", index) -> {
                val end = text.indexOf("__", index + 2)
                if (end > index + 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(index + 2, end))
                    }
                    index = end + 2
                } else {
                    append(text[index])
                    index++
                }
            }

            text[index] == '*' -> {
                val end = text.indexOf('*', index + 1)
                if (end > index) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(text[index])
                    index++
                }
            }

            text[index] == '`' -> {
                val end = text.indexOf('`', index + 1)
                if (end > index) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(text[index])
                    index++
                }
            }

            else -> {
                append(text[index])
                index++
            }
        }
    }
}
