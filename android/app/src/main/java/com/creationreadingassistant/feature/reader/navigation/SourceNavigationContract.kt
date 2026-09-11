package com.creationreadingassistant.feature.reader.navigation

import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.feature.reader.locator.ReaderLocator

/**
 * A navigation position expressed only in source coordinates.
 *
 * Screen/page indexes deliberately do not appear here: they are derived from the current renderer,
 * and cannot be persisted or used to cross between TXT, Markdown, and EPUB rendering modes.
 */
@ConsistentCopyVisibility
data class SourceNavigationTarget internal constructor(
    val bookId: String,
    val locator: ReaderLocator,
)

/**
 * The reader's in-memory navigation state.
 *
 * [normalReading] is the only position eligible for restart recovery. Entries in
 * [temporaryReturnStack] represent one-off inspection hops (for example a note or search result)
 * and intentionally disappear after a restart.
 */
data class SourceNavigationState(
    val active: SourceNavigationTarget? = null,
    val normalReading: SourceNavigationTarget? = null,
    val temporaryReturnStack: List<SourceNavigationTarget> = emptyList(),
)

/**
 * A renderer-independent position resolved from a [SourceNavigationTarget].
 *
 * [absoluteOffset] remains in source space. Chapter fields are present only for chaptered
 * documents and are never display page or LazyList indexes.
 */
data class SourceNavigationPosition(
    val absoluteOffset: Int,
    val chapterIndex: Int? = null,
    val chapterOffset: Int? = null,
)

/**
 * Canonical source-position parsing and temporary-inspection history rules.
 *
 * This is deliberately UI-free. Routes and reader hosts may consume the contract later, but must
 * not reinterpret display offsets as source positions when doing so.
 */
object SourceNavigationContract {

    /**
     * Decodes a persisted reader progress payload. A nested `locator_v2` wins because its offset
     * is globally meaningful; the top-level legacy `offset` may be chapter-local for EPUB.
     * A standalone locator payload is accepted for callers such as annotations.
     */
    fun targetFromStoredLocation(bookId: String?, locationJson: String?): SourceNavigationTarget? {
        val locator = LocatorCodec.locatorFromProgress(locationJson)
            ?: LocatorCodec.decode(locationJson)
        return target(bookId, locator)
    }

    /** Creates a canonical target, rejecting invalid or incomplete source coordinates. */
    fun target(bookId: String?, locator: ReaderLocator?): SourceNavigationTarget? {
        val normalizedBookId = bookId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalizedLocator = locator?.normalizedForSourceNavigation() ?: return null
        return SourceNavigationTarget(normalizedBookId, normalizedLocator)
    }

    /** Resolves a source position for TXT or a canonical Markdown whole-document coordinate. */
    fun resolvePlainPosition(target: SourceNavigationTarget): SourceNavigationPosition? {
        val offset = target.locator.legacyOffset ?: target.locator.charOffset ?: return null
        return SourceNavigationPosition(absoluteOffset = offset)
    }

    /**
     * Resolves an EPUB or chaptered-Markdown source position against the current chapter index.
     *
     * A locator carrying both a global offset and chapter tuple must agree. Treating conflicting
     * historic data as exact would be worse than declining the jump, because the reader could
     * land in a different chapter while still claiming a precise return position.
     */
    fun resolveChapteredPosition(
        target: SourceNavigationTarget,
        chapterStartOffsets: List<Int>,
        preferGlobalOffset: Boolean = false,
    ): SourceNavigationPosition? {
        if (chapterStartOffsets.isEmpty()) return null
        val locator = target.locator
        val explicitChapter = locator.chapterIndex
        val explicitOffset = locator.charOffset
        val chapterIndex = if (preferGlobalOffset && locator.legacyOffset != null) {
            chapterStartOffsets.indexOfLast { it <= locator.legacyOffset }.coerceAtLeast(0)
        } else {
            explicitChapter ?: locator.legacyOffset?.let { absolute ->
                chapterStartOffsets.indexOfLast { it <= absolute }.coerceAtLeast(0)
            }
        } ?: return null
        if (chapterIndex !in chapterStartOffsets.indices) return null

        val chapterStart = chapterStartOffsets[chapterIndex]
        val chapterOffset = if (preferGlobalOffset && locator.legacyOffset != null) {
            locator.legacyOffset - chapterStart
        } else {
            explicitOffset ?: locator.legacyOffset
                ?.minus(chapterStart)
                ?.takeIf { it >= 0 }
                ?: return null
        }
        val absoluteOffset = locator.legacyOffset ?: chapterStart + chapterOffset
        if (absoluteOffset < 0 || chapterOffset < 0) return null
        if (!preferGlobalOffset && explicitChapter != null && explicitOffset != null &&
            locator.legacyOffset != null && chapterStart + explicitOffset != locator.legacyOffset
        ) {
            return null
        }
        return SourceNavigationPosition(
            absoluteOffset = absoluteOffset,
            chapterIndex = chapterIndex,
            chapterOffset = chapterOffset,
        )
    }

    /**
     * Records ordinary reading progress. This is the only operation that changes the restart
     * target, and it ends any temporary inspection chain.
     */
    fun recordNormalReading(
        state: SourceNavigationState,
        target: SourceNavigationTarget,
    ): SourceNavigationState = SourceNavigationState(
        active = target,
        normalReading = target,
    )

    /**
     * Opens [destination] as a temporary inspection. The exact source position currently on
     * screen is pushed onto the return stack; ordinary progress remains untouched.
     *
     * The oldest temporary entry is dropped when [maxTemporaryHistory] is exceeded, so a burst
     * of inspection hops cannot grow unboundedly.
     */
    fun beginTemporaryInspection(
        state: SourceNavigationState,
        destination: SourceNavigationTarget,
        maxTemporaryHistory: Int = DEFAULT_MAX_TEMPORARY_HISTORY,
    ): SourceNavigationState {
        require(maxTemporaryHistory > 0) { "maxTemporaryHistory must be positive" }

        val returnTarget = state.active ?: state.normalReading
        val stack = if (returnTarget == null) {
            state.temporaryReturnStack
        } else {
            (state.temporaryReturnStack + returnTarget).takeLast(maxTemporaryHistory)
        }
        return state.copy(active = destination, temporaryReturnStack = stack)
    }

    /**
     * Returns from one temporary inspection level. If no temporary level remains, return to the
     * durable normal-reading location instead of inventing an approximate fallback.
     */
    fun returnFromTemporaryInspection(state: SourceNavigationState): SourceNavigationState {
        val returnTarget = state.temporaryReturnStack.lastOrNull() ?: state.normalReading
        return state.copy(
            active = returnTarget,
            temporaryReturnStack = state.temporaryReturnStack.dropLastSafely(),
        )
    }

    /** Drops transient inspection state; this models restart recovery without persisting it. */
    fun restoreAfterRestart(state: SourceNavigationState): SourceNavigationState =
        SourceNavigationState(active = state.normalReading, normalReading = state.normalReading)

    private fun ReaderLocator.normalizedForSourceNavigation(): ReaderLocator? {
        val validLegacyOffset = legacyOffset?.takeIf { it >= 0 }
        val hasIncompleteChapterPosition = (chapterIndex == null) != (charOffset == null)
        val validChapterPosition = if (!hasIncompleteChapterPosition &&
            chapterIndex != null &&
            charOffset != null &&
            chapterIndex >= 0 &&
            charOffset >= 0
        ) {
            chapterIndex to charOffset
        } else {
            null
        }

        if (validLegacyOffset == null && validChapterPosition == null) return null
        return ReaderLocator(
            legacyOffset = validLegacyOffset,
            chapterIndex = validChapterPosition?.first,
            charOffset = validChapterPosition?.second,
            excerptFingerprint = excerptFingerprint,
        )
    }

    private fun <T> List<T>.dropLastSafely(): List<T> =
        if (isEmpty()) emptyList() else dropLast(1)

    const val DEFAULT_MAX_TEMPORARY_HISTORY = 8
}
