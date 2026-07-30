package com.creationreadingassistant.feature.reader

import android.content.Context
import android.net.Uri
import com.creationreadingassistant.domain.model.EpubBlock
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.domain.model.EpubChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.text.RegexOption
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.collections.ArrayDeque
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * 自包含 EPUB 解析器（零外部依赖）。
 *
 * 流程：SAF Uri → 拷到缓存 → ZipFile 随机访问 → 读 container.xml 定 OPF →
 * 解析 OPF 的 metadata/manifest/spine → 按 spine 顺序抽取 XHTML 章节 →
 * 去标签提取文本块、抽出图片到缓存目录。
 */
object EpubParser {
    private const val MAX_CHAPTER_BYTES = 8L * 1024L * 1024L

    /** 单章纯文本抽取上限（字符）。超出部分丢弃：搜索退化为覆盖前 400 万字，好过 OOM 或整章空白。 */
    private const val MAX_CHAPTER_TEXT_CHARS = 4_000_000

    /** 单个标签文本的收集上限，防御畸形文档里没有闭合 '>' 的超长「标签」。 */
    private const val MAX_TAG_CHARS = 2048

    /**
     * 「当前不在属性引号内」的哨兵。
     *
     * 必须是具名常量，而不是初值/比较/复位三处各写各的字面量：只要有一处不一致，
     * 标签内每个字符都会被当成引号内容吞掉，'>' 永远处理不到、inTag 再不复位，
     * 整章正文会静默消失。
     */
    private const val NO_QUOTE = '\u0000'

    private val WHITESPACE_REGEX = Regex("\\s+")
    private val RE_IMG_SRC = Regex("src\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    /** SVG 封面页用的 <image xlink:href="…">，此前完全不识别，整页渲染为空白。 */
    private val RE_SVG_HREF = Regex("(?:xlink:)?href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    suspend fun parse(
        context: Context,
        uri: Uri,
        expectedBookId: String? = null,
        fallbackLocalPath: String? = null,
    ): EpubBook = withContext(Dispatchers.IO) {
        // 使用完整 Uri 字符串的 hashCode，避免不同书因 lastPathSegment 相同而碰撞。
        val id = expectedBookId ?: ("epub_" + uri.toString().hashCode().toString(36))
        // EPUB 是已导入的用户内容，不能只放 cacheDir：系统清缓存后书架记录还在，
        // 正文却会消失。首次打开时迁移/复制到 filesDir，后续解析只依赖应用自有副本。
        val epubFile = File(context.filesDir, "books/epub/$id.epub")
        if (!epubFile.exists() || epubFile.length() == 0L) {
            epubFile.parentFile?.mkdirs()
            val staging = File(epubFile.parentFile, "$id.importing")
            staging.delete()
            try {
                val copied = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        staging.outputStream().use { out -> input.copyTo(out) }
                    } != null
                }.getOrDefault(false)
                if (!copied) {
                    // 兼容同步下载和旧版本：外部 Uri 权限失效时，从已登记的本地路径
                    // 或旧 cacheDir 副本迁移，仍保留原书 id 与阅读进度。
                    val fallback = fallbackLocalPath
                        ?.removePrefix("file://")
                        ?.let(::File)
                        ?.takeIf { it.isFile && it.length() > 0L }
                        ?: File(context.cacheDir, "epub/$id.epub")
                            .takeIf { it.isFile && it.length() > 0L }
                    if (fallback != null) {
                        fallback.inputStream().use { input ->
                            staging.outputStream().use { out -> input.copyTo(out) }
                        }
                    }
                }
                if (staging.length() == 0L) {
                    throw IllegalStateException("无法打开 EPUB 文件，请重新导入")
                }
                if (!staging.renameTo(epubFile)) {
                    staging.copyTo(epubFile, overwrite = true)
                    staging.delete()
                }
            } catch (e: Exception) {
                staging.delete()
                throw e
            }
        }
        val cachedPath = epubFile.absolutePath

        val zip = ZipFile(epubFile)
        try {
            val containerEntry = zip.getEntry("META-INF/container.xml")
                ?: throw IllegalStateException("不是合法的 EPUB（缺少 META-INF/container.xml）")
            val opfPath = zip.getInputStream(containerEntry).use { parseContainer(it) }
            val opfDir = opfPath.substringBeforeLast('/', "")

            val opfEntry = zip.getEntry(opfPath)
                ?: throw IllegalStateException("OPF 文件缺失：$opfPath")
            val (title, creator, manifest, spine, spineTocId) = zip.getInputStream(opfEntry).use { parseOpf(it) }

            val chapterRefs = spine.mapNotNull { idref -> manifest[idref] }
                .ifEmpty {
                    // 某些转换器会生成空 spine，但 manifest 仍完整列出正文。按 manifest
                    // 原顺序兜底，排除 EPUB3 nav 文档，避免整本只得到一个空白占位章。
                    manifest.values.filter { item ->
                        item.isReadableDocument() && !item.properties
                            .split(Regex("\\s+"))
                            .any { it.equals("nav", ignoreCase = true) }
                    }
                }

            // 优先使用 EPUB 内置目录（NCX / Navigation Document），获得准确的章节标题；
            // 无内置目录时回退到 spine 顺序，过滤封面/版权等非正文条目。
            val tocEntries = resolveToc(zip, opfDir, manifest, spine, spineTocId)

            val chapters = if (tocEntries != null && tocEntries.isNotEmpty()) {
                buildChaptersFromToc(zip, opfDir, cachedPath, chapterRefs, tocEntries)
            } else {
                buildChaptersFromSpine(zip, opfDir, cachedPath, chapterRefs, manifest, spine)
            }
            if (chapters.isEmpty()) {
                throw IllegalStateException("EPUB 中没有可读取的正文文件")
            }

            EpubBook(
                id = id,
                title = title ?: "未命名书籍",
                author = creator,
                chapters = chapters,
                localUri = uri.toString(),
                cachedEpubPath = cachedPath,
            )
        } finally {
            zip.close()
        }
    }

    /**
     * 按需懒加载单章正文块：仅解当前章，复用 [extractBlocks] / [extractImage]。
     * 必须在 IO 线程调用（阅读器预加载阶段），切勿在主线程调用以免 ANR。
     */
    fun loadChapterBlocks(cachedEpubPath: String, entryPath: String, chapterDir: String): List<EpubBlock> {
        if (entryPath.isEmpty()) return emptyList()
        val zip = ZipFile(cachedEpubPath)
        try {
            val entry = findEntry(zip, entryPath) ?: return emptyList()
            if (entry.size > MAX_CHAPTER_BYTES) {
                throw IllegalStateException(
                    "当前 EPUB 章节过大（${entry.size / 1024 / 1024} MB），已停止解析以避免应用闪退。",
                )
            }
            val id = File(cachedEpubPath).nameWithoutExtension
            val imgCacheDir = File(File(cachedEpubPath).parentFile, "$id/img").also { it.mkdirs() }
            return extractStreaming(zip, entry, chapterDir, imgCacheDir, entryPath.hashCode().toString(36))
        } finally {
            zip.close()
        }
    }

    /**
     * 单章纯文本（用于搜索 / 字数统计 / 偏移索引）。
     *
     * 与 [loadChapterBlocks] **共用同一个抽取器**，再按 "\n" 拼接。这一点是必须的：
     * 此前本函数是流式状态机、渲染走正则版 extractBlocks，两套实现产出的文本长度不同，
     * 而 ReaderScreen 的 computeBlockGlobalOffsets 又按 `block.text.length + 1` 累加偏移
     * —— 搜索命中的偏移和渲染用的偏移根本对不上。当前搜索只跳到章，所以没暴露；
     * 一旦做章内精确跳转必然跳偏。共用一份实现即从根上消除该不一致。
     */
    fun loadChapterText(cachedEpubPath: String, entryPath: String, chapterDir: String): String {
        if (entryPath.isEmpty()) return ""
        val zip = ZipFile(cachedEpubPath)
        try {
            val entry = findEntry(zip, entryPath) ?: return ""
            if (entry.size > MAX_CHAPTER_BYTES) {
                throw IllegalStateException(
                    "当前 EPUB 章节过大（${entry.size / 1024 / 1024} MB），已停止解析以避免应用闪退。",
                )
            }
            // imgCacheDir = null：搜索/索引路径不抽图片，避免无谓的 I/O 与缓存写入。
            return extractStreaming(zip, entry, chapterDir, null, "")
                .filterIsInstance<EpubBlock.Text>()
                .joinToString("\n") { it.text }
        } finally {
            zip.close()
        }
    }

    /**
     * 流式抽取章节内容块。**这是本文件唯一的 HTML 抽取实现。**
     *
     * 此前有两套：渲染走 extractBlocks（把整章 HTML 读进内存做多轮正则替换），
     * 搜索走 loadChapterText（流式状态机）。除了产出不一致，正则那套在单章 > 2 MB 时
     * 还会降级成 `loadChapterText().chunked(2000)` —— 段落边界全丢、isHeading 全 false。
     * 排版层会给每个 2000 字「段」加首行缩进，正文里每 2000 字冒出一个假缩进，
     * 标题角色也没了。大章恰恰是最需要排版正确的场景。
     *
     * 现在统一为一次流式遍历：既不持有整章 HTML，又保留段落边界与标题角色，
     * 大章小章走同一条路径，没有降级分支。
     *
     * 必须在 IO 线程调用。
     *
     * @param imgCacheDir 为 null 时不抽取图片（搜索/索引路径只需要文本）。
     */
    private fun extractStreaming(
        zip: ZipFile,
        entry: ZipEntry,
        chapterDir: String,
        imgCacheDir: File?,
        prefix: String,
    ): List<EpubBlock> {
        val blocks = ArrayList<EpubBlock>()
        val sb = StringBuilder()
        var isHeading = false
        var emittedChars = 0
        var skipDepth = 0 // <script>/<style>/<head> 嵌套深度，>0 时正文整段跳过

        fun flush() {
            if (sb.isEmpty()) return
            val t = sb.toString().replace(WHITESPACE_REGEX, " ").trim()
            sb.setLength(0)
            if (t.isEmpty()) return
            blocks.add(EpubBlock.Text(t, isHeading))
            emittedChars += t.length
        }

        /** 从标签原文里取标签名。'/' 只在首位有效，遇到空白或属性即封口。 */
        fun tagNameOf(raw: String): String {
            val n = StringBuilder(12)
            for (ch in raw) {
                if (ch == '/' && n.isEmpty()) { n.append(ch); continue }
                if (ch.isLetterOrDigit()) n.append(ch) else break
            }
            return n.toString().lowercase()
        }

        fun handleTag(raw: String) {
            val name = tagNameOf(raw)
            if (skipDepth > 0) {
                if (name == "/script" || name == "/style" || name == "/head") {
                    skipDepth = (skipDepth - 1).coerceAtLeast(0)
                }
                return
            }
            when {
                name == "script" || name == "style" || name == "head" ->
                    if (!raw.trimEnd().endsWith("/")) skipDepth++ // <style/> 自闭合不进入跳过态
                name == "br" -> flush()
                name == "p" || name == "div" || name == "li" || name == "blockquote" ||
                    name == "/p" || name == "/div" || name == "/li" || name == "/blockquote" -> flush()
                // 标题开：先把上一块按旧角色收掉，再切成标题
                name.length == 2 && name[0] == 'h' && name[1] in '1'..'6' -> { flush(); isHeading = true }
                // 标题闭：必须先 flush 再复位，否则标题块会被记成正文
                name.length == 3 && name.startsWith("/h") && name[2] in '1'..'6' -> { flush(); isHeading = false }
                name == "img" || name == "image" -> {
                    flush()
                    if (imgCacheDir != null) {
                        val src = RE_IMG_SRC.find(raw)?.groupValues?.getOrNull(1)
                            ?: RE_SVG_HREF.find(raw)?.groupValues?.getOrNull(1)
                        if (src != null) {
                            extractImage(src, chapterDir, zip, imgCacheDir, "$prefix-${blocks.size}")
                                ?.let { blocks.add(EpubBlock.Image(it)) }
                        }
                    }
                }
            }
        }

        BufferedReader(InputStreamReader(zip.getInputStream(entry), Charsets.UTF_8), 64 * 1024).use { reader ->
            val tag = StringBuilder(64)
            val ent = StringBuilder(12)
            var inTag = false
            var inEnt = false
            // 标签内当前所处的引号；NO_QUOTE 表示不在引号里。
            // 属性值里可以合法出现 '>'，例如 <img alt="a > b" src="x.png"/>；
            // 不跟踪引号就会在 alt 的 '>' 处提前闭合标签，把后半截当正文渲染出来。
            var attrQuote = NO_QUOTE
            var c: Int
            while (reader.read().also { c = it } != -1) {
                // 上限保护：超大单章只取前若干万字，好过 OOM 或整章空白。
                if (emittedChars + sb.length >= MAX_CHAPTER_TEXT_CHARS) break
                val ch = c.toChar()
                if (inTag) {
                    // 注释里不做引号跟踪：注释内的孤立引号会让标签永远闭合不了。
                    val isComment = tag.length >= 3 && tag[0] == '!' && tag[1] == '-' && tag[2] == '-'
                    if (!isComment) {
                        if (attrQuote != NO_QUOTE) {
                            if (ch == attrQuote) attrQuote = NO_QUOTE
                            if (tag.length < MAX_TAG_CHARS) tag.append(ch)
                            continue
                        }
                        if (ch == '"' || ch == '\'') attrQuote = ch
                    }
                    if (ch == '>') {
                        val raw = tag.toString()
                        // HTML 注释里可以合法出现 '>'，必须等到 "-->" 才算闭合，
                        // 否则 <!--[if IE]><p>请升级</p><![endif]--> 的内容会漏成正文。
                        if (raw.startsWith("!--") && !raw.endsWith("--")) {
                            if (tag.length < MAX_TAG_CHARS) tag.append(ch)
                            continue
                        }
                        inTag = false
                        // '!' 开头是注释/DOCTYPE，'?' 开头是 XML 声明，都直接丢弃
                        if (!raw.startsWith("!") && !raw.startsWith("?")) handleTag(raw)
                        tag.setLength(0)
                    } else if (tag.length < MAX_TAG_CHARS) {
                        tag.append(ch)
                    }
                    continue
                }
                if (ch == '<') {
                    inTag = true; tag.setLength(0); attrQuote = NO_QUOTE
                    inEnt = false; ent.setLength(0)
                    continue
                }
                if (skipDepth > 0) continue
                if (inEnt) {
                    if (ch == ';') {
                        sb.append(decodeEntity(ent.toString())); ent.setLength(0); inEnt = false
                    } else if (ent.length < 16 && (ch.isLetterOrDigit() || ch == '#')) {
                        ent.append(ch)
                    } else {
                        sb.append('&'); sb.append(ent); sb.append(ch)
                        ent.setLength(0); inEnt = false
                    }
                    continue
                }
                if (ch == '&') { inEnt = true; ent.setLength(0); continue }
                sb.append(ch)
            }
            if (inEnt) { sb.append('&'); sb.append(ent) }
        }
        flush()
        return blocks
    }

    // ── OPF / container 解析 ──────────────────────────────────────────────

    /** EPUB 内置目录条目（来自 NCX 或 Navigation Document）。 */
    private data class TocItem(
        val title: String,
        val href: String,
    )

    private data class ManifestItem(
        val href: String,
        val mediaType: String,
        val properties: String,
    ) {
        fun isReadableDocument(): Boolean =
            mediaType.equals("application/xhtml+xml", ignoreCase = true) ||
                mediaType.equals("text/html", ignoreCase = true) ||
                href.substringBefore('#').substringBefore('?')
                    .substringAfterLast('.', "")
                    .lowercase() in setOf("xhtml", "html", "htm")
    }
    private data class OpfData(
        val title: String?,
        val creator: String?,
        val manifest: Map<String, ManifestItem>,
        val spine: List<String>,
        val toc: String?,
    )

    private fun parseContainer(stream: java.io.InputStream): String {
        val parser = newParser()
        parser.setInput(stream, "utf-8")
        var rootfile: String? = null
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.local() == "rootfile") {
                rootfile = parser.getAttributeValue(null, "full-path")
            }
            event = parser.next()
        }
        return rootfile ?: throw IllegalStateException("container.xml 缺少 rootfile")
    }

    private fun parseOpf(stream: java.io.InputStream): OpfData {
        val parser = newParser()
        parser.setInput(stream, "utf-8")
        var title: String? = null
        var creator: String? = null
        val manifest = mutableMapOf<String, ManifestItem>()
        val spine = mutableListOf<String>()
        var spineToc: String? = null
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.local()) {
                    "title" -> title = parser.nextText().trim().ifBlank { title }
                    "creator" -> creator = parser.nextText().trim().ifBlank { creator }
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        val mt = parser.getAttributeValue(null, "media-type") ?: ""
                        val properties = parser.getAttributeValue(null, "properties") ?: ""
                        if (id != null && href != null) {
                            manifest[id] = ManifestItem(href, mt, properties)
                        }
                    }
                    "itemref" -> {
                        val idref = parser.getAttributeValue(null, "idref")
                        if (idref != null) spine.add(idref)
                    }
                    "spine" -> {
                        spineToc = parser.getAttributeValue(null, "toc")
                    }
                }
            }
            event = parser.next()
        }
        return OpfData(title, creator, manifest, spine, spineToc)
    }

    // ── 目录（TOC）解析 ────────────────────────────────────────────────

    /**
     * 尝试从 EPUB 内置导航结构解析目录。
     * 优先 EPUB 3 Navigation Document，其次 EPUB 2 NCX。
     * 两者都没有时返回 null，调用方回退到 spine 顺序。
     */
    private fun resolveToc(
        zip: ZipFile,
        opfDir: String,
        manifest: Map<String, ManifestItem>,
        spine: List<String>,
        spineTocId: String?,
    ): List<TocItem>? {
        // 1) EPUB 3: manifest 中 properties="nav" 的文档
        val navItem = manifest.values.firstOrNull {
            it.properties.split(Regex("\\s+")).any { p -> p.equals("nav", ignoreCase = true) }
        }
        if (navItem != null) {
            val navPath = resolvePath(opfDir, navItem.href)
            val navEntry = findEntry(zip, navPath)
            if (navEntry != null) {
                val toc = zip.getInputStream(navEntry).use { parseNavDocument(it) }
                if (toc.isNotEmpty()) return toc
            }
        }
        // 2) EPUB 2: spine toc 属性指向的 NCX
        val ncxItem = spineTocId?.let { manifest[it] }
            ?: manifest.values.firstOrNull {
                it.mediaType.equals("application/x-dtbncx+xml", ignoreCase = true)
            }
        if (ncxItem != null) {
            val ncxPath = resolvePath(opfDir, ncxItem.href)
            val ncxEntry = findEntry(zip, ncxPath)
            if (ncxEntry != null) {
                val toc = zip.getInputStream(ncxEntry).use { parseNcx(it) }
                if (toc.isNotEmpty()) return toc
            }
        }
        return null
    }

    /** 解析 EPUB 2 NCX 文件中的 <navMap>。 */
    private fun parseNcx(stream: java.io.InputStream): List<TocItem> {
        val parser = newParser()
        parser.setInput(stream, "utf-8")
        val items = mutableListOf<TocItem>()
        var inNavMap = false
        var inNavPoint = false
        var inText = false
        var currentLabel = StringBuilder()
        var currentSrc: String? = null
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.local()) {
                        "navMap" -> inNavMap = true
                        "navPoint" -> if (inNavMap) {
                            inNavPoint = true; currentLabel = StringBuilder(); currentSrc = null
                        }
                        "text" -> if (inNavPoint) { inText = true; currentLabel = StringBuilder() }
                        "content" -> if (inNavPoint) {
                            currentSrc = parser.getAttributeValue(null, "src")
                        }
                    }
                }
                XmlPullParser.TEXT -> if (inText) currentLabel.append(parser.text)
                XmlPullParser.END_TAG -> {
                    when (parser.local()) {
                        "navMap" -> inNavMap = false
                        "navPoint" -> if (inNavPoint) {
                            inNavPoint = false
                            val label = currentLabel.toString().trim()
                            val src = currentSrc
                            if (label.isNotBlank() && src != null) {
                                items.add(TocItem(label, src.substringBefore('#')))
                            }
                        }
                        "text" -> inText = false
                    }
                }
            }
            event = parser.next()
        }
        return items
    }

    /** 解析 EPUB 3 Navigation Document 中的 <nav epub:type="toc">。 */
    private fun parseNavDocument(stream: java.io.InputStream): List<TocItem> {
        val html = BufferedReader(InputStreamReader(stream, Charsets.UTF_8), 32 * 1024).use { it.readText() }
        // 匹配 <nav epub:type="toc"> 或 <nav role="doc-toc">（EPUB 3.2）
        val navRegex = Regex(
            """<nav[^>]*(?:epub:type\s*=\s*["']toc["']|role\s*=\s*["']doc-toc["'])[^>]*>(.*?)</nav>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val navMatch = navRegex.find(html) ?: return emptyList()
        val navContent = navMatch.groupValues[1]
        val items = mutableListOf<TocItem>()
        val linkRegex = Regex(
            """<a\s[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        for (match in linkRegex.findAll(navContent)) {
            val href = match.groupValues[1].substringBefore('#')
            val rawText = match.groupValues[2]
            val text = TAG_REGEX.replace(rawText, "").trim()
            if (href.isNotBlank() && text.isNotBlank()) {
                items.add(TocItem(text, href))
            }
        }
        return items
    }

    private val TAG_REGEX = Regex("<[^>]+>")

    /** 根据 TOC 条目构建章节列表，使用 TOC 中的准确标题。 */
    private fun buildChaptersFromToc(
        zip: ZipFile,
        opfDir: String,
        cachedEpubPath: String,
        chapterRefs: List<ManifestItem>,
        tocEntries: List<TocItem>,
    ): List<EpubChapter> {
        // 用 spine 条目 href（去掉 fragment）做 key，匹配 TOC href
        val refMap = chapterRefs.mapIndexedNotNull { index, ref ->
            val key = ref.href.substringBefore('#')
            val resolved = resolvePath(opfDir, key)
            val entry = findEntry(zip, resolved) ?: return@mapIndexedNotNull null
            key to Triple(index, resolved, entry)
        }.toMap()

        val chapters = mutableListOf<EpubChapter>()
        for (toc in tocEntries) {
            val triple = refMap[toc.href.substringBefore('#')] ?: continue
            val (spineIndex, entryPath, entry) = triple
            val chapterDir = entryPath.substringBeforeLast('/', "")
            val buf = ByteArray(8192)
            val n = zip.getInputStream(entry).use { it.read(buf) }
            val head = if (n > 0) String(buf, 0, n, Charsets.UTF_8) else ""
            val title = toc.title.ifBlank { guessChapterTitle(head, spineIndex) }
            chapters.add(
                EpubChapter(
                    title = title,
                    entryPath = entryPath,
                    chapterDir = chapterDir,
                    cachedEpubPath = cachedEpubPath,
                    estimatedTextLength = entry.size
                        ?.coerceIn(0L, Int.MAX_VALUE.toLong())
                        ?.toInt() ?: 0,
                )
            )
        }
        return chapters
    }

    /** 无内置 TOC 时，从 spine 顺序构建章节列表，过滤封面/版权等非正文条目。 */
    private fun buildChaptersFromSpine(
        zip: ZipFile,
        opfDir: String,
        cachedEpubPath: String,
        chapterRefs: List<ManifestItem>,
        manifest: Map<String, ManifestItem>,
        spine: List<String>,
    ): List<EpubChapter> {
        // 按 spine idref 查找对应的 manifest 条目，用于过滤非正文内容
        val spineProps = spine.map { idref -> manifest[idref] }
        return chapterRefs.mapIndexedNotNull { index, ref ->
            if (isNonContentManifestItem(spineProps.getOrNull(index))) return@mapIndexedNotNull null

            val entryPath = resolvePath(opfDir, ref.href)
            val entry = findEntry(zip, entryPath) ?: return@mapIndexedNotNull null
            val chapterDir = entryPath.substringBeforeLast('/', "")
            val buf = ByteArray(8192)
            val n = zip.getInputStream(entry).use { it.read(buf) }
            val head = if (n > 0) String(buf, 0, n, Charsets.UTF_8) else ""
            EpubChapter(
                title = guessChapterTitle(head, index),
                entryPath = entryPath,
                chapterDir = chapterDir,
                cachedEpubPath = cachedEpubPath,
                estimatedTextLength = entry.size
                    ?.coerceIn(0L, Int.MAX_VALUE.toLong())
                    ?.toInt() ?: 0,
            )
        }
    }

    /** 判断 manifest 条目是否为非正文内容（封面、版权页等）。 */
    private fun isNonContentManifestItem(item: ManifestItem?): Boolean {
        if (item == null) return false
        val props = item.properties.lowercase()
        if (props.contains("cover")) return true
        val href = item.href.lowercase()
        return NON_CONTENT_PATH_PATTERNS.any { pattern -> pattern.containsMatchIn(href) }
    }

    private val NON_CONTENT_PATH_PATTERNS = listOf(
        Regex("cover|titlepage|title-page|frontmatter|copyright|colophon|dedication|frontispiece", RegexOption.IGNORE_CASE),
    )

    private fun extractImage(
        src: String,
        chapterDir: String,
        zip: ZipFile,
        imgCacheDir: File,
        key: String,
    ): String? {
        val entryPath = resolvePath(chapterDir, src)
        val entry = findEntry(zip, entryPath) ?: return null
        // 扩展名取自书内 img src，是不可信输入，必须限制为纯字母数字。
        // 否则形如 "cover.pn/../../../../databases/app.db" 的 src 会让 substringAfterLast('.')
        // 带上 "/.." 片段，使写入路径逃出缓存目录，改写应用私有文件。
        val rawExt = src.substringBefore('#').substringBefore('?').substringAfterLast('.', "")
        val ext = rawExt
            .takeIf { it.isNotEmpty() && it.length <= 5 && it.all { c -> c.isLetterOrDigit() } }
            ?.lowercase()
            ?: "png"
        val out = File(imgCacheDir, "$key.$ext")
        // 兜底断言：即使上面的过滤被绕过，也不允许写到缓存目录之外。
        if (!out.canonicalPath.startsWith(imgCacheDir.canonicalPath + File.separator)) return null
        if (!out.exists()) {
            zip.getInputStream(entry).use { inp -> out.outputStream().use { outp -> inp.copyTo(outp) } }
        }
        return out.absolutePath
    }

    private fun guessChapterTitle(html: String, index: Int): String {
        val m = Regex("<h[1-6][^>]*>(.*?)</h[1-6]>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(html)
        val fromTag = m?.groupValues?.getOrNull(1)?.let { decodeEntities(it).trim() }?.take(40)
        return fromTag?.ifBlank { null } ?: "第 ${index + 1} 章"
    }

    // ── 工具 ─────────────────────────────────────────────────────────────

    /**
     * 命名与数值实体的统一入口。
     *
     * 这里**必须**和流式路径的 [decodeEntity] 共用同一张表：此前渲染路径只认 8 个命名实体
     * 加十进制数值，于是 calibre 导出的中文书里 `&ldquo;` `&mdash;` `&hellip;` `&#x201C;`
     * 会原样显示在阅读界面上（而十进制的 `&#8220;` 却正常），两条路径行为漂移。
     */
    private val ENTITY_REGEX = Regex("&(#[xX][0-9a-fA-F]+|#\\d+|[a-zA-Z][a-zA-Z0-9]*);")
    private val INLINE_SPACE_REGEX = Regex("[ \\t\\r]+")

    private fun decodeEntities(s: String): String =
        ENTITY_REGEX.replace(s) { m -> decodeEntity(m.groupValues[1]) }
            .replace(INLINE_SPACE_REGEX, " ")

    /** 流式抽取时用的单实体解码（对照 [decodeEntities] 的逐 token 版）。 */
    private fun decodeEntity(token: String): String = when (token) {
        "amp" -> "&"
        "lt" -> "<"
        "gt" -> ">"
        "quot" -> "\""
        "apos" -> "'"
        "nbsp" -> " "
        "hellip" -> "…"
        "mdash" -> "—"
        "ndash" -> "–"
        "lsquo" -> "‘"
        "rsquo" -> "’"
        "ldquo" -> "“"
        "rdquo" -> "”"
        else -> if (token.startsWith('#')) {
            val code = if (token.length > 1 && (token[1] == 'x' || token[1] == 'X')) {
                token.substring(2).toIntOrNull(16)
            } else {
                token.substring(1).toIntOrNull()
            }
            // 必须走 Character.toChars：Int.toChar() 只取低 16 位，
            // 会把 &#128512;(U+1F600 emoji) 截断成 U+F600 私用区方块。
            // 同时排除代理项区间，避免拼出非法 UTF-16。
            if (code != null && code in 0..0x10FFFF && code !in 0xD800..0xDFFF) {
                String(Character.toChars(code))
            } else {
                "&$token;"
            }
        } else {
            "&$token;"
        }
    }

    private fun resolvePath(baseDir: String, href: String): String {
        val raw = href.substringBefore('#').substringBefore('?')
        // OPF/XHTML 里的 href 是 URI，含空格或非 ASCII 的文件名会被 percent-encode，
        // 而 ZipEntry 名是原始字符串——不解码就会 getEntry() 返回 null，整章空白。
        // 先把 '+' 保护起来：URLDecoder 会把它当空格，而在路径里 '+' 就是字面量。
        val clean = if (raw.contains('%')) {
            runCatching { java.net.URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }.getOrDefault(raw)
        } else {
            raw
        }
        if (clean.startsWith("/")) return clean.removePrefix("/")
        val parts = (baseDir.split('/') + clean.split('/')).filter { it.isNotEmpty() && it != "." }
        val stack = ArrayDeque<String>()
        for (p in parts) {
            if (p == "..") stack.removeLastOrNull() else stack.addLast(p)
        }
        return stack.joinToString("/")
    }

    /** 同时兼容调用方尚未解码的 URI 路径与已经归一化的 ZIP 条目路径。 */
    private fun findEntry(zip: ZipFile, entryPath: String): ZipEntry? =
        zip.getEntry(entryPath) ?: zip.getEntry(resolvePath("", entryPath))

    private fun newParser(): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        return factory.newPullParser()
    }

    private fun XmlPullParser.local(): String = (name ?: "").substringAfterLast(':')
}
