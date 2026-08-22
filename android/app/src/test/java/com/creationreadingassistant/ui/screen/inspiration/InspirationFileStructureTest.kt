package com.creationreadingassistant.ui.screen.inspiration

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Inspiration 拆分结构的源码级验收（自 androidTest 迁入 JVM）。
 *
 * 原 androidTest 版本用仓库根相对路径读文件，在设备上永远 ENOENT——这类
 * 「结构自检」只依赖源码树，放 JVM 单测才能真实执行（工作目录 = app 模块根）。
 */
class InspirationFileStructureTest {

    private fun moduleFile(path: String): File = File(path)

    @Test
    fun oldEntryFileIsTiny() {
        val f = moduleFile("src/main/java/com/creationreadingassistant/ui/screen/InspirationScreen.kt")
        assertTrue("兼容入口文件存在", f.exists())
        val lines = f.readLines().size
        assertTrue("兼容入口 $lines 行 ≤ 80", lines <= 80)
    }

    @Test
    fun listDetailEditorAreDistinctFilesOfSubstance() {
        val dir = moduleFile("src/main/java/com/creationreadingassistant/ui/screen/inspiration/components")
        val listF = File(dir, "InspirationList.kt").also { assertTrue("List 存在", it.exists()) }
        val detailF = File(dir, "InspirationDetail.kt").also { assertTrue("Detail 存在", it.exists()) }
        val editorF = File(dir, "InspirationEditor.kt").also { assertTrue("Editor 存在", it.exists()) }
        assertTrue("List 行数 ≥ 50", listF.readLines().size >= 50)
        assertTrue("Detail 行数 ≥ 50", detailF.readLines().size >= 50)
        assertTrue("Editor 行数 ≥ 50", editorF.readLines().size >= 50)
    }

    @Test
    fun noSingleMonolithicFileStillExists() {
        val oldFile = moduleFile("src/main/java/com/creationreadingassistant/ui/screen/InspirationScreen.kt")
        val newFile = moduleFile("src/main/java/com/creationreadingassistant/ui/screen/inspiration/InspirationScreen.kt")
        assertTrue("旧入口存在", oldFile.exists())
        assertTrue(oldFile.readLines().size <= 80)
        assertTrue("新 Screen 存在", newFile.exists())
        assertTrue("新 Screen 行数 < 1000", newFile.readLines().size < 1000)
    }

    @Test
    fun sealedPageReplacesStringModeDispatch() {
        val route = moduleFile("src/main/java/com/creationreadingassistant/ui/screen/inspiration/InspirationRoute.kt")
        assertTrue("Route 文件存在", route.exists())
        val content = route.readText()
        assertTrue("不再使用 mode == \"list\"", !content.contains("mode == \"list\""))
        assertTrue("不再使用 mode == \"detail\"", !content.contains("mode == \"detail\""))
        assertTrue("不再使用 mode == \"editor\"", !content.contains("mode == \"editor\""))
        assertTrue("使用 InspirationPage.List", content.contains("InspirationPage.List"))
        assertTrue("使用 InspirationPage.Detail", content.contains("InspirationPage.Detail"))
        assertTrue("使用 InspirationPage.Editor", content.contains("InspirationPage.Editor"))
    }
}
