package com.creationreadingassistant.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.creationreadingassistant.testutil.InMemorySharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [DurationPrefs] 的真实 DataStore 往返测试（S15-F4 / R1）。
 *
 * ## 执行顺序由方法名强制（`@FixMethodOrder(NAME_ASCENDING)`）
 * 1. `a0 ...` —— **本类第一个构造 `DurationPrefs`、第一个访问 `durationPrefsDataStore` 委托的用例**。
 *    它先断言磁盘上不存在 `duration_prefs.preferences_pb`，再启动真实 Flow 订阅并用真实读路径
 *    （`toggle()` 内部的 `ds.data.first()`）证明「空白 DataStore → 真实读取 → HOURS」，
 *    而不是只读 `stateIn` 的声明初值。磁盘一旦存在，后面任何用例都不再是空白盘冷读，
 *    所以「谁第一个跑」这件事由这条用例自己的前置断言兜住，不靠注释自觉。
 * 2. `aaa_...` —— 脏值 / 哨兵用例。
 * 3. `b..` / `c..` —— 其余用例，各自显式建立前置状态（先 `setUnit` 再等磁盘到位）。
 *
 * 为什么 a0 必须抢到「首次访问」这个名额：`preferencesDataStore(name)` 委托是**按顶级属性缓存的
 * 进程级单例**，第一次访问时用当时的 Context 解析 `filesDir` 并永久绑定 —— 所以临时目录只能做到
 * **类级**，做不到每用例一份（第三类实测前提）。
 * 注意别把这件事想成「一个 JVM 只能冷读一次」：`ds.data` 每次新收集都会重新读盘，
 * `aaa_...` 的冷解析语义就是这么保留下来的（R1 期间用磁盘上的哨兵键否证了我原来的「actor 常驻内存、
 * 外部改写不可见」推断）。
 *
 * ## 三条实测前提（写这份测试时被现实纠正过，全部保留在断言里）
 * 1. `unit.first()` / `unit.value` 在未订阅时拿到的是 `stateIn` 的**初始值** HOURS，
 *    所以「读到 DAYS / 读到真实值」必须显式挂订阅者并等上游真的产出，不能只 `first()`。
 * 2. 等待必须是**真实调度 + 真实时间**：`runTest` 的 `withTimeout` 走虚拟时间，
 *    `UnconfinedTestDispatcher` 的 scope 在测试线程阻塞时不推进，而 DataStore 读盘是真实 IO。
 *    因此本类所有 DurationPrefs 实例都用 `Dispatchers.Default` 作用域，轮询用 `Thread.sleep`，
 *    并且轮询本身要 `withContext(Dispatchers.IO)` 离开 `runTest` 的测试线程 ——
 *    否则 DataStore actor 把结果回灌给测试线程续体时没人执行它，等于自锁（实测 10 s 超时）。
 * 3. `preferencesDataStore` 委托按顶级属性缓存单例，「每用例一个目录」做不到，
 *    故本类用**类级独立临时目录** + 每用例显式前置写入 + 收敛式断言。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DurationPrefsTest {

    /**
     * R1 补强：默认值不再靠 `stateIn` 的声明初值，而是靠**真实上游读取**。
     *
     * 判定手段是 `toggle()`：它内部是 `DurationUnit.fromStorage(ds.data.first()[KEY])`，
     * 这条 `ds.data.first()` 只能等 DataStore 真的完成一次上游读取才会返回，
     * 与 `stateIn` 的初值毫无关系（这正是生产代码 KDoc 里刻意重读 DataStore 的那条路径）。
     * 配合前面「文件不存在」的断言，读出来的「无值」只能来自空白盘。
     * 于是：
     * - 空白盘 → 真实读到 HOURS → `toggle()` 必然返回 **DAYS**；
     * - 如果上游其实读到的是 DAYS（盘上真有 `days`），`toggle()` 必然返回 **HOURS**。
     * 两个方向都能区分，所以这一步是真的在验「读到什么」，不是验「初值写了什么」。
     *
     * 「第一个接触该 DataStore」不是靠注释自觉：把任意一个用写的用例改到最前重跑，
     * 本用例的第一条断言就会以「磁盘上已经有 duration_prefs.preferences_pb」失败（R1 探针实测）。
     */
    @Test
    fun `a0 blank data store default is proven by a real upstream read, not by the stateIn initial value`() {
        runTest {
            assertFalse(
                "前置条件不成立：磁盘上已经有 duration_prefs.preferences_pb，" +
                    "说明本用例不是类内第一个接触该 DataStore 的用例，空白盘前提失效",
                storeFile().exists(),
            )

            val prefs = newPrefs()
            assertEquals(
                "stateIn 声明的初值必须是 HOURS（默认单位的声明面）",
                DurationUnit.HOURS,
                prefs.unit.value,
            )

            // 起真实订阅者，确认 collect 体真的跑起来了（不是挂在一个从未启动的协程上）；
            // 随后 cancel，让下面两次 toggle 期间没有额外订阅者参与状态回灌。
            val subscribed = AtomicBoolean(false)
            val warmUp = collectScope.launch { prefs.unit.collect { subscribed.set(true) } }
            awaitSubscribed(subscribed, "空白盘上游订阅")
            warmUp.cancel()

            // 真实上游读：空白盘只能读出「无值」→ HOURS → toggle 写 DAYS 并返回 DAYS。
            assertEquals(
                "空白 DataStore 经真实 ds.data.first() 读出的当前单位必须是 HOURS" +
                    "（否则 toggle 会返回 HOURS）",
                DurationUnit.DAYS,
                prefs.toggle(),
            )
            awaitDiskText(DurationUnit.DAYS.storageValue)
            assertTrue("toggle 之前磁盘还不存在，之后必须真的写出文件", storeFile().exists())

            // 再走一次真实读：现在盘上是 DAYS，toggle 必须切回 HOURS。
            // 若上游根本没读盘而停在初值 HOURS，这里会再次得到 DAYS —— 所以这条断言
            // 与上一条一起把「真实读取发生且读到了磁盘内容」钉死。
            assertEquals(
                "真实上游读到 DAYS 后 toggle 必须切回 HOURS（读到初值会返回 DAYS）",
                DurationUnit.HOURS,
                prefs.toggle(),
            )
            awaitDiskText(DurationUnit.DAYS.storageValue, absent = true)
            awaitDiskText(DurationUnit.HOURS.storageValue)

            awaitFlowValue(prefs, DurationUnit.HOURS, "空白盘实例（默认值之后）")
        }
    }

    /**
     * 脏值 / 哨兵用例（顺序第二）。
     *
     * 实测纠正（R1 期间被磁盘字节直接否证的推断）：`ds.data` 的**每一次新收集都会重新读盘** ——
     * `WhileSubscribed(5_000)` 会让上游停掉，下一个订阅者起来时是一次全新读取，
     * `toggle()` 里的 `ds.data.first()` 本身就是独立收集。
     * 所以本用例不需要抢「类内第一次访问委托」的名额，仍然是真正的冷解析；
     * a0 的空白盘保证则来自它自己的 `assertFalse(storeFile().exists())`，与机制无关。
     *
     * 判别子是哨兵键：整档重写只会保留内存里认识的键，
     * 所以「哨兵在不在」能区分「脏文件被真的解析进内存后再写回」与「写路径凭空新建一份文件」。
     *
     * 这条断言是本类里唯一带时序成分的一处，实测到两种读取路径都会出现：
     * 本类的强制顺序下（a0 先订阅再取消）读路径会重启并重新解析磁盘，哨兵存活（r1c/r1d 通过）；
     * 而在「a0 之前没有任何订阅发生过」的人为乱序探针里，`ds.data` 直接吃了内存里已有的值，
     * 哨兵就被整档重写丢掉了。若将来这条翻转成失败，先看是不是顺序/订阅生命周期被动过，
     * 不要直接判成产品回归。
     */
    @Test
    fun `aaa_unknown persisted value falls back to hours without crashing or wiping the file`() {
        runTest {
            val dirty = dirtyPreferencesBytes(raw = "Days")
            storeFile().writeBytes(dirty)

            val prefs = newPrefs()
            // DataStore 只有在「解析失败」时才会按默认 RecoveryStrategy 清空文件；
            // 字节一字未动 = 走的是未知值回落，而不是损坏恢复。
            assertArrayEquals(
                "脏文件被改写，说明命中的是 DataStore 损坏恢复而不是未知值回落",
                dirty,
                storeFile().readBytes(),
            )

            // 真实读路径：未知值 "Days" 必须回落 HOURS，于是 toggle 写成 DAYS。
            // 如果解析失败被清档，读出来也是「无值」→ 同样返回 DAYS，
            // 所以这条必须和上面的字节不变、下面的哨兵保留一起读，才构成「回落且没清档」。
            assertEquals(
                "脏值 Days 没有被当成未知值回落（toggle 未返回 DAYS 说明上游读到的不是 HOURS）",
                DurationUnit.DAYS,
                prefs.toggle(),
            )
            awaitFlowValue(prefs, DurationUnit.DAYS, "脏盘实例（写入后）")
            val after = awaitDiskText(DurationUnit.DAYS.storageValue)
            assertTrue(
                "哨兵键丢失，说明脏文件没有被解析成内存态：${hexDump(after)}",
                text(after).contains(SENTINEL_KEY),
            )
            assertFalse(
                "脏值仍在，说明写入没有覆盖它：${hexDump(after)}",
                text(after).contains("Days"),
            )
        }
    }

    @Test
    fun `b1_setUnit days reaches disk and is read back by newly created instances`() {
        runTest {
            newPrefs().setUnit(DurationUnit.DAYS)
            awaitDiskText(DurationUnit.DAYS.storageValue)
            readBackOnFreshInstance(DurationUnit.DAYS)
            readBackOnFreshInstance(DurationUnit.DAYS)
        }
    }

    @Test
    fun `b2_setUnit hours reaches disk and replaces the previous value`() {
        runTest {
            val prefs = newPrefs()
            prefs.setUnit(DurationUnit.DAYS)
            awaitDiskText(DurationUnit.DAYS.storageValue)
            awaitFlowValue(prefs, DurationUnit.DAYS, "写入方实例")

            prefs.setUnit(DurationUnit.HOURS)
            awaitDiskText(DurationUnit.HOURS.storageValue)
            awaitDiskText(DurationUnit.DAYS.storageValue, absent = true)
            readBackOnFreshInstance(DurationUnit.HOURS)
        }
    }

    @Test
    fun `b3_toggle from hours returns days and persists days`() {
        runTest {
            val prefs = newPrefs()
            prefs.setUnit(DurationUnit.HOURS)
            awaitDiskText(DurationUnit.DAYS.storageValue, absent = true)

            assertEquals(DurationUnit.DAYS, prefs.toggle())
            awaitDiskText(DurationUnit.DAYS.storageValue)
            readBackOnFreshInstance(DurationUnit.DAYS)
        }
    }

    @Test
    fun `b4_toggle from days returns hours and persists hours`() {
        runTest {
            val prefs = newPrefs()
            prefs.setUnit(DurationUnit.DAYS)
            awaitDiskText(DurationUnit.DAYS.storageValue)

            assertEquals(DurationUnit.HOURS, prefs.toggle())
            awaitDiskText(DurationUnit.DAYS.storageValue, absent = true)
            readBackOnFreshInstance(DurationUnit.HOURS)
        }
    }

    @Test
    fun `b5_consecutive toggles agree with the returned unit and the persisted bytes`() {
        runTest {
            val prefs = newPrefs()
            prefs.setUnit(DurationUnit.HOURS)
            awaitDiskText(DurationUnit.HOURS.storageValue)

            var expected = DurationUnit.DAYS
            repeat(6) { round ->
                assertEquals("第 $round 次 toggle 返回值不符", expected, prefs.toggle())
                awaitDiskText(expected.storageValue)
                awaitDiskText(other(expected).storageValue, absent = true)
                awaitFlowValue(prefs, expected, "第 $round 次 toggle 后")
                expected = other(expected)
            }
        }
    }

    @Test
    fun `c0_toggle with no active subscriber follows the persisted value, not the stale flow value`() {
        runTest {
            val prefs = newPrefs()
            prefs.setUnit(DurationUnit.DAYS)
            awaitDiskText(DurationUnit.DAYS.storageValue)

            assertEquals(
                "前置条件不成立：无订阅者时 value 应仍是 stateIn 初始值" +
                    "（这正是 toggle 必须重读 DataStore 的原因）",
                DurationUnit.HOURS,
                prefs.unit.value,
            )
            // 持久值 DAYS → toggle 读到 DAYS → 写回 HOURS。
            assertEquals(DurationUnit.HOURS, prefs.toggle())
            awaitDiskText(DurationUnit.DAYS.storageValue, absent = true)
            // 再切一次：仍然按持久值走，不受上面那个「假 HOURS」影响。
            assertEquals(DurationUnit.DAYS, prefs.toggle())
            awaitDiskText(DurationUnit.DAYS.storageValue)
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun newPrefs(): DurationPrefs = DurationPrefs(context, realScope())

    private fun realScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default).also { createdScopes += it }

    private suspend fun readBackOnFreshInstance(expected: DurationUnit) {
        awaitFlowValue(newPrefs(), expected, "新实例")
    }

    /** 挂一个真实订阅者驱动 WhileSubscribed 的上游，再用真实时间等 StateFlow 到位。 */
    private suspend fun awaitFlowValue(prefs: DurationPrefs, expected: DurationUnit, label: String) {
        withContext(Dispatchers.IO) {
            val subscribed = AtomicBoolean(false)
            val collector: Job = collectScope.launch {
                // 必须真的订阅 unit，WhileSubscribed 的上游才会加载；lambda 顺便报告「订阅已建立」。
                prefs.unit.collect { subscribed.set(true) }
            }
            try {
                awaitSubscribed(subscribed, label)
                val reached = awaitUntil { prefs.unit.value == expected }
                assertEquals(
                    "$label：unit 流在 $TIMEOUT_MS ms 内没有产出 $expected（实际=${prefs.unit.value}，" +
                        "初始值 HOURS 不算，必须等 DataStore 上游加载完成）",
                    true,
                    reached,
                )
            } finally {
                collector.cancel()
            }
        }
    }

    /**
     * 确认收集协程真的进入了 `unit.collect {}` 并收到第一帧（订阅建立）。
     *
     * 注意不能改成「等协程结束」：StateFlow 的收集永远不结束，那样只会白等一个超时
     * （实测把整类拖到 `runTest` 的 60 s watchdog 上）。
     */
    private suspend fun awaitSubscribed(subscribed: AtomicBoolean, label: String) {
        val reached = withContext(Dispatchers.IO) { awaitUntil { subscribed.get() } }
        assertEquals(
            "$label：订阅在 $TIMEOUT_MS ms 内没有建立（collect 体从未收到第一帧）",
            true,
            reached,
        )
    }

    /** 轮询磁盘字节，等某个存储值出现（或消失），返回最后一次读到的内容。 */
    private suspend fun awaitDiskText(needle: String, absent: Boolean = false): ByteArray =
        withContext(Dispatchers.IO) {
            val reached = awaitUntil {
                val bytes = if (storeFile().exists()) storeFile().readBytes() else ByteArray(0)
                val has = text(bytes).contains(needle)
                if (absent) !has else has
            }
            val bytes = if (storeFile().exists()) storeFile().readBytes() else ByteArray(0)
            assertTrue(
                "磁盘在 $TIMEOUT_MS ms 内没有${if (absent) "移除" else "出现"} \"$needle\"；实际=${hexDump(bytes)}",
                reached,
            )
            bytes
        }

    private fun awaitUntil(predicate: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!predicate()) {
            if (System.currentTimeMillis() >= deadline) return false
            Thread.sleep(POLL_MS)
        }
        return true
    }

    private fun other(unit: DurationUnit): DurationUnit =
        if (unit == DurationUnit.HOURS) DurationUnit.DAYS else DurationUnit.HOURS

    private fun storeFile(): File = File(classDir, PREF_FILE_RELATIVE)

    private fun text(bytes: ByteArray): String = String(bytes, CHARSET)

    private fun hexDump(bytes: ByteArray): String =
        bytes.take(96).joinToString(" ") { "%02x".format(it.toInt() and 0xff) } +
            " (${bytes.size} B)"

    private companion object {
        const val PREF_FILE_RELATIVE = "datastore/duration_prefs.preferences_pb"
        const val SENTINEL_KEY = "s15f4_sentinel_key"
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 25L
        val CHARSET = Charsets.ISO_8859_1

        lateinit var classDir: File
        lateinit var context: Context

        val collectScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val createdScopes: MutableList<CoroutineScope> =
            Collections.synchronizedList(mutableListOf())

        @BeforeClass
        @JvmStatic
        fun createClassDir() {
            classDir = Files.createTempDirectory("cra-duration-prefs-test").toFile()
            File(classDir, "datastore").mkdirs()
            context = mockApplicationContext(classDir)
        }

        @AfterClass
        @JvmStatic
        fun destroyClassDir() {
            createdScopes.forEach { it.cancel() }
            createdScopes.clear()
            collectScope.cancel()
            if (::classDir.isInitialized) {
                val leftovers = classDir.walkTopDown().filter { it != classDir }.map { it.name }.toList()
                classDir.deleteRecursively()
                assertFalse("类级临时 DataStore 目录未清理，残留=$leftovers", classDir.exists())
            }
        }

        /** 自建 Context 替身：filesDir 指向本类的临时目录，与 TestDataStoreDirs 完全分离。 */
        fun mockApplicationContext(rootDir: File): Context {
            val prefsMap = HashMap<String, SharedPreferences>()
            lateinit var mockContext: Context
            mockContext = mockk {
                every { filesDir } returns rootDir
                every { applicationContext } answers { mockContext }
                every { getSharedPreferences(any(), any()) } answers {
                    prefsMap.getOrPut(firstArg()) { InMemorySharedPreferences() }
                }
                every { deleteSharedPreferences(any()) } answers {
                    prefsMap.remove(firstArg<String>()) != null
                }
            }
            return mockContext
        }

        /**
         * 手工构造「可被 protobuf 解析、但值未知」的 preferences 字节。
         *
         * 布局（与本仓库 DataStore 1.1.1 实测一致）：
         * 外层 `0a <len>` 是 PreferenceGroup 里 repeated Preference 的第 1 个字段；
         * Preference 内 `0a <klen> <key>` 是 key（字段 1），
         * `12 <vlen>` 包 oneof，`2a <vlen> <value>` 是 string_value（字段 5）。
         * 额外塞一个哨兵键：整档重写只会保留内存里认识的键，
         * 于是「哨兵在不在」能区分「外部文件被采纳」与「内存态覆盖」。
         */
        fun dirtyPreferencesBytes(raw: String): ByteArray {
            fun entry(key: String, value: String): ByteArray {
                val k = key.toByteArray(CHARSET)
                val v = value.toByteArray(CHARSET)
                val valueType = byteArrayOf(0x2a, v.size.toByte()) + v
                return byteArrayOf(0x0a, k.size.toByte()) + k +
                    byteArrayOf(0x12, valueType.size.toByte()) + valueType
            }

            fun record(body: ByteArray): ByteArray = byteArrayOf(0x0a, body.size.toByte()) + body

            return record(entry("duration_unit", raw)) + record(entry(SENTINEL_KEY, "kept"))
        }
    }
}
