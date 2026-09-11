package com.creationreadingassistant.ui.navigation

import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.feature.reader.locator.ReaderLocator
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationTarget
import com.creationreadingassistant.ui.screen.reader.ReaderNavigationMode
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * R2-J1.3：reader 临时查阅路由的纯函数工具。
 *
 * 所有 route 只承载 source locator（bookId + [ReaderLocator] 的 source 坐标），绝不引入页码、
 * 显示坐标，也绝不把临时状态塞进 highlightId。临时查阅模式经可选查询参数
 * `navigationMode=temporary` 显式表达；缺失/未知值一律按普通阅读处理，兼容既有
 * `reader/{bookId}?highlightId=&sourceLocator=` 的普通导航语义。
 *
 * 这里除 [resolveTemporaryReturnRoute] 会推进协调器的 LIFO 状态外，其余都是无副作用的纯函数，
 * 可在纯 JVM 中测试。percent-encode 用 JDK URLEncoder 实现；对 source locator JSON（无空格、
 * 无 `*`、无 `!~'()`）而言，它与 android.net.Uri.encode 的编码结果逐字节等价。
 */

/** navigationMode 查询参数的临时查阅取值。 */
const val NAVIGATION_MODE_TEMPORARY = "temporary"

/**
 * 解析 navigationMode 查询参数为阅读导航模式。
 * 仅 `temporary` 视为临时查阅；其余（含 null、空白、未知值、大小写不符）一律降级普通阅读。
 */
fun readerNavigationMode(raw: String?): ReaderNavigationMode =
    if (raw == NAVIGATION_MODE_TEMPORARY) ReaderNavigationMode.TEMPORARY else ReaderNavigationMode.NORMAL

/** 按 percent-encode 编码查询组件值；等价于 android.net.Uri.encode 对 source locator JSON 的行为。 */
private fun encodeQueryComponent(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

/**
 * 把 source target 的 locator 编成 locator JSON 并 percent-encode。
 * 仅接受携带全局 `legacyOffset` 的 target；无全局坐标（如仅章节元组）时返回 null，
 * 绝不伪造 offset=0 的假位置。
 */
fun encodeSourceLocator(target: SourceNavigationTarget): String? {
    val offset = target.locator.legacyOffset ?: return null
    if (offset < 0) return null
    val json = LocatorCodec.encode(
        legacyOffset = offset,
        chapterIndex = target.locator.chapterIndex,
        charOffset = target.locator.charOffset,
        excerpt = null,
    )
    return encodeQueryComponent(json)
}

/** target -> 普通 source 定位 route（不带 navigationMode，兼容既有普通导航）。 */
fun readerSourceRoute(target: SourceNavigationTarget): String? {
    val locator = encodeSourceLocator(target) ?: return null
    return "reader/${encodeQueryComponent(target.bookId)}?sourceLocator=$locator"
}

/** target -> 临时查阅 route（带 navigationMode=temporary）。 */
fun readerTemporaryRoute(target: SourceNavigationTarget): String? {
    val locator = encodeSourceLocator(target) ?: return null
    return "reader/${encodeQueryComponent(target.bookId)}?sourceLocator=$locator&navigationMode=$NAVIGATION_MODE_TEMPORARY"
}

/**
 * 由**已解码**的 source 坐标构造临时查阅 route（J1-I.2）。
 *
 * [SourceNavigationTarget] 的构造器是 internal，而 UI 层（如「我的 → 阅读笔记」）手上只有
 * 条目 locator 拆出来的零散字段，无法自行拼装 target。本函数是这类调用方唯一的公共入口，
 * 且刻意只接受 source 坐标：**不接受 highlightId / noteId / rawId**，避免临时查阅状态被
 * 塞进高亮参数、或让「点条目即普通跳转」与「临时查阅来源」两条语义串味。
 *
 * 校验复用 [SourceNavigationContract.target]（拒绝空书籍 ID、负 offset、半截章节坐标），
 * 因此无有效 source 坐标时返回 null —— 绝不伪造 offset=0 的假位置。是否显示入口由调用方
 * 依据同样的「有效 source locator」判据决定；本函数不做 UI 判定。
 *
 * 入口方只需 `navigate(本返回值)`；**不得**在此处推进临时查阅协调器状态
 * （`beginTemporaryInspection` 只能由 ReaderRoute 在解析到 `navigationMode=temporary` 时触发）。
 */
fun readerTemporaryRouteForSource(
    bookId: String?,
    legacyOffset: Int?,
    chapterIndex: Int?,
    charOffset: Int?,
): String? {
    val target = SourceNavigationContract.target(
        bookId,
        ReaderLocator(
            legacyOffset = legacyOffset,
            chapterIndex = chapterIndex,
            charOffset = charOffset,
            excerptFingerprint = null,
        ),
    ) ?: return null
    return readerTemporaryRoute(target)
}

/**
 * 条目是否具备「临时查看来源」所需的有效 source 坐标（J1-I.2）。
 *
 * 与 [readerTemporaryRouteForSource] **同判据**（由它派生，不重复实现规则）：临时查阅 route 必须
 * 携带全局 legacyOffset 才能跨渲染模式往返，因此只有章节元组、没有全局偏移的历史条目不可用。
 * 入口可见性必须与可跳转性一致，否则会出现「按钮在、点了没反应」。
 */
fun hasTemporaryInspectionSource(bookId: String?, legacyOffset: Int?): Boolean =
    readerTemporaryRouteForSource(
        bookId = bookId,
        legacyOffset = legacyOffset,
        chapterIndex = null,
        charOffset = null,
    ) != null

/**
 * target -> 返回「普通阅读处」route。仅携带 sourceLocator（不带 highlightId、不带 navigationMode），
 * 因为最终返回目的地是普通阅读位置。target 为 null（无可返回位置）返回 null，调用方保持
 * 正常离开 reader 的行为，不伪造 target。
 */
fun readerReturnRoute(target: SourceNavigationTarget?): String? {
    target ?: return null
    val locator = encodeSourceLocator(target) ?: return null
    return "reader/${encodeQueryComponent(target.bookId)}?sourceLocator=$locator"
}

/**
 * 把 route 的 bookId + sourceLocator（已由导航组件解码为 locator JSON）还原为 [SourceNavigationTarget]。
 * 供 ReaderRoute 在临时模式进入时取得目的地；无有效 source 坐标返回 null。
 */
fun sourceTarget(bookId: String?, sourceLocatorJson: String?): SourceNavigationTarget? =
    SourceNavigationContract.target(bookId, LocatorCodec.decode(sourceLocatorJson))

/**
 * 执行一次 LIFO 临时返回，返回「应导航到的 reader route」；无可返回位置（临时栈为空）返回 null。
 * 返回目标完全由 [TemporaryReadingNavigationViewModel] 的 state 决定（逐层 LIFO），
 * 不依赖 NavController back stack 数量；也绝不根据返回目标伪造 offset。
 *
 * 中间层（返回后临时栈仍非空）继续以 `navigationMode=temporary` 呈现，允许逐层回退；
 * 最后一层（返回后临时栈清空，回到普通阅读处）不带 navigationMode，恢复普通阅读语义。
 */
fun resolveTemporaryReturnRoute(
    temporaryNavigation: TemporaryReadingNavigationViewModel,
): String? {
    if (!temporaryNavigation.hasReturnableTarget) return null
    val returnTarget = temporaryNavigation.state.value.temporaryReturnStack.lastOrNull()
    temporaryNavigation.returnFromTemporaryInspection()
    val stillTemporary = temporaryNavigation.state.value.temporaryReturnStack.isNotEmpty()
    if (returnTarget == null) return null
    return if (stillTemporary) readerTemporaryRoute(returnTarget) else readerReturnRoute(returnTarget)
}

/**
 * 从 route 字符串还原其 sourceLocator 的 [ReaderLocator]（测试与诊断用）。
 * 仅解析 `sourceLocator` 查询组件并反向 percent-decode，不触碰其它参数。
 */
fun decodeSourceLocatorFromRoute(route: String): ReaderLocator? {
    val query = route.substringAfter('?', missingDelimiterValue = "")
    val encoded = query.split('&')
        .firstOrNull { it.startsWith("sourceLocator=") }
        ?.removePrefix("sourceLocator=")
        ?: return null
    val decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
    return LocatorCodec.decode(decoded)
}