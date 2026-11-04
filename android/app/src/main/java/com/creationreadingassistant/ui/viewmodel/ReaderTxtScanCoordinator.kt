package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.TxtFileIndex
import com.creationreadingassistant.feature.reader.doc.TxtFileScanner
import com.creationreadingassistant.feature.reader.doc.TxtScanMonitor
import com.creationreadingassistant.feature.reader.doc.TxtTocProfile
import com.creationreadingassistant.feature.reader.rules.RuleEngine
import com.creationreadingassistant.feature.reader.rules.RulesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * TXT 目录规则识别、进度上报、取消与切书时效性协同器。
 *
 * 负责管理异步扫描 Job、[TxtRuleScanCoordinator] 令牌竞态校验、[TxtScanMonitor] 进度派发与
 * [TxtRuleScanResult] / [TxtRuleScanStatus] 状态流维护。
 */
internal class ReaderTxtScanCoordinator {

    private val _txtRuleScanResult = MutableStateFlow<TxtRuleScanResult?>(null)
    val txtRuleScanResult: StateFlow<TxtRuleScanResult?> = _txtRuleScanResult.asStateFlow()

    private val _txtRuleScanStatus = MutableStateFlow<TxtRuleScanStatus>(TxtRuleScanStatus.Idle)
    val txtRuleScanStatus: StateFlow<TxtRuleScanStatus> = _txtRuleScanStatus.asStateFlow()

    private val tokenCoordinator = TxtRuleScanCoordinator()
    private var txtScanJob: Job? = null

    /**
     * 测试 seam：默认走真实扫描器；测试可注入受控实现来验证取消 / 进度时序。
     */
    var txtFileScanner: (File, String, TxtScanMonitor?) -> TxtFileIndex =
        { file, ruleId, monitor -> TxtFileScanner.scan(file, ruleId, monitor) }

    /**
     * 测试 seam：按显式 [TxtTocProfile] 扫描（多规则目录重扫入口）。
     */
    var txtFileScannerProfile: (File, TxtTocProfile, TxtScanMonitor?) -> TxtFileIndex =
        { file, profile, monitor -> TxtFileScanner.scan(file, profile, monitor) }

    /**
     * 切换书籍时调用：作废上一本书在途的规则扫描并重置状态。
     */
    fun onBookSwitched() {
        tokenCoordinator.invalidate()
        txtScanJob?.cancel()
        txtScanJob = null
        _txtRuleScanStatus.value = TxtRuleScanStatus.Idle
    }

    /**
     * 单规则扫描（旧兼容/测试路径）。
     */
    fun scanTxtTocRule(
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
        bookId: String,
        filePath: String,
        ruleId: String,
    ) {
        txtScanJob?.cancel()
        val requestId = tokenCoordinator.begin(bookId, ruleId)
        _txtRuleScanStatus.value = TxtRuleScanStatus.Running(bookId, ruleId, progress = null)
        txtScanJob = scope.launch(dispatcher) {
            try {
                val file = File(filePath)
                val monitor = object : TxtScanMonitor {
                    override fun isCancelled(): Boolean =
                        !tokenCoordinator.isCurrent(bookId, ruleId, requestId)

                    override fun onProgress(fraction: Float?) {
                        if (tokenCoordinator.isCurrent(bookId, ruleId, requestId)) {
                            _txtRuleScanStatus.value = TxtRuleScanStatus.Running(bookId, ruleId, fraction)
                        }
                    }
                }
                val newIndex = txtFileScanner(file, ruleId, monitor)
                if (!tokenCoordinator.isCurrent(bookId, ruleId, requestId)) return@launch
                val newDocument = PlainTextDocument.fromFileIndex(file, newIndex)
                if (!tokenCoordinator.isCurrent(bookId, ruleId, requestId)) return@launch
                _txtRuleScanResult.value = TxtRuleScanResult(
                    bookId = bookId,
                    ruleId = ruleId,
                    requestId = requestId,
                    fileIndex = newIndex,
                    document = newDocument,
                )
                _txtRuleScanStatus.value = TxtRuleScanStatus.Completed(bookId, ruleId, newIndex.chapters.size)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (!tokenCoordinator.isCurrent(bookId, ruleId, requestId)) return@launch
                _txtRuleScanResult.value = TxtRuleScanResult(
                    bookId = bookId,
                    ruleId = ruleId,
                    requestId = requestId,
                    error = e.message ?: "切换目录规则失败",
                )
                _txtRuleScanStatus.value = TxtRuleScanStatus.Failed(
                    bookId = bookId,
                    ruleId = ruleId,
                    message = e.message ?: "切换目录规则失败",
                )
            }
        }
    }

    /**
     * 多规则目录重扫：规则写入成功后重新识别当前流式 TXT。
     */
    fun rescanTxtToc(
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
        activeBookIdProvider: () -> String,
        bookId: String,
        filePath: String,
        profileKeyHint: String,
        rulesRepository: RulesRepository,
    ) {
        txtScanJob?.cancel()
        txtScanJob = scope.launch(dispatcher) {
            var requestId = 0L
            var activeKey: String? = null
            try {
                if (activeBookIdProvider() != bookId) return@launch
                val snapshot = rulesRepository.observe(bookId).first()
                val profile = RuleEngine.tocProfile(snapshot.effectiveToc)
                if (profile.key != profileKeyHint) return@launch

                activeKey = profile.key
                requestId = tokenCoordinator.begin(bookId, profile.key)
                _txtRuleScanStatus.value = TxtRuleScanStatus.Running(bookId, profile.key, progress = null)
                val file = File(filePath)
                val monitor = object : TxtScanMonitor {
                    override fun isCancelled(): Boolean =
                        !tokenCoordinator.isCurrent(bookId, profile.key, requestId)

                    override fun onProgress(fraction: Float?) {
                        if (tokenCoordinator.isCurrent(bookId, profile.key, requestId)) {
                            _txtRuleScanStatus.value = TxtRuleScanStatus.Running(bookId, profile.key, fraction)
                        }
                    }
                }
                val newIndex = txtFileScannerProfile(file, profile, monitor)
                if (!tokenCoordinator.isCurrent(bookId, profile.key, requestId)) return@launch
                val newDocument = PlainTextDocument.fromFileIndex(file, newIndex)
                if (!tokenCoordinator.isCurrent(bookId, profile.key, requestId)) return@launch
                _txtRuleScanResult.value = TxtRuleScanResult(
                    bookId = bookId,
                    ruleId = profile.key,
                    requestId = requestId,
                    fileIndex = newIndex,
                    document = newDocument,
                )
                _txtRuleScanStatus.value = TxtRuleScanStatus.Completed(bookId, profile.key, newIndex.chapters.size)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                val key = activeKey ?: return@launch
                if (!tokenCoordinator.isCurrent(bookId, key, requestId)) return@launch
                _txtRuleScanResult.value = TxtRuleScanResult(
                    bookId = bookId,
                    ruleId = key,
                    requestId = requestId,
                    error = e.message ?: "切换目录规则失败",
                )
                _txtRuleScanStatus.value = TxtRuleScanStatus.Failed(
                    bookId = bookId,
                    ruleId = key,
                    message = e.message ?: "切换目录规则失败",
                )
            }
        }
    }

    /**
     * 显式取消当前 TXT 规则扫描。
     */
    fun cancelTxtTocScan() {
        val inFlight = txtScanJob?.isActive == true
        txtScanJob?.cancel()
        txtScanJob = null
        val active = tokenCoordinator.invalidate()
        _txtRuleScanStatus.value = if (inFlight && active != null) {
            TxtRuleScanStatus.Cancelled(active.first, active.second)
        } else {
            TxtRuleScanStatus.Idle
        }
    }

    /**
     * ViewModel 销毁时清理。
     */
    fun clear() {
        txtScanJob?.cancel()
        txtScanJob = null
    }
}
