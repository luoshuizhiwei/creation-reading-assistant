package com.creationreadingassistant.feature.reader.rules

import com.creationreadingassistant.data.local.dao.ReaderCorrectionDao
import com.creationreadingassistant.data.local.dao.ReaderTextRuleDao
import com.creationreadingassistant.data.local.dao.ReaderTextRulePositionUpdate
import com.creationreadingassistant.data.local.entity.ReaderCorrectionEntity
import com.creationreadingassistant.data.local.entity.ReaderTextRuleEntity
import com.creationreadingassistant.feature.reader.doc.TxtTocProfile
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * 当前书可管理的规则快照。
 *
 * [tocRules] / [replaceRules] 为该书完整可管理列表（标准恒在、宽松内置全部列出、
 * 全局自定义 + 本书 PER_BOOK 自定义；别的书的 PER_BOOK 规则不出现），
 * [effectiveToc] / [effectiveReplace] 为过滤 enabled 后的生效列表。
 * 内置宽松规则对外始终使用 `num-dot` 等语义 id，Room 绑定行 id 为仓库私有。
 *
 * [corrections] 是本书单处纠错记录（E2，含已撤销历史）；生效中的纠错以锚定
 * [ReplaceRule]（id 前缀 `correction:`，position = Int.MAX_VALUE）追加在
 * [effectiveReplace] 之后——普通规则先应用、纠错按 source 锚点最后叠加。
 */
data class RuleSnapshot(
    val bookId: String,
    val tocRules: List<TocRule>,
    val replaceRules: List<ReplaceRule>,
    val effectiveToc: List<TocRule>,
    val effectiveReplace: List<ReplaceRule>,
    val corrections: List<CorrectionRecord> = emptyList(),
) {
    /**
     * 当前书 TXT 目录识别的执行快照（P1-A）：小文件目录构建与大文件流式扫描
     * 都使用该 profile；[TxtTocProfile.key] 即扫描 / 索引身份，规则集合、
     * 顺序或内容变化后 key 变化，旧结果自动失效。
     */
    val effectiveTocProfile: TxtTocProfile
        get() = RuleEngine.tocProfile(effectiveToc)

    companion object {
        /** 无书身份时的安全空快照：不触达 Room，仅作 UI 占位。 */
        fun empty(bookId: String = ""): RuleSnapshot = RuleSnapshot(
            bookId = bookId,
            tocRules = emptyList(),
            replaceRules = emptyList(),
            effectiveToc = emptyList(),
            effectiveReplace = emptyList(),
        )
    }
}

/**
 * 规则写入结果。验证/冲突类失败一律不落库。
 */
sealed interface RuleMutationResult {
    /** 写入成功（启停/删除/排序/无 id 保存之外的通用成功）。 */
    data object Success : RuleMutationResult

    /** 自定义规则保存成功，[id] 为落库的规则 id（新规则可能由仓库生成）。 */
    data class Saved(val id: String) : RuleMutationResult

    /** 目标规则不存在或不属于当前书。 */
    data object NotFound : RuleMutationResult

    /**
     * 单处纠错没有可用的 source 锚点（E2）：调用方没有选区、或锚点区间非法。
     * [reason] 为面向用户的说明，反馈行直接展示。
     */
    data class NotAnchorable(val reason: String) : RuleMutationResult

    /** 保存前校验或 id 冲突失败；[errors] 为空表示冲突类别未细分。 */
    data class Rejected(val errors: List<RuleValidationError>) : RuleMutationResult

    /**
     * 旧 DataStore 单选迁移结果。
     * [effectiveRuleId] 为迁移后生效的语义规则 id（[BuiltinTocRules.STANDARD_ID]
     * 表示保持/回退标准）；[legacyValue] 为原始旧值（null 表示从未设置）。
     * 旧值未知时 `effectiveRuleId == "builtin"` 且 `legacyValue` 非空且非
     * `"builtin"`，调用方可据此识别安全回退。
     */
    data class Migrated(val effectiveRuleId: String, val legacyValue: String?) : RuleMutationResult
}

/** 规则的只读身份（类型 + 作用域），见 [RulesRepository.describeRule]。 */
data class RuleDescriptor(
    val kind: RuleKind,
    val scope: RuleScope,
)

/**
 * 规则写入命令。调用方只与语义 id 交互，不接触 Room 行/绑定 id。
 * 内置规则无修改 pattern/name 或删除路径；标准内置不可启停。
 */
sealed interface RuleCommand {
    /** 保存自定义 TOC 规则；[id] 为 null 时由仓库生成。 */
    data class SaveCustomToc(
        val id: String? = null,
        val name: String,
        val pattern: String,
        val scope: RuleScope = RuleScope.PER_BOOK,
        val enabled: Boolean = true,
    ) : RuleCommand

    /** 保存自定义 REPLACE 规则；[id] 为 null 时由仓库生成。 */
    data class SaveCustomReplace(
        val id: String? = null,
        val name: String,
        val pattern: String,
        val replacement: String = "",
        val scope: RuleScope = RuleScope.PER_BOOK,
        val enabled: Boolean = true,
    ) : RuleCommand

    /** 启停宽松内置 TOC 规则（[ruleId] 为语义 id，如 `num-dot`）。 */
    data class ToggleBuiltinToc(val ruleId: String, val enabled: Boolean) : RuleCommand

    /** 启停自定义规则（TOC/REPLACE 通用）。 */
    data class ToggleCustom(val ruleId: String, val enabled: Boolean) : RuleCommand

    /** 删除自定义规则；内置规则在此被拒绝。 */
    data class DeleteCustom(val ruleId: String) : RuleCommand

    /**
     * 按 id 列表排序（TOC/REPLACE 各自）。
     * [ruleIds] 必须恰为当前书可管理的自定义规则（GLOBAL + 本书 PER_BOOK）全集；
     * 内置规则位置由代码 seed 固定，不参与排序。
     */
    data class ReorderRules(val kind: RuleKind, val ruleIds: List<String>) : RuleCommand

    /**
     * 迁移旧 DataStore 逐书单选 ruleId（`txt_toc_rule_<bookId>`）。
     * [legacyRuleId] 为旧值；builtin/空值只保持标准，宽松内置为当前书建立 enabled
     * 绑定，未知旧值安全回退标准（结果可识别，不崩溃）。
     */
    data class MigrateLegacyTocRule(val legacyRuleId: String?) : RuleCommand

    /**
     * 快速单规则入口（TOC Sheet 单选）：把当前书生效目录归一化为
     * 「标准 + 恰好一个宽松内置」或仅标准，其余宽松内置绑定一律禁用。
     * 结果以 [RuleMutationResult.Migrated] 返回（[legacyValue] 为 null），
     * 调用方据此同步旧单选显示 id。Room 始终是目录状态的唯一来源。
     */
    data class SelectSingleTocRule(val ruleId: String) : RuleCommand

    /**
     * 保存单处纠错（E2）：只在 [sourceStart, sourceEnd) 这一处生效的覆盖修正。
     * [findText] 是该区间保存时的 display 文本（用户所见），应用时做内容校验。
     * 锚点是全书 source 坐标，由调用方（ReaderViewModel）从当前选区回查填入。
     */
    data class SaveSingleCorrection(
        val sourceStart: Int,
        val sourceEnd: Int,
        val findText: String,
        val replaceText: String,
    ) : RuleCommand

    /** 撤销单处纠错：记录保留为历史（可恢复），正文回落原文。 */
    data class UndoCorrection(val correctionId: String) : RuleCommand

    /** 恢复已撤销的单处纠错。 */
    data class RestoreCorrection(val correctionId: String) : RuleCommand
}

/**
 * 阅读器规则深模块：封装 Room 行/绑定 id 的全部细节，对外只暴露
 * [observe] 快照与 [execute] 命令两个入口。
 *
 * 映射约定：
 * - 标准内置（`builtin`）恒开启、不落行；
 * - 宽松内置按书落一行，行 id 为仓库私有确定性 id `builtin:<语义id>:<bookId>`；
 * - 自定义规则以调用方 id（或仓库生成 id）落行，GLOBAL 行 book_id 为 null；
 * - 自定义 TOC 规则 position 恒在宽松内置之后（≥ seeds 最大 position + 1）。
 */
@Singleton
class RulesRepository @Inject constructor(
    private val dao: ReaderTextRuleDao,
    private val correctionDao: ReaderCorrectionDao,
) {
    fun observe(bookId: String): Flow<RuleSnapshot> =
        combine(dao.observeAll(), correctionDao.observeForBook(bookId)) { rows, corrections ->
            buildSnapshot(bookId, rows, corrections)
        }

    suspend fun execute(bookId: String, command: RuleCommand): RuleMutationResult = when (command) {
        is RuleCommand.SaveCustomToc -> saveCustomToc(bookId, command)
        is RuleCommand.SaveCustomReplace -> saveCustomReplace(bookId, command)
        is RuleCommand.ToggleBuiltinToc -> toggleBuiltinToc(bookId, command)
        is RuleCommand.ToggleCustom -> toggleCustom(bookId, command)
        is RuleCommand.DeleteCustom -> deleteCustom(bookId, command.ruleId)
        is RuleCommand.ReorderRules -> reorder(bookId, command)
        is RuleCommand.MigrateLegacyTocRule -> migrateLegacy(bookId, command.legacyRuleId)
        is RuleCommand.SelectSingleTocRule -> selectSingleTocRule(bookId, command.ruleId)
        is RuleCommand.SaveSingleCorrection -> saveSingleCorrection(bookId, command)
        is RuleCommand.UndoCorrection -> undoCorrection(bookId, command.correctionId, undone = true)
        is RuleCommand.RestoreCorrection -> undoCorrection(bookId, command.correctionId, undone = false)
    }

    /**
     * 只读：某条自定义规则的「类型 + 作用域」。供上层在规则变更**之前**判定
     * 受影响范围（如「替换规则变更后要重建哪些书的搜索索引」——删除后行已不在，
     * 必须在 execute 前查）。未知 id 返回 null。
     */
    suspend fun describeRule(ruleId: String): RuleDescriptor? {
        val row = dao.getById(ruleId) ?: return null
        val kind = RuleKind.entries.firstOrNull { it.name == row.kind } ?: return null
        return RuleDescriptor(kind = kind, scope = scopeOf(row.scope))
    }

    // ── 快照组装 ────────────────────────────────────────────────────────

    private fun buildSnapshot(
        bookId: String,
        rows: List<ReaderTextRuleEntity>,
        correctionRows: List<ReaderCorrectionEntity>,
    ): RuleSnapshot {
        val tocRows = rows.filter { it.kind == RuleKind.TOC.name }
        val replaceRows = rows.filter { it.kind == RuleKind.REPLACE.name }

        val tocRules = (
            BuiltinTocRules.seeds.map { seed ->
                if (seed.id == BuiltinTocRules.STANDARD_ID) {
                    seed
                } else {
                    val binding = tocRows.firstOrNull { it.id == bindingStorageId(bookId, seed.id) }
                    seed.copy(enabled = binding?.enabled ?: false, scope = RuleScope.PER_BOOK)
                }
            } + tocRows.filter { !it.builtin && isVisible(it, bookId) }.map { it.toTocRule() }
            ).sortedWith(compareBy<TocRule> { it.position }.thenBy { it.id })

        val replaceRules = replaceRows
            .filter { !it.builtin && isVisible(it, bookId) }
            .map { it.toReplaceRule() }
            .sortedWith(compareBy<ReplaceRule> { it.position }.thenBy { it.id })

        val corrections = correctionRows
            .filter { it.book_id == bookId }
            .map { it.toCorrectionRecord() }
        // 生效纠错以锚定规则追加在普通规则之后（position = Int.MAX_VALUE）：
        // 引擎先应用普通正则规则，再按 source 锚点逐条叠加纠错。
        val anchoredCorrections = corrections
            .filter { it.active }
            .map { it.toAnchoredRule() }

        return RuleSnapshot(
            bookId = bookId,
            tocRules = tocRules,
            replaceRules = replaceRules,
            effectiveToc = tocRules.filter { it.enabled },
            effectiveReplace = replaceRules.filter { it.enabled } + anchoredCorrections,
            corrections = corrections,
        )
    }

    private fun isVisible(row: ReaderTextRuleEntity, bookId: String): Boolean =
        row.scope == RuleScope.GLOBAL.name || row.book_id == bookId

    private fun ReaderTextRuleEntity.toTocRule() = TocRule(
        id = id,
        name = name,
        pattern = pattern,
        builtin = builtin,
        enabled = enabled,
        scope = scopeOf(scope),
        position = position,
    )

    private fun ReaderTextRuleEntity.toReplaceRule() = ReplaceRule(
        id = id,
        name = name,
        pattern = pattern.orEmpty(),
        replacement = replacement,
        enabled = enabled,
        position = position,
        scope = scopeOf(scope),
    )

    private fun ReaderCorrectionEntity.toCorrectionRecord() = CorrectionRecord(
        id = id,
        sourceStart = source_start,
        sourceEnd = source_end,
        findText = find_text,
        replaceText = replace_text,
        undone = status != ReaderCorrectionEntity.STATUS_ACTIVE,
        createdAt = created_at,
    )

    /** 生效纠错 → 锚定规则（执行身份 id 与规则表 id 空间隔离，杜绝碰撞）。 */
    private fun CorrectionRecord.toAnchoredRule() = ReplaceRule(
        id = "$CORRECTION_RULE_ID_PREFIX$id",
        name = "单处纠错",
        pattern = "",
        replacement = replaceText,
        enabled = true,
        position = Int.MAX_VALUE,
        scope = RuleScope.PER_BOOK,
        anchor = CorrectionAnchor(
            sourceStart = sourceStart,
            sourceEnd = sourceEnd,
            findText = findText,
        ),
    )

    // ── 单处纠错（E2）─────────────────────────────────────────────────

    private suspend fun saveSingleCorrection(
        bookId: String,
        command: RuleCommand.SaveSingleCorrection,
    ): RuleMutationResult {
        if (command.sourceStart < 0 || command.sourceEnd <= command.sourceStart) {
            return RuleMutationResult.NotAnchorable("纠错锚点区间无效，请重新在正文中选择文字")
        }
        val findText = command.findText
        if (findText.isBlank()) {
            return RuleMutationResult.NotAnchorable("纠错内容为空，请重新在正文中选择文字")
        }
        if (command.replaceText == findText) {
            return RuleMutationResult.NotAnchorable("替换后的文字与原文相同，无需纠错")
        }
        val now = System.currentTimeMillis()
        val id = "$CORRECTION_ID_PREFIX${UUID.randomUUID()}"
        correctionDao.upsert(
            ReaderCorrectionEntity(
                id = id,
                book_id = bookId,
                source_start = command.sourceStart,
                source_end = command.sourceEnd,
                find_text = findText,
                replace_text = command.replaceText,
                status = ReaderCorrectionEntity.STATUS_ACTIVE,
                created_at = now,
                updated_at = now,
            ),
        )
        return RuleMutationResult.Saved(id)
    }

    private suspend fun undoCorrection(
        bookId: String,
        correctionId: String,
        undone: Boolean,
    ): RuleMutationResult {
        val row = correctionDao.getById(correctionId) ?: return RuleMutationResult.NotFound
        if (row.book_id != bookId) return RuleMutationResult.NotFound
        val target = if (undone) {
            ReaderCorrectionEntity.STATUS_UNDONE
        } else {
            ReaderCorrectionEntity.STATUS_ACTIVE
        }
        return if (correctionDao.updateStatus(correctionId, target, System.currentTimeMillis()) == 1) {
            RuleMutationResult.Success
        } else {
            RuleMutationResult.NotFound
        }
    }

    private fun scopeOf(value: String): RuleScope =
        RuleScope.entries.firstOrNull { it.name == value } ?: RuleScope.PER_BOOK

    // ── 保存自定义 ──────────────────────────────────────────────────────

    private suspend fun saveCustomToc(bookId: String, command: RuleCommand.SaveCustomToc): RuleMutationResult {
        val ruleId = command.id ?: "$CUSTOM_TOC_ID_PREFIX${UUID.randomUUID()}"
        val check = checkSave(ruleId, RuleKind.TOC, bookId)
        if (check is SaveCheck.Reject) return check.result

        val validation = RuleEngine.validateTocRule(command.pattern)
        if (!validation.valid) return RuleMutationResult.Rejected(validation.errors)

        val existing = (check as? SaveCheck.UseExisting)?.row
        val now = System.currentTimeMillis()
        dao.upsert(
            ReaderTextRuleEntity(
                id = ruleId,
                kind = RuleKind.TOC.name,
                name = command.name.trim(),
                pattern = command.pattern,
                replacement = "",
                builtin = false,
                enabled = command.enabled,
                scope = command.scope.name,
                book_id = if (command.scope == RuleScope.PER_BOOK) bookId else null,
                position = existing?.position ?: nextPosition(RuleKind.TOC),
                created_at = existing?.created_at ?: now,
                updated_at = now,
            ),
        )
        return RuleMutationResult.Saved(ruleId)
    }

    private suspend fun saveCustomReplace(bookId: String, command: RuleCommand.SaveCustomReplace): RuleMutationResult {
        val ruleId = command.id ?: "$CUSTOM_REPLACE_ID_PREFIX${UUID.randomUUID()}"
        val check = checkSave(ruleId, RuleKind.REPLACE, bookId)
        if (check is SaveCheck.Reject) return check.result

        val validation = RuleEngine.validateReplaceRule(command.pattern, command.replacement)
        if (!validation.valid) return RuleMutationResult.Rejected(validation.errors)

        val existing = (check as? SaveCheck.UseExisting)?.row
        val now = System.currentTimeMillis()
        dao.upsert(
            ReaderTextRuleEntity(
                id = ruleId,
                kind = RuleKind.REPLACE.name,
                name = command.name.trim(),
                pattern = command.pattern,
                replacement = command.replacement,
                builtin = false,
                enabled = command.enabled,
                scope = command.scope.name,
                book_id = if (command.scope == RuleScope.PER_BOOK) bookId else null,
                position = existing?.position ?: nextPosition(RuleKind.REPLACE),
                created_at = existing?.created_at ?: now,
                updated_at = now,
            ),
        )
        return RuleMutationResult.Saved(ruleId)
    }

    /**
     * 保存前 id 冲突检查：内置 seed 与绑定前缀保留、他书/他类规则不可劫持。
     * 通过后返回 [SaveCheck.Ok]（新规则）或 [SaveCheck.UseExisting]（更新既有行）。
     */
    private suspend fun checkSave(id: String, kind: RuleKind, bookId: String): SaveCheck {
        if (BuiltinTocRules.isSeed(id)) return SaveCheck.Reject(rejected(RuleValidationError.IMMUTABLE_BUILTIN))
        if (id.startsWith(BUILTIN_BINDING_PREFIX)) return SaveCheck.Reject(rejected(RuleValidationError.ID_CONFLICT))
        val existing = dao.getById(id) ?: return SaveCheck.Ok
        if (existing.builtin) return SaveCheck.Reject(rejected(RuleValidationError.IMMUTABLE_BUILTIN))
        if (existing.kind != kind.name) return SaveCheck.Reject(rejected(RuleValidationError.ID_CONFLICT))
        if (existing.scope == RuleScope.PER_BOOK.name && existing.book_id != null && existing.book_id != bookId) {
            return SaveCheck.Reject(rejected(RuleValidationError.ID_CONFLICT))
        }
        return SaveCheck.UseExisting(existing)
    }

    private sealed interface SaveCheck {
        data object Ok : SaveCheck
        data class UseExisting(val row: ReaderTextRuleEntity) : SaveCheck
        data class Reject(val result: RuleMutationResult.Rejected) : SaveCheck
    }

    // ── 启停 / 删除 ─────────────────────────────────────────────────────

    private suspend fun toggleBuiltinToc(bookId: String, command: RuleCommand.ToggleBuiltinToc): RuleMutationResult {
        val seed = BuiltinTocRules.byId[command.ruleId] ?: return RuleMutationResult.NotFound
        if (seed.id == BuiltinTocRules.STANDARD_ID) {
            return rejected(RuleValidationError.IMMUTABLE_BUILTIN)
        }
        if (command.enabled) {
            upsertBuiltinBinding(bookId, seed)
        } else {
            dao.updateEnabled(bindingStorageId(bookId, seed.id), enabled = false, updatedAt = System.currentTimeMillis())
        }
        return RuleMutationResult.Success
    }

    private suspend fun toggleCustom(bookId: String, command: RuleCommand.ToggleCustom): RuleMutationResult {
        if (BuiltinTocRules.isSeed(command.ruleId)) return rejected(RuleValidationError.IMMUTABLE_BUILTIN)
        val existing = dao.getById(command.ruleId) ?: return RuleMutationResult.NotFound
        if (existing.builtin) return rejected(RuleValidationError.IMMUTABLE_BUILTIN)
        if (existing.scope == RuleScope.PER_BOOK.name && existing.book_id != bookId) return RuleMutationResult.NotFound
        dao.updateEnabled(command.ruleId, command.enabled, System.currentTimeMillis())
        return RuleMutationResult.Success
    }

    private suspend fun deleteCustom(bookId: String, ruleId: String): RuleMutationResult {
        if (BuiltinTocRules.isSeed(ruleId)) return rejected(RuleValidationError.IMMUTABLE_BUILTIN)
        val existing = dao.getById(ruleId) ?: return RuleMutationResult.NotFound
        if (existing.builtin) return rejected(RuleValidationError.IMMUTABLE_BUILTIN)
        if (existing.scope == RuleScope.PER_BOOK.name && existing.book_id != bookId) return RuleMutationResult.NotFound
        return if (dao.deleteCustom(ruleId) == 1) {
            RuleMutationResult.Success
        } else {
            RuleMutationResult.NotFound
        }
    }

    // ── 排序 ────────────────────────────────────────────────────────────

    private suspend fun reorder(bookId: String, command: RuleCommand.ReorderRules): RuleMutationResult {
        if (command.ruleIds.isEmpty()) return RuleMutationResult.Success
        val rows = dao.observeAll().first()
        val manageable = rows.filter { it.kind == command.kind.name && !it.builtin && isVisible(it, bookId) }
        val manageableIds = manageable.map { it.id }.toSet()
        if (command.ruleIds.size != manageable.size || command.ruleIds.toSet() != manageableIds) {
            return rejected(RuleValidationError.ID_CONFLICT)
        }
        val base = builtinPositionFloor(command.kind) + 1
        val now = System.currentTimeMillis()
        dao.updatePositions(
            command.ruleIds.mapIndexed { index, id ->
                ReaderTextRulePositionUpdate(id = id, position = base + index, updatedAt = now)
            },
        )
        return RuleMutationResult.Success
    }

    // ── 旧 DataStore 单选迁移 ───────────────────────────────────────────

    private suspend fun migrateLegacy(bookId: String, legacyRuleId: String?): RuleMutationResult {
        val value = legacyRuleId?.takeIf { it.isNotBlank() }
        if (value == null || value == BuiltinTocRules.STANDARD_ID) {
            return RuleMutationResult.Migrated(BuiltinTocRules.STANDARD_ID, legacyRuleId)
        }
        val seed = BuiltinTocRules.byId[value]
            ?: return RuleMutationResult.Migrated(BuiltinTocRules.STANDARD_ID, legacyRuleId)
        upsertBuiltinBinding(bookId, seed)
        return RuleMutationResult.Migrated(seed.id, legacyRuleId)
    }

    // ── 快速单规则归一化（单一状态源）────────────────────────────────────

    /**
     * 归一化到单规则：目标宽松内置启用、其余宽松内置禁用；`builtin` 或未知
     * 规则只保留标准（禁用全部宽松）。不删除绑定行（用户可再次启用），
     * 不触碰 REPLACE 规则。返回 [RuleMutationResult.Migrated]（legacyValue 为
     * null，语义为「快速单选」，非迁移）。
     */
    private suspend fun selectSingleTocRule(bookId: String, ruleId: String): RuleMutationResult {
        val target = BuiltinTocRules.byId[ruleId]
        val effectiveId = if (target == null || target.id == BuiltinTocRules.STANDARD_ID) {
            BuiltinTocRules.STANDARD_ID
        } else {
            upsertBuiltinBinding(bookId, target)
            target.id
        }
        val now = System.currentTimeMillis()
        BuiltinTocRules.seeds
            .filter { it.id != BuiltinTocRules.STANDARD_ID && it.id != effectiveId }
            .forEach { seed ->
                dao.updateEnabled(bindingStorageId(bookId, seed.id), enabled = false, updatedAt = now)
            }
        return RuleMutationResult.Migrated(effectiveId, null)
    }

    // ── 私有工具 ────────────────────────────────────────────────────────

    /** 宽松内置按书的确定性存储 id：`builtin:<语义id>:<bookId>`。 */
    private fun bindingStorageId(bookId: String, seedId: String): String =
        "$BUILTIN_BINDING_PREFIX$seedId:$bookId"

    private suspend fun upsertBuiltinBinding(bookId: String, seed: TocRule) {
        val storageId = bindingStorageId(bookId, seed.id)
        val now = System.currentTimeMillis()
        val existing = dao.getById(storageId)
        dao.upsert(
            ReaderTextRuleEntity(
                id = storageId,
                kind = RuleKind.TOC.name,
                name = seed.name,
                pattern = null,
                replacement = "",
                builtin = true,
                enabled = true,
                scope = RuleScope.PER_BOOK.name,
                book_id = bookId,
                position = seed.position,
                created_at = existing?.created_at ?: now,
                updated_at = now,
            ),
        )
    }

    /** 新规则 position：kind 内全局最大 position + 1，且不低于内置占位。 */
    private suspend fun nextPosition(kind: RuleKind): Int {
        val floor = builtinPositionFloor(kind)
        val maxPosition = dao.observeAll().first()
            .filter { it.kind == kind.name }
            .maxOfOrNull { it.position } ?: floor
        return maxOf(maxPosition, floor) + 1
    }

    private fun builtinPositionFloor(kind: RuleKind): Int =
        if (kind == RuleKind.TOC) BuiltinTocRules.seeds.maxOf { it.position } else -1

    private fun rejected(vararg errors: RuleValidationError): RuleMutationResult.Rejected =
        RuleMutationResult.Rejected(errors.toList())

    private companion object {
        const val BUILTIN_BINDING_PREFIX = "builtin:"
        const val CUSTOM_TOC_ID_PREFIX = "custom-toc-"
        const val CUSTOM_REPLACE_ID_PREFIX = "custom-replace-"
        const val CORRECTION_ID_PREFIX = "corr-"
        const val CORRECTION_RULE_ID_PREFIX = "correction:"
    }
}
