/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package local.creationReadingAssistant.reader.legado.text

data class TextLine(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val indentPx: Float = 0f,
    val paragraphEnd: Boolean = false,
)

data class TextPage(
    val startOffset: Int,
    val endOffset: Int,
    val lines: List<TextLine>,
) {
    val isEmpty: Boolean get() = lines.isEmpty()
}
