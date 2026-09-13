package com.creationreadingassistant.feature.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * StarDict 离线词典核心（R3-X1）的 JVM 测试。
 *
 * 词库在测试里就地合成（写真实的 .ifo/.idx/.dict/.dict.dz），因此校验的是**真实解析路径**，
 * 不是替身：索引字节序二分、sametypesequence 解码、.dict.dz 解压、zip 导入与 zip-slip 防护。
 */
class StarDictTest {

    @get:Rule
    val temp = TemporaryFolder()

    // ── 合成词库 ─────────────────────────────────────────────────────

    private fun intBe(v: Int) = byteArrayOf(
        (v shr 24).toByte(),
        (v shr 16).toByte(),
        (v shr 8).toByte(),
        v.toByte(),
    )

    /** 写一套合法词库；[sameTypeSequence] 为空表示数据块内联类型字节。 */
    private fun writeDict(
        dir: File,
        base: String,
        entries: List<Pair<String, String>>,
        sameTypeSequence: String = "m",
        bookName: String = "TestDict",
        inlineTypeByte: Char = 'm',
    ): File {
        dir.mkdirs()
        val data = ByteArrayOutputStream()
        val idx = ByteArrayOutputStream()
        entries.forEach { (word, content) ->
            val payload = if (sameTypeSequence.isEmpty()) {
                byteArrayOf(inlineTypeByte.code.toByte()) + content.toByteArray(Charsets.UTF_8) + byteArrayOf(0)
            } else {
                content.toByteArray(Charsets.UTF_8)
            }
            val offset = data.size()
            data.write(payload)
            idx.write(word.toByteArray(Charsets.UTF_8))
            idx.write(0)
            idx.write(intBe(offset))
            idx.write(intBe(payload.size))
        }
        val idxBytes = idx.toByteArray()
        File(dir, "$base.idx").writeBytes(idxBytes)
        File(dir, "$base.dict").writeBytes(data.toByteArray())
        File(dir, "$base.ifo").writeText(
            buildString {
                appendLine("StarDict's dict ifo file")
                appendLine("version=2.4.2")
                appendLine("bookname=$bookName")
                appendLine("wordcount=${entries.size}")
                appendLine("idxfilesize=${idxBytes.size}")
                if (sameTypeSequence.isNotEmpty()) appendLine("sametypesequence=$sameTypeSequence")
                appendLine("description=测试词库")
            },
        )
        return dir
    }

    private val sampleEntries = listOf(
        "apple" to "苹果",
        "banana" to "香蕉",
        "cherry" to "樱桃",
    )

    // ── .ifo ─────────────────────────────────────────────────────────

    @Test
    fun `parseInfo reads the required fields`() {
        val info = StarDictFormat.parseInfo(
            """
            StarDict's dict ifo file
            version=2.4.2
            bookname=汉英词典
            wordcount=42
            idxfilesize=1024
            sametypesequence=m
            description=说明
            """.trimIndent(),
        )

        assertNotNull(info)
        assertEquals("汉英词典", info!!.bookName)
        assertEquals(42, info.wordCount)
        assertEquals(1024L, info.indexFileSize)
        assertEquals("m", info.sameTypeSequence)
        assertEquals(32, info.indexOffsetBits)
    }

    @Test
    fun `parseInfo rejects metadata without the mandatory keys`() {
        assertNull(StarDictFormat.parseInfo("bookname=只有名字"))
        assertNull(StarDictFormat.parseInfo("wordcount=3\nidxfilesize=9"))
        assertNull(StarDictFormat.parseInfo("bookname=x\nwordcount=0\nidxfilesize=9"))
        assertNull(StarDictFormat.parseInfo(""))
    }

    @Test
    fun `parseInfo honours 64 bit index offsets`() {
        val info = StarDictFormat.parseInfo(
            "bookname=x\nwordcount=1\nidxfilesize=1\nidxoffsetbits=64\n",
        )

        assertEquals(64, info!!.indexOffsetBits)
        assertEquals(12, StarDictFormat.entryTailBytes(info.indexOffsetBits))
    }

    // ── 解码 ─────────────────────────────────────────────────────────

    @Test
    fun `decodeEntry returns the plain text for a single lowercase type`() {
        val block = "苹果 apple".toByteArray(Charsets.UTF_8)

        val (text, code) = StarDictFormat.decodeEntry(block, "m")

        assertEquals("苹果 apple", text)
        assertEquals('m', code)
    }

    @Test
    fun `decodeEntry strips markup for pango style types`() {
        val block = "<b>apple</b> n. 苹果 &amp; 苹果树".toByteArray(Charsets.UTF_8)

        val (text, code) = StarDictFormat.decodeEntry(block, "g")

        assertEquals('g', code)
        assertFalse("标记必须被去掉", text.contains("<b>"))
        assertTrue(text.contains("苹果"))
        assertTrue("实体必须反转义", text.contains("& 苹果树"))
    }

    @Test
    fun `decodeEntry handles inline type bytes when sametypesequence is absent`() {
        val block = "m".toByteArray(Charsets.UTF_8) + "香蕉".toByteArray(Charsets.UTF_8) + byteArrayOf(0)

        val (text, code) = StarDictFormat.decodeEntry(block, "")

        assertEquals("香蕉", text)
        assertEquals('m', code)
    }

    @Test
    fun `decodeEntry marks binary payloads instead of dumping bytes`() {
        val block = "W".toByteArray(Charsets.UTF_8) + ByteArray(16) { 7 }

        val (text, code) = StarDictFormat.decodeEntry(block, "")

        assertEquals('W', code)
        assertEquals("[音频]", text)
    }

    @Test
    fun `decodeEntry on an empty block yields empty text`() {
        val (text, _) = StarDictFormat.decodeEntry(ByteArray(0), "m")

        assertEquals("", text)
    }

    // ── 打开与查询 ────────────────────────────────────────────────────

    @Test
    fun `lookup finds an exact word`() {
        val dir = writeDict(temp.newFolder(), "basic", sampleEntries)

        StarDictDictionary.open(dir, "basic")!!.use { dict ->
            val hits = dict.lookup("banana")

            assertEquals(1, hits.size)
            assertEquals("香蕉", hits.first().content)
        }
    }

    @Test
    fun `lookup falls back to lower case`() {
        val dir = writeDict(temp.newFolder(), "basic", sampleEntries)

        StarDictDictionary.open(dir, "basic")!!.use { dict ->
            assertEquals("苹果", dict.lookup("APPLE").firstOrNull()?.content)
        }
    }

    @Test
    fun `lookup trims surrounding blanks`() {
        val dir = writeDict(temp.newFolder(), "basic", sampleEntries)

        StarDictDictionary.open(dir, "basic")!!.use { dict ->
            assertEquals("樱桃", dict.lookup("  cherry ").firstOrNull()?.content)
        }
    }

    @Test
    fun `lookup returns empty for unknown or blank words`() {
        val dir = writeDict(temp.newFolder(), "basic", sampleEntries)

        StarDictDictionary.open(dir, "basic")!!.use { dict ->
            assertTrue(dict.lookup("durian").isEmpty())
            assertTrue(dict.lookup("").isEmpty())
            assertTrue(dict.lookup("   ").isEmpty())
        }
    }

    @Test
    fun `lookup returns every homograph and respects the limit`() {
        val dir = writeDict(
            temp.newFolder(),
            "homograph",
            listOf("bank" to "银行", "bank" to "河岸", "bank" to "存款"),
        )

        StarDictDictionary.open(dir, "homograph")!!.use { dict ->
            assertEquals(3, dict.lookup("bank").size)
            assertEquals(2, dict.lookup("bank", limit = 2).size)
        }
    }

    @Test
    fun `lookup is case sensitive first then falls back`() {
        val dir = writeDict(
            temp.newFolder(),
            "mixed",
            listOf("Apple" to "大写苹果", "apple" to "小写苹果"),
        )

        StarDictDictionary.open(dir, "mixed")!!.use { dict ->
            assertEquals("大写苹果", dict.lookup("Apple").first().content)
            assertEquals("小写苹果", dict.lookup("apple").first().content)
        }
    }

    @Test
    fun `lookup decodes inline typed dictionaries`() {
        val dir = writeDict(
            temp.newFolder(),
            "inline",
            listOf("grape" to "葡萄", "lemon" to "柠檬"),
            sameTypeSequence = "",
        )

        StarDictDictionary.open(dir, "inline")!!.use { dict ->
            assertEquals("葡萄", dict.lookup("grape").firstOrNull()?.content)
        }
    }

    // ── .dict.dz ─────────────────────────────────────────────────────

    @Test
    fun `decompressDictDz expands a gzipped dict in place`() {
        val dir = temp.newFolder()
        val payload = ByteArray(4096) { (it % 251).toByte() }
        val dz = File(dir, "z.dict.dz")
        GZIPOutputStream(dz.outputStream()).use { it.write(payload) }

        val out = File(dir, "z.dict")
        assertTrue(StarDictFormat.decompressDictDz(dz, out))
        assertTrue(out.readBytes().contentEquals(payload))
        assertFalse("临时文件不得残留", File(dir, "z.dict.part").exists())
    }

    @Test
    fun `open falls back to dict dz when only the compressed file exists`() {
        val dir = writeDict(temp.newFolder(), "packed", sampleEntries)
        val dict = File(dir, "packed.dict")
        val compressed = File(dir, "packed.dict.dz")
        GZIPOutputStream(compressed.outputStream()).use { it.write(dict.readBytes()) }
        dict.delete()

        StarDictDictionary.open(dir, "packed")!!.use { loaded ->
            assertEquals("香蕉", loaded.lookup("banana").firstOrNull()?.content)
        }
    }

    @Test
    fun `open returns null when the triple is incomplete`() {
        val dir = temp.newFolder()
        File(dir, "broken.ifo").writeText("bookname=x\nwordcount=1\nidxfilesize=1\n")

        assertNull(StarDictDictionary.open(dir, "broken"))
        assertNull(StarDictDictionary.open(temp.newFolder(), "missing"))
    }

    // ── 索引健壮性 ────────────────────────────────────────────────────

    @Test
    fun `index drops a truncated trailing entry instead of failing`() {
        val dir = writeDict(temp.newFolder(), "trunc", sampleEntries)
        val idx = File(dir, "trunc.idx")
        val raw = idx.readBytes()
        idx.writeBytes(raw.copyOf(raw.size - 3))

        StarDictDictionary.open(dir, "trunc")!!.use { dict ->
            assertEquals("苹果", dict.lookup("apple").firstOrNull()?.content)
            assertEquals("香蕉", dict.lookup("banana").firstOrNull()?.content)
            // 尾部被截断的 cherry 自然查不到，但整本词典仍可用
            assertTrue(dict.lookup("cherry").isEmpty())
        }
    }

    // ── zip 导入 ──────────────────────────────────────────────────────

    private fun zipOf(vararg files: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun dictDirWithFiles(base: String): File {
        val dir = writeDict(temp.newFolder(), base, sampleEntries)
        return dir
    }

    @Test
    fun `installFromZip installs a dictionary and it becomes queryable`() {
        val source = dictDirWithFiles("basic")
        val zipBytes = zipOf(
            "basic.ifo" to File(source, "basic.ifo").readBytes(),
            "basic.idx" to File(source, "basic.idx").readBytes(),
            "basic.dict" to File(source, "basic.dict").readBytes(),
        )
        val target = File(temp.newFolder(), "dictionaries")

        val result = StarDictImporter.installFromZip(zipBytes.inputStream(), target)

        assertTrue("导入应成功：$result", result is StarDictImporter.Result.Installed)
        val installed = (result as StarDictImporter.Result.Installed).dictionaries
        assertEquals(1, installed.size)
        assertEquals("basic", installed.first().baseName)
        assertEquals("TestDict", installed.first().bookName)
        assertEquals(3, installed.first().wordCount)
        assertFalse("暂存目录不得残留", File(target, ".staging").exists())

        StarDictDictionary.open(File(target, "basic"), "basic")!!.use { dict ->
            assertEquals("樱桃", dict.lookup("cherry").firstOrNull()?.content)
        }
    }

    @Test
    fun `installFromZip unpacks a dict dz archive`() {
        val source = writeDict(temp.newFolder(), "packed", sampleEntries)
        val compressed = ByteArrayOutputStream()
        GZIPOutputStream(compressed).use { it.write(File(source, "packed.dict").readBytes()) }
        val zipBytes = zipOf(
            "packed.ifo" to File(source, "packed.ifo").readBytes(),
            "packed.idx" to File(source, "packed.idx").readBytes(),
            "packed.dict.dz" to compressed.toByteArray(),
        )
        val target = File(temp.newFolder(), "dictionaries")

        val result = StarDictImporter.installFromZip(zipBytes.inputStream(), target)

        assertTrue(result is StarDictImporter.Result.Installed)
        val dir = File(target, "packed")
        assertTrue("解压后的 .dict 应就位", File(dir, "packed.dict").isFile)
        assertFalse("压缩包内不得留 .dz", File(dir, "packed.dict.dz").exists())
        StarDictDictionary.open(dir, "packed")!!.use { dict ->
            assertEquals("香蕉", dict.lookup("banana").firstOrNull()?.content)
        }
    }

    @Test
    fun `installFromZip ignores zip slip paths and keeps only base names`() {
        val source = writeDict(temp.newFolder(), "basic", sampleEntries)
        val target = File(temp.newFolder(), "dictionaries")
        val outside = File(target.parentFile, "evil.idx")
        val zipBytes = zipOf(
            "../../evil.idx" to File(source, "basic.idx").readBytes(),
            "nested/dir/basic.ifo" to File(source, "basic.ifo").readBytes(),
            "nested/basic.idx" to File(source, "basic.idx").readBytes(),
            "nested/basic.dict" to File(source, "basic.dict").readBytes(),
        )

        val result = StarDictImporter.installFromZip(zipBytes.inputStream(), target)

        assertFalse("恶意路径不得写出目标目录", outside.exists())
        assertTrue(result is StarDictImporter.Result.Installed)
    }

    @Test
    fun `installFromZip rejects archives without a complete star dict triple`() {
        val target = File(temp.newFolder(), "dictionaries")

        val onlyText = zipOf("readme.txt" to "hello".toByteArray())
        assertEquals(
            StarDictImporter.Result.NotAStarDict,
            StarDictImporter.installFromZip(onlyText.inputStream(), target),
        )

        val missingDict = zipOf("x.ifo" to "bookname=x\nwordcount=1\nidxfilesize=1\n".toByteArray())
        assertEquals(
            StarDictImporter.Result.NotAStarDict,
            StarDictImporter.installFromZip(missingDict.inputStream(), target),
        )
        assertFalse(File(target, ".staging").exists())
    }

    @Test
    fun `listInstalled and uninstall round trip`() {
        val source = dictDirWithFiles("basic")
        val zipBytes = zipOf(
            "basic.ifo" to File(source, "basic.ifo").readBytes(),
            "basic.idx" to File(source, "basic.idx").readBytes(),
            "basic.dict" to File(source, "basic.dict").readBytes(),
        )
        val target = File(temp.newFolder(), "dictionaries")
        StarDictImporter.installFromZip(zipBytes.inputStream(), target)

        val listed = StarDictImporter.listInstalled(target)
        assertEquals(1, listed.size)
        assertEquals("TestDict", listed.first().bookName)

        assertTrue(StarDictImporter.uninstall(listed.first().dir))
        assertTrue(StarDictImporter.listInstalled(target).isEmpty())
    }

    @Test
    fun `listInstalled returns empty for a missing root`() {
        assertTrue(StarDictImporter.listInstalled(File(temp.newFolder(), "nope")).isEmpty())
    }
}
