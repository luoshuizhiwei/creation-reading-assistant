package com.creationreadingassistant.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.feature.dictionary.DictionaryLookupEntry
import com.creationreadingassistant.feature.dictionary.DictionaryRepository
import com.creationreadingassistant.feature.dictionary.InstalledDictionary
import com.creationreadingassistant.feature.dictionary.StarDictImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 离线词典面板状态（R3-X1）。 */
internal data class DictionaryUiState(
    val word: String = "",
    val loading: Boolean = false,
    /** 是否已经跑过一次查询；用来区分「没查过」与「查了但没有命中」。 */
    val searched: Boolean = false,
    val results: List<DictionaryLookupEntry> = emptyList(),
    val installed: List<InstalledDictionary> = emptyList(),
    /**
     * 本次查询里打开 / 读取失败的词库名。
     *
     * 与「没有命中」是两件事：词库读不上时**不能**告诉用户「这个词不在库里」。
     */
    val unreadableDictionaries: List<String> = emptyList(),
    /** 查询链路本身失败（词库目录不可读、磁盘 IO 异常）；同样不等于「查不到」。 */
    val error: String? = null,
    /** 一次性提示（导入成功/失败、卸载完成），消费后应由 UI 清空。 */
    val message: String? = null,
)

/** 面板顶部的状态副标题（纯函数，JVM 可测）。 */
internal fun dictionaryStatusLine(state: DictionaryUiState): String = when {
    state.loading -> "查询中…"
    state.installed.isEmpty() -> "尚未安装离线词库"
    else -> "已装 ${state.installed.size} 部词库"
}

/**
 * 「没有可展示释义」时要给用户看的说明；有命中或仍在查询中返回 `null`。
 *
 * 这个函数是「不得静默伪造释义」的反面约束：它同时保证**不得静默伪造「查不到」**。
 * 覆盖五种终止态：空选区 / 无词库 / 有词库但查不到 / 词库打不开 / 查询链路失败。
 */
internal fun dictionaryEmptyNotice(state: DictionaryUiState): String? {
    if (state.loading || state.results.isNotEmpty() || !state.searched) return null

    state.error?.let { return it }

    if (state.word.isBlank()) {
        return "没有可查的词。请先在正文里选中要查的文字，再从选区菜单打开词典。"
    }

    if (state.installed.isEmpty()) {
        return "还没装离线词库。导入 StarDict 词库（把 .ifo/.idx/.dict 或 .dict.dz 打成 zip）后即可离线查词。"
    }

    val broken = state.unreadableDictionaries
    if (broken.isNotEmpty()) {
        val detail = broken.joinToString("、")
        val readable = state.installed.size - broken.size
        return if (readable <= 0) {
            "已装的 ${state.installed.size} 部词库这次都没能打开（$detail），" +
                "所以无法确认「${state.word}」是否收录。请重新导入词库。"
        } else {
            "已装 ${state.installed.size} 部词库，其中「$detail」这次没能打开；" +
                "在能读的 $readable 部里没有「${state.word}」。"
        }
    }

    return "已安装的词库里没有「${state.word}」。可以换个词，或在选区菜单把首选词典改为「系统/在线」。"
}

/**
 * 离线词典面板 ViewModel（R3-X1）。
 *
 * 词库为空或查不到时都把**已安装词库列表**一并暴露出去：用户当场就能判断是该导入词库，
 * 还是该换一个词 —— 避免只显示「未找到」而让人以为是功能坏了。
 *
 * 所有仓储调用都经 `runCatching`：`viewModelScope` 里未捕获的异常会直接打穿到默认
 * 异常处理器并**终止进程**，一个损坏的词库文件不该让整个阅读器崩掉。
 */
@HiltViewModel
internal class DictionaryViewModel @Inject constructor(
    private val repository: DictionaryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DictionaryUiState())
    val state: StateFlow<DictionaryUiState> = _state

    private var lastQuery: String? = null

    fun refreshInstalled() {
        viewModelScope.launch {
            runCatching { repository.installed() }
                .onSuccess { installed -> _state.value = _state.value.copy(installed = installed) }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(error = readFailureMessage(throwable))
                }
        }
    }

    fun lookup(word: String) {
        val key = word.trim()
        lastQuery = key.ifEmpty { null }

        if (key.isEmpty()) {
            // 空选区是一个**明确的终止态**，不是「还没查」：直接落到空选区说明，
            // 否则面板会停在既无加载、也无解释的中间态，看起来像坏了。
            _state.value = _state.value.copy(
                word = "",
                loading = false,
                searched = true,
                results = emptyList(),
                unreadableDictionaries = emptyList(),
                error = null,
            )
            return
        }

        _state.value = _state.value.copy(
            word = key,
            loading = true,
            searched = false,
            results = emptyList(),
            unreadableDictionaries = emptyList(),
            error = null,
        )
        viewModelScope.launch {
            runCatching {
                val installed = repository.installed()
                installed to repository.lookup(key)
            }.onSuccess { (installed, outcome) ->
                _state.value = _state.value.copy(
                    loading = false,
                    searched = true,
                    results = outcome.entries,
                    installed = installed,
                    unreadableDictionaries = outcome.unreadableDictionaries,
                    error = null,
                )
            }.onFailure { throwable ->
                _state.value = _state.value.copy(
                    loading = false,
                    searched = true,
                    results = emptyList(),
                    unreadableDictionaries = emptyList(),
                    error = readFailureMessage(throwable),
                )
            }
        }
    }

    fun install(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val result = runCatching { repository.install(uri) }
                .getOrElse { StarDictImporter.Result.Failed(readFailureMessage(it)) }
            val message = when (result) {
                is StarDictImporter.Result.Installed ->
                    "已导入 ${result.dictionaries.joinToString("、") { it.bookName }}"

                StarDictImporter.Result.NotAStarDict ->
                    "压缩包里没有可用的 StarDict 词库（需要同名 .ifo + .idx + .dict 或 .dict.dz）"

                is StarDictImporter.Result.Failed -> "导入失败：${result.reason}"
            }
            _state.value = _state.value.copy(
                loading = false,
                installed = runCatching { repository.installed() }.getOrDefault(_state.value.installed),
                message = message,
            )
            // 导入后把刚才没查到的词再查一次，用户不用手动重试
            lastQuery?.let { lookup(it) }
        }
    }

    fun uninstall(baseName: String) {
        viewModelScope.launch {
            val ok = runCatching { repository.uninstall(baseName) }.getOrDefault(false)
            _state.value = _state.value.copy(
                installed = runCatching { repository.installed() }.getOrDefault(_state.value.installed),
                message = if (ok) "已卸载词库" else "卸载失败",
            )
        }
    }

    fun consumeMessage() {
        if (_state.value.message != null) _state.value = _state.value.copy(message = null)
    }

    /** 用户在面板里手动改查词（例如换个词或拼错后重试）。 */
    fun retry(word: String) = lookup(word)

    private fun readFailureMessage(throwable: Throwable): String =
        "读取离线词库失败（${throwable::class.java.simpleName}），请检查词库目录或重新导入。"
}
