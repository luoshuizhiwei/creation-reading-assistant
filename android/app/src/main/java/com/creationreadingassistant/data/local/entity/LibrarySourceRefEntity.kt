package com.creationreadingassistant.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 来源可观测状态。`available` 表示最近一次观测仍能看到该来源；`missing` 表示在一次
 * 完整（未截断）的授权目录扫描中，同根来源未再出现。它只描述来源，不影响阅读：
 * 正文事实源始终是 `books` / 内部稳定副本。
 */
enum class LibrarySourceAvailability(val storageValue: String) {
    AVAILABLE("available"),
    MISSING("missing");

    companion object {
        fun fromStorage(value: String?): LibrarySourceAvailability =
            entries.firstOrNull { it.storageValue == value } ?: AVAILABLE
    }
}

/**
 * 书籍与其来源文件的引用关系（路线 §4.5 `library_source_refs`）。
 *
 * 这张表不是正文事实源：来源不可用时阅读器仍只依赖内部稳定副本。它的用途是
 * 精确标记已入架、识别改名/移动、发现内容更新与来源失效，为目录页的分层判定
 * （方案 §5.6）提供比较基线。
 *
 * 主键是 `book_id`（一书一条，重复导入同一本书会 upsert 覆盖为最新来源）；
 * `size` / `last_modified` / `content_hash` 固定为导入时的观测值，作为「内容有更新」
 * 的比较基线，日常观测刷新只更新 `last_seen_at` / `availability`，不回写这三列。
 */
@Entity(
    tableName = "library_source_refs",
    indices = [
        Index(value = ["provider_authority", "document_id"]),
        Index(value = ["content_hash"]),
        Index(value = ["candidate_fingerprint"]),
        Index(value = ["root_id"]),
    ],
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["book_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class LibrarySourceRefEntity(
    @PrimaryKey val book_id: String,
    /** 导入时来源所处的授权根目录（tree document id）。来自系统选择器且无法证明归属时为 null，绝不伪造。 */
    val root_id: String?,
    val provider_authority: String,
    /** 非文档型 URI（无法经 DocumentsContract 解析）时为 null，此时只能靠内容哈希/指纹比对。 */
    val document_id: String?,
    val display_name: String,
    val format: String,
    val size: Long,
    val last_modified: Long?,
    val content_hash: String?,
    /** 超大文件（>32 MiB）的「大小 + 首尾分块哈希」候选指纹，只用于去重提示，不是安全签名。 */
    val candidate_fingerprint: String?,
    val last_seen_at: Long,
    val availability: String,
)
