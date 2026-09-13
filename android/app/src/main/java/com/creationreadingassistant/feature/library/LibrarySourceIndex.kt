package com.creationreadingassistant.feature.library

import android.net.Uri
import android.provider.DocumentsContract
import com.creationreadingassistant.data.local.dao.LibrarySourceRefDao
import com.creationreadingassistant.data.local.entity.LibrarySourceAvailability
import com.creationreadingassistant.data.local.entity.LibrarySourceRefEntity
import javax.inject.Inject
import javax.inject.Singleton

/** 一次可判定精确来源匹配的最小键（authority + documentId）。 */
data class SourceKey(val authority: String?, val documentId: String?) {
    /** 两段都可用时才能做 §5.6 的第①层精确匹配。 */
    val exactMatchable: Boolean
        get() = !authority.isNullOrBlank() && !documentId.isNullOrBlank()
}

/**
 * 从任意来源 URI 提取 [SourceKey]。所有平台调用都容错：非文档型 URI、provider
 * 异常或测试桩缺失时返回 null 分量，调用方按「无法精确判定」降级处理。
 */
fun sourceKeyOf(uri: Uri): SourceKey = SourceKey(
    authority = runCatching { uri.authority }.getOrNull()?.takeIf { it.isNotBlank() },
    documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull(),
)

/**
 * 判定导入来源是否落在用户已授权的根目录树内，命中则返回该树的 document id 作为
 * `root_id`。只做可证明的匹配：authority 一致且 documentId 以树根 id 为前缀并停在
 * 边界上（`primary:Books` 不会误吞 `primary:Books2`）。对使用不透明 id 的 provider
 * （网盘、downloads 等）返回 null —— 宁可缺省，不伪造归属。
 */
fun sourceRootIdFor(uri: Uri, root: LibraryRoot?): String? {
    if (root == null) return null
    val authority = runCatching { uri.authority }.getOrNull() ?: return null
    val treeAuthority = runCatching { root.treeUri.authority }.getOrNull()
    if (authority != treeAuthority) return null
    val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
    val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(root.treeUri) }.getOrNull()
        ?: return null
    if (documentId == treeDocumentId) return treeDocumentId
    if (!documentId.startsWith(treeDocumentId)) return null
    val rest = documentId.substring(treeDocumentId.length)
    return if (rest.startsWith("/")) treeDocumentId else null
}

/** 来源引用的领域视图（与 Room 实体解耦，供 UI 与判定层消费）。 */
data class LibrarySourceRef(
    val bookId: String,
    val rootId: String?,
    val providerAuthority: String,
    val documentId: String?,
    val displayName: String,
    val format: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long?,
    val contentHash: String?,
    val candidateFingerprint: String?,
    val lastSeenAtMillis: Long,
    val availability: LibrarySourceAvailability,
)

fun LibrarySourceRefEntity.toDomain(): LibrarySourceRef = LibrarySourceRef(
    bookId = book_id,
    rootId = root_id,
    providerAuthority = provider_authority,
    documentId = document_id,
    displayName = display_name,
    format = format,
    sizeBytes = size,
    lastModifiedMillis = last_modified,
    contentHash = content_hash,
    candidateFingerprint = candidate_fingerprint,
    lastSeenAtMillis = last_seen_at,
    availability = LibrarySourceAvailability.fromStorage(availability),
)

/** 一次观测/对账计划的落库动作集合。纯函数产出，由 [LibrarySourceIndex] 执行。 */
internal data class SourceReconciliationPlan(
    val seenBookIds: List<String>,
    val backfillDocumentIds: List<String>,
    val missingBookIds: List<String>,
)

internal object SourceReconciliation {

    /**
     * 目录/扫描观测后的对账计划。约定：
     *  - `seenDocumentIds` 是本次**实际看到**的文件 documentId 集合；
     *  - 只有 `missing=allowMissing`（完整未截断扫描）时才允许把「同根但未出现」的
     *    来源标记为 missing —— 单目录浏览无法证明文件不在树的其它位置；
     *  - documentId 为 null 的引用永远不参与 missing 判定（无法与观测对齐）。
     */
    fun plan(
        refs: List<LibrarySourceRef>,
        authority: String,
        rootId: String?,
        seenDocumentIds: Set<String>,
        allowMissing: Boolean,
    ): SourceReconciliationPlan {
        val seenRefBookIds = mutableListOf<String>()
        val backfillDocumentIds = mutableListOf<String>()
        val missingBookIds = mutableListOf<String>()
        for (ref in refs) {
            val documentId = ref.documentId
            val seen = documentId != null && ref.providerAuthority == authority && documentId in seenDocumentIds
            if (seen) {
                seenRefBookIds += ref.bookId
                if (ref.rootId == null && rootId != null) backfillDocumentIds += documentId!!
            } else if (allowMissing &&
                rootId != null &&
                ref.rootId == rootId &&
                ref.providerAuthority == authority &&
                documentId != null &&
                ref.availability == LibrarySourceAvailability.AVAILABLE
            ) {
                missingBookIds += ref.bookId
            }
        }
        return SourceReconciliationPlan(seenRefBookIds, backfillDocumentIds, missingBookIds)
    }
}

/**
 * 来源索引（方案 §7.5）。只保存来源关系与观测状态，不改变 `books.local_uri`、
 * 正文路径或阅读坐标的事实源；不在任何轮询中运行 —— 只被导入成功与用户显式的
 * 浏览/识别刷新触发。
 */
@Singleton
class LibrarySourceIndex @Inject constructor(
    private val dao: LibrarySourceRefDao,
) {

    /** 当前全部引用快照。行数以书架规模为上界（一书一条），供分层判定做内存匹配。 */
    suspend fun snapshot(): List<LibrarySourceRef> = dao.getAll().map { it.toDomain() }

    /** 按候选指纹查引用（导入去重用；指纹只对 >32 MiB 文件存在）。 */
    suspend fun getByFingerprint(fingerprint: String): List<LibrarySourceRef> =
        dao.getByFingerprint(fingerprint).map { it.toDomain() }

    /** 导入成功后落一条来源引用。写入失败不阻断导入，由调用方容错。 */
    suspend fun record(entity: LibrarySourceRefEntity) = dao.upsert(entity)

    /**
     * 轻量观测：目录页成功列出/一次扫描看到这些文件时调用。刷新 last_seen_at、
     * 翻回 available，并在能证明归属时回填缺失的 root_id。绝不标记 missing。
     */
    suspend fun markObserved(
        authority: String,
        rootId: String?,
        seenDocumentIds: Collection<String>,
        nowMillis: Long,
    ) {
        val seen = seenDocumentIds.filterNotNull().toSet()
        if (seen.isEmpty()) return
        val plan = SourceReconciliation.plan(
            refs = dao.getAll().map { it.toDomain() },
            authority = authority,
            rootId = rootId,
            seenDocumentIds = seen,
            allowMissing = false,
        )
        apply(plan, authority, rootId, nowMillis)
    }

    /**
     * 完整扫描后的对账：与 [markObserved] 相同的观测刷新，外加把「同根、此前可见、
     * 本次完整扫描未出现」的来源标记为 missing。调用方必须在扫描未截断时才允许
     * 传 `allowMissing = true`。
     */
    suspend fun reconcileAfterScan(
        authority: String,
        rootId: String?,
        seenDocumentIds: Collection<String>,
        nowMillis: Long,
        allowMissing: Boolean,
    ) {
        val seen = seenDocumentIds.filterNotNull().toSet()
        if (seen.isEmpty() && !allowMissing) return
        val plan = SourceReconciliation.plan(
            refs = dao.getAll().map { it.toDomain() },
            authority = authority,
            rootId = rootId,
            seenDocumentIds = seen,
            allowMissing = allowMissing,
        )
        apply(plan, authority, rootId, nowMillis)
    }

    private suspend fun apply(plan: SourceReconciliationPlan, authority: String, rootId: String?, nowMillis: Long) {
        if (plan.seenBookIds.isNotEmpty()) {
            dao.updateObservation(plan.seenBookIds, nowMillis, LibrarySourceAvailability.AVAILABLE.storageValue)
        }
        if (plan.backfillDocumentIds.isNotEmpty() && rootId != null) {
            dao.backfillRootId(rootId, authority, plan.backfillDocumentIds)
        }
        if (plan.missingBookIds.isNotEmpty()) {
            dao.updateAvailability(plan.missingBookIds, LibrarySourceAvailability.MISSING.storageValue)
        }
    }
}
