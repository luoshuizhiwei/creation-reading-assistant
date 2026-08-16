package com.creationreadingassistant.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 阅读器文本规则（目录 TOC 规则与替换 REPLACE 规则共用一张持久化表）。
 *
 * - [kind]/[scope] 以 String 落库（取值对应 feature 层 [RuleKind]/[RuleScope]
 *   的 name），数据层不依赖 feature 层枚举，映射由 RulesRepository 完成。
 * - [builtin] 内置规则由代码 seed，不允许删除/修改；DAO 的 delete 接口在
 *   SQL 层限定 `builtin = 0`，从接口上杜绝误删内置规则。
 * - [book_id] 对 books.id 外键 ON DELETE CASCADE：删除书籍时级联清理其按书规则。
 * - 显式索引覆盖 kind/scope/book_id/position 四条查询路径。
 */
@Entity(
    tableName = "reader_text_rules",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("kind"),
        Index("scope"),
        Index("book_id"),
        Index("position"),
    ],
)
data class ReaderTextRuleEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val name: String,
    /** 内置规则的 pattern 为 null，模式定义在 TxtChapterDetector 内。 */
    val pattern: String?,
    /** 空串表示删除命中文本。 */
    val replacement: String,
    val builtin: Boolean,
    val enabled: Boolean,
    val scope: String,
    /** 仅 PER_BOOK 规则非空，指向 books.id。 */
    val book_id: String?,
    /** 应用顺序：同一作用域内按 position 升序。 */
    val position: Int,
    val created_at: Long,
    val updated_at: Long,
)
