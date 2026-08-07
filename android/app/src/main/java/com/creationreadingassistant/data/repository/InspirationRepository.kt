package com.creationreadingassistant.data.repository

import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.InspirationVariantDao
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.InspirationVariantEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 灵感仓储 —— 封装灵感与 AI 候选版本（variants）的 Room 读写。
 * 由 InspirationViewModel 独占使用，ViewModel 不再直接依赖 DAO（APP_MODULE_PLAN §7.2）。
 */
@Singleton
class InspirationRepository @Inject constructor(
    private val inspirationDao: InspirationDao,
    private val variantDao: InspirationVariantDao,
) {
    fun observeAllActive(): Flow<List<InspirationEntity>> = inspirationDao.observeAllActive()

    suspend fun getById(id: String): InspirationEntity? = inspirationDao.getById(id)

    suspend fun getByIds(ids: Collection<String>): List<InspirationEntity> = inspirationDao.getByIds(ids)

    /** 全局搜索：标题/正文模糊检索（InspirationDao.search）。 */
    suspend fun search(q: String): List<InspirationEntity> = inspirationDao.search(q)

    suspend fun upsert(entity: InspirationEntity) = inspirationDao.upsert(entity)

    /** 软删除灵感（置 deleted_at），不存在时为无操作。 */
    suspend fun deleteInspiration(id: String) {
        val existing = inspirationDao.getById(id) ?: return
        inspirationDao.upsert(existing.copy(deleted_at = Instant.now().toString()))
    }

    /**
     * 采用某个 AI 候选版本为正文：候选内容为空或灵感不存在时为无操作。
     */
    suspend fun applyVariant(inspirationId: String, variant: InspirationVariantEntity) {
        val content = variant.content ?: return
        val existing = inspirationDao.getById(inspirationId) ?: return
        inspirationDao.upsert(existing.copy(body = content, updated_at = Instant.now().toString()))
    }

    /** 观察某条灵感的 AI 候选版本。 */
    fun observeVariants(inspirationId: String): Flow<List<InspirationVariantEntity>> =
        variantDao.observeByInspiration(inspirationId)

    /** 保存一条 AI 候选版本（不覆盖正文）。 */
    suspend fun saveVariant(variant: InspirationVariantEntity) = variantDao.upsert(variant)

    /** 删除某个候选版本。 */
    suspend fun deleteVariant(variantId: String) = variantDao.delete(variantId)
}
