package com.creationreadingassistant.feature.reader.doc

/**
 * A checkpoint recording the exact mapping between a character offset and a byte offset.
 * Recorded at line boundaries during scanning to enable precise byte-level seeking.
 *
 * @param charOffset absolute character offset in the decoded text
 * @param byteOffset absolute byte offset in the raw file
 */
data class CharByteCheckpoint(val charOffset: Long, val byteOffset: Long)

/**
 * Streaming-scanned chapter index for large TXT/Markdown files.
 * Built by [TxtFileScanner] in a single sequential pass.
 * Memory footprint: ~300 KB for a 50 MB / 5000-chapter file.
 *
 * @param chapters ordered list of chapter entries covering the whole file
 * @param totalCharCount total character count across all chapters
 * @param encoding detected text encoding (e.g. "UTF-8", "GB18030")
 * @param detectedRuleId the TOC rule used during scanning
 * @param checkpoints sorted list of char↔byte checkpoints for precise byte seeking;
 *        includes chapter boundaries and intra-chapter checkpoints every MAX_UNIT_CHARS
 */
data class TxtFileIndex(
    val chapters: List<ChapterEntry>,
    val totalCharCount: Long,
    val encoding: String,
    val detectedRuleId: String?,
    val checkpoints: List<CharByteCheckpoint> = emptyList(),
)

/**
 * A single chapter entry within a [TxtFileIndex].
 *
 * @param index zero-based chapter index
 * @param title chapter title (e.g. "第一章 初见", or "全文" if undetected)
 * @param charStart absolute character offset in the full text
 * @param byteStart byte offset in the raw file
 * @param byteLength byte length of this chapter (including title line)
 * @param charCount character count of this chapter
 */
data class ChapterEntry(
    val index: Int,
    val title: String,
    val charStart: Long,
    val byteStart: Long,
    val byteLength: Int,
    val charCount: Long,
)
