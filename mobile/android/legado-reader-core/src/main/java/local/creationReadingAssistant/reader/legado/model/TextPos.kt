/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Adapted from Luoyacheng/legado-E at 21855a7bf901becfd1caba5cf30a3c84fd1533e1.
 */
package local.creationReadingAssistant.reader.legado.model

import androidx.annotation.Keep

@Keep
data class TextPos(
    var relativePagePos: Int,
    var lineIndex: Int,
    var columnIndex: Int,
) {
    fun update(relativePos: Int, lineIndex: Int, charIndex: Int) {
        relativePagePos = relativePos
        this.lineIndex = lineIndex
        columnIndex = charIndex
    }

    fun update(pos: TextPos) = update(pos.relativePagePos, pos.lineIndex, pos.columnIndex)

    fun compare(pos: TextPos): Int = compare(pos.relativePagePos, pos.lineIndex, pos.columnIndex)

    fun compare(relativePos: Int, lineIndex: Int, charIndex: Int): Int = when {
        relativePagePos < relativePos -> -3
        relativePagePos > relativePos -> 3
        this.lineIndex < lineIndex -> -2
        this.lineIndex > lineIndex -> 2
        columnIndex < charIndex -> -1
        columnIndex > charIndex -> 1
        else -> 0
    }

    fun reset() {
        relativePagePos = 0
        lineIndex = -1
        columnIndex = -1
    }

    fun isSelected(): Boolean = lineIndex >= 0 && columnIndex >= 0
}
