package com.creationreadingassistant.feature.library

import android.content.Context
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * 「来源内容相对导入基线是否已变化」的纯判定（M-BUG-03）。
 *
 * 与 [com.creationreadingassistant.ui.viewmodel.LibraryBrowserPolicy] 第①层（同一来源位置）
 * 的判据保持一致：`size` 或 `last_modified` 任一与导入基线不同即视为内容有更新。
 *
 * 观测值缺失（provider 不返回该列、或该列 <= 0）时不判定为变化 —— 宁可漏报，不可误报：
 * 误报会让用户在没改过文件的情况下看到「可更新」标签，比漏报更难排查。
 */
internal fun isSourceContentUpdated(
    baselineSize: Long,
    baselineLastModified: Long?,
    observedSize: Long?,
    observedLastModified: Long?,
): Boolean {
    if (observedSize != null && observedSize != baselineSize) return true
    if (observedLastModified != null &&
        baselineLastModified != null &&
        observedLastModified != baselineLastModified
    ) {
        return true
    }
    return false
}

/**
 * 书架侧「来源文件在系统目录被改过」的自动检测（M-BUG-03）。
 *
 * 与 [LibrarySourceIndex] 的目录观测不同：本检测**不浏览目录**，而是按
 * `library_source_refs` 已记录的 `document_id` 逐个 stat 单个文档，因此开销与
 * **书架规模**成正比（一书一次 provider IPC），与目录大小无关。
 *
 * 安全约定（必须遵守，违反会破坏 `library_source_refs` 的基线语义）：
 * - 只读 `COLUMN_SIZE` / `COLUMN_LAST_MODIFIED`；**绝不回写**基线三列
 *   （`size` / `last_modified` / `content_hash` 固定为导入时观测值）；
 * - 任何解析失败、授权失效、provider 异常一律**跳过该书**：不标记 missing、不误报更新；
 * - `document_id` 为 null 的引用无法定位，直接跳过；
 * - 本检测**不影响阅读**：正文事实源始终是内部稳定副本，这里只产出「可更新」提示。
 */
@Singleton
class ShelfSourceUpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rootStore: LibraryRootStore,
) {

    /**
     * 返回「来源内容相对导入基线已变化」的 bookId 集合。
     *
     * 逐本**串行**执行：书架规模有限（一书一条引用），串行可避免同时打爆 SAF provider
     * 造成系统级 ANR 或 provider 限流。整体失败时返回空集合（调用方保持无标签状态）。
     */
    suspend fun checkUpdated(refs: List<LibrarySourceRef>): Set<String> = withContext(Dispatchers.IO) {
        if (refs.isEmpty()) return@withContext emptySet()
        // loadRoot() 是 suspend：不能用 runCatching 包（会吞掉 CancellationException，
        // 使取消失效），显式区分取消与真实失败。
        val treeUri = try {
            rootStore.loadRoot()?.treeUri
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val rootDocumentId = treeUri?.let {
            runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull()
        }
        val resolver = context.contentResolver
        val updated = mutableSetOf<String>()
        for (ref in refs) {
            // 循环内没有其它挂起点，必须显式检查取消，否则取消后仍会把整架书 stat 完。
            coroutineContext.ensureActive()
            // 定位与观测都收敛在 LibrarySourceIndex 的共用工具里，与「按来源刷新」同一口径。
            val uri = sourceDocumentUriOf(ref, treeUri, rootDocumentId) ?: continue
            val observed = querySourceMetadata(resolver, uri) ?: continue
            if (isSourceContentUpdated(ref.sizeBytes, ref.lastModifiedMillis, observed.first, observed.second)) {
                updated += ref.bookId
            }
        }
        updated
    }
}
