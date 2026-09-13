package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.layout.adaptivePageMetrics
import com.creationreadingassistant.ui.theme.LocalComponentSpec

// ===================== 底部弹层：书籍详情 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookDetailSheet(
    book: BookEntity,
    progressById: Map<String, ReadingProgressEntity>,
    sessions: List<ReadingSessionEntity>,
    notes: List<NoteEntity>,
    highlights: List<HighlightEntity>,
    inspirations: List<InspirationEntity>,
    shelves: List<ShelfEntity>,
    categories: List<CategoryEntity>,
    allTags: List<TagEntity>,
    assignedTagIds: List<String>,
    onDismiss: () -> Unit,
    onContinue: (BookEntity) -> Unit,
    onDownload: (BookEntity) -> Unit,
    onDelete: (String) -> Unit,
    onMessage: (String) -> Unit,
    onRemoveShelf: (String) -> Unit,
    onRemoveCategory: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onUpdateBook: (String, String?, String?) -> Unit,
    onChangeCover: () -> Unit,
    onChangeTextCover: () -> Unit,
    onResetCover: () -> Unit,
) {
    val layout = LocalLayoutTokens.current
    val percent = progressFor(progressById, book.id)
    val readiness = book.readiness()
    val progress = progressById[book.id]

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = layout.contentMaxWidth,
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val adaptive = adaptivePageMetrics(maxWidth, layout)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = adaptive.horizontalPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp),
            ) {
                // 1. 书籍元数据卡片
                BookDetailHeaderSection(
                    book = book,
                    percent = percent,
                    readiness = readiness,
                    onUpdateBook = onUpdateBook,
                    onChangeCover = onChangeCover,
                    onChangeTextCover = onChangeTextCover,
                    onResetCover = onResetCover,
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 2. 行为按钮（CTA重塑）
                BookDetailCtaSection(
                    book = book,
                    percent = percent,
                    readiness = readiness,
                    onContinue = onContinue,
                    onDownload = onDownload,
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 3. 阅读统计（4列独立微彩底座矩阵）
                BookDetailStatsSection(
                    progress = progress,
                    sessions = sessions,
                    percent = percent,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 4. 阅读记录（折叠微岛，解决失控倾泻）
                BookDetailSessionsSection(sessions = sessions)

                Spacer(modifier = Modifier.height(12.dp))

                // 5. 书签与笔记
                BookDetailNotesSection(notes = notes, highlights = highlights)

                Spacer(modifier = Modifier.height(12.dp))

                // 6. 灵感
                BookDetailInspirationsSection(inspirations = inspirations)

                Spacer(modifier = Modifier.height(12.dp))

                // 7. 文件信息
                BookDetailFileInfoSection(book = book)

                Spacer(modifier = Modifier.height(12.dp))

                // 8. 所在书单
                BookDetailShelvesSection(shelves = shelves, onRemoveShelf = onRemoveShelf)

                Spacer(modifier = Modifier.height(12.dp))

                // 9. 所属分类
                BookDetailCategoriesSection(categories = categories, onRemoveCategory = onRemoveCategory)

                Spacer(modifier = Modifier.height(12.dp))

                // 10. 书籍标签
                BookDetailTagsSection(
                    allTags = allTags,
                    assignedTagIds = assignedTagIds,
                    onAddTag = onAddTag,
                    onRemoveTag = onRemoveTag,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 11. 删除本书
                BookDetailDeleteSection(book = book, onDelete = onDelete)

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
