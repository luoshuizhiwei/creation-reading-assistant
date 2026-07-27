package com.creationreadingassistant.feature.reader.locator

import com.creationreadingassistant.data.local.dao.ReaderAnchorCacheDao
import com.creationreadingassistant.data.local.entity.ReaderAnchorCacheEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnchorCacheStore @Inject constructor(
    private val dao: ReaderAnchorCacheDao,
) {
    suspend fun get(kind: String, entityId: String, contentKey: String): ResolvedAnchor? =
        runCatching { dao.get(kind, entityId, contentKey) }.getOrNull()?.let {
            ResolvedAnchor(
                chapterIndex = it.chapter_index,
                charOffset = it.char_offset,
                confidence = AnchorConfidence.entries.firstOrNull { c -> c.storedValue == it.confidence }
                    ?: AnchorConfidence.APPROXIMATE,
            )
        }

    suspend fun save(
        kind: String,
        entityId: String,
        contentKey: String,
        anchor: ResolvedAnchor,
    ) {
        runCatching {
            dao.upsert(
                ReaderAnchorCacheEntity(
                    kind = kind,
                    entity_id = entityId,
                    content_key = contentKey,
                    chapter_index = anchor.chapterIndex,
                    char_offset = anchor.charOffset,
                    confidence = anchor.confidence.storedValue,
                    resolved_at = System.currentTimeMillis(),
                ),
            )
        }
    }
}
