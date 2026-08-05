package com.creationreadingassistant.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.screen.profile.AboutSubPage
import com.creationreadingassistant.ui.screen.profile.AiSettingsSubPage
import com.creationreadingassistant.ui.screen.profile.AppearanceSubPage
import com.creationreadingassistant.ui.screen.profile.DiagnosticsSubPage
import com.creationreadingassistant.ui.screen.profile.LibrarySubPage
import com.creationreadingassistant.ui.screen.profile.LocalProfileSnackbar
import com.creationreadingassistant.ui.screen.profile.PrivacySubPage
import com.creationreadingassistant.ui.screen.profile.ProfileAction
import com.creationreadingassistant.ui.screen.profile.ProfileHomeScreen
import com.creationreadingassistant.ui.screen.profile.ProfileSubPage
import com.creationreadingassistant.ui.screen.profile.ProfileUiState
import com.creationreadingassistant.ui.screen.profile.ReadingNotesSubPage
import com.creationreadingassistant.ui.screen.profile.ReaderSettingsSubPage
import com.creationreadingassistant.ui.screen.profile.StorageSubPage
import com.creationreadingassistant.ui.screen.profile.SyncSubPage
import com.creationreadingassistant.ui.screen.profile.WebDavSubPage
import com.creationreadingassistant.ui.screen.profile.subPageTitle

/**
 * 「我的」页——薄壳渲染层。
 *
 * 仅负责：AppScreenScaffold 壳 + when(currentSubPage) 路由到首页或子页。
 * 所有副作用 / ViewModel / Snackbar / 导航均在 ProfileRoute 层处理。
 */
@Composable
internal fun ProfileScreen(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var moreExpanded by remember { mutableStateOf(false) }

    AppScreenScaffold(
        modifier = modifier,
        title = if (state.currentSubPage == null) "我的" else subPageTitle(state.currentSubPage),
        navigationIcon = if (state.currentSubPage != null) {
            {
                IconButton(onClick = { onAction(ProfileAction.GoBack) }) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "返回")
                }
            }
        } else {
            null
        },
        actions = if (state.currentSubPage == null) {
            {
                Box {
                    IconButton(onClick = { moreExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("数据备份") },
                            onClick = {
                                moreExpanded = false
                                onAction(ProfileAction.OpenSubPage(ProfileSubPage.STORAGE))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("隐私安全") },
                            onClick = {
                                moreExpanded = false
                                onAction(ProfileAction.OpenSubPage(ProfileSubPage.PRIVACY))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("关于应用") },
                            onClick = {
                                moreExpanded = false
                                onAction(ProfileAction.OpenSubPage(ProfileSubPage.ABOUT))
                            },
                        )
                    }
                }
            }
        } else {
            {}
        },
        snackbarHost = {
            LocalProfileSnackbar.current?.let { SnackbarHost(it) }
        },
    ) { scaffoldPadding ->
        when (state.currentSubPage) {
            null -> ProfileHomeScreen(state, onAction, scaffoldPadding)
            ProfileSubPage.SYNC -> SyncSubPage(state, onAction, scaffoldPadding)
            ProfileSubPage.WEBDAV -> WebDavSubPage(state, onAction, scaffoldPadding)
            ProfileSubPage.APPEARANCE -> AppearanceSubPage(state, onAction, scaffoldPadding)
            ProfileSubPage.READER -> ReaderSettingsSubPage(state, onAction, scaffoldPadding)
            ProfileSubPage.AI -> AiSettingsSubPage(state, onAction, scaffoldPadding)
            ProfileSubPage.TAGS, ProfileSubPage.CATEGORIES, ProfileSubPage.SHELVES ->
                LibrarySubPage(state, onAction, scaffoldPadding, state.currentSubPage!!)
            ProfileSubPage.NOTES ->
                ReadingNotesSubPage(state, onAction, scaffoldPadding, state.currentSubPage!!)
            ProfileSubPage.READING -> {} // navigates to "my-reading", never reaches here
            ProfileSubPage.STORAGE -> StorageSubPage(state, onAction, scaffoldPadding)
            ProfileSubPage.PRIVACY -> PrivacySubPage(state, onAction, scaffoldPadding)
            ProfileSubPage.ABOUT -> AboutSubPage(state, onAction, scaffoldPadding)
            ProfileSubPage.DIAGNOSTICS -> DiagnosticsSubPage(state, onAction, scaffoldPadding)
        }
    }
}
