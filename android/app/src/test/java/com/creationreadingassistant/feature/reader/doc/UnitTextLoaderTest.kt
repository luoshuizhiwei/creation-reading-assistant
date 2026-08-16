package com.creationreadingassistant.feature.reader.doc

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors

/**
 * 滚动 TXT 读取单元异步加载器（ReaderContentHost 组合路径的公开 loader seam）。
 *
 * 覆盖 P2 期望行为：阻塞读取在 IO dispatcher 执行、loading/error/成功状态可观察、
 * 快速切章/文档变化时旧请求不得覆盖新状态。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UnitTextLoaderTest {

    private val docA = Any()
    private val docB = Any()

    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "test-io").apply { isDaemon = true }
    }
    private val ioDispatcher = ioExecutor.asCoroutineDispatcher()

    @After
    fun tearDown() {
        ioExecutor.shutdown()
    }

    @Test
    fun `blocking read runs on ioDispatcher`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        var readThread: String? = null

        loader.load(docA, 0) {
            readThread = Thread.currentThread().name
            "测试文本"
        }

        assertEquals("test-io", readThread)
        assertEquals(UnitTextState.Loaded("测试文本"), loader.stateFor(docA, 0).value)
    }

    @Test
    fun `state is Loading until load completes then Loaded`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        val gate = CompletableDeferred<Unit>()
        val job = launch {
            loader.load(docA, 0) {
                gate.await()
                "正文"
            }
        }
        runCurrent()
        assertEquals(UnitTextState.Loading, loader.stateFor(docA, 0).value)

        gate.complete(Unit)
        job.join()

        assertEquals(UnitTextState.Loaded("正文"), loader.stateFor(docA, 0).value)
    }

    @Test
    fun `read exception produces Failed state`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)

        loader.load(docA, 0) { throw IOException("读取出错") }

        val state = loader.stateFor(docA, 0).value
        assertTrue(state is UnitTextState.Failed)
        assertEquals("读取出错", (state as UnitTextState.Failed).cause.message)
    }

    @Test
    fun `in-flight load from previous document is discarded after switchDocument`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        val gate = CompletableDeferred<Unit>()
        val job = launch {
            loader.load(docA, 0) {
                gate.await()
                "旧文档文本"
            }
        }
        runCurrent()

        loader.switchDocument(docB)
        gate.complete(Unit)
        job.join()

        // 旧文档在途结果被丢弃：状态不得变成 Loaded("旧文档文本")
        assertEquals(UnitTextState.Loading, loader.stateFor(docA, 0).value)

        loader.load(docB, 0) { "新文档文本" }
        assertEquals(UnitTextState.Loaded("新文档文本"), loader.stateFor(docB, 0).value)
    }

    @Test
    fun `cache is reset when document changes`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        var reads = 0

        loader.load(docA, 0) {
            reads += 1
            "A文档文本"
        }
        loader.switchDocument(docB)
        loader.load(docB, 0) {
            reads += 1
            "B文档文本"
        }

        assertEquals("切换文档后同一 unitIndex 必须重新读取", 2, reads)
        assertEquals(UnitTextState.Loaded("B文档文本"), loader.stateFor(docB, 0).value)
    }

    @Test
    fun `cache hit skips blocking read`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        var reads = 0
        loader.load(docA, 0) {
            reads += 1
            "测试文本"
        }
        loader.release(docA, 0) // 模拟 item 离开组合后重新组合

        loader.load(docA, 0) {
            reads += 1
            throw IllegalStateException("缓存命中时不应触发读取")
        }

        assertEquals(1, reads)
        assertEquals(UnitTextState.Loaded("测试文本"), loader.stateFor(docA, 0).value)
    }

    @Test
    fun `first frame of new document must not reuse previous document cache`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        loader.switchDocument(docA)
        loader.load(docA, 0) { "A文档文本" }
        assertEquals(UnitTextState.Loaded("A文档文本"), loader.stateFor(docA, 0).value)

        // 新文档首帧：组合先调 stateFor(docB)，LaunchedEffect 的 switchDocument(docB)
        // 尚未执行 —— 不得从旧文档缓存生成 Loaded 旧文本
        assertEquals(UnitTextState.Loading, loader.stateFor(docB, 0).value)
    }

    @Test
    fun `first frame state returned by stateFor receives Loaded after switchDocument and load`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        loader.switchDocument(docA)
        loader.load(docA, 0) { "A文档文本" }

        // Compose 首帧时序：组合期 stateFor 先取得 UI 订阅的 state，
        // 同帧 LaunchedEffect 才 switchDocument，随后 item LaunchedEffect 才 load。
        val uiState = loader.stateFor(docB, 0)
        loader.switchDocument(docB)
        loader.load(docB, 0) { "B文档文本" }

        // UI 订阅的必须是同一个可观察 state 实例，且它必须收到 Loaded，而不是永久 Loading
        assertSame("switchDocument 不得丢弃新文档首帧已创建的 state 槽", uiState, loader.stateFor(docB, 0))
        assertEquals(UnitTextState.Loaded("B文档文本"), uiState.value)
    }

    @Test
    fun `first frame new document state ignores old document cache and in-flight result`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        loader.switchDocument(docA)
        loader.load(docA, 0) { "A文档缓存文本" }

        val gate = CompletableDeferred<Unit>()
        val oldInFlight = launch {
            loader.load(docA, 1) {
                gate.await()
                "A文档在途文本"
            }
        }
        runCurrent()

        // 新文档首帧：stateFor 先于 switchDocument；旧文档在途结果不得泄漏进来
        val uiState = loader.stateFor(docB, 0)
        loader.switchDocument(docB)

        gate.complete(Unit)
        oldInFlight.join()
        assertEquals("旧文档在途结果不得写入新文档 state", UnitTextState.Loading, uiState.value)

        loader.load(docB, 0) { "B文档文本" }
        assertEquals("新文档读取必须真实执行，不得复用旧文档缓存", UnitTextState.Loaded("B文档文本"), uiState.value)
    }

    @Test
    fun `load for new document before switchDocument does not serve stale cache`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        loader.switchDocument(docA)
        loader.load(docA, 0) { "A文档文本" }

        // 极端顺序：新文档 load 先于 switchDocument 到达；
        // 缓存读取必须按文档身份绑定，旧文本不得写入新文档状态槽
        loader.load(docB, 0) { "B文档文本" }

        assertEquals(UnitTextState.Loading, loader.stateFor(docB, 0).value)
    }

    @Test
    fun `cancelled load does not deliver result`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        val gate = CompletableDeferred<Unit>()
        val job = launch {
            loader.load(docA, 0) {
                gate.await()
                "迟到的文本"
            }
        }
        runCurrent()

        job.cancel()
        gate.complete(Unit)
        runCurrent()

        assertTrue(job.isCancelled)
        assertEquals(UnitTextState.Loading, loader.stateFor(docA, 0).value)
    }

    // ── 重试：Failed → Loading → Loaded/Failed（显式点击，不靠滚走碰运气）──

    @Test
    fun `retry after failure reloads and succeeds`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        var reads = 0
        loader.load(docA, 0) {
            reads += 1
            throw IOException("首次读取失败")
        }
        assertTrue(loader.stateFor(docA, 0).value is UnitTextState.Failed)

        loader.retry(docA, 0) {
            reads += 1
            "重试成功"
        }

        assertEquals("重试必须重新读取，不得复用失败缓存", 2, reads)
        assertEquals(UnitTextState.Loaded("重试成功"), loader.stateFor(docA, 0).value)
    }

    @Test
    fun `retry transitions Failed to Loading before Loaded`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        loader.load(docA, 0) { throw IOException("失败") }
        assertTrue(loader.stateFor(docA, 0).value is UnitTextState.Failed)

        val gate = CompletableDeferred<Unit>()
        val job = launch {
            loader.retry(docA, 0) {
                gate.await()
                "重试正文"
            }
        }
        runCurrent()
        assertEquals(UnitTextState.Loading, loader.stateFor(docA, 0).value)

        gate.complete(Unit)
        job.join()
        assertEquals(UnitTextState.Loaded("重试正文"), loader.stateFor(docA, 0).value)
    }

    @Test
    fun `retry that fails again produces Failed with new cause`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        loader.load(docA, 0) { throw IOException("第一次失败") }

        loader.retry(docA, 0) { throw IOException("第二次失败") }

        val state = loader.stateFor(docA, 0).value
        assertTrue(state is UnitTextState.Failed)
        assertEquals("第二次失败", (state as UnitTextState.Failed).cause.message)
    }

    @Test
    fun `fast consecutive retries only accept latest generation`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        loader.load(docA, 0) { throw IOException("初始失败") }

        val gateOld = CompletableDeferred<Unit>()
        val gateNew = CompletableDeferred<Unit>()
        val oldRetry = launch {
            loader.retry(docA, 0) {
                gateOld.await()
                "旧代重试"
            }
        }
        runCurrent()
        val newRetry = launch {
            loader.retry(docA, 0) {
                gateNew.await()
                "新代重试"
            }
        }
        runCurrent()

        gateOld.complete(Unit)
        oldRetry.join()
        runCurrent()
        assertEquals("旧代重试结果不得写入", UnitTextState.Loading, loader.stateFor(docA, 0).value)

        gateNew.complete(Unit)
        newRetry.join()
        assertEquals(UnitTextState.Loaded("新代重试"), loader.stateFor(docA, 0).value)
    }

    @Test
    fun `retry from previous document does not write back after switchDocument`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        loader.load(docA, 0) { throw IOException("旧文档失败") }

        val gate = CompletableDeferred<Unit>()
        val job = launch {
            loader.retry(docA, 0) {
                gate.await()
                "旧文档重试文本"
            }
        }
        runCurrent()
        loader.switchDocument(docB)
        gate.complete(Unit)
        job.join()

        assertEquals("切文档后旧重试不得写回", UnitTextState.Loading, loader.stateFor(docA, 0).value)
    }

    @Test
    fun `invalidate drops in-flight result and resets state to Loading`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        val gate = CompletableDeferred<Unit>()
        val job = launch {
            loader.load(docA, 0) {
                gate.await()
                "在途文本"
            }
        }
        runCurrent()
        assertEquals(UnitTextState.Loading, loader.stateFor(docA, 0).value)

        loader.invalidate(docA, 0)
        gate.complete(Unit)
        job.join()

        assertEquals("invalidate 后在途结果不得写入", UnitTextState.Loading, loader.stateFor(docA, 0).value)
    }

    // ── P2-2：失败 sticky —— 滚出/回不得自动重试，只有显式 retry/invalidate/切文档清除 ──

    @Test
    fun `release after failure keeps Failed sticky and re-entry does not auto retry`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        loader.load(docA, 0) { throw IOException("读取失败") }
        assertTrue(loader.stateFor(docA, 0).value is UnitTextState.Failed)

        loader.release(docA, 0) // 滚出视口

        var reads = 0
        loader.load(docA, 0) { // 滚回视口：组合自动 load 不得自动重试
            reads += 1
            "不应读取"
        }

        assertEquals("滚回视口不得自动重试", 0, reads)
        assertTrue(loader.stateFor(docA, 0).value is UnitTextState.Failed)
    }

    @Test
    fun `stateFor after release of failed unit still reports Failed`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        loader.load(docA, 0) { throw IOException("读取失败") }
        loader.release(docA, 0)

        val state = loader.stateFor(docA, 0).value
        assertTrue(state is UnitTextState.Failed)
        assertEquals("读取失败", (state as UnitTextState.Failed).cause.message)
    }

    @Test
    fun `explicit retry clears sticky failure and reloads`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        loader.load(docA, 0) { throw IOException("首次失败") }
        loader.release(docA, 0)

        var reads = 0
        loader.retry(docA, 0) {
            reads += 1
            "重试成功"
        }

        assertEquals("显式 retry 必须重新读取", 1, reads)
        assertEquals(UnitTextState.Loaded("重试成功"), loader.stateFor(docA, 0).value)
    }

    @Test
    fun `invalidate clears sticky failure back to Loading`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        loader.load(docA, 0) { throw IOException("失败") }
        loader.release(docA, 0)

        loader.invalidate(docA, 0)

        assertEquals(UnitTextState.Loading, loader.stateFor(docA, 0).value)
    }

    @Test
    fun `switchDocument releases sticky failure of previous document`() = runTest {
        val loader = UnitTextLoader(ioDispatcher = ioDispatcher)
        loader.load(docA, 0) { throw IOException("失败") }
        loader.release(docA, 0)
        assertTrue(loader.stateFor(docA, 0).value is UnitTextState.Failed)

        loader.switchDocument(docB)

        // 旧文档失败不得保留 sticky：回到 Loading，等待新上下文重新加载
        assertEquals(UnitTextState.Loading, loader.stateFor(docA, 0).value)

        loader.load(docB, 0) { "新文档文本" }
        assertEquals(UnitTextState.Loaded("新文档文本"), loader.stateFor(docB, 0).value)
    }
}
