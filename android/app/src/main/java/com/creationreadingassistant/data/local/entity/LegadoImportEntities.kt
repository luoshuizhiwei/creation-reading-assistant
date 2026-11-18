package com.creationreadingassistant.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Legado 记录导入契约（LEGADO-IMPORT-PLAN-v2）的独立来源账本与批次表。
 *
 * 职责边界（见契约 §3/§5/§9）：
 *  - 外部会话/按日/书级快照**绝不**写入 reading_sessions，也从不参与本地
 *    阅读时长统计；来源库是用户确认的一组同一阅读库存档，作为外层命名空间。
 *  - 副本键 = 原始 bookUrl＋书名＋作者（与会话副本身份条件一致）；作品键 =
 *    原始书名＋作者，保留空作者。`book_url` 为空串的会话归作品主体。
 *  - 绑定以副本主体为唯一键，本地 book_id 可空（硬删 SET NULL），历史保留。
 *  - 批次项记录逐书 prior progress JSON 与导入后比对值，支撑一次性撤销；
 *    待关联位置（未落地的 bookshelf 条目）也作为批次项持久化（kind =
 *    [LegadoBatchItemKind.PENDING_POSITION]），状态由主体、绑定与位置条目得出。
 *
 * 时区：三份上游记录文件都没有时区字段，本层不存时区列；原始日期/绝对时刻
 * 原样保留（契约 §5 方案 A：unknown，无入口从会话重建日视图）。
 */

/** 来源库：用户确认的一组来自同一阅读库存档的备份。 */
@Serializable
@Entity(tableName = "legado_import_sources")
data class LegadoImportSourceEntity(
    @PrimaryKey val source_id: String,
    /** 用户可读标签；默认取归档文件名（中性代称规则见契约 §8）。 */
    val label: String,
    /** 适配器与固定上游提交标识，如 `md3@4efe2496`。 */
    val adapter_version: String,
    val created_at: String,
)

/** 作品/副本身份。subject_id = hash(source_id, kind, book_name, book_author, book_url)。 */
@Serializable
@Entity(
    tableName = "legado_import_subjects",
    indices = [Index("source_id")],
    foreignKeys = [ForeignKey(
        entity = LegadoImportSourceEntity::class,
        parentColumns = ["source_id"],
        childColumns = ["source_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class LegadoImportSubjectEntity(
    @PrimaryKey val subject_id: String,
    val source_id: String,
    /** "copy"（副本，book_url 非空）或 "work"（作品级，book_url 空串）。 */
    val kind: String,
    val book_name: String,
    val book_author: String,
    val book_url: String,
    /** bookshelf 的 origin 提示；会话行没有 origin，不能塞进会话键。 */
    val origin: String?,
    val created_at: String,
)

/** 副本主体 ↔ 本地书绑定。binding_id = subject_id（一副本至多一条绑定）。 */
@Serializable
@Entity(
    tableName = "legado_book_bindings",
    indices = [Index("subject_id"), Index("book_id")],
    foreignKeys = [
        ForeignKey(
            entity = LegadoImportSubjectEntity::class,
            parentColumns = ["subject_id"],
            childColumns = ["subject_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class LegadoBookBindingEntity(
    @PrimaryKey val binding_id: String,
    val subject_id: String,
    /** 硬删书时由 Room 置空；null = 待关联（历史保留）。 */
    val book_id: String?,
    val confirmed_at: String,
    /** 确认依据，固定 "preview-user-confirm"。 */
    val confirmation: String,
    /** 确认时的本地文档身份（章起点表+总字符的 hash），正文变更时要求重验。 */
    val content_identity: String?,
    /** 每次重新确认递增；绑定失效重验后 +1。 */
    val content_version: Int,
    /** 1 = 正文缺失/版本变更，恢复可用后需重新确认。 */
    val needs_review: Int,
)

/** 外部会话（时间线首选层）。session_id = hash(source_id, 上游七元组)。 */
@Serializable
@Entity(
    tableName = "legado_external_sessions",
    indices = [Index("source_id"), Index("subject_id"), Index("created_by_batch")],
    foreignKeys = [
        ForeignKey(
            entity = LegadoImportSourceEntity::class,
            parentColumns = ["source_id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LegadoImportSubjectEntity::class,
            parentColumns = ["subject_id"],
            childColumns = ["subject_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LegadoExternalSessionEntity(
    @PrimaryKey val session_id: String,
    val source_id: String,
    val subject_id: String,
    /** 上游原始设备标识；空串保留为「未知设备」，不替成当前设备。 */
    val device_id: String,
    val start_ms: Long,
    val end_ms: Long,
    /** 上游 `words` 字段：章节序号（durChapterIndex），不是字数；缺失保留 null，不默填 0。 */
    val chapter_index: Long?,
    /** 首个写入本行的批次；同内容重复导入只记引用（批次项 kind=*_ref）。 */
    val created_by_batch: String?,
    val created_at: String,
)

/** 按日快照（原始 civil date 原样保留；readWords 是上游原字段，非真字数）。 */
@Serializable
@Entity(
    tableName = "legado_external_day_snapshots",
    indices = [Index("source_id"), Index("subject_id")],
    foreignKeys = [
        ForeignKey(
            entity = LegadoImportSourceEntity::class,
            parentColumns = ["source_id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LegadoImportSubjectEntity::class,
            parentColumns = ["subject_id"],
            childColumns = ["subject_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LegadoExternalDaySnapshotEntity(
    @PrimaryKey val snapshot_id: String,
    val source_id: String,
    val subject_id: String,
    val device_id: String,
    /** 上游写入的原始自然日（yyyy-MM-dd），不随本机时区变化。 */
    val date: String,
    val read_time_ms: Long,
    val read_words: Long,
    val first_read_ms: Long,
    val last_read_ms: Long,
    val created_by_batch: String?,
    val updated_at: String,
)

/** 书级快照。 */
@Serializable
@Entity(
    tableName = "legado_external_book_snapshots",
    indices = [Index("source_id"), Index("subject_id")],
    foreignKeys = [
        ForeignKey(
            entity = LegadoImportSourceEntity::class,
            parentColumns = ["source_id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LegadoImportSubjectEntity::class,
            parentColumns = ["subject_id"],
            childColumns = ["subject_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LegadoExternalBookSnapshotEntity(
    @PrimaryKey val snapshot_id: String,
    val source_id: String,
    val subject_id: String,
    val device_id: String,
    val read_time_ms: Long,
    val last_read_ms: Long,
    val created_by_batch: String?,
    val updated_at: String,
)

/** 导入批次（一次性撤销的单位）。 */
@Serializable
@Entity(tableName = "legado_import_batches", indices = [Index("source_id")])
data class LegadoImportBatchEntity(
    @PrimaryKey val batch_id: String,
    val source_id: String,
    val adapter_version: String,
    /** sha256(整包字节)；文件名不决定身份。 */
    val archive_fingerprint: String,
    /** 归档中性代称（默认文件名；报告/日志一律用代称，契约 §8）。 */
    val archive_label: String,
    val created_at: String,
    /** committed / undone_full / undone_partial。 */
    val status: String,
    /** 撤销结果摘要（含保留原因）；committed 时为 null。 */
    val undo_note: String?,
)

/** 批次项：进度变更、外部行新建/引用/替换、绑定创建、待关联位置。 */
@Serializable
@Entity(
    tableName = "legado_import_batch_items",
    indices = [Index("batch_id"), Index("subject_id")],
    foreignKeys = [ForeignKey(
        entity = LegadoImportBatchEntity::class,
        parentColumns = ["batch_id"],
        childColumns = ["batch_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class LegadoImportBatchItemEntity(
    @PrimaryKey val item_id: String,
    val batch_id: String,
    val subject_id: String?,
    /** 见 [LegadoBatchItemKind]。 */
    val kind: String,
    /** 进度类项的目标本地书；外部行/待关联为 null。 */
    val book_id: String?,
    /** 受影响外部账本行 ID（session_id / snapshot_id）；进度类为 null。 */
    val row_key: String?,
    /**
     * 结构化载荷：进度类 = {prior: 实体JSON或null, after: 内容指纹}；
     * 快照替换 = {prior: 行JSON, after: 内容指纹}；待关联 = {坐标, 原因, 位置序号}。
     */
    val payload_json: String,
)

/** 批次项 kind 的常量与撤销语义说明（契约 §9）。 */
object LegadoBatchItemKind {
    const val PROGRESS_REPLACED = "progress_replaced"
    const val PROGRESS_CREATED = "progress_created"
    const val SESSION_CREATED = "session_created"
    const val SESSION_REF = "session_ref"
    const val DAY_CREATED = "day_created"
    const val DAY_REPLACED = "day_replaced"
    const val DAY_REF = "day_ref"
    const val BOOK_CREATED = "book_created"
    const val BOOK_REPLACED = "book_replaced"
    const val BOOK_REF = "book_ref"
    const val BINDING_CREATED = "binding_created"
    const val SNAPSHOT_CONFLICT = "snapshot_conflict"
    const val PENDING_POSITION = "pending_position"

    /** 撤销时需要校验「导入后基准仍一致」的项。 */
    val revertWithCheck = setOf(
        PROGRESS_REPLACED, PROGRESS_CREATED, DAY_REPLACED, BOOK_REPLACED,
    )

    /** 撤销时可能因「其他有效归属」而保留的外部新建项。 */
    val externalCreated = setOf(SESSION_CREATED, DAY_CREATED, BOOK_CREATED)
}
