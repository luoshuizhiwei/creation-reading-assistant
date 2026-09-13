package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.feature.library.LibrarySourceRef
import com.creationreadingassistant.feature.library.RecognitionDecision
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 目录浏览页的纯判定与格式化规则。
 *
 * 这些规则直接决定用户看到什么（哪个候选被默认勾选、哪一行被标成已入架/疑似/更新），
 * 所以与 Compose、DataStore、ContentResolver、SAF URI 解耦：只吃基本类型与
 * [LibrarySourceRef] 快照，单独由 JVM 测试覆盖。UI 与 ViewModel 共用同一份实现，
 * 避免「页面显示的档位」和「测试断言的档位」各写一套。
 */
object LibraryBrowserPolicy {

    /** 去掉扩展名后的文件名主体；无扩展名时返回原名。 */
    fun baseName(fileName: String): String = fileName.substringBeforeLast('.').trim()

    /** 与来源引用里 format 列同一口径的扩展名归一化（epub / txt / md）。 */
    fun formatOf(fileName: String): String? = when (val ext = fileName.substringAfterLast('.', "").lowercase()) {
        "epub" -> "epub"
        "txt" -> "txt"
        "md", "markdown" -> "md"
        else -> null
    }

    /**
     * 分层重复/更新判定（方案 §5.6，自上而下取最强证据）：
     *
     * ① `authority + documentId` 与来源引用一致 → 精确已在书架；
     *    若同位置的大小/修改时间相对导入基线变化 → 「内容有更新」；
     * ② 候选指纹一致（>32 MiB 文件的大小 + 首尾分块哈希）→ 同内容（改名/移动）；
     * ③ 格式 + 文件名 + 大小一致 → 只能标「疑似」，不是事实；
     * ④ 以上都不中，但文件名主体与书架标题一致 → 最弱的「疑似（按名称）」。
     *
     * 弱判定（③④）不得作为跳过导入的依据；最终去重仍由 `ShelfImporter` 的
     * 内容哈希 / 候选指纹在导入时裁决。
     */
    fun shelfMatch(
        authority: String?,
        documentId: String?,
        displayName: String,
        format: String?,
        sizeBytes: Long?,
        lastModifiedMillis: Long?,
        fingerprint: String?,
        refs: List<LibrarySourceRef>,
        liveBookIds: Set<String>,
        shelfTitles: Set<String>,
    ): LibraryShelfMatch? {
        var best: LibraryShelfMatch? = null
        for (ref in refs) {
            if (ref.bookId !in liveBookIds) continue
            val match = matchRef(ref, authority, documentId, displayName, format, sizeBytes, lastModifiedMillis, fingerprint)
            if (match != null && (best == null || match.kind.priority < best.kind.priority)) {
                best = match
                if (best.kind.priority == 0) return best
            }
        }
        if (best == null && isWeakDuplicate(displayName, shelfTitles)) {
            best = LibraryShelfMatch(LibraryShelfMatchKind.NAME_ONLY, null)
        }
        return best
    }

    private fun matchRef(
        ref: LibrarySourceRef,
        authority: String?,
        documentId: String?,
        displayName: String,
        format: String?,
        sizeBytes: Long?,
        lastModifiedMillis: Long?,
        fingerprint: String?,
    ): LibraryShelfMatch? {
        // ①/④ 同一来源位置：以导入时基线比较大小与修改时间。
        if (authority != null && documentId != null &&
            ref.providerAuthority == authority &&
            ref.documentId == documentId
        ) {
            val sizeChanged = sizeBytes != null && sizeBytes != ref.sizeBytes
            val timeChanged = lastModifiedMillis != null && ref.lastModifiedMillis != null &&
                lastModifiedMillis != ref.lastModifiedMillis
            return if (sizeChanged || timeChanged) {
                LibraryShelfMatch(LibraryShelfMatchKind.CONTENT_UPDATED, ref.bookId)
            } else {
                LibraryShelfMatch(LibraryShelfMatchKind.EXACT_SOURCE, ref.bookId)
            }
        }
        // ② 同内容：候选指纹一致（仅 >32 MiB 文件存在该证据）。
        if (fingerprint != null && ref.candidateFingerprint != null && ref.candidateFingerprint == fingerprint) {
            return LibraryShelfMatch(LibraryShelfMatchKind.SAME_CONTENT, ref.bookId)
        }
        // ③ 格式 + 文件名 + 大小一致：只能给「疑似」。
        if (format != null && sizeBytes != null &&
            ref.format == format &&
            ref.displayName.equals(displayName, ignoreCase = true) &&
            ref.sizeBytes == sizeBytes
        ) {
            return LibraryShelfMatch(LibraryShelfMatchKind.POSSIBLE, null)
        }
        return null
    }

    /**
     * 弱重复判定：仅按文件名主体与书架标题比对（忽略大小写）。
     *
     * 这是分层判定里最弱的一级（④）——不同目录下的同名文件、改名后的同一本书都会误判。
     * 调用方只能把它呈现为「疑似」，不得据此跳过导入。
     */
    fun isWeakDuplicate(fileName: String, shelfTitles: Set<String>): Boolean {
        val base = baseName(fileName).lowercase()
        return base.isNotEmpty() && base in shelfTitles
    }

    /** 识别结果分档（方案 6.3 的四个页签）。 */
    fun matchesFilter(
        decision: RecognitionDecision,
        shelfMatch: LibraryShelfMatch?,
        filter: RecognitionFilter,
    ): Boolean = when (filter) {
        RecognitionFilter.ALL -> true
        // 「推荐」页签只展示真正的新书：有任何入架证据（确认或疑似）都归到「已入架」。
        RecognitionFilter.RECOMMENDED -> decision == RecognitionDecision.RECOMMENDED && shelfMatch == null
        // 「已入架」只承接可读候选；未识别的文件即使撞名也不属于这一档，否则会诱导用户重复导入。
        RecognitionFilter.IN_SHELF -> shelfMatch != null && decision != RecognitionDecision.REJECTED
        RecognitionFilter.REJECTED -> decision == RecognitionDecision.REJECTED
    }

    /**
     * 默认勾选口径：只有「推荐且没有任何入架证据」默认勾选。
     *
     * 已入架/疑似入架单独作为一档并由用户决定，避免默认就替用户重复导入。
     */
    fun selectedByDefault(decision: RecognitionDecision, shelfMatch: LibraryShelfMatch?): Boolean =
        decision == RecognitionDecision.RECOMMENDED && shelfMatch == null

    fun formatSize(bytes: Long?): String {
        if (bytes == null || bytes < 0) return "大小未知"
        if (bytes < 1_024) return "$bytes B"
        val kb = bytes / 1_024.0
        if (kb < 1_024) return String.format(Locale.CHINA, "%.1f KB", kb)
        val mb = kb / 1_024.0
        if (mb < 1_024) return String.format(Locale.CHINA, "%.1f MB", mb)
        return String.format(Locale.CHINA, "%.2f GB", mb / 1_024.0)
    }

    fun formatTime(millis: Long?): String {
        if (millis == null || millis <= 0) return "时间未知"
        return SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(millis))
    }
}
