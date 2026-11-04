package com.creationreadingassistant.acceptance

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 临时正文精确窗口宽度：抗页界拆断窗口宽度参考。 */
private const val TEMPORARY_BODY_WINDOW = 12

/** 目标一：第一章标题（全书唯一出现 1 次，非普通 S0，消除循环填充句歧义）。 */
private const val TARGET1_HEADING = "第一章 序章"

/**
 * 174-v1：外部 Notes 临时查阅合同 B 回归（合同更正批；复用 J1 生产夹具与真实 UI 全链）。
 *
 * ## 本批合同 B（Root 174 裁决：只改测试断言到现有合同，不做新产品入口）
 *
 * 173 只读诊断结论 + 本批逐条源码复核的生产事实链：
 * 1. 生产 temporary 入口 = `reader/{bookId}?sourceLocator=<encoded>&navigationMode=temporary`
 *    （readerTemporaryRouteForSource）；reader 域外的真实调用点在「我的 → 阅读笔记」条目卡的
 *    「临时查看来源」（AnnotationEntryCard.kt:400-405 → ProfileRoute.kt:267-280）。
 * 2. 底栏只在五个顶层路由显示（AppNavigation.kt:201）；导航离开 reader 域即 endReaderSession
 *    清 active 与整条临时栈、保留 normalReading 持久指针（AppNavigation.kt:209-213、
 *    SourceNavigationContract.kt:178-183）。
 * 3. beginTemporaryInspection 的返回目标**只认当前 active**（SourceNavigationContract.kt:150-164）；
 *    外部进入时 active 已被清空 → 临时栈保持为空 → hasReturnableTemporaryTarget=false
 *    （ReaderRoute.kt:230-231）→ 顶栏不组合「返回阅读处」按钮（ReaderChrome.kt:622-637）。
 * 4. 因此外部临时查阅的 Back 合同：performTemporaryReturn 拿不到 LIFO 路由 → popBackStack
 *    （ReaderRoute.kt:123-137、ReaderTemporaryRoute.kt:142-145）→ 回真实来源页 =
 *    profile/notes 阅读笔记列表（OpenSubPage 走真实二级路由，ProfileRoute.kt:480-486）。
 *
 * ## 断言（合同 B 落地；每条都有上述源码依据，不发明行为）
 *
 * 用例1 普通建立 → 真实退出建立 S0 → 底栏我的 → 阅读笔记 → 临时查阅（来源一）→
 *        12 列隔离 → Back 回 Notes 原条目 → 明确重新普通打开回 S0：
 *  - 真实书架 tile 就绪（Home→Shelf、精确 book-tile、contentA.marker1 正文就绪——170 修复保留）；
 *  - 退出前后位置都按 J1RealReaderFixtures.assertObservedPersistedLocation 核（页起始>0 且
 *    ≤ offset1，持久合同=「包含目标位点的当前页首」），且都不落在两个临时 sourceOffset；
 *    S0 在书架顶层可见之后建立——退出保存的生产可观察边界（ReaderExitBoundary 保证退出保存
 *    先于导航完成），不把正常退出的合法写入算临时写入，也不事后把临时写入回填 S0；
 *  - 进入 Notes 列表先证「本轮两条夹具条目都在列表」，再用生产关键词过滤把目标条目隔离成
 *    唯一卡片（按 Text 语义匹配卡片渲染的批注体；187：列表页不渲染笔记标题，标题仅存在于库与筛选层），点唯一「临时查看来源」
 *    胶囊——不用任意同名节点；
 *  - 临时正文就绪（175-v1 去歧义修复）：当前页 text 语义（PagedTxtReaderHost.kt:399-402，按视觉行以 \n 拼接）
 *    去 \n 后包含该来源派生的全书唯一目标正文 targetText（目标一为序章标题、目标二为 snippet2，均唯一且互斥，
 *    排版视觉换行由去 \n 容差消除，且反向断言对立目标与初始 marker1 均不出现），彻底消除旧 12 字窗口在循环填充句中
 *    重复命中上百次的歧义；夹具定位关系在 setUp 实核（全书唯一性、互斥性、非 S0 性、无跨自然段换行）；
 *  - 外部来源合同负断言：「返回阅读处」contentDescription 不存在（栈空必无）；初始页
 *    marker1 不在当前页（确已离开初始页）；
 *  - 临时期间与 Back 回 Notes 后普通行 12 列 exact 逐列冻结（assertOrdinaryRowUnchanged）；
 *  - 回普通位 = 明确重新普通打开（Notes → Back → 顶层 → 底栏书架 → tile），普通正文与派生
 *    位置恢复 S0（assertBackAtOrdinarySpot）——这是重开恢复持久指针的独立行为，
 *    **不是 Back 凭空恢复 reader**。
 *
 * 用例2 第一次 Back 实际回列表后直接选第二条不同 offset（新代次单层会话）：
 *  - 第二次的隔离/正文就绪/负断言/12 列冻结/Back 回列表同上（6000 区窗口与 2000 区分属
 *    两个不同填充句，正文互斥可区分）；
 *  - 不携带前次临时栈或源状态（离域时 endReaderSession 已清空，重进前无任何 reader 会话）。
 *
 * ## 诚实边界（单独记未覆盖，不得当已完成）
 *
 *  - reader 内发起临时查阅的 LIFO 返回目标链（「返回阅读处」按钮路径）生产可达，但本测试
 *    入口链（外部 Notes）不经过它——仍未覆盖；
 *  - reader 内缺少「临时查看来源」真实入口的功能缺口（173 结论）仍未覆盖；
 *  - 两方法技术 ID 为复用冻结 host 方法绑定而原样保留；方法名中的 ReturnsToOrdinarySpot /
 *    reentersTemporarily 在本合同下的实际语义是「重新普通打开恢复持久指针」与「不携带状态
 *    的再次临时进入」，不代表原「Back 回普通位」目标已完成；
 *  - 本批仅定向编译与测试 APK 构建，NOT_RUN on device；171 两方法原红、历史红与恢复缺证永留。
 *
 * ## 归属与清理
 *
 * runId 从 instrumentation 参数 `craRunId` 取得（冻结 host 派发传入），缺省生成
 * `T153V1-<ts>` 并记录；两本测试 TXT 经 J1RealReaderFixtures.setupRunFixtures（ledger 登记）；
 * 两条来源笔记 id 带 `cra153-<runId>-` 前缀（写前归属可核，前缀为既有冻结值不改）；
 * 清理只做 cleanRunFixtures（books+dirs）与 onboarding 恢复，笔记按前缀可辨识、不硬删未知数据。
 */
class NestedTemporaryReaderEntryTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private val runId: String =
        InstrumentationRegistry.getArguments().getString("craRunId")
            ?: "T153V1-" + (System.nanoTime() % 1_000_000_000L)

    private val bookIdA = "cra153-$runId-book-a"
    private val bookIdB = "cra153-$runId-book-b"
    private val bookIdUnreadable = "cra153-$runId-book-unreadable"

    private var sourceOffset1: Int = 0
    private var sourceOffset2: Int = 0
    private lateinit var target1Text: String
    private lateinit var target2Text: String

    private lateinit var db: AppDatabase
    private lateinit var ledger: J1RealReaderFixtures.RunLifecycleLedger
    private lateinit var contentA: J1RealReaderFixtures.BookAContent
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null

    @Before
    fun setUpFixtures() {
        db = J1RealReaderFixtures.openDatabase(appContext)
        originalOnboarded = J1RealReaderFixtures.prepareOnboardingState(appContext)
        ledger = J1RealReaderFixtures.RunLifecycleLedger(runId)
        val contents = J1RealReaderFixtures.setupRunFixtures(
            context = appContext,
            db = db,
            runId = runId,
            bookIdA = bookIdA,
            bookIdB = bookIdB,
            bookIdUnreadable = bookIdUnreadable,
            ledger = ledger,
        )
        contentA = contents.first
        target1Text = TARGET1_HEADING
        sourceOffset1 = contentA.bodyText.indexOf(target1Text)
        target2Text = contentA.snippet2
        sourceOffset2 = contentA.offset2
        checkFixtureLocatorRelationship()
        seedSourceNotes()
        // 夹具就绪后再拉起真实 MainActivity（J1 既有模式，避免启动时序竞态）。
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDownFixtures() {
        scenario?.close()
        J1RealReaderFixtures.restoreOnboardingState(appContext, originalOnboarded)
        J1RealReaderFixtures.cleanRunFixtures(appContext, db, ledger)
    }

    /** 列表卡片的生产渲染身份（187 根因）：AnnotationEntryCard 渲染批注体
     *  （entry.annotation = NoteEntity.body；AnnotationEntryCard.kt 个人批注气泡 Text），
     *  该页从不组合 entry.title（grep 0 命中）——标题仅存在于库层与 matchesKeyword 筛选层。 */
    private val noteBody1 = "测试 TXT 笔记正文（src1）"
    private val noteBody2 = "测试 TXT 笔记正文（src2）"

    /** 两条甲书来源笔记（同书不同目标）；locator_json 用生产 LocatorCodec（嵌套 locator_v2 口径）。 */
    private fun seedSourceNotes() {
        runBlocking {
            db.noteDao().upsert(noteEntityFor("src1", sourceOffset1, "测试 TXT 甲 来源一", noteBody1))
            db.noteDao().upsert(noteEntityFor("src2", sourceOffset2, "测试 TXT 甲 来源二", noteBody2))
        }
    }

    private fun noteEntityFor(tag: String, offset: Int, title: String, body: String): NoteEntity = NoteEntity(
        id = "cra153-$runId-note-$tag",
        book_id = bookIdA,
        title = title,
        body = body,
        locator_json = LocatorCodec.encode(
            legacyOffset = offset, chapterIndex = null, charOffset = null, excerpt = null,
        ),
        created_at = "2026-10-27T00:00:00Z",
        updated_at = "2026-10-27T00:00:00Z",
        kind = "note",
    )

    /**
     * 先核 fixture 定位关系（175 合同去歧义要求）：
     * 1. 目标 1 与目标 2 文本非空且在全书中出现次数恰好为 1（全局唯一，消除循环填充句歧义）；
     * 2. 两目标不同（target1Text != target2Text，sourceOffset1 != sourceOffset2）；
     * 3. 两目标均不是普通初始位置 S0（不同于 offset1，且均不包含普通初始 marker1）；
     * 4. 两目标文本均不包含换行（段落完整，排版去 \n 容差不改变单句完整性）；
     * 5. 互斥性：目标 1 文本不包含目标 2，目标 2 文本不包含目标 1。
     */
    private fun checkFixtureLocatorRelationship() {
        val text = contentA.bodyText
        check(target1Text.isNotBlank()) { "target1Text 不能为空" }
        check(target2Text.isNotBlank()) { "target2Text 不能为空" }
        check(sourceOffset1 >= 0) { "sourceOffset1 必须在正文中存在 (got $sourceOffset1)" }
        check(sourceOffset2 >= 0 && sourceOffset2 < text.length) { "sourceOffset2 必须在正文中合法 (got $sourceOffset2)" }

        val count1 = countOccurrences(text, target1Text)
        check(count1 == 1) { "target1Text='$target1Text' 必须在全书中唯一（实际出现 $count1 次）" }

        val count2 = countOccurrences(text, target2Text)
        check(count2 == 1) { "target2Text='$target2Text' 必须在全书中唯一（实际出现 $count2 次）" }

        check(sourceOffset1 != sourceOffset2) { "两个 sourceOffset 必须互斥 (got $sourceOffset1)" }
        check(target1Text != target2Text) { "两个目标正文必须互不相同" }

        check(sourceOffset1 != contentA.offset1 && sourceOffset2 != contentA.offset1) {
            "两目标 offset 不能与普通初始位点 S0 (offset1=${contentA.offset1}) 相同"
        }
        check(!target1Text.contains(contentA.marker1) && !target2Text.contains(contentA.marker1)) {
            "两目标正文均不得包含普通初始位点 marker1"
        }

        check(!target1Text.contains('\n')) { "target1Text 包含换行，跨自然段" }
        check(!target2Text.contains('\n')) { "target2Text 包含换行，跨自然段" }

        check(!target1Text.contains(target2Text) && !target2Text.contains(target1Text)) {
            "目标 1 与目标 2 文本不得互相包含"
        }
    }

    private fun countOccurrences(fullText: String, sub: String): Int {
        if (sub.isEmpty()) return 0
        var count = 0
        var idx = 0
        while (true) {
            val found = fullText.indexOf(sub, idx)
            if (found == -1) break
            count++
            idx = found + sub.length
        }
        return count
    }

    /** 真实 UI：启动页 Home → 底栏「书架」→ 甲书；普通阅读就绪以生产正文文本出现为准。 */
    private fun openBookAOrdinarily() {
        composeRule.waitForIdle()
        // 生产 startDestination 是 Home，book-tile 只在 Shelf。先证明顶层页面，
        // 再走真实底栏 Role.Tab 入口「书架」，等本轮同一 book 的精确 tag 可见。
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isEmpty()) {
            // 书架页标题「书架」本身可点（打开整理器），必须用底栏项独有的 selectable 语义定位。
            composeRule.onNode(hasText("书架") and isSelectable()).performClick()
            composeRule.waitForIdle()
        }
        openBookAFromShelfTile()
    }

    /** 真实 UI：精确 book-tile → 普通阅读就绪（contentA.marker1 文本节点出现且可见）。 */
    private fun openBookAFromShelfTile() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("book-tile-$bookIdA").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("book-tile-$bookIdA").assertExists().performClick()
        // 普通阅读就绪：J1 夹具把本书进度播种在 initialLocA（offset1=4014），生产分页从
        // 「包含 offset1 的那一页」恢复（页起始 ≤ offset1）。复用 170 已验证语义：等该页真实
        // text 语义节点出现并可见（PagedTxtReaderHost.kt:399-402；marker 两侧【】禁尾/禁首且
        // 西文串为原子单元，不会被换行拆断）。
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(contentA.marker1, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(contentA.marker1, substring = true)).assertIsDisplayed()
        composeRule.waitForIdle()
    }

    /** 生产 chrome 合同（183-v1 根因）：ReaderChromeReducer 初值 controlsVisible=true 且 Enter→Visible，
     *  ReaderScreen BackHandler 分支序里 controlsVisible 层先于退出边界消费 Back——控件可见时
     *  第一次系统 Back 只隐藏控件层（ToggleControls(false)），不导航。本 helper 用生产
     *  reader-progress-scrubber 语义判控件层是否可见（autoHideSeconds>0 时可能已被计时隐藏，
     *  不假设初始态）；可见则按真实用户路径一次 Back 藏控件并断言其消失，再交由调用方
     *  的下一次 Back 进入退出边界。不循环、不 sleep、不注入状态。 */
    private fun hideReaderChromeIfVisible() {
        composeRule.waitForIdle()
        val chromeVisible = composeRule.onAllNodesWithTag("reader-progress-scrubber")
            .fetchSemanticsNodes().isNotEmpty()
        if (chromeVisible) {
            pressSystemBack()
            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodesWithTag("reader-progress-scrubber")
                    .fetchSemanticsNodes().isEmpty()
            }
        }
    }

    /** 真实退出：控件层已隐藏后系统 Back 进退出边界（保存→drain→导航）→ 书架顶层。书架可见即
     *  退出导航完成；生产退出保存（ReaderExitBoundary）先于导航完成，此刻普通行即退出后终值
     *  （S0 的可观察边界）。181 根因修复：Back 前先按生产合同隐藏控件层，否则首次 Back 被
     *  controlsVisible 分支消费、永不导航（181 两方法 275 行 15s 超时）。 */
    private fun exitReaderToTopLevel() {
        hideReaderChromeIfVisible()
        pressSystemBack()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    /** 真实 UI：唯一 selectable 底栏「我的」→「阅读笔记」二级路由；先证本轮两条夹具条目都在列表。
     *  187 根因修复：等的是卡片**实际渲染**的批注体（noteBody1/2）——旧断言等笔记标题精确
     *  Text，但生产列表页不渲染标题（AnnotationEntryCard 只组合 excerpt/annotation），设备上
     *  永不满足 → 185 两方法 313 行 15s 超时。标题仍由库层/关键词筛选层承载，不放宽任何断言。 */
    private fun openReadingNotesList() {
        composeRule.onNode(hasText("我的") and isSelectable()).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("阅读笔记").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("阅读笔记").assertExists().performClick()
        composeRule.waitForIdle()
        // 列表真实身份：本轮 fixture 的两条精确条目都已渲染（此后再用关键词过滤逐条隔离）。
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(noteBody1).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText(noteBody2).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** 卡片渲染批注体节点按 Text 语义匹配（输入框是 EditableText，不会混入；187：匹配目标
     *  从笔记标题切到实际渲染的 entry.annotation 体，标题不作为 Text 上屏）。 */
    private fun renderedBodyMatcher(body: String) = SemanticsMatcher("cardRenderedBody('$body')") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains(body) } == true
    }

    private fun renderedBodyNodes(body: String) =
        composeRule.onAllNodes(renderedBodyMatcher(body)).fetchSemanticsNodes()

    /** 用生产关键词过滤把目标条目隔离成唯一卡片（渲染批注体恰 1、胶囊恰 1），再点唯一「临时查看来源」。
     *  关键词走生产 matchesKeyword（标题/摘录/批注/章节名全检），此处输入批注体同样命中且逐卡唯一。 */
    private fun isolateNotesEntry(noteBody: String) {
        val filterField = composeRule.onNode(hasSetTextAction())
        filterField.performClick()
        filterField.performTextClearance()
        filterField.performTextInput(noteBody)
        composeRule.waitUntil(timeoutMillis = 15_000) {
            renderedBodyNodes(noteBody).size == 1 &&
                composeRule.onAllNodesWithText("临时查看来源").fetchSemanticsNodes().size == 1
        }
        composeRule.onAllNodes(renderedBodyMatcher(noteBody))[0].assertIsDisplayed()
        composeRule.onNodeWithText("临时查看来源").assertIsDisplayed()
    }

    /** 真实 UI：隔离精确条目 → 唯一胶囊 → 临时正文按该目标精确就绪 + 合同 B 负断言。 */
    private fun inspectSourceTemporarily(
        noteBody: String,
        sourceOffset: Int,
        targetText: String,
        otherTargetText: String,
    ) {
        isolateNotesEntry(noteBody)
        composeRule.onNodeWithText("临时查看来源").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        awaitTemporaryBodyAtTarget(sourceOffset, targetText, otherTargetText, noteBody)
    }

    /** 当前页 text 语义原文（PageLayer 仅对当前页暴露 accessibleText，过渡帧为 null）。 */
    private fun displayedPageTexts(): List<String> =
        composeRule.onAllNodes(
            SemanticsMatcher("exposesTextSemantics") { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.isNotEmpty() == true
            },
        ).fetchSemanticsNodes().mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)
                ?.joinToString(separator = "") { it.text }
                ?.takeIf { it.isNotBlank() }
        }

    /** 当前页去 \n 后是否包含目标正文（消除排版视觉换行拆断）。 */
    private fun pageShowsTargetText(targetText: String): Boolean =
        displayedPageTexts().any { page ->
            val flat = page.replace("\n", "")
            flat.contains(targetText)
        }

    /**
     * 临时正文就绪 + 外部来源负断言（175 去歧义目标正文判据）：
     *  - 页 text 语义去 \n 后包含全书唯一的 targetText（消除排版折行拆断，且能证明命中指定唯一目标）；
     *  - 对立目标 otherTargetText 不得出现在当前页（两临时来源严格互斥，无法碰巧通过）；
     *  - 「返回阅读处」必不存在（外部进入 active 已清、栈空，ReaderChrome.kt:622-637 不组合）；
     *  - 初始页 marker1 不在当前页（确已离开初始位点 S0 所在页）。
     */
    private fun awaitTemporaryBodyAtTarget(
        sourceOffset: Int,
        targetText: String,
        otherTargetText: String,
        label: String,
    ) {
        composeRule.waitUntil(timeoutMillis = 15_000) { pageShowsTargetText(targetText) }
        assertTrue(
            "$label: temporary page must display unique target text '$targetText' for sourceOffset $sourceOffset",
            pageShowsTargetText(targetText),
        )
        assertTrue(
            "$label: other target text '$otherTargetText' must not be on current temporary page",
            !pageShowsTargetText(otherTargetText),
        )
        composeRule.onNodeWithContentDescription("返回阅读处").assertDoesNotExist()
        assertTrue(
            "$label: initial-page marker1 must not be on the temporary page",
            composeRule.onAllNodesWithText(contentA.marker1, substring = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    /** 真实 Back 后回到 Notes 列表原条目：过滤域与目标条目卡重新在屏，并可再次隔离成唯一卡片
     *  （合同 B 的返回目的地证据：profile/notes 真实二级路由原样回退）。 */
    private fun awaitBackOnNotesList(noteBody: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() &&
                renderedBodyNodes(noteBody).isNotEmpty()
        }
        isolateNotesEntry(noteBody)
    }

    /** 合同 B 的回普通位：Notes 子页系统 Back → 我的顶层（底栏回归）→ 底栏「书架」→ tile
     *  重新普通打开。这是明确的重新普通打开（恢复持久指针），不是临时 Back 直接恢复 reader。 */
    private fun reopenBookAOrdinarilyFromNotes() {
        pressSystemBack()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(hasText("书架") and isSelectable()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("书架") and isSelectable()).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty()
        }
        openBookAFromShelfTile()
    }

    private fun pressSystemBack() {
        scenario!!.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    /** 普通行等待建立并返回快照（recordNormalReading 防抖落库；夹具已播种 initialLocA）。 */
    private fun awaitOrdinaryRow(): J1RealReaderFixtures.ProgressRowSnapshot {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            J1RealReaderFixtures.captureProgressRow(db, bookIdA) != null
        }
        val row = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        assertNotNull("ordinary progress row must exist for book A", row)
        return row!!
    }

    /** 解析普通行 current_location_json 的派生 legacyOffset（嵌套 locator_v2 wins 口径）。 */
    private fun rowOffset(row: J1RealReaderFixtures.ProgressRowSnapshot?): Int? =
        row?.let {
            LocatorCodec.locatorFromProgress(it.current_location_json)?.legacyOffset
        }

    /** 位置合法性：行存在、页起始非零且 ≤ offset1（生产持久合同），且不落在两个临时 sourceOffset。
     *  用于普通退出前后——正常退出可能合法写入时间等元数据，这里只锁位置边界。 */
    private fun assertRowPositionLegal(row: J1RealReaderFixtures.ProgressRowSnapshot?, label: String) {
        assertNotNull("$label: progress row must exist", row)
        J1RealReaderFixtures.assertObservedPersistedLocation(row!!, contentA.offset1, label)
        val off = rowOffset(row)
        assertTrue(
            "$label: row position must not sit at temporary source offsets (got $off)",
            off != sourceOffset1 && off != sourceOffset2,
        )
    }

    /** 隔离断言：临时期间普通行 == S0（逐列 exact，nullable 对齐）且位置绝不落在临时源。 */
    private fun assertOrdinaryRowUnchanged(
        s0: J1RealReaderFixtures.ProgressRowSnapshot?,
        during: J1RealReaderFixtures.ProgressRowSnapshot?,
        label: String,
    ) {
        if (s0 == null) {
            assertEquals("$label: ordinary row must stay absent", null, during)
        } else {
            assertNotNull("$label: ordinary row must not vanish", during)
            s0.assertExactEquals(during, label)
        }
        val off = rowOffset(during)
        assertTrue(
            "$label: ordinary row must never sit at temporary source offsets (got $off)",
            off == null || (off != sourceOffset1 && off != sourceOffset2),
        )
    }

    /** 重开恢复断言：明确重新普通打开后的派生位置 == S0 页起始（持久指针恢复，未被两个
     *  临时 sourceOffset 改写）。合同 B 下由重开触发，不是 Back 凭空恢复 reader。 */
    private fun assertBackAtOrdinarySpot(
        s0: J1RealReaderFixtures.ProgressRowSnapshot,
        after: J1RealReaderFixtures.ProgressRowSnapshot?,
        label: String,
    ) {
        val s0Offset = rowOffset(s0)
        val afterOffset = rowOffset(after)
        assertEquals("$label: must restore the exact ordinary spot", s0Offset, afterOffset)
        assertTrue(
            "$label: must not be a temporary source offset",
            afterOffset != sourceOffset1 && afterOffset != sourceOffset2,
        )
    }

    // ── 用例1：普通建立 → 真实退出建立 S0 → 底栏我的进 Notes → 临时查阅（来源一）→
    //    12 列隔离 → Back 回 Notes 原条目（合同 B）→ 明确重新普通打开回 S0 ──
    @Test
    fun normalThenSingleTemporaryEntry_isolatesOrdinaryRow_andReturnsToOrdinarySpot() {
        openBookAOrdinarily()
        assertRowPositionLegal(awaitOrdinaryRow(), "beforeNormalExit")

        exitReaderToTopLevel()
        val s0 = awaitOrdinaryRow()
        assertRowPositionLegal(s0, "s0AfterNormalExit")

        openReadingNotesList()
        inspectSourceTemporarily(noteBody1, sourceOffset1, target1Text, target2Text)

        val during = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        assertOrdinaryRowUnchanged(s0, during, "duringTemporary")

        // 临时 reader 同一 chrome 合同：控件可见时首次 Back 只藏控件（ReaderScreen 分支序），
        // 先藏控件再 Back 才走 performTemporaryReturn（外部 entry 栈空 → popBackStack 回 Notes）。
        hideReaderChromeIfVisible()
        pressSystemBack()
        awaitBackOnNotesList(noteBody1)
        val afterBack = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        assertOrdinaryRowUnchanged(s0, afterBack, "afterBackToNotes")

        reopenBookAOrdinarilyFromNotes()
        assertBackAtOrdinarySpot(s0, J1RealReaderFixtures.captureProgressRow(db, bookIdA), "reopenNormal")
    }

    // ── 用例2：第一次 Back 实际回列表后直接选第二条不同 offset（新代次单层，不携带前次栈/状态） ──
    @Test
    fun secondSourceEntry_sameBookDifferentTarget_reentersTemporarilyWithoutCarriedState() {
        openBookAOrdinarily()
        assertRowPositionLegal(awaitOrdinaryRow(), "beforeNormalExit")

        exitReaderToTopLevel()
        val s0 = awaitOrdinaryRow()
        assertRowPositionLegal(s0, "s0AfterNormalExit")

        openReadingNotesList()
        inspectSourceTemporarily(noteBody1, sourceOffset1, target1Text, target2Text)
        val duringFirst = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        assertOrdinaryRowUnchanged(s0, duringFirst, "duringFirstTemporary")

        // 临时 reader 同一 chrome 合同：控件可见时首次 Back 只藏控件（ReaderScreen 分支序），
        // 先藏控件再 Back 才走 performTemporaryReturn（外部 entry 栈空 → popBackStack 回 Notes）。
        hideReaderChromeIfVisible()
        pressSystemBack()
        awaitBackOnNotesList(noteBody1)
        val afterFirstBack = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        assertOrdinaryRowUnchanged(s0, afterFirstBack, "afterFirstBack")

        // 第一次 Back 已实际回列表：直接选第二条（不同 offset）——新代次单层会话，无 reader 会话。
        inspectSourceTemporarily(noteBody2, sourceOffset2, target2Text, target1Text)
        val duringSecond = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        assertOrdinaryRowUnchanged(s0, duringSecond, "duringSecondTemporary")

        // 临时 reader 同一 chrome 合同：控件可见时首次 Back 只藏控件（ReaderScreen 分支序），
        // 先藏控件再 Back 才走 performTemporaryReturn（外部 entry 栈空 → popBackStack 回 Notes）。
        hideReaderChromeIfVisible()
        pressSystemBack()
        awaitBackOnNotesList(noteBody2)
        val afterSecondBack = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        assertOrdinaryRowUnchanged(s0, afterSecondBack, "afterSecondBack")

        reopenBookAOrdinarilyFromNotes()
        assertBackAtOrdinarySpot(s0, J1RealReaderFixtures.captureProgressRow(db, bookIdA), "reopenNormal")
    }
}
