package com.creationreadingassistant.acceptance

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * CRA-ABC-DEVICE-CONTRACT-PREP-v1 / 任务34-v1 —— A 用的**任务专属**最小「测试 EPUB」夹具。
 *
 * 与既有 `D2EpubTestFixture` 的差别（本任务需要，故独立实现、不改公共原件）：
 *  · **两个章节**（ch1 / ch2），spine 有序，可验证跨章导航；
 *  · 每章有**可验证的正文 marker**（唯一字符串），供 UI 断言真实章节目标；
 *  · 提供 EPUB 3 nav 目录（toc.xhtml），可验证目录目标。
 *
 * 纯 Java/Kotlin 标准库，零第三方依赖；固定时间戳保证字节确定。
 */
object Task34EpubFixture {

    const val CHAPTER1_MARKER = "TASK34_EPUB_CH1_MARKER_甲章正文"
    const val CHAPTER2_MARKER = "TASK34_EPUB_CH2_MARKER_乙章正文"

    fun containerXml(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()

    fun opfXml(title: String, identifier: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>$title</dc:title>
            <dc:identifier id="uid">$identifier</dc:identifier>
            <dc:language>zh-CN</dc:language>
          </metadata>
          <manifest>
            <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
            <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
          </manifest>
          <spine>
            <itemref idref="c1"/>
            <itemref idref="c2"/>
          </spine>
        </package>
    """.trimIndent()

    fun navXml(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
          <head><title>目录</title></head>
          <body>
            <nav epub:type="toc" id="toc">
              <ol>
                <li><a href="text/ch1.xhtml">第一章 甲</a></li>
                <li><a href="text/ch2.xhtml">第二章 乙</a></li>
              </ol>
            </nav>
          </body>
        </html>
    """.trimIndent()

    fun chapterXhtml(title: String, marker: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml">
          <head><title>$title</title></head>
          <body>
            <h2>$title</h2>
            <p>$marker</p>
          </body>
        </html>
    """.trimIndent()

    /** 生成一个两章、带 nav 目录的最小合法 EPUB。 */
    fun createTwoChapterEpub(
        dest: File,
        title: String = "测试 EPUB 甲与乙",
        identifier: String = "task34-test-epub",
    ): File {
        dest.parentFile?.mkdirs()
        ZipOutputStream(dest.outputStream().buffered()).use { zos ->
            fun addEntry(path: String, content: String) {
                val bytes = content.toByteArray(Charsets.UTF_8)
                val entry = ZipEntry(path).apply {
                    size = bytes.size.toLong()
                    time = 1704067200000L // 固定时间戳，字节确定
                }
                zos.putNextEntry(entry); zos.write(bytes); zos.closeEntry()
            }
            addEntry("mimetype", "application/epub+zip")
            addEntry("META-INF/container.xml", containerXml())
            addEntry("OPS/package.opf", opfXml(title, identifier))
            addEntry("OPS/nav.xhtml", navXml())
            addEntry("OPS/text/ch1.xhtml", chapterXhtml("第一章 甲", CHAPTER1_MARKER))
            addEntry("OPS/text/ch2.xhtml", chapterXhtml("第二章 乙", CHAPTER2_MARKER))
        }
        return dest
    }

    /** 离线结构核验：两章、nav、mimetype、container 均在位。 */
    fun verify(file: File): Map<String, Any> {
        require(file.isFile && file.length() > 0) { "EPUB 不存在或为空：${file.absolutePath}" }
        val entries = mutableSetOf<String>()
        ZipFile(file).use { zip ->
            val e = zip.entries()
            while (e.hasMoreElements()) entries.add(e.nextElement().name)
        }
        val required = setOf(
            "mimetype", "META-INF/container.xml", "OPS/package.opf",
            "OPS/nav.xhtml", "OPS/text/ch1.xhtml", "OPS/text/ch2.xhtml",
        )
        return mapOf(
            "present" to required.all { it in entries },
            "missing" to required.filter { it !in entries },
            "sha256" to sha256(file.readBytes()),
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
