package com.creationreadingassistant.feature.reader

import android.content.Context
import android.content.ContextWrapper
import java.io.File

/**
 * 把 `filesDir` / `cacheDir` 重定向到**测试专属唯一目录**的 Context 包装。
 *
 * ## 为什么需要它
 *
 * `EpubRepository` 与 `EpubParser` 把内部副本路径**硬编码**成
 * `File(context.filesDir, "books/epub/<bookId>.epub")`。此前测试直接把
 * `ApplicationProvider.getApplicationContext()` 交给仓储，于是内部副本、`.importing`、
 * `.repairing`、`.backup` 全部落在**应用正式私有目录** `files/books/epub/` 下 ——
 * 与真机上用户已导入的书共用同一个目录。bookId 虽由含 UUID 的 Uri 派生，
 * 但 32 位 `hashCode` 不是无碰撞保证，仅凭「UUID 因而不可能碰撞」不足以作为
 * 不触碰既有书的依据。
 *
 * 本类改为：仓储只拿到 [IsolatedTestContext]，其 `filesDir` / `cacheDir`
 * 指向本次测试创建的唯一根目录 [testRoot]。正式 `files/books/epub/` 不再出现在
 * 任何被测写入路径上。
 *
 * ## 覆盖范围审查（必须如实记录边界）
 *
 * 经 `grep` 枚举，`EpubRepository` / `EpubParser` 可达的 `context` 成员**只有三个**：
 *
 * | 成员 | 是否被本类覆盖 | 说明 |
 * |---|---|---|
 * | `filesDir`（`getFilesDir()`） | ✅ 覆盖 → `testRoot/files` | 内部副本、staging、backup 的唯一写入根 |
 * | `cacheDir`（`getCacheDir()`） | ✅ 覆盖 → `testRoot/cache` | `EpubParser` 的旧 cacheDir 兜底读取路径 |
 * | `applicationContext`（`getApplicationContext()`） | ✅ 覆盖 → 返回本包装自身 | `ContextWrapper` 默认实现会返回 base 的**真实 Application Context**，被测代码一旦改用 `context.applicationContext.filesDir` 即静默逃逸出 [testRoot]，且 [assertInsideRoot] 只校验调用方传入的路径、救不了这种间接取径（S04 独立审查 P1-2 / P2-3）。故覆写为返回 `this`，使任何经 `applicationContext` 的再次取径仍落在隔离目录内 |
 * | `contentResolver` | ➖ 不覆盖，委托真实实现 | 该路径只读：[openInputStream] / `query` / `openAssetFileDescriptor`，**无任何写操作**（已 grep 确认无 `insert`/`update`/`delete`/`openOutputStream`），不会写入应用存储 |
 *
 * **未被覆盖、也不在解析链路上**的成员（保留说明以免误导）：
 * `getExternalFilesDir` / `getNoBackupFilesDir` / `getDir`（`getDir` 在 API 24+ 委托到
 * `getDataDir`）/ `databasePath`。这些成员在本测试可达路径中**从未被调用**，
 * 故不重定向；若将来生产代码改用它们，本包装需同步扩展。
 *
 * ## 路径不变量
 *
 * 所有写入与清理前必须经 [assertInsideRoot] 校验：解析后的**绝对路径**（`canonicalFile`，
 * 已解析 `..` 与符号链接）必须落在 [testRoot] 之内。这是路径隔离本身，
 * 而不是「因为名字带 UUID 所以安全」这一概率性论据。
 */
internal class IsolatedTestContext(
    base: Context,
    /** 本次测试的唯一根目录；生命期由测试负责创建与删除。 */
    val testRoot: File,
) : ContextWrapper(base) {

    /** 内部副本等写入根：`<testRoot>/files`。 */
    val filesRoot: File = File(testRoot, "files")

    /** 缓存根：`<testRoot>/cache`。 */
    val cacheRoot: File = File(testRoot, "cache")

    override fun getFilesDir(): File = filesRoot.also { it.mkdirs() }

    override fun getCacheDir(): File = cacheRoot.also { it.mkdirs() }

    /**
     * 返回本包装自身，而非 base 的真实 Application Context。
     *
     * `ContextWrapper.getApplicationContext()` 的默认实现委托到 base，返回的是
     * **正式应用**的 Context —— 其 `filesDir` / `cacheDir` 指向正式私有目录。
     * 若被测代码改写为 `context.applicationContext.filesDir`，会静默绕开本类的
     * 重定向与 [assertInsideRoot] 防线（该防线只校验调用方传入的路径，拦不住
     * 这种间接取径）。覆写为返回 `this` 后，无论被测代码经几次
     * `applicationContext` 转手，取到的仍是本包装，路径仍落在 [testRoot] 内。
     */
    override fun getApplicationContext(): IsolatedTestContext = this

    /**
     * 校验 [file] 的规范绝对路径落在 [testRoot] 之内，通过后原样返回该文件。
     *
     * 用 `canonicalFile` 而非 `absoluteFile`：前者会解析 `..` 与符号链接，
     * 使 `testRoot/../../etc` 这类路径无法绕过前缀检查。
     * 相等或位于其下（带分隔符前缀）均视为在根内 —— 单纯 `startsWith(rootPath)`
     * 会误放行 `testRoot-evil` 这种同前缀兄弟目录。
     *
     * @throws IllegalStateException 路径逃逸出测试专属目录时抛出（**拒绝操作**，
     *         而不是照常删除）。
     */
    fun assertInsideRoot(file: File): File {
        val root = testRoot.canonicalFile
        val resolved = file.canonicalFile
        val inside = resolved == root || resolved.path.startsWith(root.path + File.separator)
        check(inside) {
            "拒绝操作测试专属目录之外的路径：$resolved（root=$root）。" +
                "本测试只允许读写自身创建的目录。"
        }
        return file
    }
}
