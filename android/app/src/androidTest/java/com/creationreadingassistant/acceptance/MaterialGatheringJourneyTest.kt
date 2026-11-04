package com.creationreadingassistant.acceptance

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Process
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.ui.screen.reader.sheets.ReaderExportReceipts
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.SearchIndexBookRefreshCoordinator
import com.creationreadingassistant.data.settings.ImportHistoryStore
import com.creationreadingassistant.feature.library.LibraryRootStore
import com.creationreadingassistant.feature.library.LibrarySourceIndex
import com.creationreadingassistant.feature.library.ShelfImporter
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.FileOps
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.time.Instant

/**
 * 任务65-v3 取材真实接缝仪器（TXT 测试资产 → 真实阅读 → 划词摘录+个人想法 →
 * 真实 Room → 真实检索 → 回到正确正文 → 返回原阅读处 → 进度不被污染 →
 * 归类/导出入口）。
 *
 * ## 硬边界（与 66 六项必改同标准）
 *
 * - 所有权：导入前后 activeBookIds 差集唯一，不用书名/文件名 LIKE 认领；
 * - 不删除：仪器内不做任何删除（DB 行、目录都不删），全部登记后交外层隔离合同；
 * - 不吞异常：关键 UI 动作直接调用；失败即抛出（轮询谓词除外）；
 * - 真断言：进度快照 `assertExactEquals`、摘录/想法非空且入库、检索命中可见；
 * - Throwable 全保留：tearDown 汇总所有异常为 suppressed；
 * - 无 Hilt 测试组件：与修正版 MD 仪器同一构造合同。
 *
 * ## 任务65-v4 修正（只修入口，不动其余断言）
 *
 * 65-v3 设备日志（logs/10-material-instrument.log）在 P1 失败：
 * 启动后直接等 `book-tile-<id>`，但生产 `NavHost.startDestination = Home`
 * （AppNavigation.kt:401），书架卡片在启动页不可见。
 *
 * 本版改为显式导航并逐步证明页面：
 * 启动 → 证明顶层页面（`home-screen`/`shelf-screen` testTag）→ 若在首页，
 * 点导航项「书架」（`hasText("书架") and isSelectable()`，Role.Tab）→ 证明书架页
 * → 本轮 `book-tile-<id>` → 阅读器正文就绪 **且** 本轮夹具章节标题可见。
 * P5 的「首页」跳转同样改用 selectable 语义，搜索入口改用公共 testTag
 * `home-search-btn`（不再依赖 contentDescription 文本）。
 *
 * 未增加等待时长、未放宽断言、未使用模糊匹配；失败仍然直接抛出。
 *
 * ## 任务69-v1-B：取材闭环补齐（归类最终持久化 + 同批导出入口）
 *
 * 1. **归类**：通过真实语义入口（占位符「新标签，逗号或空格分隔…」「新建分类名称…」+
 *    同行「添加」按钮）写入本轮唯一标签/分类，保存后从 **Room** 读取
 *    `inspirations.payload` 的 `tags`/`categoryIds`，并核对 `tags`/`categories` 表实际行。
 * 2. **导出**：从静态描述改为实际入口操作。
 * 3. **失败证据**：任何阶段失败先写 `mat_fail_<phase>.json` + 截图，再抛出原始异常。
 *
 * ## 任务69-v2-B 返修（Root 六项）
 *
 * 1. **import 修正**：`ActivityResultContract` 的实际包是
 *    `androidx.activity.result.contract`（v1-B 写成了 `androidx.activity.result`）。
 * 2. **归类严格断言**：本轮分类行 id 必须非空、真实存在，且必须出现在
 *    `payload.categoryIds` 中；任何悬空 id 都失败。诊断 JSON 先落盘，断言通过后才写
 *    `mat_30_save pass=true`。
 * 3. **导出边界**：删除“单独 `setContent` 装配 NotesSheet + file:// 回调”的做法；
 *    改为沿真实阅读器「更多 → 笔记与标注 → 导出」生产入口操作，并验证面板显示本轮数据。
 *    `CreateDocument` 的系统 URI 结果与文档权限**待设备**：仪器只记录
 *    `exportFileWritten=false` / `PENDING_DEVICE_SAF`，不冒充导出验收。
 * 4. **不删除未知目标**：证据文件用 `createNewFile()` 独占创建，已存在即拒绝。
 * 5. **失败取证不吞错**：取证异常以 `addSuppressed` 附加到主异常，根因保持可见。
 *
 * ## 任务69-v3-B：导出链严格闭环
 *
 * 不再“打开选择器后取消”。完整流程：
 * 1. 真实阅读器「更多 → 笔记与标注 → 导出」并验证面板显示本轮灵感；
 * 2. 系统 DocumentsUI 必须出现；通过 `uiautomator dump` 定位并点击「保存/Save」，
 *    找不到可点击节点即失败；
 * 3. 保存后必须产生**且仅产生一份**本轮新文档（按书名前缀 + 前后 id 差集）；
 * 4. 应用用 `ContentResolver` 按真实 `content://` URI 读回，核对非空、runId、
 *    真实摘录、个人想法、提供者 size 与读回字节数；
 * 5. 选择器未出现/取消/保存失败/URI 缺失/权限不可读/内容不符 → 一律失败，
 *    `mat_70_export` 不写 pass；
 * 6. 导出文档登记为本轮资产并**保留**（不删除）；未知同名/后代/探测失败不处理。
 *
 * ## 任务69-v4-B：导出适配收口
 *
 * 1. **不再写公共临时文件**：保存按钮改为遍历 UiAutomation 无障碍节点树定位并点击，
 *    彻底去掉 `/data/local/tmp` 的 `uiautomator dump` + `rm -f`（无独占创建/删除授权）。
 * 2. **承接系统真实 URI**：通过生产 `ReaderExportReceipts` 取得 `CreateDocument` 实际返回的
 *    URI，应用按该 URI 读回；不再按 MediaStore 差集拼 `content://media/...`。
 *    MediaStore 差集降级为辅助证据（提供者非媒体库时为空属正常）。
 * 3. **提前登记资产**：取得 URI 后立即 `recordAsset` 并写 `mat_70_export_asset`，
 *    再执行读取/内容断言；读取失败也保留已创建文档的线索。
 * 4. **保留异常链**：URI 读取失败包装为 `AssertionError` 时附原异常为 cause；
 *    取证失败继续 `addSuppressed`，不覆盖主错。
 *
 * ## 任务70-v1-A：保存位置确认与失败结果收口
 *
 * 1. **保存前真实导航**：打开 DocumentsUI 的 roots 抽屉 → 点「Downloads」→
 *    确认当前位置 → 把文件名改成含 runId 的本轮唯一名称。任何一步无法确认即失败，
 *    绝不直接点保存，也不沿用选择器上次停留目录。
 * 2. **不只看 authority**：导航/位置/文件名在点击保存前确认；保存后再断言
 *    URI authority 是约定的 Downloads 提供者，display name 必须等于本轮唯一名称
 *    （被系统静默改名通常意味着同名冲突，按失败处理）。
 * 3. **失败/取消可区分**：通过生产回执 `ReaderExportReceipts` 读取真实结果；
 *    写入失败保留原始异常并立即失败（不再等超时），用户取消单独识别，均不得写成成功。
 */
/** 79-v3-BC 系统文档选择器及提供者包名白名单校验（顶层函数保证嵌套类直接可见） */
internal fun isDocumentsUiPackage(pkg: CharSequence?): Boolean =
    pkg == "com.android.documentsui" || pkg == "com.google.android.documentsui"

@RunWith(AndroidJUnit4::class)
class MaterialGatheringJourneyTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var runId: String
    private lateinit var resultsDir: File
    private lateinit var db: AppDatabase
    private lateinit var importer: ShelfImporter
    private lateinit var ledger: J1RealReaderFixtures.RunLifecycleLedger
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null

    private val createdAssets = JSONArray()
    private var lastPhase: String = "start"

    /**
     * 80-v2-A：本轮**真实选区**，保存前从选区工具条微条的语义取得（唯一基准）。
     *
     * 不取整棵语义树的最长文本（那必然是渲染后的整页正文），也不在保存后从 payload
     * 反推——payload 只能被它校验，不能当它的来源。
     */
    private var capturedSelection: String = ""

    /**
     * 长按后语义树里最长的文本 = **渲染后的整页正文**（带排版硬换行、按视口截断），
     * 不是选区。只用作 mat_60「临时查阅到达正确正文」的页面可见性探针与诊断留痕。
     */
    private var renderedPageProbe: String = ""

    // ── 69-v1-B：本轮归类与导出资产 ──────────────────────────────────────────
    /** 本轮唯一标签/分类名（含 runId，避免与既有行冲突）。 */
    private var uniqueTag: String = ""
    private var uniqueCategory: String = ""

    private fun ideasText(): String = "个人想法：这条摘录来自本轮真实阅读，标记 $runId。"

    // ── 夹具（TXT 测试资产，不触碰正式书库） ─────────────────────────────────

    private fun fixtureFile(): File =
        File(context.getExternalFilesDir(null), "mat-fixtures/$FIXTURE_NAME-$runId.txt").apply {
            parentFile?.mkdirs()
        }

    private fun txtBody(): String = buildString {
        appendLine("第1章 取材真实接缝")
        appendLine()
        (1..18).forEach { p ->
            appendLine(
                "第${p}段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，" +
                    "不包含任何正式书库内容。"
            )
            appendLine()
        }
        appendLine("第2章 后续正文")
        appendLine()
        (1..18).forEach { p ->
            appendLine("第2章第${p}段：继续提供可滚动、可定位的中性文本，供真实阅读与回源验收使用。")
            appendLine()
        }
    }

    // ── DB / 生产导入构造 ────────────────────────────────────────────────────

    private fun rawOne(sql: String): String? =
        db.openHelper.writableDatabase.query(sql).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }

    private fun activeBookIds(): Set<String> {
        val ids = LinkedHashSet<String>()
        db.openHelper.readableDatabase
            .query("SELECT id FROM books WHERE deleted_at IS NULL")
            .use { c -> while (c.moveToNext()) ids.add(c.getString(0)) }
        return ids
    }

    private object RealFileOps : FileOps {
        override fun renameTo(source: File, dest: File): Boolean = source.renameTo(dest)
        override fun copyTo(source: File, dest: File) {
            source.copyTo(dest, overwrite = true)
        }
    }

    private fun buildImporter(): ShelfImporter {
        val bookDao = db.bookDao()
        val contentDao = db.bookContentDao()
        val fileDao = db.bookFileDao()
        val progressDao = db.readingProgressDao()
        val repository = BookRepository(
            bookDao = bookDao,
            bookContentDao = contentDao,
            bookFileDao = fileDao,
            progressDao = progressDao,
            sessionDao = db.readingSessionDao(),
            noteDao = db.noteDao(),
            highlightDao = db.highlightDao(),
            inspirationDao = db.inspirationDao(),
            bookTagDao = db.bookTagDao(),
            bookCategoryDao = db.bookCategoryDao(),
            shelfBookDao = db.shelfBookDao(),
            chapterReadDao = db.chapterReadDao(),
            context = context,
        )
        val epubRepository = EpubRepository(
            context = context,
            bookDao = bookDao,
            readingProgressDao = progressDao,
            ioDispatcher = Dispatchers.IO,
            fileOps = RealFileOps,
        )
        return ShelfImporter(
            context = context,
            repository = repository,
            bookDao = bookDao,
            bookContentDao = contentDao,
            bookFileDao = fileDao,
            epubRepository = epubRepository,
            importHistoryStore = ImportHistoryStore(context, CoroutineScope(Dispatchers.IO)),
            sourceIndex = LibrarySourceIndex(db.librarySourceRefDao()),
            rootStore = LibraryRootStore(context, CoroutineScope(Dispatchers.IO)),
            ioDispatcher = Dispatchers.IO,
            indexRefreshCoordinator = mockk<SearchIndexBookRefreshCoordinator>(relaxed = true),
        )
    }

    // ── 等待/语义工具 ────────────────────────────────────────────────────────

    private fun waitFor(phase: String, timeoutMs: Long, description: String, block: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val observed = try {
                block()
            } catch (t: Throwable) {
                false
            }
            if (observed) return true
            composeRule.waitForIdle()
            Thread.sleep(POLL_INTERVAL_MS)
        }
        android.util.Log.w(TAG, "$phase 等待超时（$timeoutMs ms）：$description")
        return false
    }

    private fun readerReady(): Boolean =
        composeRule.onAllNodesWithContentDescription("阅读正文已就绪").fetchSemanticsNodes().isNotEmpty()

    private fun readerGone(): Boolean =
        composeRule.onAllNodesWithContentDescription("阅读正文已就绪").fetchSemanticsNodes().isEmpty()

    private fun textVisible(text: String): Boolean =
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    // ── 顶层导航入口（65-v4） ────────────────────────────────────────────────
    //
    // 原失败（logs/10-material-instrument.log）：启动后直接等 `book-tile-<id>`，
    // 但生产 NavHost.startDestination = Home（AppNavigation.kt:401），
    // 书架卡片在启动页不可见。修正 = 显式执行 首页 → 书架 的真实导航并逐步证明页面。
    //
    // 书架页标题「书架」本身 clickable（打开整理器），所以不能用文本盲点；
    // 必须用导航项独有的 selectable 语义（Role.Tab）精确定位。

    private fun topLevelPage(): String? = when {
        composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty() -> "shelf"
        composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty() -> "home"
        else -> null
    }

    private fun clickTopLevelTab(label: String) {
        composeRule.onNode(hasText(label) and isSelectable()).performClick()
        composeRule.waitForIdle()
    }

    private fun ensureOnShelf(phase: String): Boolean {
        val appeared = waitFor(phase, UI_TIMEOUT_MS, "首页或书架页面出现") { topLevelPage() != null }
        if (!appeared) return false
        if (topLevelPage() == "shelf") return true
        clickTopLevelTab("书架")
        return waitFor(phase, UI_TIMEOUT_MS, "书架页面出现") { topLevelPage() == "shelf" }
    }

    /** 入口失败时的**有界**诊断：只在失败时写，不改变断言结论、不吞异常。 */
    private fun writeEntryDiagnostics(phase: String, bookId: String) {
        val details = linkedMapOf<String, Any?>(
            "detectedPage" to (topLevelPage() ?: "<none>"),
            "homeScreenNodes" to composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().size,
            "shelfScreenNodes" to composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().size,
            "bookTileNodes" to composeRule.onAllNodesWithTag("book-tile-$bookId").fetchSemanticsNodes().size,
            "selectableNodes" to composeRule.onAllNodes(isSelectable()).fetchSemanticsNodes().size,
            "homeTabVisible" to composeRule.onAllNodes(hasText("首页") and isSelectable())
                .fetchSemanticsNodes().isNotEmpty(),
            "shelfTabVisible" to composeRule.onAllNodes(hasText("书架") and isSelectable())
                .fetchSemanticsNodes().isNotEmpty(),
            "semanticsTreeHead" to runCatching {
                composeRule.onRoot().printToString(maxDepth = 5).take(4000)
            }.getOrElse { "<unavailable: $it>" },
        )
        writeEvidence(phase, false, details)
    }

    private fun shelfEntryProven(phase: String, bookId: String): Boolean {
        val ok = ensureOnShelf(phase)
        if (!ok) writeEntryDiagnostics(phase, bookId)
        return ok
    }

    /**
     * 80-v2-A：从选区工具条微条读取本轮真实选区。
     *
     * 必须唯一命中：找不到或命中多个都直接失败，**不回退**到「全树最长文本」之类的猜测。
     */
    private fun selectionToolbarExcerpt(): String {
        val nodes = composeRule.onAllNodesWithTag(
            ExportExcerptRoundTripVerdict.SELECTION_TOOLBAR_EXCERPT_TAG,
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals(
            "选区工具条微条必须唯一命中（语义标识 " +
                "${ExportExcerptRoundTripVerdict.SELECTION_TOOLBAR_EXCERPT_TAG}），实得 ${nodes.size} 个",
            1, nodes.size,
        )
        return nodes.first().config.getOrNull(SemanticsProperties.Text)
            ?.joinToString("") { it.text }
            .orEmpty()
    }

    /** 递归收集当前语义树里的全部文本（只用于整页正文探针，不作为摘录来源）。 */
    private fun allSemanticTexts(): List<String> {
        val out = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { out.add(it.text) }
            node.children.forEach { child -> walk(child) }
        }
        walk(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return out
    }

    // ── 75-v1-B：灵感 sheet 字段的精确定位与保存前读回 ───────────────────────
    //
    // 原实现用全局 hasSetTextAction() 的第 0/1 个节点猜“标题 / 我的想法”，而同一 sheet
    // 还有「新建分类名称…」「新标签，逗号或空格分隔…」两个 BasicTextField。
    // 改为：先按字段身份（标签语义）定位可编辑节点并证明唯一匹配；填写后在保存前读回，
    // 确认本轮内容确实进入各自字段。不使用全局顺序、最后一个输入框或最长文本猜字段。

    private fun subtreeTexts(node: SemanticsNode): List<String> {
        val out = mutableListOf<String>()
        node.config.getOrNull(SemanticsProperties.Text)?.forEach { out.add(it.text) }
        node.config.getOrNull(SemanticsProperties.EditableText)?.let { out.add(it.text) }
        node.children.forEach { child -> out.addAll(subtreeTexts(child)) }
        return out
    }

    private fun editableTextOf(node: SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.EditableText)?.text
            ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
            ?: ""

    private fun sameBounds(a: SemanticsNode, b: SemanticsNode): Boolean {
        val x = a.boundsInRoot
        val y = b.boundsInRoot
        return kotlin.math.abs(x.top - y.top) < 1f &&
            kotlin.math.abs(x.left - y.left) < 1f &&
            kotlin.math.abs(x.bottom - y.bottom) < 1f &&
            kotlin.math.abs(x.right - y.right) < 1f
    }

    private data class FieldResolution(
        val node: SemanticsNode?,
        val unique: Boolean,
        val diagnostics: Map<String, Any?>,
    )

    private fun resolveEditableField(label: String): FieldResolution {
        val editable = composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        val diag = linkedMapOf<String, Any?>(
            "fieldLabel" to label,
            "editableNodeCount" to editable.size,
        )
        if (editable.isEmpty()) {
            diag["matchStrategy"] = "no-editable-node"
            return FieldResolution(null, false, diag)
        }
        // 策略 1：可编辑节点自身子树内出现该字段标签（label/占位符被合并进字段语义时命中）。
        val labelled = editable.filter { node -> subtreeTexts(node).any { it.contains(label) } }
        diag["labelMatchCount"] = labelled.size
        diag["allEditableTextLengths"] = editable.joinToString(",") { editableTextOf(it).length.toString() }
        diag["allEditableContainMarker"] = editable.joinToString(",") {
            editableTextOf(it).contains(runId).toString()
        }
        if (labelled.size == 1) {
            diag["matchStrategy"] = "subtree-label"
            return FieldResolution(labelled[0], true, diag)
        }
        // 策略 2：与字段标签垂直距离最近的可编辑节点（沿用 addChipViaUi 已验证的口径）。
        val labelNodes = composeRule.onAllNodes(hasText(label, substring = true)).fetchSemanticsNodes()
        diag["labelNodeCount"] = labelNodes.size
        if (labelNodes.isEmpty()) {
            diag["matchStrategy"] = "no-label-node"
            return FieldResolution(null, false, diag)
        }
        val labelY = labelNodes[0].boundsInRoot.center.y
        val ranked = editable.mapIndexed { index, node ->
            index to kotlin.math.abs(node.boundsInRoot.center.y - labelY)
        }.sortedBy { it.second }
        diag["matchStrategy"] = "nearest-by-y"
        diag["nearestDistancePx"] = ranked.firstOrNull()?.second?.toInt()
        diag["secondNearestDistancePx"] = ranked.getOrNull(1)?.second?.toInt()
        val unique = ranked.isNotEmpty() &&
            (ranked.size == 1 || (ranked[1].second - ranked[0].second) > 1f)
        diag["uniqueMinimum"] = unique
        return FieldResolution(if (unique) editable[ranked[0].first] else null, unique, diag)
    }

    private fun editableInteractionFor(target: SemanticsNode): SemanticsNodeInteraction {
        val nodes = composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        val index = nodes.indexOfFirst { sameBounds(it, target) }
        assertTrue("已解析的字段节点必须仍在当前可编辑节点集合中", index >= 0)
        return composeRule.onAllNodes(hasSetTextAction())[index]
    }

    /** 保存前读回：按解析出的同一节点取回当前可编辑文本；找不到返回 null（调用方据此失败）。 */
    private fun readBack(target: SemanticsNode): String? {
        val nodes = composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        return nodes.firstOrNull { sameBounds(it, target) }?.let { editableTextOf(it) }
    }

    private fun writeEvidence(phase: String, ok: Boolean, details: Map<String, Any?>) {
        lastPhase = phase
        val obj = JSONObject().put("phase", phase).put("pass", ok)
            .put("at", Instant.now().toString())
            .put("runId", runId)
            .put("pid", Process.myPid())
            .put("packageName", context.packageName)
        details.forEach { (k, v) -> obj.put(k, v?.toString() ?: "") }
        writeNewFile(File(resultsDir, "$phase.json"), obj.toString(2))
        android.util.Log.i(TAG, "$phase pass=$ok ${obj.toString(0)}")
    }

    private fun recordAsset(kind: String, path: String, reason: String) {
        createdAssets.put(JSONObject().put("kind", kind).put("path", path).put("retainReason", reason))
    }

    // ── 69-v1-B：失败先取证 ──────────────────────────────────────────────────

    private fun markPhase(phase: String) {
        lastPhase = phase
    }

    /** 任何阶段失败：先写 JSON + 截图，再抛出原始异常（不吞、不覆盖）。 */
    private fun writeFailureEvidence(phase: String, throwable: Throwable) {
        try {
            val obj = JSONObject()
                .put("phase", phase)
                .put("pass", false)
                .put("at", Instant.now().toString())
                .put("runId", runId)
                .put("pid", Process.myPid())
                .put("exception", throwable.javaClass.name)
                .put("message", throwable.message ?: "")
                .put("stack", throwable.stackTraceToString().take(8000))
                .put("lastPhase", lastPhase)
            writeNewFile(File(resultsDir, "mat_fail_$phase.json"), obj.toString(2))
        } catch (t: Throwable) {
            // 取证失败不得吞掉主异常：附加为 suppressed，保持根因可见。
            throwable.addSuppressed(t)
        }
        try {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                ?.let { bmp: Bitmap ->
                    File(resultsDir, "mat_fail_$phase.png").outputStream()
                        .use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bmp.recycle()
                }
        } catch (t: Throwable) {
            throwable.addSuppressed(t)
        }
    }

    /** 新资产创建：目标已存在即拒绝（不删除未知同名文件）。 */
    private fun writeNewFile(file: File, content: String) {
        if (!file.createNewFile()) {
            throw IllegalStateException("证据文件已存在，拒绝覆盖：${file.absolutePath}")
        }
        file.writeText(content)
    }

    /** 79-v2-B：单次无障碍探测抽样快照，包含完整目标根与同次采样的有界元数据，杜绝跨采样拼接。 */
    internal data class DocumentsUiSample(
        val root: AccessibilityNodeInfo?,
        val activePackage: String,
        val windowCount: Int,
        val windowPackages: String,
        val rootNull: Boolean,
        val foundInWindows: Boolean,
        val ambiguity: Boolean,
        val candidateCount: Int,
        val errorStage: String,
        val lastError: String,
        val cause: Throwable? = null,
        val detector: String = "active/interactive accessibility root (Android/Google DocumentsUI)",
    ) {
        fun toEvidenceMap(): Map<String, Any?> = mapOf(
            "activePackage" to activePackage,
            "windowCount" to windowCount,
            "windowPackages" to windowPackages,
            "rootNull" to rootNull,
            "foundInWindows" to foundInWindows,
            "ambiguity" to ambiguity,
            "candidateCount" to candidateCount,
            "errorStage" to errorStage,
            "lastError" to lastError,
            "detector" to detector,
        )
    }

    /** 79-v2-B：无障碍候选窗口与可交互属性抽象，支持生产环境与脱机定向验证共享同一套判定逻辑。 */
    internal class DocumentsUiWindowCandidate(
        val packageName: String,
        val isFocused: Boolean,
        val isActive: Boolean,
        val root: AccessibilityNodeInfo?,
    )

    /** 79-v2-B：文档选择器无障碍探测探针接口与默认实现。 */
    internal class DocumentsUiPickerProbe(
        private val ensureFlags: () -> Unit,
        private val getActiveRoot: () -> AccessibilityNodeInfo?,
        private val getWindowCandidates: () -> List<DocumentsUiWindowCandidate>,
    ) {
        fun sample(): DocumentsUiSample {
            var errorStage = ""
            var lastError = ""
            var cause: Throwable? = null

            // 1. 确保辅助功能标志（记录平台异常阶段并保留原始异常对象，不吞错）
            try {
                ensureFlags()
            } catch (e: Exception) {
                errorStage = "ensure_flags"
                lastError = "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
                cause = e
            }

            // 2. 查活动根节点
            var activeRoot: AccessibilityNodeInfo? = null
            var rootNull = false
            var activePkg = ""
            if (errorStage.isEmpty()) {
                try {
                    activeRoot = getActiveRoot()
                    if (activeRoot == null) {
                        rootNull = true
                    } else {
                        activePkg = activeRoot.packageName?.toString().orEmpty()
                    }
                } catch (e: Exception) {
                    errorStage = "query_active_root"
                    lastError = "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
                    cause = e
                }
            }

            // 3. 查窗口列表（单次读取产生候选、数量与包名，不单独二次读取 getWindowCount 且不吞错）
            var candidates: List<DocumentsUiWindowCandidate> = emptyList()
            val windowPkgs = mutableListOf<String>()
            var windowCount = 0
            if (errorStage.isEmpty()) {
                try {
                    candidates = getWindowCandidates()
                    windowCount = candidates.size
                    for (c in candidates) {
                        if (c.packageName.isNotBlank() && c.packageName !in windowPkgs) {
                            windowPkgs.add(c.packageName)
                        }
                    }
                } catch (e: Exception) {
                    errorStage = "query_windows"
                    lastError = "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
                    cause = e
                }
            }

            // 4. 目标窗口消歧与选择（必须选中具有前台焦点或活跃状态的可操作目标）
            var foundRoot: AccessibilityNodeInfo? = null
            var foundInWindows = false
            var ambiguity = false
            var validCandidateCount = 0

            if (errorStage.isEmpty()) {
                // 筛选包名为 DocumentsUI 且处于前台可交互状态（focused 或 active）的有效窗口候选
                // 严格排除后台同包窗口（!isFocused && !isActive）
                val validCandidates = candidates.filter { c ->
                    isDocumentsUiPackage(c.packageName) && (c.isFocused || c.isActive)
                }
                validCandidateCount = validCandidates.size

                if (validCandidates.size == 1) {
                    // 唯一前台候选直接选中
                    foundRoot = validCandidates.first().root
                    foundInWindows = true
                } else if (validCandidates.size > 1) {
                    // 多候选时优先按输入焦点消歧
                    val focused = validCandidates.filter { it.isFocused }
                    if (focused.size == 1) {
                        foundRoot = focused.first().root
                        foundInWindows = true
                    } else if (focused.isEmpty() && validCandidates.filter { it.isActive }.size == 1) {
                        foundRoot = validCandidates.first { it.isActive }.root
                        foundInWindows = true
                    } else {
                        // 无法决出唯一目标，标记歧义并拒绝盲选
                        ambiguity = true
                    }
                } else {
                    // 窗口列表未检出有效前台候选，回退检查 activeRoot 是否为 DocumentsUI
                    if (activeRoot != null && isDocumentsUiPackage(activePkg)) {
                        foundRoot = activeRoot
                        foundInWindows = false
                    }
                }
            }

            val resolvedPkg = when {
                foundRoot != null -> foundRoot.packageName?.toString().orEmpty().ifBlank {
                    candidates.firstOrNull { it.root === foundRoot }?.packageName.orEmpty()
                }.ifBlank { activePkg }
                isDocumentsUiPackage(activePkg) -> activePkg
                else -> activePkg
            }

            return DocumentsUiSample(
                root = foundRoot,
                activePackage = resolvedPkg,
                windowCount = windowCount,
                windowPackages = windowPkgs.take(8).joinToString(","),
                rootNull = rootNull,
                foundInWindows = foundInWindows,
                ambiguity = ambiguity,
                candidateCount = validCandidateCount,
                errorStage = errorStage,
                lastError = lastError,
                cause = cause,
            )
        }
    }

    private val defaultPickerProbe by lazy {
        DocumentsUiPickerProbe(
            ensureFlags = {
                val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
                val info = automation.serviceInfo ?: AccessibilityServiceInfo()
                if ((info.flags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS) == 0) {
                    info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    automation.serviceInfo = info
                }
            },
            getActiveRoot = {
                InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            },
            getWindowCandidates = {
                val windows = InstrumentationRegistry.getInstrumentation().uiAutomation.windows
                windows.map { w ->
                    val r = w.root
                    DocumentsUiWindowCandidate(
                        packageName = r?.packageName?.toString().orEmpty(),
                        isFocused = w.isFocused,
                        isActive = w.isActive,
                        root = r,
                    )
                }
            },
        )
    }

    private var pickerProbe: DocumentsUiPickerProbe = defaultPickerProbe

    /** 79-v3-BC 记录单次最新选择器快照与首个平台异常（防覆盖）。 */
    private var latestPickerSample: DocumentsUiSample = DocumentsUiSample(
        root = null,
        activePackage = "",
        windowCount = 0,
        windowPackages = "",
        rootNull = false,
        foundInWindows = false,
        ambiguity = false,
        candidateCount = 0,
        errorStage = "",
        lastError = "",
    )
    private var firstPickerErrorSample: DocumentsUiSample? = null

    private fun sampleDocumentsUi(): DocumentsUiSample {
        val sample = pickerProbe.sample()
        latestPickerSample = sample
        if (sample.lastError.isNotBlank() && firstPickerErrorSample == null) {
            firstPickerErrorSample = sample
        }
        return sample
    }

    private fun documentsUiRoot(): AccessibilityNodeInfo? =
        sampleDocumentsUi().root

    /** 79-v2-B：通过真实 DocumentsUI 窗口根节点判定选择器是否在前台。 */
    private fun documentsUiFocused(): Boolean =
        documentsUiRoot() != null

    private fun nodeLabel(node: AccessibilityNodeInfo): String =
        (node.text?.toString() ?: "").ifBlank {
            node.contentDescription?.toString() ?: ""
        }.trim()

    private fun collectAccessibilityNodes(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var scanned = 0
        while (queue.isNotEmpty() && scanned < MAX_A11Y_NODES) {
            val node = queue.removeFirst()
            scanned++
            if (predicate(node)) out.add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return out
    }

    private fun accessibilityLabels(root: AccessibilityNodeInfo): List<String> =
        collectAccessibilityNodes(root) { nodeLabel(it).isNotBlank() }
            .map { nodeLabel(it) }
            .distinct()

    /** 按标签点击；标签节点本身不可点时向上找可点击祖先。 */
    private fun clickLabel(labels: Set<String>): Boolean {
        val root = documentsUiRoot() ?: return false
        val matches = collectAccessibilityNodes(root) { nodeLabel(it) in labels }
        for (node in matches) {
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) target = target.parent
            if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return false
    }

    private fun waitForNodeLabel(labels: Set<String>, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val root = documentsUiRoot()
            if (root != null && accessibilityLabels(root).any { it in labels }) return true
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return false
    }

    /** 把选择器里的建议文件名改为本轮唯一名称，并读回确认。 */
    private fun setUniqueDocumentName(expectedName: String): Boolean {
        val root = documentsUiRoot() ?: return false
        val edits = collectAccessibilityNodes(root) {
            it.className?.toString()?.contains("EditText", ignoreCase = true) == true
        }
        if (edits.isEmpty()) return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, expectedName)
        }
        if (!edits.first().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false
        val deadline = System.currentTimeMillis() + 3_000L
        while (System.currentTimeMillis() < deadline) {
            val current = documentsUiRoot() ?: return false
            val confirmed = collectAccessibilityNodes(current) {
                it.className?.toString()?.contains("EditText", ignoreCase = true) == true
            }.any { it.text?.toString() == expectedName }
            if (confirmed) return true
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return false
    }

    /** MediaStore 可见的 Downloads 文件名（辅助冲突核对；不可见时不当作证据）。 */
    private fun queryDownloadedDisplayNames(): Set<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptySet()
        val names = linkedSetOf<String>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads.DISPLAY_NAME),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) c.getString(0)?.let { names.add(it) }
            }
        }
        return names
    }

    /**
     * 选择器里一个候选节点及其最近可点击祖先的结构化证据。
     *
     * 「整棵树里有『下载』文字」不能证明找到了正确的可点击导航项：工具栏标题/面包屑同样带
     * 「下载」，抽屉开不开都成立。因此导航决策只使用**可操作**节点，并把
     * class / clickable / enabled / selected / checked / visible / bounds / 可点击祖先
     * 一并记录下来，失败时可直接定位原因。
     */
    private data class PickerNode(
        val label: String,
        val className: String,
        val clickable: Boolean,
        val enabled: Boolean,
        val selected: Boolean,
        val checked: Boolean,
        val visible: Boolean,
        val bounds: String,
        val clickTargetClass: String,
        val clickTargetLabel: String,
        val clickTargetClickable: Boolean,
    ) {
        val actionable: Boolean get() = enabled && visible && (clickable || clickTargetClickable)

        fun toEvidence(): String =
            "$label|class=$className|clickable=$clickable|enabled=$enabled" +
                "|selected=$selected|checked=$checked|visible=$visible|bounds=$bounds" +
                "|target=$clickTargetClass($clickTargetLabel,clickable=$clickTargetClickable)"
    }

    /**
     * 只取**新鲜的 roots_list 导航子树**作为作用域。
     *
     * 面包屑、工具栏标题、文件列表里都可能出现同名「下载」文字；从整个 root 收集候选会误选，
     * 因此导航相关的候选与点击一律限定在该子树内；祖先遍历也不越过它的边界。
     */
    private fun rootsListNode(): AccessibilityNodeInfo? {
        val root = documentsUiRoot() ?: return null
        return collectAccessibilityNodes(root) {
            it.viewIdResourceName?.endsWith("roots_list") == true
        }.firstOrNull()
    }

    private fun describePickerNodes(labels: Set<String>): List<PickerNode> {
        val scope = rootsListNode() ?: return emptyList()
        return collectAccessibilityNodes(scope) { nodeLabel(it) in labels }.map { node ->
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) {
                if (target === scope) break
                target = target.parent
            }
            val rect = Rect()
            node.getBoundsInScreen(rect)
            PickerNode(
                label = nodeLabel(node),
                className = node.className?.toString().orEmpty(),
                clickable = node.isClickable,
                enabled = node.isEnabled,
                selected = node.isSelected,
                checked = node.isChecked,
                visible = node.isVisibleToUser,
                bounds = "${rect.left},${rect.top},${rect.right},${rect.bottom}",
                clickTargetClass = target?.className?.toString().orEmpty(),
                clickTargetLabel = target?.let { nodeLabel(it) }.orEmpty(),
                clickTargetClickable = target?.isClickable == true,
            )
        }
    }

    /** 抽屉是否真的展开：以 DocumentsUI 的 roots_list 容器出现为结构判据。 */
    private fun drawerRootsPresent(): Boolean = rootsListNode() != null

    private fun waitForDrawerRoots(present: Boolean, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (drawerRootsPresent() == present) return true
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return false
    }

    /**
     * 等到「可操作的」目标条目真的出现。
     *
     * 抽屉有展开动画；旧实现被面包屑里的同名文字立刻满足，点击时抽屉尚未就绪，
     * 于是整条导航必然失败。这里必须等到节点可操作才算就绪。
     */
    private fun waitForActionablePickerNode(labels: Set<String>, timeoutMs: Long): PickerNode? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            describePickerNodes(labels).firstOrNull { it.actionable }?.let { return it }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return null
    }

    /**
     * 在**新鲜的 roots_list 子树**内按 label+class 重新确认目标身份，再对自身及祖先依次尝试
     * ACTION_CLICK；祖先遍历不越过 roots_list 边界，避免点到子树外的同名节点。
     */
    private fun clickPickerNode(node: PickerNode): Pair<Boolean, String> {
        val scope = rootsListNode() ?: return false to "roots_list_missing_at_click"
        val matches = collectAccessibilityNodes(scope) {
            nodeLabel(it) == node.label &&
                it.className?.toString().orEmpty() == node.className &&
                it.isVisibleToUser
        }
        if (matches.isEmpty()) return false to "target_absent_in_fresh_roots_list"
        for (match in matches) {
            var target: AccessibilityNodeInfo? = match
            var level = 0
            while (target != null) {
                if (target.isClickable && target.isEnabled &&
                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                ) {
                    return true to "clicked@level$level:${target.className}"
                }
                if (target === scope) break
                target = target.parent
                level++
            }
        }
        return false to "no_clickable_target_accepted(matches=${matches.size})"
    }

    /** 当前目录的真实位置指示：DocumentsUI 的面包屑段（按界面顺序）。 */
    private fun breadcrumbSegments(): List<String> {
        val root = documentsUiRoot() ?: return emptyList()
        return collectAccessibilityNodes(root) {
            it.viewIdResourceName?.endsWith("breadcrumb_text") == true
        }.map { nodeLabel(it) }.filter { it.isNotBlank() }
    }

    /**
     * 位置是否为约定的 Downloads 根。
     *
     * 依据：面包屑**恰有一段**且该段属于下载标签集合。
     * 只有一段才能说明「当前就在该根下」；两段及以上说明还在某个子目录里
     * （诊断实测：初始为 "MuMu 共享文件夹" → "Download" 两段，点击抽屉「下载」后变为一段「下载」）。
     */
    private fun locationIsDownloadsRoot(): Boolean {
        val segments = breadcrumbSegments()
        return segments.size == 1 && segments.first() in DOWNLOAD_LABELS
    }

    private fun waitForDownloadsRoot(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (locationIsDownloadsRoot()) return true
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return false
    }

    /**
     * 保存前真实导航并确认约定的文档提供者位置（Documents 提供者的 Downloads 根）。
     *
     * 判据全部是**结构/状态**证据，不是「某段文字可见」：
     *  - 抽屉是否打开：DocumentsUI 的 `roots_list` 容器真实出现（不是标签差集——根名
     *    「下载」「MuMu 共享文件夹」在面包屑里本来就存在，抽屉打开不会新增这些标签）；
     *  - 目标条目是否就绪：出现**可操作**（自身或最近可点击祖先 clickable、enabled、
     *    visibleToUser）的「下载」条目，而不是被面包屑里的同名文字立刻满足；
     *  - 导航是否生效：抽屉**真的关闭**（`roots_list` 消失）。
     * 任何一步无法确认都返回 prepared=false；调用方必须失败，不能直接点保存。
     */
    private fun prepareDocumentsUiDestination(expectedName: String): Map<String, Any?> {
        val before = documentsUiRoot()?.let { accessibilityLabels(it) }?.toSet().orEmpty()
        val rootsOpened = clickLabel(ROOT_DRAWER_LABELS)
        val drawerOpened = rootsOpened && waitForDrawerRoots(true, PICKER_TIMEOUT_MS)
        val candidate = if (drawerOpened) {
            waitForActionablePickerNode(DOWNLOAD_LABELS, PICKER_TIMEOUT_MS)
        } else {
            null
        }
        var downloadClicked = false
        var clickOutcome = ""
        if (candidate != null) {
            val (clicked, outcome) = clickPickerNode(candidate)
            downloadClicked = clicked
            clickOutcome = outcome
        }
        // 抽屉关闭只是必要条件；位置必须由**实际可取得的位置指示**确认。
        val drawerClosed = downloadClicked && waitForDrawerRoots(false, PICKER_TIMEOUT_MS)
        val atDownloadsRoot = drawerClosed && waitForDownloadsRoot(PICKER_TIMEOUT_MS)
        val locationConfirmed = atDownloadsRoot
        val fileNameSet = locationConfirmed && setUniqueDocumentName(expectedName)
        val nameConflict = expectedName in queryDownloadedDisplayNames()
        val after = documentsUiRoot()?.let { accessibilityLabels(it) }?.toSet().orEmpty()
        val candidates = if (drawerOpened) describePickerNodes(DOWNLOAD_LABELS) else emptyList()
        val observedLocation = breadcrumbSegments()
        return mapOf(
            "prepared" to (rootsOpened && drawerOpened && locationConfirmed && fileNameSet && !nameConflict),
            "rootsOpened" to rootsOpened,
            "drawerOpened" to drawerOpened,
            "drawerClosedAfterClick" to drawerClosed,
            "downloadVisible" to (candidate != null),
            "downloadClicked" to downloadClicked,
            "locationConfirmed" to locationConfirmed,
            "atDownloadsRoot" to atDownloadsRoot,
            "observedLocation" to observedLocation.joinToString(" > "),
            "observedLocationSegments" to observedLocation.size,
            "fileNameSet" to fileNameSet,
            "clickOutcome" to clickOutcome,
            "candidateCount" to candidates.size,
            "actionableCount" to candidates.count { it.actionable },
            "candidates" to candidates.take(8).joinToString(" ;; ") { it.toEvidence() },
            "expectedName" to expectedName,
            "nameConflict" to nameConflict,
            "providerLocation" to observedLocation.joinToString(" > "),
            "providerLocationBasis" to "documentsui:id/breadcrumb_text 观测值（非硬编码）",
            "labelsBefore" to before.joinToString("|"),
            "labelsAfter" to after.joinToString("|"),
        )
    }

    /**
     * 在系统文档选择器中点击「保存/Save」。
     *
     * 直接遍历 UiAutomation 的无障碍节点树定位并点击，**不写任何公共临时文件**
     * （旧实现向 /data/local/tmp 写 XML 再 rm，既无独占创建证明，也缺删除授权）。
     * 找不到可点击节点即返回 false，由调用方断言失败。
     */
    private fun tapDocumentsUiSave(): Boolean {
        val root = documentsUiRoot() ?: return false
        val texts = mutableListOf<String>()
        var scanned = 0
        var clicked = false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && scanned < MAX_A11Y_NODES && !clicked) {
            val node = queue.removeFirst()
            scanned++
            val label = (node.text?.toString() ?: "").ifBlank {
                node.contentDescription?.toString() ?: ""
            }
            if (label.isNotBlank()) texts.add(label)
            if (label in SAVE_LABELS) {
                var target: AccessibilityNodeInfo? = node
                while (target != null && !target.isClickable) target = target.parent
                if (target != null) {
                    clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        saveClickDiagnostics = mapOf(
            "scannedNodes" to scanned,
            "labelsSample" to texts.take(40).joinToString("|"),
            "clicked" to clicked,
        )
        return clicked
    }

    private var saveClickDiagnostics: Map<String, Any?> = emptyMap()

    private data class DocumentMeta(val name: String?, val size: Long?)

    /** 按系统返回的真实 URI 读取文档元数据（提供者声明值，可为空）。 */
    private fun queryDocumentMeta(uri: Uri): DocumentMeta {
        var name: String? = null
        var size: Long? = null
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        return DocumentMeta(name, size)
    }

    /** 本轮导出前/后的 Downloads 文档 id（按本轮书名前缀过滤）。 */
    private fun queryDownloadedMarkdownIds(prefix: String): Set<Long> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptySet()
        val ids = linkedSetOf<Long>()
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
            "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
            arrayOf("$prefix%"),
            null,
        )?.use { c -> while (c.moveToNext()) ids.add(c.getLong(0)) }
        return ids
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // ── 69-v1-B：归类真实入口与持久化核对 ────────────────────────────────────

    /**
     * 通过真实语义入口（占位符 + 同行「添加」）添加一枚胶囊。
     * 「添加」按钮按与输入框的垂直距离取最近者——分类/标签两条 QuickAddInlineBar
     * 都有同名按钮，不能用“最后一个”猜测。
     */
    private fun addChipViaUi(placeholder: String, value: String) {
        // 占位符文本可能作为独立子节点存在（BasicTextField.decorationBox），
        // 因此先按文本定位标签，再取垂直距离最近的**可输入节点**，不依赖语义合并。
        val label = composeRule.onNode(hasText(placeholder, substring = true))
        val labelY = label.fetchSemanticsNode().boundsInRoot.center.y
        val fields = composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        assertTrue("「$placeholder」附近必须有输入框", fields.isNotEmpty())
        val fieldIndex = fields.indices.minByOrNull {
            kotlin.math.abs(fields[it].boundsInRoot.center.y - labelY)
        }!!
        val field = composeRule.onAllNodes(hasSetTextAction())[fieldIndex]
        field.performTextInput(value)
        composeRule.waitForIdle()
        val fieldY = field.fetchSemanticsNode().boundsInRoot.center.y
        val addNodes = composeRule.onAllNodesWithText("添加").fetchSemanticsNodes()
        assertTrue("「$placeholder」旁必须有「添加」按钮", addNodes.isNotEmpty())
        val addIndex = addNodes.indices.minByOrNull {
            kotlin.math.abs(addNodes[it].boundsInRoot.center.y - fieldY)
        }!!
        composeRule.onAllNodesWithText("添加")[addIndex].performClick()
        composeRule.waitForIdle()
    }

    private fun payloadStringList(payload: String?, key: String): List<String> {
        if (payload.isNullOrBlank()) return emptyList()
        return runCatching {
            Json.parseToJsonElement(payload).jsonObject[key]?.jsonArray
                ?.map { it.jsonPrimitive.content } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    // ── 生命周期 ─────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        runId = "MAT65V3-" + java.util.UUID.randomUUID().toString().take(6)
        resultsDir = File(context.getExternalFilesDir(null), "mat-results-$runId").apply { mkdirs() }
        recordAsset("dir", resultsDir.absolutePath, "本轮阶段证据（JSON），验收后原样保留")
        db = J1RealReaderFixtures.openDatabase(context)
        importer = buildImporter()
        ledger = J1RealReaderFixtures.RunLifecycleLedger(runId)
        originalOnboarded = J1RealReaderFixtures.prepareOnboardingState(context)
    }

    @After
    fun tearDown() {
        val errors = mutableListOf<Throwable>()
        fun attempt(block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                errors += t
            }
        }
        attempt { scenario?.close() }
        attempt { J1RealReaderFixtures.restoreOnboardingState(context, originalOnboarded) }
        attempt {
            File(resultsDir, "mat_99_assets.json").writeText(
                JSONObject().put("runId", runId)
                    .put("lastPhase", lastPhase)
                    .put("selectionCaptured", capturedSelection)
                    .put("policy", "仪器内不删除本轮数据；由外层隔离实例+卸载包恢复合同处理")
                    .put("assets", createdAssets)
                    .toString(2)
            )
        }
        attempt { db.close() }
        if (errors.isNotEmpty()) {
            val failure = AssertionError("tearDown 失败 ${errors.size} 项（全部原始异常保留在 suppressed）")
            errors.forEach { failure.addSuppressed(it) }
            throw failure
        }
    }

    // ── 旅程 ─────────────────────────────────────────────────────────────────

    @Test
    fun mat_journey_excerptIdea_persisted_searchReturns_correctBody_progressFrozen() {
        runBlocking {
        try {
        // P0：真实生产导入（所有权 = 差集）
        markPhase("mat_00_import")
        val file = fixtureFile()
        file.writeText(txtBody())
        recordAsset("file", file.absolutePath, "本轮 TXT 测试资产（不触碰正式书库）")
        val beforeIds = activeBookIds()
        importer.importFiles(listOf(Uri.fromFile(file)), IMPORT_SOURCE_LABEL)
        val imported = waitFor("mat_00_import", IMPORT_TIMEOUT_MS, "导入后恰好多出一行 txt 书") {
            val added = activeBookIds() - beforeIds
            added.size == 1 && rawOne(
                "SELECT format FROM books WHERE id='${added.first()}' AND deleted_at IS NULL"
            ) == "txt"
        }
        assertTrue("真实生产导入必须且只能新增一行本轮 txt 书", imported)
        val newIds = activeBookIds() - beforeIds
        assertTrue("导入差集必须恰为 1 行，实得 ${newIds.size}", newIds.size == 1)
        val bookId = newIds.first()
        recordAsset("book", bookId, "本轮生产导入创建的书行；不删除")
        recordAsset("dir", File(context.filesDir, "books/$bookId").absolutePath, "生产导入正文副本；不删除")
        writeEvidence(
            "mat_00_import", true,
            mapOf(
                "entry" to "ShelfImporter.importFiles（生产公开构造）",
                "ownership" to "前后 activeBookIds 差集唯一",
                "bookId" to bookId,
                "fixture" to file.absolutePath,
            )
        )

        // P1：真实打开（首页 → 书架 → 本轮书卡 → 阅读器）
        markPhase("mat_10_open")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitForIdle()
        assertTrue(
            "启动后必须能证明所在顶层页面（首页/书架）",
            waitFor("mat_10_open", UI_TIMEOUT_MS, "顶层页面出现") { topLevelPage() != null }
        )
        val pageBeforeNav = topLevelPage()
        assertTrue("必须能显式进入书架（当前页 ${pageBeforeNav ?: "<none>"}）", shelfEntryProven("mat_10_open", bookId))
        assertTrue("书架必须出现本轮书籍卡片", waitFor("mat_10_open", UI_TIMEOUT_MS, "书架卡片出现") {
            composeRule.onAllNodesWithTag("book-tile-$bookId").fetchSemanticsNodes().isNotEmpty()
        })
        composeRule.onNodeWithTag("book-tile-$bookId").performClick()
        composeRule.waitForIdle()
        assertTrue("阅读器正文必须真实就绪", waitFor("mat_10_open", UI_TIMEOUT_MS, "正文就绪") { readerReady() })
        assertTrue(
            "阅读器必须显示本轮导入书籍的正文（第1/2章标题之一可见）",
            waitFor("mat_10_open", UI_TIMEOUT_MS, "本轮书籍正文标题") {
                textVisible("取材真实接缝") || textVisible("后续正文")
            }
        )
        writeEvidence(
            "mat_10_open", true,
            mapOf(
                "entry" to "MainActivity → 首页/书架导航项 → 书架 → book-tile-$bookId（真实 UI 阅读入口）",
                "pageAfterLaunch" to (pageBeforeNav ?: "<none>"),
                "navigationPerformed" to (pageBeforeNav != "shelf"),
                "pageAfterNavigation" to (topLevelPage() ?: "<none>"),
                "bodyIdentityProven" to true,
            )
        )

        // P2：真实长按选区 → 更多 → 记为灵感
        markPhase("mat_20_select")
        composeRule.onRoot().performTouchInput { longClick(center) }
        composeRule.waitForIdle()
        val toolbarVisible = waitFor("mat_20_select", UI_TIMEOUT_MS, "划词工具条出现") {
            textVisible("更多") && (textVisible("高亮") || textVisible("复制"))
        }
        assertTrue("真实长按必须唤出划词工具条", toolbarVisible)
        // 80-v2-A：选区只能来自保存前的真实 UI 语义。
        // 旧实现 allSemanticTexts() 遍历整棵 root 树取最长文本，而工具条的选中文本微条是
        // maxLines = 1 + Ellipsis（见 ReaderSelectionToolbar 的 SelectionToolbar），
        // 结构上永远短于整页正文，所以「取最长者」必然落到页面正文节点（360 字/18 行），
        // 既不是选区、也不存在于书中，任何导出都不可能逐字含它。
        capturedSelection = selectionToolbarExcerpt()
        assertTrue(
            "必须从选区工具条微条取得本轮真实选区（语义标识 " +
                "${ExportExcerptRoundTripVerdict.SELECTION_TOOLBAR_EXCERPT_TAG}），" +
                "实得「$capturedSelection」",
            capturedSelection.isNotBlank()
        )
        // 页面可见性探针：只服务 mat_60「临时查阅到达正确正文」，不参与摘录断言。
        renderedPageProbe = allSemanticTexts()
            .filter { it.isNotBlank() }
            .maxByOrNull { it.length }
            .orEmpty()
        writeEvidence(
            "mat_20_select_semantics", false,
            linkedMapOf<String, Any?>(
                "capturedSelectionLength" to capturedSelection.length,
                "capturedSelectionLines" to (capturedSelection.count { it == '\n' } + 1),
                "renderedPageProbeLength" to renderedPageProbe.length,
                "source" to "选区工具条微条语义（保存前采集）；页面探针不参与摘录断言",
            )
        )
        composeRule.onNodeWithText("更多").performClick()
        composeRule.waitForIdle()
        assertTrue("「更多」菜单必须出现「记为灵感」", waitFor("mat_20_select", UI_TIMEOUT_MS, "记为灵感可见") {
            textVisible("记为灵感")
        })
        composeRule.onNodeWithText("记为灵感").performClick()
        composeRule.waitForIdle()

        // P3：灵感 sheet —— 标题/我的想法/标签（轻量归类）→ 保存灵感
        markPhase("mat_30_save")
        assertTrue("灵感面板必须出现标题输入", waitFor("mat_30_save", UI_TIMEOUT_MS, "标题输入可见") {
            composeRule.onAllNodesWithText("标题").fetchSemanticsNodes().isNotEmpty()
        })
        assertTrue("灵感面板必须出现「我的想法」输入", waitFor("mat_30_save", UI_TIMEOUT_MS, "想法输入可见") {
            composeRule.onAllNodesWithText("我的想法").fetchSemanticsNodes().isNotEmpty()
        })
        val titleField = resolveEditableField("标题")
        val ideaField = resolveEditableField("我的想法")
        assertTrue(
            "标题字段必须唯一匹配一个可编辑节点：${titleField.diagnostics}",
            titleField.unique && titleField.node != null
        )
        assertTrue(
            "「我的想法」字段必须唯一匹配一个可编辑节点：${ideaField.diagnostics}",
            ideaField.unique && ideaField.node != null
        )
        // 断言已保证非空；取显式非空局部变量，避免依赖跨语句智能转换。
        val titleNode = titleField.node!!
        val ideaNode = ideaField.node!!
        // 标题框有生产预填值（阅读灵感：$bookTitle）：先清空再输入，保证本轮标记即完整标题。
        editableInteractionFor(titleNode).performTextClearance()
        composeRule.waitForIdle()
        editableInteractionFor(titleNode).performTextInput(runId)
        composeRule.waitForIdle()
        editableInteractionFor(ideaNode).performTextClearance()
        composeRule.waitForIdle()
        editableInteractionFor(ideaNode).performTextInput(ideasText())
        composeRule.waitForIdle()
        // 保存前读回：确认标题与个人想法确实进入各自字段（不是靠输入框顺序推断）。
        val titleTextBeforeSave = readBack(titleNode)
        val ideaTextBeforeSave = readBack(ideaNode)
        val titleReadbackMatches = titleTextBeforeSave == runId
        val ideaReadbackMatches = ideaTextBeforeSave?.contains(runId) == true
        writeEvidence(
            "mat_30_save_fields", false,
            linkedMapOf<String, Any?>(
                "titleResolution" to titleField.diagnostics,
                "ideaResolution" to ideaField.diagnostics,
                "titleReadbackLength" to (titleTextBeforeSave?.length ?: -1),
                "ideaReadbackLength" to (ideaTextBeforeSave?.length ?: -1),
                "titleReadbackMatches" to titleReadbackMatches,
                "ideaReadbackMatches" to ideaReadbackMatches,
                "ideaContainsMarker" to (ideaTextBeforeSave?.contains(runId) ?: false),
                "note" to "字段定位与保存前读回诊断；pass=false 仅表示保存断言尚未执行",
            )
        )
        assertTrue("保存前标题字段读回必须等于本轮标记，实得「$titleTextBeforeSave」", titleReadbackMatches)
        assertTrue(
            "保存前「我的想法」字段读回必须含本轮标记，实得长度 ${ideaTextBeforeSave?.length}",
            ideaReadbackMatches
        )
        // 69-v1-B：真实语义入口写入本轮唯一标签与分类（不再按输入框数量猜测）
        uniqueTag = "取材标签-$runId"
        uniqueCategory = "取材分类-$runId"
        assertTrue("标签输入入口必须存在", waitFor("mat_30_save", UI_TIMEOUT_MS, "标签输入可见") {
            composeRule.onAllNodes(hasText("新标签，逗号或空格分隔…", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        })
        addChipViaUi("新标签，逗号或空格分隔…", uniqueTag)
        assertTrue("分类输入入口必须存在", waitFor("mat_30_save", UI_TIMEOUT_MS, "分类输入可见") {
            composeRule.onAllNodes(hasText("新建分类名称…", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        })
        addChipViaUi("新建分类名称…", uniqueCategory)
        // 两个创建动作都走生产 ReaderAction（io 异步落库），等真实行出现
        assertTrue("标签必须真实写入 tags 表", waitFor("mat_30_save", SAVE_TIMEOUT_MS, "标签行出现") {
            (rawOne("SELECT COUNT(*) FROM tags WHERE name='$uniqueTag' AND deleted_at IS NULL")
                ?.toIntOrNull() ?: 0) == 1
        })
        assertTrue("分类必须真实写入 categories 表", waitFor("mat_30_save", SAVE_TIMEOUT_MS, "分类行出现") {
            (rawOne("SELECT COUNT(*) FROM categories WHERE name='$uniqueCategory' AND deleted_at IS NULL")
                ?.toIntOrNull() ?: 0) == 1
        })
        assertTrue("必须能点击「保存灵感」", waitFor("mat_30_save", UI_TIMEOUT_MS, "保存按钮可用") {
            textVisible("保存灵感")
        })
        composeRule.onNodeWithText("保存灵感").performClick()
        composeRule.waitForIdle()
        val saved = waitFor("mat_30_save", SAVE_TIMEOUT_MS, "Room 中出现本轮灵感") {
            (rawOne(
                "SELECT COUNT(*) FROM inspirations WHERE source_book_id='$bookId' " +
                    "AND deleted_at IS NULL AND (title='$runId' OR body LIKE '%$runId%')"
            )?.toIntOrNull() ?: 0) >= 1
        }
        assertTrue("真实保存后 Room inspirations 必须出现本轮记录", saved)
        val inspIdByTitle = rawOne(
            "SELECT id FROM inspirations WHERE source_book_id='$bookId' AND deleted_at IS NULL " +
                "AND title='$runId' ORDER BY created_at DESC LIMIT 1"
        )
        val inspIdByBody = rawOne(
            "SELECT id FROM inspirations WHERE source_book_id='$bookId' AND deleted_at IS NULL " +
                "AND body LIKE '%$runId%' ORDER BY created_at DESC LIMIT 1"
        )
        val inspId = inspIdByTitle ?: inspIdByBody
        val inspTitle = inspId?.let {
            rawOne("SELECT COALESCE(title,'') FROM inspirations WHERE id='$it'")
        } ?: ""
        val inspBody = rawOne("SELECT COALESCE(body,'') FROM inspirations WHERE id='$inspId'") ?: ""
        val inspPayload = rawOne("SELECT COALESCE(payload,'') FROM inspirations WHERE id='$inspId'") ?: ""
        writeEvidence(
            "mat_30_save_body", false,
            linkedMapOf<String, Any?>(
                "inspirationId" to inspId,
                "inspIdByTitle" to inspIdByTitle,
                "inspIdByBody" to inspIdByBody,
                "titleEqualsMarker" to (inspTitle == runId),
                "titleLength" to inspTitle.length,
                "bodyContainsMarker" to inspBody.contains(runId),
                "bodyLength" to inspBody.length,
                "payloadLength" to inspPayload.length,
                "note" to "保存后 Room 字段诊断；pass=false 仅表示断言尚未执行",
            )
        )
        assertTrue(
            "灵感正文（个人想法）必须真实入库（id=$inspId，长度 ${inspBody.length}）",
            inspBody.contains(runId)
        )
        assertTrue("灵感必须带来源快照 payload（含摘录 locator）", inspPayload.isNotBlank())

        // 80-v2-A 第二段：用**保存前**独立取得的选区校验持久化 payload。
        // 解码失败必须抛出（不吞异常），否则「读不到」会被伪装成「没有摘录」。
        val persistedExcerpts = try {
            ExportExcerptRoundTripVerdict.decodePersistedExcerpts(inspPayload)
        } catch (e: ExportExcerptRoundTripVerdict.PayloadDecodeException) {
            throw AssertionError("灵感 payload 无法解码：${e.message}", e)
        }
        val bookTextForExcerpt = txtBody()
        writeEvidence(
            "mat_30_save_excerpt", false,
            linkedMapOf<String, Any?>(
                "persistedExcerptCount" to persistedExcerpts.size,
                "persistedExcerptLengths" to persistedExcerpts.joinToString(",") { it.length.toString() },
                "capturedSelectionLength" to capturedSelection.length,
                "selectionIsPersisted" to
                    ExportExcerptRoundTripVerdict.selectionIsPersisted(capturedSelection, persistedExcerpts),
                "persistedExcerptsAreBookText" to persistedExcerpts.all { it in bookTextForExcerpt },
                "renderedPageProbeIsBookText" to (renderedPageProbe in bookTextForExcerpt),
                "note" to "取材取证：保存前选区 vs 持久化 payload；pass=false 仅表示断言尚未执行",
            )
        )
        assertTrue("灵感 payload 必须至少含一条来源摘录", persistedExcerpts.isNotEmpty())
        assertTrue(
            "保存前读到的选区必须原样落库（逐字相等，不做 trim/前缀匹配）；" +
                "选区长度 ${capturedSelection.length}，payload 摘录 " +
                persistedExcerpts.joinToString(" | ") { "[${it.length}]" },
            ExportExcerptRoundTripVerdict.selectionIsPersisted(capturedSelection, persistedExcerpts)
        )
        // 辅助检查（不能替代上面的选区身份校验）：摘录必须是本书正文的逐字连续子串，
        // 且不得等于整本书正文——后者说明取的是整页而不是选区。
        persistedExcerpts.forEach { ex ->
            assertTrue("持久化摘录必须是本轮导入正文的逐字连续子串（长度 ${ex.length}）", ex in bookTextForExcerpt)
            assertTrue("持久化摘录不得等于整本书正文", ex != bookTextForExcerpt)
        }

        // 69-v2-B：归类最终持久化——先写诊断，再严格断言
        val payloadTags = payloadStringList(inspPayload, "tags")
        val payloadCategoryIds = payloadStringList(inspPayload, "categoryIds")
        val tagRowId = rawOne("SELECT id FROM tags WHERE name='$uniqueTag' AND deleted_at IS NULL LIMIT 1")
        val tagType = tagRowId?.let {
            rawOne("SELECT COALESCE(type,'') FROM tags WHERE id='$it'")
        }
        val categoryRowId = rawOne(
            "SELECT id FROM categories WHERE name='$uniqueCategory' AND deleted_at IS NULL LIMIT 1")
        val unresolvedCategoryIds = payloadCategoryIds.filter { id ->
            (rawOne("SELECT COUNT(*) FROM categories WHERE id='$id' AND deleted_at IS NULL")
                ?.toIntOrNull() ?: 0) != 1
        }
        val classificationDiagnostics = mapOf(
            "inspirationId" to inspId,
            "excerpt" to capturedSelection,
            "body" to inspBody,
            "uniqueTag" to uniqueTag,
            "payloadTags" to payloadTags.joinToString(","),
            "tagRowId" to tagRowId,
            "tagType" to tagType,
            "uniqueCategory" to uniqueCategory,
            "categoryRowId" to categoryRowId,
            "payloadCategoryIds" to payloadCategoryIds.joinToString(","),
            "unresolvedCategoryIds" to unresolvedCategoryIds.joinToString(","),
        )
        // 诊断先落盘（pass=false 仅表示“断言尚未执行”，不是通过证据）
        writeEvidence("mat_30_save_diagnostics", false, classificationDiagnostics)

        assertTrue(
            "灵感 payload.tags 必须包含本轮唯一标签「$uniqueTag」，实得 $payloadTags",
            payloadTags.contains(uniqueTag)
        )
        assertNotNull("标签必须真实存在于 tags 表", tagRowId)
        assertEquals("标签类型必须为 inspiration", "inspiration", tagType)
        assertNotNull("分类必须真实存在于 categories 表", categoryRowId)
        assertTrue(
            "payload.categoryIds 必须包含本轮分类真实行 id「$categoryRowId」，实得 $payloadCategoryIds",
            payloadCategoryIds.contains(categoryRowId)
        )
        assertTrue(
            "payload.categoryIds 不得含悬空 id，悬空=$unresolvedCategoryIds",
            unresolvedCategoryIds.isEmpty()
        )
        writeEvidence("mat_30_save", true, classificationDiagnostics)

        // P4：退出阅读器，记录普通进度基线。
        // 与 MD 退出同一生产接缝：翻页/滚动后 controlsVisible 可能为 false，顶栏「返回」不在语义树；
        // 使用中央轻点唤出工具栏的真实入口，必要时才走生产系统返回。
        markPhase("mat_40_exit")
        val exitAttempt = ReaderChromeExitSupport.exitViaBack(composeRule)
        if (exitAttempt.failure != null) {
            val failure = exitAttempt.failure
            runCatching {
                writeEvidence(
                    "mat_40_exit", false,
                    exitAttempt.diagnostics() + mapOf(
                        "bookId" to bookId,
                        "readerReady" to readerReady(),
                        "originalException" to failure.toString(),
                    )
                )
            }.onFailure { failure.addSuppressed(it) }
            throw failure
        }
        composeRule.waitForIdle()
        // 73-v1-B：必需断言先行——退出动作已发出但页面仍在时必须先留失败证据；
        // 成功记录（mat_40_exit pass=true）只能在离开阅读器与真实进度行断言均满足之后写。
        val leftReader = waitFor("mat_40_exit", UI_TIMEOUT_MS, "离开阅读器") { readerGone() }
        if (!leftReader) {
            writeEvidence(
                "mat_40_exit", false,
                exitAttempt.diagnostics() + mapOf(
                    "bookId" to bookId,
                    "readerReady" to readerReady(),
                    "readerGone" to false,
                    "exitActionIssued" to true,
                    "originalException" to "退出动作已发出但阅读页面仍在（未离开阅读器）",
                )
            )
        }
        assertTrue("必须真实离开阅读器", leftReader)
        val progressBefore = J1RealReaderFixtures.captureProgressRow(db, bookId)
        if (progressBefore == null) {
            writeEvidence(
                "mat_40_exit", false,
                exitAttempt.diagnostics() + mapOf(
                    "bookId" to bookId,
                    "readerReady" to readerReady(),
                    "readerGone" to true,
                    "progressRowPresent" to false,
                    "originalException" to "退出后未读到真实进度行（普通阅读基线缺失）",
                )
            )
        }
        assertTrue("阅读器退出后必须有真实进度行（普通阅读基线）", progressBefore != null)
        writeEvidence(
            "mat_40_exit", true,
            exitAttempt.diagnostics() + mapOf(
                "bookId" to bookId,
                "readerGone" to true,
                "progressRowPresent" to true,
                "note" to "离开阅读器与真实进度行断言均满足后才记录通过",
            )
        )

        // P5：真实检索 → 灵感命中 → 查阅原文位置 → 返回阅读处 → 进度逐字段不变
        markPhase("mat_50_search")
        clickTopLevelTab("首页")
        val homeProven = waitFor("mat_50_search", UI_TIMEOUT_MS, "首页出现") { topLevelPage() == "home" }
        if (!homeProven) writeEntryDiagnostics("mat_50_search", bookId)
        assertTrue("必须能证明已进入首页（当前页 ${topLevelPage() ?: "<none>"}）", homeProven)
        assertTrue("首页必须出现搜索入口", waitFor("mat_50_search", UI_TIMEOUT_MS, "搜索入口") {
            composeRule.onAllNodesWithTag("home-search-btn").fetchSemanticsNodes().isNotEmpty()
        })
        composeRule.onNodeWithTag("home-search-btn").performClick()
        composeRule.waitForIdle()
        assertTrue("搜索页必须有输入框", waitFor("mat_50_search", UI_TIMEOUT_MS, "搜索输入框") {
            composeRule.onAllNodesWithText("搜索书籍、灵感、笔记、高亮")
                .fetchSemanticsNodes().isNotEmpty()
        })
        val searchBox = composeRule.onNode(hasText("搜索书籍、灵感、笔记、高亮"))
        searchBox.performTextClearance()
        searchBox.performTextInput(runId)
        composeRule.waitForIdle()
        // 76-v1-B：搜索框、书籍和灵感都包含 runId；只点击标题精确匹配且类型为灵感的卡。
        // SearchResultCard 的 clickable Surface 合并标题与类型语义，不能按节点列表顺序选首项。
        val inspirationHit = hasText(runId, substring = false) and
            hasText("灵感", substring = false) and hasClickAction()
        assertTrue("真实检索必须找回本轮灵感结果卡", waitFor("mat_50_search", SEARCH_TIMEOUT_MS, "精确灵感卡出现") {
            composeRule.onAllNodes(inspirationHit).fetchSemanticsNodes().isNotEmpty()
        })
        val hit = composeRule.onAllNodes(inspirationHit).fetchSemanticsNodes()
        assertEquals("本轮灵感结果卡必须唯一（精确标题+灵感类型）", 1, hit.size)
        composeRule.onNode(inspirationHit).performClick()
        composeRule.waitForIdle()

        markPhase("mat_60_return")
        assertTrue("必须进入灵感详情并可「查阅原文位置」", waitFor("mat_60_return", UI_TIMEOUT_MS, "查阅原文位置可见") {
            textVisible("查阅原文位置")
        })
        composeRule.onNodeWithText("查阅原文位置").performClick()
        composeRule.waitForIdle()
        assertTrue("临时查阅必须到达正确正文（摘录可见且正文就绪）",
            waitFor("mat_60_return", UI_TIMEOUT_MS, "临时查阅正文") {
                readerReady() && textVisible(renderedPageProbe)
            })
        assertTrue("临时查阅必须提供「返回阅读处」", waitFor("mat_60_return", UI_TIMEOUT_MS, "返回阅读处可见") {
            composeRule.onAllNodesWithContentDescription("返回阅读处").fetchSemanticsNodes().isNotEmpty()
        })
        composeRule.onNodeWithContentDescription("返回阅读处").performClick()
        composeRule.waitForIdle()
        assertTrue("返回阅读处必须真实离开临时查阅", waitFor("mat_60_return", UI_TIMEOUT_MS, "临时查阅已退出") {
            composeRule.onAllNodesWithContentDescription("返回阅读处").fetchSemanticsNodes().isEmpty()
        })
        val progressAfter = J1RealReaderFixtures.captureProgressRow(db, bookId)
        assertTrue("返回后普通进度行必须仍存在", progressAfter != null)
        progressBefore!!.assertExactEquals(progressAfter!!, "临时查询后普通阅读进度零污染")
        writeEvidence(
            "mat_60_return", true,
            mapOf(
                "searchTerm" to runId,
                "excerpt" to capturedSelection,
                "progressExactEquals" to true,
                "note" to "进度逐字段比对，临时查阅不改普通进度",
            )
        )

        // P6（69-v4-B）：真实阅读器入口 → 系统文档选择器 → 系统返回 URI → 读回
        markPhase("mat_70_export")
        assertTrue("导出前必须仍在阅读器", waitFor("mat_70_export", UI_TIMEOUT_MS, "阅读器就绪") {
            readerReady()
        })
        val bookTitle = rawOne("SELECT COALESCE(title,'') FROM books WHERE id='$bookId'") ?: ""
        assertTrue("导出前必须能确定本轮书名", bookTitle.isNotBlank())
        val exportPrefix = "《$bookTitle》笔记-"
        // MediaStore 差集仅作辅助核对，不作为 URI/授权来源。
        val idsBefore = queryDownloadedMarkdownIds(exportPrefix)
        ReaderExportReceipts.clear()
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.waitForIdle()
        assertTrue("更多菜单必须出现「笔记与标注」", waitFor("mat_70_export", UI_TIMEOUT_MS, "笔记与标注可见") {
            textVisible("笔记与标注")
        })
        composeRule.onNodeWithText("笔记与标注").performClick()
        composeRule.waitForIdle()
        assertTrue("笔记与标注面板必须打开（真实导出入口所在）", waitFor("mat_70_export", UI_TIMEOUT_MS, "导出入口可见") {
            textVisible("导出")
        })
        // 77-v1-B：ModalBottomSheet 有独立 root；定位标题所属面板的唯一列表，不能滑全局 root。
        val notesPanel = hasAnyDescendant(hasText("笔记与标注", substring = false))
        val notesListMatcher = hasScrollAction() and hasAnyAncestor(notesPanel)
        assertEquals("笔记与标注面板必须有唯一滚动列表", 1,
            composeRule.onAllNodes(notesListMatcher).fetchSemanticsNodes().size)
        val notesList = composeRule.onNode(notesListMatcher)
        val panelRunData = hasText(runId, substring = false) and hasAnyAncestor(notesListMatcher)
        var panelShowsRunData = composeRule.onNode(panelRunData).isDisplayed()
        repeat(6) {
            if (!panelShowsRunData) {
                notesList.performTouchInput { swipeUp() }
                composeRule.waitForIdle()
                panelShowsRunData = composeRule.onNode(panelRunData).isDisplayed()
            }
        }
        assertTrue("笔记与标注面板必须显示本轮真实灵感（runId=$runId）", panelShowsRunData)
        // 导出在固定标题栏 trailing 中，不属于 LazyColumn；无须回顶，更不能下滑整个 sheet。
        val exportAction = hasText("导出", substring = false) and hasClickAction() and
            hasAnyAncestor(notesPanel)
        assertEquals("笔记与标注面板必须有唯一导出按钮", 1,
            composeRule.onAllNodes(exportAction).fetchSemanticsNodes().size)
        composeRule.onNode(exportAction).assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // 1) 系统文档选择器必须出现（未出现即失败）
        val pickerAppeared = waitFor("mat_70_export", PICKER_TIMEOUT_MS, "DocumentsUI") {
            documentsUiFocused()
        }
        val sample = latestPickerSample
        writeEvidence("mat_70_export_picker", pickerAppeared, sample.toEvidenceMap())
        if (!pickerAppeared) {
            val errorSample = firstPickerErrorSample ?: sample
            if (errorSample.lastError.isNotBlank()) {
                throw AssertionError(
                    "导出系统文档选择器在阶段[${errorSample.errorStage}]发生平台异常: ${errorSample.lastError}",
                    errorSample.cause
                )
            }
            if (sample.ambiguity) {
                throw AssertionError("导出系统文档选择器候选歧义(count=${sample.candidateCount})，拒绝盲选")
            }
            assertTrue("导出必须拉起系统文档选择器", pickerAppeared)
        }
        //    不能沿用选择器上次停留目录，也不能只在事后记录 URI authority。
        val safeTitle = bookTitle.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "未命名" }
        val expectedName = "《$safeTitle》笔记-$runId.md"
        val destination = prepareDocumentsUiDestination(expectedName)
        writeEvidence("mat_70_export_destination", destination["prepared"] == true, destination)
        assertTrue("保存前必须真实导航并确认 Downloads 位置与本轮唯一文件名：$destination",
            destination["prepared"] == true)
        // 3) 必须在选择器中确认保存（无障碍节点树定位，不写公共临时文件）
        assertTrue("必须在系统选择器中确认保存", tapDocumentsUiSave())
        assertTrue("保存后必须离开系统选择器", waitFor("mat_70_export", EXPORT_WRITE_TIMEOUT_MS, "选择器关闭") {
            !documentsUiFocused()
        })
        // 4) 必须取得系统 CreateDocument 的实际结果；失败/取消不得以超时代替。
        val outcomeReady = waitFor("mat_70_export", EXPORT_WRITE_TIMEOUT_MS, "系统返回导出结果") {
            ReaderExportReceipts.latest() != null ||
                ReaderExportReceipts.latestFailure() != null ||
                ReaderExportReceipts.wasCancelled()
        }
        ReaderExportReceipts.latestFailure()?.let { failure ->
            throw AssertionError("生产导出写入失败：${failure.message}", failure.cause)
        }
        assertTrue("系统选择器不得以取消冒充导出成功", !ReaderExportReceipts.wasCancelled())
        assertTrue("系统必须在超时内返回 CreateDocument 结果 URI", outcomeReady)
        val receipt = ReaderExportReceipts.latest()
        assertNotNull("系统必须返回真实的 CreateDocument URI", receipt)
        val uri = receipt!!.uri
        assertEquals("导出 URI 必须是 content scheme", "content", uri.scheme)
        assertEquals("导出必须落在约定的系统文档提供者", EXPECTED_DOCUMENT_AUTHORITY, uri.authority)

        // 5) 一旦取得 URI 立即登记资产（读取/断言失败也保留线索）
        recordAsset("document", uri.toString(),
            "本轮唯一导出文档；保留（不删除），未知同名/后代不处理")
        writeEvidence(
            "mat_70_export_asset", true,
            mapOf(
                "registeredBeforeRead" to true,
                "uri" to uri.toString(),
                "providerAuthority" to uri.authority,
                "expectedProviderAuthority" to EXPECTED_DOCUMENT_AUTHORITY,
                "expectedDisplayName" to expectedName,
                "receiptCompletedAtMillis" to receipt.completedAtMillis,
                "creationStep" to "reader More → Notes → Export → DocumentsUI roots→Downloads → save → CreateDocument result",
                "retention" to "保留本轮唯一文档；不删除，未知同名/后代不处理",
                "note" to "资产登记成功，不等于导出验收；读取与内容断言随后执行",
            )
        )

        // 6) 应用必须按真实 URI 读回（异常保留原 cause）
        val meta = queryDocumentMeta(uri)
        assertEquals("导出文档必须使用本轮唯一名称（不得静默改名/覆盖同名）", expectedName, meta.name)
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (t: Throwable) {
            throw AssertionError("应用无法按系统返回的 content URI 读取导出文档：$uri", t)
        }
        assertNotNull("应用必须能按系统返回的 content URI 读取导出文档", bytes)
        assertTrue("导出文档必须非空", bytes!!.isNotEmpty())
        val text = bytes.toString(Charsets.UTF_8)
        assertTrue("导出文档必须含本轮 runId", text.contains(runId))
        assertTrue(
            "导出文档必须完整保留本轮选区（只允许移除引用前缀；缺字/截断/错段/行乱序/非连续拼接均失败）；" +
                "选区长度 ${capturedSelection.length}",
            ExportExcerptRoundTripVerdict.exportContainsExcerpt(text, capturedSelection)
        )
        assertTrue("导出文档必须含个人想法", text.contains(ideasText()))
        assertEquals("读回字节数必须与提供者声明 size 一致",
            meta.size ?: bytes.size.toLong(), bytes.size.toLong())

        // 7) MediaStore 差集仅作辅助证据（提供者非媒体库时为空是正常的）
        val auxNewIds = queryDownloadedMarkdownIds(exportPrefix) - idsBefore
        val auxNameVisible = expectedName in queryDownloadedDisplayNames()
        writeEvidence(
            "mat_70_export", true,
            mapOf(
                "entry" to "阅读器「更多」→「笔记与标注」→「导出」→ 选择器导航 Downloads→保存（真实生产入口）",
                "panelShowsRunData" to panelShowsRunData,
                "pickerOpened" to true,
                "saveConfirmed" to true,
                "saveClickDiagnostics" to saveClickDiagnostics,
                "destinationDiagnostics" to destination,
                "uri" to uri.toString(),
                "providerAuthority" to uri.authority,
                "expectedProviderAuthority" to EXPECTED_DOCUMENT_AUTHORITY,
                "displayName" to meta.name,
                "expectedDisplayName" to expectedName,
                "displayNameMatchesExpected" to (meta.name == expectedName),
                "mediaStoreAuxNameVisible" to auxNameVisible,
                "bytes" to bytes.size,
                "providerSize" to (meta.size ?: "unknown"),
                "sha256" to sha256Hex(bytes),
                "containsRunId" to true,
                "containsExcerpt" to true,
                "containsIdea" to true,
                "readBackViaContentResolver" to true,
                "mediaStoreAuxNewIds" to auxNewIds.joinToString(","),
                "retention" to "保留本轮唯一文档；不删除，未知同名/后代不处理",
                "note" to "真实 DocumentsUI 导航 Downloads + 系统 CreateDocument 返回 URI；未使用 file://、预写文档或旧 run 资产",
            )
        )
        } catch (t: Throwable) {
            writeFailureEvidence(lastPhase, t)
            throw t
        }
        }
    }

    /**
     * 79-v2-B：针对 DocumentsUiPickerProbe 的真实 Kotlin 字节码行为定向验证。
     * 可由 C 角色在真机/模拟器测试环境下通过定向命令直接执行：
     * adb shell am instrument -w -r -e class com.creationreadingassistant.acceptance.MaterialGatheringJourneyTest#documentsUiPickerProbe_behaviorVerification ...
     */
    @Test
    fun documentsUiPickerProbe_behaviorVerification() {
        // 场景 1a：活动根节点回退验证（有效 DocumentsUI 包名，窗口列表未提供有效前台候选，退回 activeRoot）
        val root1 = AccessibilityNodeInfo.obtain()
        root1.packageName = "com.android.documentsui"
        val p1 = DocumentsUiPickerProbe(
            ensureFlags = {},
            getActiveRoot = { root1 },
            getWindowCandidates = { emptyList() },
        )
        val s1 = p1.sample()
        assertSame("断言1a：应返回预期非空root", root1, s1.root)
        assertEquals("断言1a活动包匹配", "com.android.documentsui", s1.activePackage)
        assertFalse("断言1a不来自windows", s1.foundInWindows)

        // 场景 1b：拒绝空包名（生产白名单正确拒绝未设置包名的空节点，返回null）
        val rootEmpty = AccessibilityNodeInfo.obtain()
        val p1Empty = DocumentsUiPickerProbe(
            ensureFlags = {},
            getActiveRoot = { rootEmpty },
            getWindowCandidates = { emptyList() },
        )
        val s1Empty = p1Empty.sample()
        assertNull("断言1b：空包名应被正确拒绝，root为空", s1Empty.root)
        assertFalse("断言1b不来自windows", s1Empty.foundInWindows)

        // 场景 1c：拒绝宿主应用包名（生产白名单正确拒绝宿主自身）
        val rootHost = AccessibilityNodeInfo.obtain()
        rootHost.packageName = "com.creationreadingassistant.qahostj1persistv2"
        val p1Host = DocumentsUiPickerProbe(
            ensureFlags = {},
            getActiveRoot = { rootHost },
            getWindowCandidates = { emptyList() },
        )
        val s1Host = p1Host.sample()
        assertNull("断言1c：宿主包名应被拒绝，root为空", s1Host.root)
        assertFalse("断言1c不来自windows", s1Host.foundInWindows)

        // 场景 1d：拒绝无关系统包名（生产白名单正确拒绝无关包名）
        val rootOther = AccessibilityNodeInfo.obtain()
        rootOther.packageName = "com.android.systemui"
        val p1Other = DocumentsUiPickerProbe(
            ensureFlags = {},
            getActiveRoot = { rootOther },
            getWindowCandidates = { emptyList() },
        )
        val s1Other = p1Other.sample()
        assertNull("断言1d：无关包名应被拒绝，root为空", s1Other.root)
        assertFalse("断言1d不来自windows", s1Other.foundInWindows)

        // 场景 2：活动根为空，唯一前台有效候选窗口（非空可操作目标 root 属性）
        val root2 = AccessibilityNodeInfo.obtain()
        root2.packageName = "com.android.documentsui"
        val p2 = DocumentsUiPickerProbe(
            ensureFlags = {},
            getActiveRoot = { null },
            getWindowCandidates = {
                listOf(DocumentsUiWindowCandidate("com.android.documentsui", isFocused = true, isActive = true, root = root2))
            },
        )
        val s2 = p2.sample()
        assertSame("断言2：应返回预期非空目标root", root2, s2.root)
        assertTrue("断言2应成功在windows找到有效候选", s2.foundInWindows && s2.candidateCount == 1)
        assertEquals("断言2包名匹配", "com.android.documentsui", s2.activePackage)

        // 场景 3：后台同包窗口，严格排除 !isFocused && !isActive 的同包与无关包
        val root3 = AccessibilityNodeInfo.obtain()
        root3.packageName = "com.android.documentsui"
        val p3 = DocumentsUiPickerProbe(
            ensureFlags = {},
            getActiveRoot = { null },
            getWindowCandidates = {
                listOf(
                    DocumentsUiWindowCandidate("com.android.documentsui", isFocused = false, isActive = false, root = root3),
                    DocumentsUiWindowCandidate("com.android.systemui", isFocused = false, isActive = true, root = root3),
                )
            },
        )
        val s3 = p3.sample()
        assertNull("断言3后台同包及无关包应被排除，root应为空", s3.root)
        assertEquals("断言3有效候选应为0", 0, s3.candidateCount)
        assertFalse("断言3未能由windows找到", s3.foundInWindows)

        // 场景 4：候选判歧与焦点优先
        // 4a：同为 focused 无法判歧，拒绝盲选
        val root4a1 = AccessibilityNodeInfo.obtain()
        root4a1.packageName = "com.android.documentsui"
        val root4a2 = AccessibilityNodeInfo.obtain()
        root4a2.packageName = "com.android.documentsui"
        val p4a = DocumentsUiPickerProbe(
            ensureFlags = {},
            getActiveRoot = { null },
            getWindowCandidates = {
                listOf(
                    DocumentsUiWindowCandidate("com.android.documentsui", isFocused = true, isActive = true, root = root4a1),
                    DocumentsUiWindowCandidate("com.android.documentsui", isFocused = true, isActive = true, root = root4a2),
                )
            },
        )
        val s4a = p4a.sample()
        assertTrue("断言4a多焦点候选无法区分应歧义", s4a.ambiguity && s4a.candidateCount == 2)
        assertNull("断言4a歧义时root应为空", s4a.root)

        // 4b：成功精准选择具有焦点的目标窗口
        val root4bFocused = AccessibilityNodeInfo.obtain()
        root4bFocused.packageName = "com.android.documentsui"
        val root4bActive = AccessibilityNodeInfo.obtain()
        root4bActive.packageName = "com.android.documentsui"
        val p4b = DocumentsUiPickerProbe(
            ensureFlags = {},
            getActiveRoot = { null },
            getWindowCandidates = {
                listOf(
                    DocumentsUiWindowCandidate("com.android.documentsui", isFocused = false, isActive = true, root = root4bActive),
                    DocumentsUiWindowCandidate("com.android.documentsui", isFocused = true, isActive = true, root = root4bFocused),
                )
            },
        )
        val s4b = p4b.sample()
        assertFalse("断言4b不应歧义", s4b.ambiguity)
        assertSame("断言4b应精准选择具有焦点的目标root", root4bFocused, s4b.root)
        assertEquals("断言4b候选数应为2", 2, s4b.candidateCount)

        // 场景 5：捕获与传递异常，原始异常阶段与 cause 保全
        val flagEx = SecurityException("permission denied")
        val p5Flags = DocumentsUiPickerProbe(
            ensureFlags = { throw flagEx },
            getActiveRoot = { null },
            getWindowCandidates = { emptyList() },
        )
        val s5Flags = p5Flags.sample()
        assertEquals("断言5服务配置异常阶段", "ensure_flags", s5Flags.errorStage)
        assertTrue("断言5错误信息包含异常类型", s5Flags.lastError.contains("SecurityException"))
        assertSame("断言5保留原始异常cause", flagEx, s5Flags.cause)
        assertNull("断言5异常时root应为空", s5Flags.root)

        val windowEx = IllegalStateException("ipc failed")
        val p5Windows = DocumentsUiPickerProbe(
            ensureFlags = {},
            getActiveRoot = { null },
            getWindowCandidates = { throw windowEx },
        )
        val s5Windows = p5Windows.sample()
        assertEquals("断言5窗口查询异常阶段", "query_windows", s5Windows.errorStage)
        assertTrue("断言5错误信息包含异常类型", s5Windows.lastError.contains("IllegalStateException"))
        assertSame("断言5保留原始异常cause", windowEx, s5Windows.cause)
        assertNull("断言5异常时root应为空", s5Windows.root)

        // 场景 6：单次窗口列表与元数据快照一致性
        val root6 = AccessibilityNodeInfo.obtain()
        root6.packageName = "com.google.android.documentsui"
        val candidates6 = listOf(
            DocumentsUiWindowCandidate("com.google.android.documentsui", isFocused = true, isActive = true, root = root6)
        )
        val p6 = DocumentsUiPickerProbe(
            ensureFlags = {},
            getActiveRoot = { null },
            getWindowCandidates = { candidates6 },
        )
        val s6 = p6.sample()
        assertSame("断言6：应返回预期root", root6, s6.root)
        assertEquals("断言6包名匹配", "com.google.android.documentsui", s6.activePackage)
        assertEquals("断言6窗口候选列表size", candidates6.size, s6.windowCount)
        val map = s6.toEvidenceMap()
        assertEquals("activePackage 一致", s6.activePackage, map["activePackage"])
        assertEquals("windowCount 一致", s6.windowCount, map["windowCount"])
        assertEquals("errorStage 一致", s6.errorStage, map["errorStage"])
    }

        private companion object {
        const val TAG = "MaterialGatheringJourney"
        const val FIXTURE_NAME = "取材测试"
        const val IMPORT_SOURCE_LABEL = "任务65-v3 取材仪器导入"

        const val POLL_INTERVAL_MS = 300L
        const val IMPORT_TIMEOUT_MS = 30_000L
        const val UI_TIMEOUT_MS = 20_000L
        const val SAVE_TIMEOUT_MS = 15_000L
        const val SEARCH_TIMEOUT_MS = 25_000L
        const val EXPORT_TIMEOUT_MS = 20_000L
        const val PICKER_TIMEOUT_MS = 15_000L
        const val EXPORT_WRITE_TIMEOUT_MS = 30_000L

        /** 约定的导出提供者（Documents 提供者的 Downloads 根）。 */
        const val EXPECTED_DOCUMENT_AUTHORITY = "com.android.providers.downloads.documents"

        /** DocumentsUI 的保存动作标签（中文/英文系统）。 */
        val SAVE_LABELS = setOf("保存", "Save")

        /** 打开 roots 抽屉的动作标签（中文/英文系统）。 */
        val ROOT_DRAWER_LABELS = setOf(
            "Show roots", "Open drawer", "Navigation",
            "显示根目录", "显示根", "根目录", "打开根目录",
        )

        /** roots 抽屉中的 Downloads 位置标签（中文/英文系统）。 */
        val DOWNLOAD_LABELS = setOf("Downloads", "Download", "下载", "下载内容")

        /** 无障碍树遍历上限，防止异常页面导致长时间占用。 */
        const val MAX_A11Y_NODES = 400
    }
}
