/* SPDX-License-Identifier: GPL-3.0-only */
package local.creationReadingAssistant.reader.legado.epub

import me.ag2s.epublib.domain.LazyResource
import me.ag2s.epublib.domain.Resource
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.util.LinkedHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile

data class EpubChapter(
    val index: Int,
    val title: String,
    val href: String,
    val level: Int,
)

data class EpubChapterContent(
    val text: String,
    val html: String,
    val containsImages: Boolean,
)

/**
 * Lazy EPUB publication used by the native reader.
 *
 * The initial open keeps only package metadata, spine descriptors and lazy ZIP
 * resource handles. XHTML is parsed one chapter at a time and retained in a
 * small bounded LRU. Images are streamed directly from the EPUB and are never
 * copied into a book-sized byte-array map.
 */
class EpubReaderDocument internal constructor(
    val title: String,
    val author: String?,
    val chapters: List<EpubChapter>,
    private val chapterResources: List<Resource>,
    private val resourcesByHref: Map<String, Resource>,
    private val zipFile: ZipFile,
    private val ownedTemporaryFile: File?,
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val prefetchExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "native-epub-prefetch").apply { isDaemon = true }
    }
    private val chapterCache = object : LinkedHashMap<Int, EpubChapterContent>(
        MAX_CACHED_CHAPTERS + 1,
        0.75f,
        true,
    ) {}
    private val chapterLocks = Array(chapters.size) { Any() }
    private var cachedCharacterCount = 0

    init {
        require(chapters.isNotEmpty()) { "EPUB 没有可阅读的 spine 正文章节" }
        require(chapterResources.size == chapters.size) { "EPUB 章节描述与资源数量不一致" }
    }

    fun chapterContent(index: Int): EpubChapterContent {
        ensureOpen()
        val safeIndex = index.coerceIn(0, chapters.lastIndex)
        synchronized(chapterCache) {
            chapterCache[safeIndex]?.let { return it }
        }

        synchronized(chapterLocks[safeIndex]) {
            synchronized(chapterCache) {
                chapterCache[safeIndex]?.let { return it }
            }
            ensureOpen()
            val parsed = parseChapter(chapterResources[safeIndex])
            synchronized(chapterCache) {
                chapterCache[safeIndex]?.let { return it }
                chapterCache[safeIndex] = parsed
                cachedCharacterCount += parsed.cacheWeight
                trimChapterCache(keepIndex = safeIndex)
            }
            return parsed
        }
    }

    fun chapterText(index: Int): String = chapterContent(index).text

    /** Warm the neighbouring spine items without blocking page turns. */
    fun prefetchAround(index: Int) {
        if (closed.get()) return
        listOf(index + 1, index - 1)
            .filter { it in chapters.indices }
            .forEach { target ->
                runCatching {
                    prefetchExecutor.execute {
                        if (!closed.get() && !isChapterCached(target)) {
                            runCatching { chapterContent(target) }
                        }
                    }
                }
            }
    }

    fun openImageStream(chapterHref: String, source: String): InputStream? {
        ensureOpen()
        val normalized = resolveRelativeHref(chapterHref, source)
        val resource = resourcesByHref[normalized]
            ?: resourcesByHref[normalized.substringAfterLast('/')]
            ?: return null
        if (resource.size > MAX_IMAGE_RESOURCE_BYTES) return null
        return resource.inputStream
    }

    fun chapterIndexForHref(href: String?): Int? {
        val target = normalizeHref(href.orEmpty())
        if (target.isBlank()) return null
        return chapters.firstOrNull { chapter ->
            val candidate = normalizeHref(chapter.href)
            candidate == target || candidate.substringAfterLast('/') == target.substringAfterLast('/')
        }?.index
    }

    internal fun isChapterCached(index: Int): Boolean = synchronized(chapterCache) {
        chapterCache.containsKey(index)
    }

    internal fun cachedChapterCount(): Int = synchronized(chapterCache) { chapterCache.size }

    internal fun isResourceInitialized(href: String): Boolean {
        val resource = resourcesByHref[normalizeHref(href)] ?: return false
        return resource !is LazyResource || resource.isInitialized
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        prefetchExecutor.shutdownNow()
        synchronized(chapterCache) {
            chapterCache.clear()
            cachedCharacterCount = 0
        }
        resourcesByHref.values.toSet().forEach { resource -> runCatching { resource.close() } }
        runCatching { zipFile.close() }
        ownedTemporaryFile?.let { file -> runCatching { file.delete() } }
    }

    private fun parseChapter(resource: Resource): EpubChapterContent {
        if (resource.size > MAX_CHAPTER_RESOURCE_BYTES) {
            throw IllegalArgumentException("EPUB 单个章节超过 16 MB，无法安全分页")
        }
        val document = resource.inputStream.use { input ->
            Jsoup.parse(input, resource.inputEncoding, resource.href.orEmpty())
        }
        document.select("script,style,noscript,nav").remove()
        var containsImages = false
        document.select("svg").forEach { svg ->
            containsImages = true
            val label = svg.attr("aria-label").trim().takeIf { it.isNotBlank() }
                ?: svg.select("title").text().trim().takeIf { it.isNotBlank() }
                ?: "SVG 插图"
            svg.after(Element("p").text("[图片：$label]"))
            svg.remove()
        }
        document.select("img").forEach { image ->
            containsImages = true
            val label = image.attr("alt").trim().takeIf { it.isNotBlank() }
                ?: image.attr("title").trim().takeIf { it.isNotBlank() }
                ?: "插图"
            if (image.attr("alt").isBlank()) {
                image.attr("alt", "[图片：$label]")
            }
        }
        val sanitizedHtml = document.body().html()
        val blocks = document.body().select("h1,h2,h3,h4,h5,h6,p,li,blockquote,pre,figcaption")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
        val text = if (blocks.isNotEmpty()) blocks.joinToString("\n\n") else document.body().text().trim()
        return EpubChapterContent(
            text = text,
            html = sanitizedHtml,
            containsImages = containsImages,
        )
    }

    private fun trimChapterCache(keepIndex: Int) {
        val iterator = chapterCache.entries.iterator()
        while (
            iterator.hasNext() &&
            (chapterCache.size > MAX_CACHED_CHAPTERS || cachedCharacterCount > MAX_CACHED_CHARACTERS)
        ) {
            val entry = iterator.next()
            if (entry.key == keepIndex) {
                if (chapterCache.size == 1) break
                continue
            }
            cachedCharacterCount -= entry.value.cacheWeight
            iterator.remove()
        }
    }

    private val EpubChapterContent.cacheWeight: Int
        get() = text.length + html.length

    private fun resolveRelativeHref(baseHref: String, source: String): String {
        val cleanSource = normalizeHref(source)
        if (cleanSource.isBlank()) return cleanSource
        val combined = if (cleanSource.startsWith('/')) {
            cleanSource.removePrefix("/")
        } else {
            val parent = normalizeHref(baseHref).substringBeforeLast('/', "")
            if (parent.isBlank()) cleanSource else "$parent/$cleanSource"
        }
        val segments = arrayListOf<String>()
        combined.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments += part
            }
        }
        return segments.joinToString("/")
    }

    private fun ensureOpen() {
        check(!closed.get()) { "EPUB 文档已经关闭" }
    }

    private fun normalizeHref(value: String): String {
        val clean = value.substringBefore('#').substringBefore('?').replace('\\', '/').removePrefix("./")
        return runCatching { URLDecoder.decode(clean, "UTF-8") }.getOrDefault(clean)
    }

    companion object {
        private const val MAX_CACHED_CHAPTERS = 4
        private const val MAX_CACHED_CHARACTERS = 2_000_000
        private const val MAX_CHAPTER_RESOURCE_BYTES = 16L * 1024L * 1024L
        private const val MAX_IMAGE_RESOURCE_BYTES = 32L * 1024L * 1024L
    }
}
