package com.creationreadingassistant.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.LocalFlatCards
import com.creationreadingassistant.ui.components.LocalSettingsDensity
import com.creationreadingassistant.ui.components.SettingsDensity
import com.creationreadingassistant.ui.layout.CompactSettingsLayoutTokens
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.profile.AboutSubPage
import com.creationreadingassistant.ui.screen.profile.AiSettingsSubPage
import com.creationreadingassistant.ui.screen.profile.AppearanceSubPage
import com.creationreadingassistant.ui.screen.profile.DiagnosticsSubPage
import com.creationreadingassistant.ui.screen.profile.GoalSubPage
import com.creationreadingassistant.ui.screen.profile.LibrarySubPage
import com.creationreadingassistant.ui.screen.profile.LocalProfileSnackbar
import com.creationreadingassistant.ui.screen.profile.PrivacySubPage
import com.creationreadingassistant.ui.screen.profile.ProfileAction
import com.creationreadingassistant.ui.screen.profile.ProfileHomeScreen
import com.creationreadingassistant.ui.screen.profile.ProfileSubPage
import com.creationreadingassistant.ui.screen.profile.ProfileTopBarSearchField
import com.creationreadingassistant.ui.screen.profile.ProfileUiState
import com.creationreadingassistant.ui.screen.profile.ReadingNotesSubPage
import com.creationreadingassistant.ui.screen.profile.ReaderSettingsSubPage
import com.creationreadingassistant.ui.screen.profile.SelectionSubPage
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
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // 离开首页进入子页时自动收起搜索模式
    val isHome = state.currentSubPage == null
    val effectiveSearchActive = isHome && searchActive

    AppScreenScaffold(
        modifier = modifier,
        title = when {
            effectiveSearchActive -> ""
            isHome -> "我的"
            else -> subPageTitle(state.currentSubPage)
        },
        titleContent = if (effectiveSearchActive) {
            {
                ProfileTopBarSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                )
            }
        } else null,
        navigationIcon = when {
            effectiveSearchActive -> {
                {
                    IconButton(onClick = {
                        searchActive = false
                        searchQuery = ""
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "退出搜索")
                    }
                }
            }
            !isHome -> {
                {
                    IconButton(onClick = { onAction(ProfileAction.GoBack) }) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "返回")
                    }
                }
            }
            else -> null
        },
        actions = if (isHome && !effectiveSearchActive) {
            {
                // 右上角搜索图标按钮：轻量不占首页正文空间
                IconButton(onClick = { searchActive = true }) {
                    Icon(Icons.Outlined.Search, contentDescription = "搜索设置项")
                }
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
        compactTopBar = !isHome,
    ) { scaffoldPadding ->
        // 整个「我的」树（首页 + 二级子页）共用同一套紧凑空间令牌、扁平卡片与行密度，
        // 否则首页走 Compact 组件族、子页走 SettingRow 族，两层会长得不一样。
        // 页头仍按是否首页分档：顶级页保留 26sp 展示衬线，二级子页走 18sp 无衬线。
        CompositionLocalProvider(
            LocalLayoutTokens provides CompactSettingsLayoutTokens,
            LocalFlatCards provides true,
            LocalSettingsDensity provides SettingsDensity.COMPACT,
        ) {
            // 局部捕获：when 分支内直接拿到已匹配的非空枚举值，无需强解包
            val subPage = state.currentSubPage
            if (subPage == null) {
                ProfileHomeScreen(
                    state = state,
                    onAction = onAction,
                    scaffoldPadding = scaffoldPadding,
                    searchActive = effectiveSearchActive,
                    searchQuery = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onCloseSearch = {
                        searchActive = false
                        searchQuery = ""
                    },
                )
            } else {
                when (subPage) {
                    ProfileSubPage.SYNC -> SyncSubPage(state, onAction, scaffoldPadding)
                    ProfileSubPage.WEBDAV -> WebDavSubPage(state, onAction, scaffoldPadding)
                    ProfileSubPage.APPEARANCE -> AppearanceSubPage(state, onAction, scaffoldPadding)
                    ProfileSubPage.READER -> ReaderSettingsSubPage(state, onAction, scaffoldPadding)
                    ProfileSubPage.SELECTION -> SelectionSubPage(state, onAction, scaffoldPadding)
                    ProfileSubPage.GOAL -> GoalSubPage(state, onAction, scaffoldPadding)
                    ProfileSubPage.AI -> AiSettingsSubPage(state, onAction, scaffoldPadding)
                    ProfileSubPage.TAGS, ProfileSubPage.CATEGORIES, ProfileSubPage.SHELVES ->
                        LibrarySubPage(state, onAction, scaffoldPadding, subPage)
                    ProfileSubPage.NOTES ->
                        ReadingNotesSubPage(state, onAction, scaffoldPadding, subPage)
                    ProfileSubPage.READING -> {} // navigates to "my-reading", never reaches here
                    ProfileSubPage.STORAGE -> StorageSubPage(state, onAction, scaffoldPadding)
                    ProfileSubPage.PRIVACY -> PrivacySubPage(state, onAction, scaffoldPadding)
                    ProfileSubPage.ABOUT -> AboutSubPage(state, onAction, scaffoldPadding)
                    ProfileSubPage.DIAGNOSTICS -> DiagnosticsSubPage(state, onAction, scaffoldPadding)
                }
            }
        }
    }
}
