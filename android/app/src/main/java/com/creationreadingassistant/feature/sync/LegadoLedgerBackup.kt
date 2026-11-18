package com.creationreadingassistant.feature.sync

import com.creationreadingassistant.data.local.dao.LegadoImportDao
import com.creationreadingassistant.data.local.entity.LegadoBookBindingEntity
import com.creationreadingassistant.data.local.entity.LegadoExternalBookSnapshotEntity
import com.creationreadingassistant.data.local.entity.LegadoExternalDaySnapshotEntity
import com.creationreadingassistant.data.local.entity.LegadoExternalSessionEntity
import com.creationreadingassistant.data.local.entity.LegadoImportBatchEntity
import com.creationreadingassistant.data.local.entity.LegadoImportBatchItemEntity
import com.creationreadingassistant.data.local.entity.LegadoImportSourceEntity
import com.creationreadingassistant.data.local.entity.LegadoImportSubjectEntity
import kotlinx.serialization.Serializable

/** 外部阅读账本及撤销所需的完整依赖。调用方在 Room 事务内读取和恢复。 */
@Serializable
data class LegadoLedgerBackup(
    val sources: List<LegadoImportSourceEntity>,
    val subjects: List<LegadoImportSubjectEntity>,
    val bindings: List<LegadoBookBindingEntity>,
    val sessions: List<LegadoExternalSessionEntity>,
    val days: List<LegadoExternalDaySnapshotEntity>,
    val books: List<LegadoExternalBookSnapshotEntity>,
    val batches: List<LegadoImportBatchEntity>,
    val batchItems: List<LegadoImportBatchItemEntity>,
) {
    internal suspend fun restore(dao: LegadoImportDao, availableBookIds: Set<String>) {
        val incomingBindings = bindings.map { binding ->
            if (binding.book_id == null || binding.book_id in availableBookIds) binding
            else binding.copy(book_id = null, needs_review = 1)
        }
        missing(sources, dao.getSources()) { it.source_id }.forEach { dao.insertSource(it) }
        dao.insertSubjectsIfAbsent(missing(subjects, dao.getAllSubjects()) { it.subject_id })
        missing(incomingBindings, dao.getAllBindings()) { it.binding_id }.forEach { dao.insertBinding(it) }
        dao.insertSessionsIfAbsent(missing(sessions, dao.getAllSessions()) { it.session_id })
        dao.insertDaySnapshotsIfAbsent(missing(days, dao.getAllDaySnapshots()) { it.snapshot_id })
        dao.insertBookSnapshotsIfAbsent(missing(books, dao.getAllBookSnapshots()) { it.snapshot_id })
        missing(batches, dao.getAllBatches()) { it.batch_id }.forEach { dao.insertBatch(it) }
        dao.insertBatchItems(missing(batchItems, dao.getAllBatchItems()) { it.item_id })
    }

    private fun <T> missing(incoming: List<T>, existing: List<T>, id: (T) -> String): List<T> {
        val local = existing.associateBy(id)
        val seen = HashSet<String>()
        return incoming.filter { row ->
            val key = id(row)
            require(seen.add(key)) { "外部阅读记录备份存在重复条目。" }
            val previous = local[key]
            // 同一 ID 的撤销状态、账本快照或绑定可能已改变。覆盖会使批次失去正确归属。
            check(previous == null || previous == row) { "外部阅读记录与本地版本冲突，已取消本次恢复。" }
            previous == null
        }
    }

    internal companion object {
        suspend fun read(dao: LegadoImportDao): LegadoLedgerBackup? {
            val sources = dao.getSources()
            if (sources.isEmpty()) return null
            return LegadoLedgerBackup(
                sources,
                dao.getAllSubjects(),
                dao.getAllBindings(),
                dao.getAllSessions(),
                dao.getAllDaySnapshots(),
                dao.getAllBookSnapshots(),
                dao.getAllBatches(),
                dao.getAllBatchItems(),
            )
        }
    }
}
