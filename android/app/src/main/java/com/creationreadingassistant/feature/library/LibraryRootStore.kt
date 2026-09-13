package com.creationreadingassistant.feature.library

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.creationreadingassistant.data.local.CoroutineScopeModule.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.libraryRootDataStore by preferencesDataStore(name = "library_root")

private val KEY_TREE_URI = stringPreferencesKey("tree_uri")
private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
private val KEY_LAST_LOCATION_DOCUMENT_ID = stringPreferencesKey("last_location_document_id")
private val KEY_SORT_MODE = stringPreferencesKey("sort_mode")

/** The sort choices shared by the future directory browser and its saved state. */
enum class LibrarySortMode(val storageValue: String) {
    NAME("name"),
    MODIFIED_DESC("modified_desc"),
    SIZE_DESC("size_desc"),
    ;

    companion object {
        fun fromStorage(value: String?): LibrarySortMode =
            entries.firstOrNull { it.storageValue == value } ?: NAME
    }
}

/**
 * The one configured, user-authorized local-library root.
 *
 * `treeUri` is deliberately retained as a URI rather than converted into a filesystem path:
 * the SAF provider remains the authority for both access and navigation.
 */
data class LibraryRoot(
    val treeUri: Uri,
    val displayName: String,
    val lastLocationDocumentId: String?,
    val sortMode: LibrarySortMode,
)

/**
 * Persists the narrow amount of state needed to reopen an app-managed source library.
 *
 * This Module never takes or releases SAF permissions. The picker Adapter owns permission
 * changes; callers must save a root only after its read grant has been confirmed. Keeping that
 * side effect outside this Module makes configuration recovery testable and prevents a stale
 * DataStore value from being mistaken for access permission.
 */
@Singleton
class LibraryRootStore @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val dataStore = context.libraryRootDataStore

    val root: StateFlow<LibraryRoot?> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::toLibraryRoot)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Synchronously reads the configured root straight from DataStore.
     *
     * [root] is `WhileSubscribed`, so its value is only trustworthy while someone collects it
     * (the browser ViewModel). Importers that need the root once per batch must use this instead
     * of reading the StateFlow, which can be a stale `null` when nothing subscribes.
     */
    suspend fun loadRoot(): LibraryRoot? = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::toLibraryRoot)
        .first()

    /**
     * Replaces the configured root and resets the last browsed child, which belongs to the
     * previous tree and must never be reused for a different SAF provider or directory.
     */
    suspend fun saveRoot(treeUri: Uri, displayName: String) {
        require(treeUri.scheme == ContentResolver.SCHEME_CONTENT) {
            "书籍目录必须是 SAF content URI"
        }
        val normalizedName = displayName.trim().ifBlank { "书籍目录" }
        dataStore.edit { preferences ->
            preferences[KEY_TREE_URI] = treeUri.toString()
            preferences[KEY_DISPLAY_NAME] = normalizedName
            preferences.remove(KEY_LAST_LOCATION_DOCUMENT_ID)
        }
    }

    /** Saves the current folder within the configured root; blank values mean return to root. */
    suspend fun setLastLocation(documentId: String?) {
        dataStore.edit { preferences ->
            if (toContentUri(preferences[KEY_TREE_URI]) == null) return@edit
            val normalized = documentId?.trim().orEmpty()
            if (normalized.isEmpty()) preferences.remove(KEY_LAST_LOCATION_DOCUMENT_ID)
            else preferences[KEY_LAST_LOCATION_DOCUMENT_ID] = normalized
        }
    }

    /** Sort is a browser preference and intentionally survives changing or clearing a root. */
    suspend fun setSortMode(sortMode: LibrarySortMode) {
        dataStore.edit { preferences -> preferences[KEY_SORT_MODE] = sortMode.storageValue }
    }

    /** Removes only root-specific state; it never deletes or alters a user-owned source folder. */
    suspend fun clearRoot() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_TREE_URI)
            preferences.remove(KEY_DISPLAY_NAME)
            preferences.remove(KEY_LAST_LOCATION_DOCUMENT_ID)
        }
    }

    private fun toLibraryRoot(preferences: androidx.datastore.preferences.core.Preferences): LibraryRoot? {
        val treeUri = toContentUri(preferences[KEY_TREE_URI]) ?: return null
        return LibraryRoot(
            treeUri = treeUri,
            displayName = preferences[KEY_DISPLAY_NAME]?.trim().takeUnless { it.isNullOrEmpty() } ?: "书籍目录",
            lastLocationDocumentId = preferences[KEY_LAST_LOCATION_DOCUMENT_ID]?.trim().takeUnless { it.isNullOrEmpty() },
            sortMode = LibrarySortMode.fromStorage(preferences[KEY_SORT_MODE]),
        )
    }

    private fun toContentUri(raw: String?): Uri? = raw
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let(Uri::parse)
        ?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
}
