package com.creationreadingassistant.acceptance

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * CRA-D2-DEVICE-ARTIFACTS-REPAIR-v4
 *
 * 最小、有效、可离线验证的「测试 EPUB」夹具生成器与离线校验器。
 *
 * 覆盖：
 * - 真实 ZIP 封装结构；
 * - mimetype 声明；
 * - META-INF/container.xml 指向 OPF 清单；
 * - OPS/package.opf 包含合法 metadata/manifest/spine；
 * - OPS/text/ch1.xhtml 包含合法 XHTML 章节正文；
 * - 纯 Java/Kotlin 标准库实现，零第三方打包依赖，可在离线 JVM 与设备测试双端验证。
 */
object D2EpubTestFixture {

    fun containerXml(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()

    fun opfXml(title: String, identifier: String = "d2-test-epub"): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>$title</dc:title>
            <dc:identifier id="uid">$identifier</dc:identifier>
            <dc:language>zh-CN</dc:language>
          </metadata>
          <manifest>
            <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
          </manifest>
          <spine>
            <itemref idref="c1"/>
          </spine>
        </package>
    """.trimIndent()

    fun chapterXhtml(title: String, body: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml">
          <head>
            <title>$title</title>
          </head>
          <body>
            <h2>$title</h2>
            <p>$body</p>
          </body>
        </html>
    """.trimIndent()

    /**
     * 生成一个最小合法的 EPUB 文件。
     */
    fun createMinimalEpub(
        dest: File,
        title: String = "测试EPUB",
        body: String = "测试章节第一段正文ALPHA。\n第二段正文BETA。\n",
        identifier: String = "d2-test-epub",
    ): File {
        dest.parentFile?.mkdirs()
        ZipOutputStream(dest.outputStream().buffered()).use { zos ->
            fun addEntry(path: String, content: String) {
                val bytes = content.toByteArray(Charsets.UTF_8)
                val entry = ZipEntry(path).apply {
                    size = bytes.size.toLong()
                    time = 1704067200000L // 固定时间戳：2024-01-01 00:00:00 UTC，确保多次生成字节与哈希 100% 确定无漂移
                }
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }

            // 1. mimetype (必须在首位，且内容为 application/epub+zip)
            addEntry("mimetype", "application/epub+zip")
            // 2. META-INF/container.xml
            addEntry("META-INF/container.xml", containerXml())
            // 3. OPS/package.opf
            addEntry("OPS/package.opf", opfXml(title, identifier))
            // 4. OPS/text/ch1.xhtml
            addEntry("OPS/text/ch1.xhtml", chapterXhtml(title, body))
        }
        return dest
    }

    /**
     * 离线结构完整性检查：验证生成的 EPUB 文件是否满足 [EpubParser] 所需的全部条目与解析前提。
     */
    fun verifyEpub(file: File): D2EpubVerificationResult {
        require(file.exists() && file.isFile) { "EPUB 文件不存在: ${file.absolutePath}" }
        val size = file.length()
        require(size > 0L) { "EPUB 文件大小必须大于 0" }

        val md5 = computeFileMd5(file)
        val sha256 = computeFileSha256(file)

        ZipFile(file).use { zip ->
            val mimetypeEntry = zip.getEntry("mimetype")
                ?: return D2EpubVerificationResult(false, "缺少 mimetype 条目", size, md5, sha256)
            val mimetypeContent = zip.getInputStream(mimetypeEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (!mimetypeContent.startsWith("application/epub+zip")) {
                return D2EpubVerificationResult(false, "mimetype 内容非法: $mimetypeContent", size, md5, sha256)
            }

            val containerEntry = zip.getEntry("META-INF/container.xml")
                ?: return D2EpubVerificationResult(false, "缺少 META-INF/container.xml", size, md5, sha256)
            val containerContent = zip.getInputStream(containerEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (!containerContent.contains("OPS/package.opf")) {
                return D2EpubVerificationResult(false, "container.xml 未指向 OPS/package.opf", size, md5, sha256)
            }

            val opfEntry = zip.getEntry("OPS/package.opf")
                ?: return D2EpubVerificationResult(false, "缺少 OPS/package.opf", size, md5, sha256)
            val opfContent = zip.getInputStream(opfEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (!opfContent.contains("<manifest>") || !opfContent.contains("<spine>")) {
                return D2EpubVerificationResult(false, "OPF 缺少 manifest 或 spine", size, md5, sha256)
            }

            val chapterEntry = zip.getEntry("OPS/text/ch1.xhtml")
                ?: return D2EpubVerificationResult(false, "缺少 OPS/text/ch1.xhtml", size, md5, sha256)
            val chapterContent = zip.getInputStream(chapterEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (chapterContent.isBlank()) {
                return D2EpubVerificationResult(false, "章节内容为空", size, md5, sha256)
            }

            return D2EpubVerificationResult(
                isValid = true,
                message = "EPUB 结构完整有效",
                sizeBytes = size,
                md5 = md5,
                sha256 = sha256,
                chapterPreview = chapterContent.take(100),
            )
        }
    }

    fun computeFileMd5(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun computeFileSha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

data class D2EpubVerificationResult(
    val isValid: Boolean,
    val message: String,
    val sizeBytes: Long,
    val md5: String,
    val sha256: String,
    val chapterPreview: String? = null,
)
