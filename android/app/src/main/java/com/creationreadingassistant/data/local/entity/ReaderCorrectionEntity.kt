package com.creationreadingassistant.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 单处纠错记录（E2，v13 新增）。
 *
 * 用户在正文选区上保存的「只改这一处」纠错：
 * - [source_start]/[source_end] 是**全书 source 坐标**（半开区间），来自选区经
 *   投影映射的精确回查；与高亮/笔记同一坐标系，重分页与重启后仍然成立。
 * - [find_text] 是保存时该区间在 display 空间的文本（用户所见）；应用时用于
 *   内容校验，漂移则诚实跳过。
 * - [replace_text] 为空串表示删除该区间文本。
 * - [status]：ACTIVE 参与投影；UNDONE 已撤销（保留为历史，可再恢复）。
 * - 原文件永不修改：纠错只是投影层的覆盖叠加，撤销即恢复原文。
 * - [book_id] 外键 ON DELETE CASCADE：删书时级联清理其纠错记录。
 */
@Entity(
    tableName = "reader_text_corrections",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("book_id"),
        Index("status"),
    ],
)
data class ReaderCorrectionEntity(
    @PrimaryKey val id: String,
    val book_id: String,
    val source_start: Int,
    val source_end: Int,
    val find_text: String,
    val replace_text: String,
    val status: String,
    val created_at: Long,
    val updated_at: Long,
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_UNDONE = "UNDONE"
    }
}
