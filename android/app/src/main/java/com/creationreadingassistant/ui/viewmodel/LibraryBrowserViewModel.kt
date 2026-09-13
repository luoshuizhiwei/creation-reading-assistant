package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.feature.library.LibraryEntry
import com.creationreadingassistant.feature.library.LibraryListing
import com.creationreadingassistant.feature.library.LibraryListingIssue
import com.creationreadingassistant.feature.library.LibraryRoot
import com.creationreadingassistant.feature.library.LibraryRootStore
import com.creationreadingassistant.feature.library.LibrarySortMode
import com.creationreadingassistant.feature.library.LibrarySource
import com.creationreadingassistant.feature.library.LibrarySourceIndex
import com.creationreadingassistant.feature.library.LibrarySourceRef
import com.creationreadingassistant.feature.library.RecognitionEvent
import com.creationreadingassistant.feature.library.RecognitionRequest
import com.creationreadingassistant.feature.library.RecognizedBookCandidate
import com.creationreadingassistant.feature.library.SmartBookRecognizer
import com.creationreadingassistant.feature.library.sourceKeyOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the in-app source-library browser and the smart-recognition result view.
 *
 * Responsibilities are deliberately narrow: this ViewModel reads the configured root, lists one
 * folder at a time, keeps page-local browsing state (breadcrumb, search, selection) and drives the
 * recognition flow. It never imports, copies or deletes anything — ticking files hands their URIs
 * to the shared `ShelfViewModel`, so the existing importer stays the only Module that writes the
 * shelf and the private copies.
 *
 * The source index ([LibrarySourceIndex]) is read-only here: this ViewModel refreshes the
 * observation columns on explicit user actions (open page, refresh, run recognition) and never
 * polls. Browsing state is page-local; only the root, last folder and sort order are persisted.
 */
@HiltViewModel
class LibraryBrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rootStore: LibraryRootStore,
    private val librarySource: LibrarySource,
    private val recognizer: SmartBookRecognizer,
    private val sourceIndex: LibrarySourceIndex,
    bookRepository: BookRepository,
) : ViewModel() {

    private data class BrowserCore(
        val crumbs: List<LibraryCrumb> = emptyList(),
        val directories: List<LibraryEntry> = emptyList(),
        val files: List<LibraryEntry> = emptyList(),
        val selection: Set<String> = emptySet(),
        val query: String = "",
        val isLoading: Boolean = false,
        val truncated: Boolean = false,
        val unreadable: Boolean = false,
    )

    private val core = MutableStateFlow(BrowserCore())

    /** 书架快照：在册书籍 id（过滤软删除后的在册集合）与标题（弱判定兜底）。 */
    private data class ShelfBooksSnapshot(val ids: Set<String>, val titles: Set<String>)

    private val shelfBooks: StateFlow<ShelfBooksSnapshot> = bookRepository.observeBooks()
        .map { books ->
            ShelfBooksSnapshot(
                ids = books.map { it.id }.toSet(),
                titles = books.mapNotNull { it.title.trim().lowercase().takeIf(String::isNotEmpty) }.toSet(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShelfBooksSnapshot(emptySet(), emptySet()))

    /** 来源引用快照；由 [refreshSourceIndex] 在显式刷新点更新，绝不轮询。 */
    private val sourceRefs = MutableStateFlow<List<LibrarySourceRef>>(emptyList())

    val uiState: StateFlow<LibraryBrowserUiState> = combine(
        rootStore.root,
        core,
        shelfBooks,
        sourceRefs,
    ) { root, snapshot, books, refs ->
        val query = snapshot.query.trim()
        LibraryBrowserUiState(
            hasRoot = root != null,
            rootName = root?.displayName.orEmpty(),
            crumbs = snapshot.crumbs,
            directories = snapshot.directories,
            files = snapshot.files
                .filter { query.isEmpty() || it.displayName.contains(query, ignoreCase = true) }
                .map { entry ->
                    LibraryFileRow(
                        entry = entry,
                        shelfMatch = LibraryBrowserPolicy.shelfMatch(
                            authority = runCatching { entry.uri.authority }.getOrNull(),
                            documentId = entry.documentId,
                            displayName = entry.displayName,
                            format = LibraryBrowserPolicy.formatOf(entry.displayName),
                            sizeBytes = entry.sizeBytes,
                            lastModifiedMillis = entry.lastModifiedMillis,
                            fingerprint = null,
                            refs = refs,
                            liveBookIds = books.ids,
                            shelfTitles = books.titles,
                        ),
                    )
                },
            selection = snapshot.selection,
            query = snapshot.query,
            sortMode = root?.sortMode ?: LibrarySortMode.NAME,
            isLoading = snapshot.isLoading,
            truncated = snapshot.truncated,
            unreadable = snapshot.unreadable,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryBrowserUiState())

    private val recognitionCore = MutableStateFlow(LibraryRecognitionUiState())

    val recognition: StateFlow<LibraryRecognitionUiState> = combine(
        recognitionCore,
        shelfBooks,
        sourceRefs,
    ) { state, books, refs ->
        state.copy(
            rows = state.rows.map { row ->
                row.copy(shelfMatch = matchForCandidate(row.candidate, refs, books))
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryRecognitionUiState())

    private val _message = MutableStateFlow<LibraryMessage?>(null)
    val message: StateFlow<LibraryMessage?> = _message

    private var lastLoadedTreeUri: String? = null
    private var recognitionJob: Job? = null

    init {
        viewModelScope.launch {
            rootStore.root.collect { root ->
                when {
                    root == null -> {
                        lastLoadedTreeUri = null
                        core.value = BrowserCore()
                    }
                    // 排序变化也会发射新值；只有换了树才需要重建面包屑。
                    root.treeUri.toString() != lastLoadedTreeUri -> {
                        lastLoadedTreeUri = root.treeUri.toString()
                        restoreLocation(root)
                    }
                }
            }
        }
    }

    // ===== 根目录授权 =====

    /**
     * Persists the picked folder after confirming the read grant.
     *
     * `takePersistableUriPermission` is what makes the root survive a process restart, so a failure
     * is reported instead of saving a root the app could not read after the next launch.
     */
    fun saveRoot(treeUri: Uri) {
        viewModelScope.launch {
            val granted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.isSuccess
            if (!granted) {
                _message.value = LibraryMessage.ROOT_UNREADABLE
                return@launch
            }
            rootStore.saveRoot(treeUri, resolveDisplayName(treeUri))
            _message.value = LibraryMessage.ROOT_SAVED
        }
    }

    fun clearRoot() {
        viewModelScope.launch {
            rootStore.clearRoot()
            _message.value = LibraryMessage.ROOT_CLEARED
        }
    }

    // ===== 目录浏览 =====

    fun reload() {
        viewModelScope.launch { loadDirectory(currentDocumentId()) }
    }

    fun openDirectory(documentId: String, displayName: String) {
        val index = core.value.crumbs.indexOfFirst { it.documentId == documentId }
        if (index >= 0) {
            navigateToCrumb(index)
            return
        }
        viewModelScope.launch {
            core.update {
                it.copy(crumbs = it.crumbs + LibraryCrumb(documentId, displayName), selection = emptySet())
            }
            rootStore.setLastLocation(documentId)
            loadDirectory(documentId)
        }
    }

    fun navigateToCrumb(index: Int) {
        val crumbs = core.value.crumbs
        if (index !in crumbs.indices) return
        val target = crumbs[index]
        viewModelScope.launch {
            core.update { it.copy(crumbs = crumbs.take(index + 1), selection = emptySet()) }
            rootStore.setLastLocation(target.documentId)
            loadDirectory(target.documentId)
        }
    }

    fun goUp() {
        val crumbs = core.value.crumbs
        if (crumbs.size > 1) navigateToCrumb(crumbs.size - 2)
    }

    fun setQuery(value: String) {
        core.update { it.copy(query = value) }
    }

    fun setSortMode(mode: LibrarySortMode) {
        viewModelScope.launch {
            rootStore.setSortMode(mode)
            loadDirectory(currentDocumentId())
        }
    }

    // ===== 选择 =====

    fun toggleFile(documentId: String) {
        core.update { snapshot ->
            val selection = if (documentId in snapshot.selection) {
                snapshot.selection - documentId
            } else {
                snapshot.selection + documentId
            }
            snapshot.copy(selection = selection)
        }
    }

    fun selectAllVisible() {
        core.update { it.copy(selection = it.files.map(LibraryEntry::documentId).toSet()) }
    }

    fun invertSelection() {
        core.update { snapshot ->
            val visible = snapshot.files.map(LibraryEntry::documentId).toSet()
            snapshot.copy(selection = visible - snapshot.selection)
        }
    }

    fun clearSelection() {
        core.update { it.copy(selection = emptySet()) }
    }

    /** URIs of the ticked files, ready to hand to the shelf importer. */
    fun selectedUris(): List<Uri> {
        val selection = core.value.selection
        return core.value.files.filter { it.documentId in selection }.map { it.uri }
    }

    // ===== 智能识别 =====

    fun startRecognition() {
        val root = rootStore.root.value ?: return
        recognitionJob?.cancel()
        recognitionCore.value = LibraryRecognitionUiState(isRunning = true)
        recognitionJob = viewModelScope.launch {
            refreshSourceIndex()
            try {
                recognizer.recognize(RecognitionRequest(root)).collect { event ->
                    when (event) {
                        is RecognitionEvent.Warning -> recognitionCore.update {
                            it.copy(warnings = it.warnings + event.message)
                        }
                        is RecognitionEvent.CandidateClassified -> recognitionCore.update { state ->
                            val candidate: RecognizedBookCandidate = event.candidate
                            // 分层判定取快照；书架后续变化不会回头改写用户已经确认过的选择，
                            // 需要时由「只选推荐」重新收敛。
                            val alreadyOnShelf = matchForCandidate(candidate, sourceRefs.value, shelfBooks.value)
                            state.copy(
                                rows = state.rows + LibraryRecognitionRow(candidate, shelfMatch = alreadyOnShelf),
                                selected = if (LibraryBrowserPolicy.selectedByDefault(candidate.decision, alreadyOnShelf)) {
                                    state.selected + candidate.uri.toString()
                                } else {
                                    state.selected
                                },
                            )
                        }
                        is RecognitionEvent.Progress -> recognitionCore.update { it.copy(summary = event.summary) }
                        is RecognitionEvent.Completed -> {
                            recognitionCore.update {
                                it.copy(summary = event.summary, isRunning = false, finished = true)
                            }
                            // 完整（未截断）扫描后才允许下「来源失效」结论；截断的扫描只见部分树。
                            reconcileAfterScan(allowMissing = !event.summary.truncated)
                        }
                        else -> Unit
                    }
                }
            } catch (error: CancellationException) {
                // 取消是用户意图，不是失败；让 finally 收敛运行态。
                throw error
            } finally {
                recognitionCore.update { it.copy(isRunning = false) }
            }
        }
    }

    fun stopRecognition() {
        recognitionJob?.cancel()
        recognitionJob = null
        recognitionCore.update { it.copy(isRunning = false) }
    }

    fun toggleRecognitionCandidate(key: String) {
        recognitionCore.update { state ->
            val selected = if (key in state.selected) state.selected - key else state.selected + key
            state.copy(selected = selected)
        }
    }

    /** 默认只勾选「推荐且未入架」，与方案 6.3 一致。 */
    fun selectRecommended() {
        recognitionCore.update { state ->
            state.copy(
                selected = state.rows
                    .filter { LibraryBrowserPolicy.selectedByDefault(it.candidate.decision, it.shelfMatch) }
                    .map(LibraryRecognitionRow::key)
                    .toSet(),
            )
        }
    }

    fun clearRecognitionSelection() {
        recognitionCore.update { it.copy(selected = emptySet()) }
    }

    /** URIs of the ticked recognition candidates, ready to hand to the shelf importer. */
    fun selectedRecognitionUris(): List<Uri> {
        val state = recognitionCore.value
        return state.rows.filter { it.key in state.selected }.map { it.candidate.uri }
    }

    fun reportEmptySelection() {
        _message.value = LibraryMessage.NOTHING_SELECTED
    }

    fun reportImportStarted() {
        _message.value = LibraryMessage.IMPORT_STARTED
    }

    fun consumeMessage() {
        _message.value = null
    }

    // ===== 内部 =====

    private fun currentDocumentId(): String? = core.value.crumbs.lastOrNull()?.documentId

    private suspend fun loadDirectory(documentId: String?) {
        val root = rootStore.root.value
        if (root == null) {
            core.value = BrowserCore()
            return
        }
        core.update { it.copy(isLoading = true) }
        val listing = try {
            librarySource.list(root, documentId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            core.update {
                it.copy(
                    isLoading = false,
                    unreadable = true,
                    directories = emptyList(),
                    files = emptyList(),
                    truncated = false,
                    selection = emptySet(),
                )
            }
            return
        }
        val unreadable = listing.issue == LibraryListingIssue.UNREADABLE
        core.update { snapshot ->
            snapshot.copy(
                isLoading = false,
                directories = listing.entries.filter { it.isDirectory },
                files = listing.entries.filterNot { it.isDirectory },
                truncated = listing.truncated,
                unreadable = unreadable,
                // 换目录后旧选择属于另一棵子树；不可读时同样必须失效。
                selection = if (unreadable) emptySet() else snapshot.selection,
            )
        }
        observeListing(root, listing)
    }

    /**
     * 轻量观测：目录列成功后把本目录看到的来源引用标记为仍可见（刷新 last_seen_at、
     * 翻回 available、能证明归属时回填 root_id）。只更新观测列，绝不标记 missing ——
     * 单目录列表无法证明文件不在树的其他位置。失败静默：观测不是浏览的前提。
     */
    private suspend fun observeListing(root: LibraryRoot, listing: LibraryListing) {
        val authority = runCatching { root.treeUri.authority }.getOrNull() ?: return
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(root.treeUri) }.getOrNull()
        val seenIds = listing.entries.filterNot { it.isDirectory }.map { it.documentId }
        runCatching {
            sourceIndex.markObserved(authority, rootId, seenIds, System.currentTimeMillis())
        }
        refreshSourceIndex()
    }

    private suspend fun refreshSourceIndex() {
        sourceRefs.value = runCatching { sourceIndex.snapshot() }.getOrDefault(emptyList())
    }

    /**
     * 识别完成后的对账。只有完整（未截断）扫描才允许把「同根、此前可见、本次未出现」
     * 的来源标记为来源失效（方案 §8.3 的来源状态展示）；截断扫描只做观测刷新。
     */
    private suspend fun reconcileAfterScan(allowMissing: Boolean) {
        val root = rootStore.root.value ?: return
        val authority = runCatching { root.treeUri.authority }.getOrNull() ?: return
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(root.treeUri) }.getOrNull()
        val seenIds = recognitionCore.value.rows.mapNotNull { row ->
            sourceKeyOf(row.candidate.uri).documentId
        }
        runCatching {
            sourceIndex.reconcileAfterScan(authority, rootId, seenIds, System.currentTimeMillis(), allowMissing)
        }
        refreshSourceIndex()
    }

    private fun matchForCandidate(
        candidate: RecognizedBookCandidate,
        refs: List<LibrarySourceRef>,
        books: ShelfBooksSnapshot,
    ): LibraryShelfMatch? {
        val key = sourceKeyOf(candidate.uri)
        return LibraryBrowserPolicy.shelfMatch(
            authority = key.authority,
            documentId = key.documentId,
            displayName = candidate.displayName,
            format = candidate.format,
            sizeBytes = candidate.sizeBytes,
            lastModifiedMillis = null,
            fingerprint = candidate.fingerprint,
            refs = refs,
            liveBookIds = books.ids,
            shelfTitles = books.titles,
        )
    }

    /**
     * Reopens the folder the user last browsed.
     *
     * Only a direct child of the root is restored: the store keeps a single document ID, so a
     * deeper location cannot be re-derived without inventing breadcrumb segments the user never
     * walked. A stale ID is cleared rather than silently kept.
     */
    private suspend fun restoreLocation(root: LibraryRoot) {
        val rootCrumb = LibraryCrumb(null, root.displayName)
        val target = root.lastLocationDocumentId
        if (target == null) {
            core.value = BrowserCore(crumbs = listOf(rootCrumb))
            loadDirectory(null)
            return
        }
        val direct = try {
            librarySource.list(root, null)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
        val hit = direct?.entries?.firstOrNull { it.isDirectory && it.documentId == target }
        if (hit == null) {
            rootStore.setLastLocation(null)
            core.value = BrowserCore(crumbs = listOf(rootCrumb))
            loadDirectory(null)
            return
        }
        core.value = BrowserCore(crumbs = listOf(rootCrumb, LibraryCrumb(hit.documentId, hit.displayName)))
        loadDirectory(hit.documentId)
    }

    private fun resolveDisplayName(treeUri: Uri): String {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return DEFAULT_ROOT_NAME
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val name = runCatching {
            context.contentResolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
            }
        }.getOrNull()
        return name?.trim().takeUnless { it.isNullOrEmpty() } ?: DEFAULT_ROOT_NAME
    }

    private companion object {
        const val DEFAULT_ROOT_NAME = "书籍目录"
    }
}
