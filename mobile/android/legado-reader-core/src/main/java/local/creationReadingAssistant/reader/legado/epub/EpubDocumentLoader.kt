/*
 * SPDX-License-Identifier: GPL-3.0-only
 * EPUB package parsing is provided by the vendored me.ag2s.epublib sources
 * from Luoyacheng/legado-E at 21855a7bf901becfd1caba5cf30a3c84fd1533e1.
 */
package local.creationReadingAssistant.reader.legado.epub

import android.content.Context
import android.net.Uri
import me.ag2s.epublib.domain.EpubBook
import me.ag2s.epublib.domain.Resource
import me.ag2s.epublib.domain.TOCReference
import me.ag2s.epublib.epub.EpubReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipFile

object EpubDocumentLoader {
    private const val MAX_COMPRESSED_BYTES = 160L * 1024L * 1024L

    @JvmStatic
    fun load(context: Context, rawUri: String): EpubReaderDocument {
        val uri = Uri.parse(rawUri)
        validateSize(context, uri)
        val source = localFileFor(context, uri)
        return loadFromFile(source.file, source.owned)
    }

    /** Test and compatibility entry point; the temporary ZIP is owned by the returned document. */
    @JvmStatic
    fun load(input: InputStream): EpubReaderDocument {
        val file = File.createTempFile("native-reader-", ".epub")
        return try {
            FileOutputStream(file).use { output -> copyWithLimit(input, output) }
            loadFromFile(file, ownedTemporaryFile = true)
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    private fun loadFromFile(file: File, ownedTemporaryFile: Boolean): EpubReaderDocument {
        val zipFile = ZipFile(file)
        return try {
            val book = EpubReader().readEpubLazy(zipFile, "utf-8")
            toReaderDocument(book, zipFile, file.takeIf { ownedTemporaryFile })
        } catch (error: Throwable) {
            runCatching { zipFile.close() }
            if (ownedTemporaryFile) file.delete()
            throw error
        }
    }

    private fun toReaderDocument(
        book: EpubBook,
        zipFile: ZipFile,
        ownedTemporaryFile: File?,
    ): EpubReaderDocument {
        val toc = linkedMapOf<String, Pair<String, Int>>()
        flattenToc(book.tableOfContents.tocReferences, 1, toc)
        val chapters = arrayListOf<EpubChapter>()
        val chapterResources = arrayListOf<Resource>()

        book.spine.spineReferences.forEach { reference ->
            val resource = reference.resource ?: return@forEach
            if (!isReadableSpineResource(resource)) return@forEach
            val href = normalizeHref(resource.href)
            val tocEntry = toc[href] ?: toc.entries.firstOrNull { (key, _) ->
                key.substringAfterLast('/') == href.substringAfterLast('/')
            }?.value
            val chapterIndex = chapters.size
            chapters += EpubChapter(
                index = chapterIndex,
                title = tocEntry?.first?.takeIf { it.isNotBlank() }
                    ?: resource.title?.takeIf { it.isNotBlank() }
                    ?: "第 ${chapterIndex + 1} 节",
                href = href,
                level = tocEntry?.second ?: 1,
            )
            chapterResources += resource
        }

        if (chapters.isEmpty()) {
            throw IllegalArgumentException("EPUB 没有可阅读的 spine 正文章节")
        }

        val resourcesByHref = linkedMapOf<String, Resource>()
        book.resources.all.forEach { resource ->
            val href = normalizeHref(resource.href)
            if (href.isNotBlank()) {
                resourcesByHref[href] = resource
                resourcesByHref.putIfAbsent(href.substringAfterLast('/'), resource)
            }
        }

        return EpubReaderDocument(
            title = book.title?.takeIf { it.isNotBlank() && !it.contains('\uFFFD') } ?: "未命名 EPUB",
            author = book.metadata.authors.firstOrNull()?.toString()?.takeIf { it.isNotBlank() },
            chapters = chapters,
            chapterResources = chapterResources,
            resourcesByHref = resourcesByHref,
            zipFile = zipFile,
            ownedTemporaryFile = ownedTemporaryFile,
        )
    }

    private fun isReadableSpineResource(resource: Resource): Boolean {
        val href = resource.href.orEmpty().lowercase()
        return href.endsWith(".xhtml") || href.endsWith(".html") || href.endsWith(".htm")
    }

    private fun flattenToc(
        references: List<TOCReference>,
        level: Int,
        output: MutableMap<String, Pair<String, Int>>,
    ) {
        references.forEach { reference ->
            val href = normalizeHref(reference.resource?.href ?: reference.completeHref)
            if (href.isNotBlank() && !output.containsKey(href)) {
                output[href] = reference.title.orEmpty() to level
            }
            flattenToc(reference.children, level + 1, output)
        }
    }

    private data class LocalSource(val file: File, val owned: Boolean)

    private fun localFileFor(context: Context, uri: Uri): LocalSource = when (uri.scheme?.lowercase()) {
        "file", null, "" -> LocalSource(fileFor(uri), false)
        "content" -> {
            val directory = File(context.cacheDir, "native-reader-epub").apply { mkdirs() }
            val temporary = File.createTempFile("publication-", ".epub", directory)
            try {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "无法打开 EPUB 内容地址" }
                    FileOutputStream(temporary).use { output -> copyWithLimit(input, output) }
                }
                LocalSource(temporary, true)
            } catch (error: Throwable) {
                temporary.delete()
                throw error
            }
        }
        else -> throw IllegalArgumentException("不支持的 EPUB 文件地址：${uri.scheme}")
    }

    private fun copyWithLimit(input: InputStream, output: FileOutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_COMPRESSED_BYTES) {
                throw IllegalArgumentException("EPUB 文件超过 160 MB，暂不适合在手机端直接解析")
            }
            output.write(buffer, 0, count)
        }
    }

    private fun normalizeHref(value: String?): String {
        val clean = value.orEmpty()
            .substringBefore('#')
            .substringBefore('?')
            .replace('\\', '/')
            .removePrefix("./")
        return runCatching { URLDecoder.decode(clean, "UTF-8") }.getOrDefault(clean)
    }

    private fun validateSize(context: Context, uri: Uri) {
        val size = when (uri.scheme?.lowercase()) {
            "content" -> context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            "file", null, "" -> fileFor(uri).length()
            else -> -1L
        }
        if (size > MAX_COMPRESSED_BYTES) {
            throw IllegalArgumentException("EPUB 文件超过 160 MB，暂不适合在手机端直接解析")
        }
    }

    private fun fileFor(uri: Uri): File {
        val path = if (uri.scheme.equals("file", true)) uri.path else uri.toString()
        if (path.isNullOrBlank()) throw IllegalArgumentException("EPUB 文件路径为空")
        val file = File(path)
        if (!file.isFile) throw IllegalArgumentException("EPUB 文件不存在或无法访问")
        return file
    }
}
