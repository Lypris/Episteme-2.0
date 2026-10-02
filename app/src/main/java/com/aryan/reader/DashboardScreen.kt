/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.aryan.reader.data.RecentFileItem
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel, navController: NavHostController, onSearch: (String) -> Unit) {
    val books by viewModel.dashboard.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_home)) }, actions = {
                IconButton(onClick = { navController.navigateIfReady(AppDestinations.SETTINGS_SCREEN_ROUTE) }) {
                    Icon(Icons.Default.Settings, stringResource(R.string.unified_library_open_settings))
                }
            })
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(bottom = 24.dp)) {
            item { DashboardRow(R.string.dashboard_continue, books.continuing, true, viewModel::onRecentFileClicked) }
            item { DashboardRow(R.string.dashboard_suggested, books.suggested, false, viewModel::onRecentFileClicked) }
            item { DashboardRow(R.string.dashboard_added, books.added, false, viewModel::onRecentFileClicked) }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.dashboard_quick), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { onSearch("search") }) {
                        Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.nav_search))
                    }
                    TextButton(onClick = { onSearch("scan") }) { Text(stringResource(R.string.search_scan)) }
                    TextButton(onClick = {
                        if (state.syncedFolders.isEmpty()) viewModel.navigateToFolderSync()
                        else viewModel.rescanLibraryForNewBooks()
                    }, enabled = !state.isRefreshing) { Text(stringResource(R.string.dashboard_sync)) }
                    TextButton(onClick = { onSearch("wishlist") }) {
                        Icon(Icons.Default.Star, null); Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.search_wishlist))
                    }
                    state.bannerMessage?.let { Text(it.message) }
                }
            }
        }
    }
}

@Composable
private fun DashboardRow(title: Int, books: List<RecentFileItem>, progress: Boolean, onOpen: (RecentFileItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(title), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge)
        if (books.isEmpty()) {
            Text(stringResource(R.string.dashboard_empty), Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(books, key = { it.bookId }) { book ->
                    Column(Modifier.width(144.dp).clickable { onOpen(book) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(Modifier.fillMaxWidth().height(208.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            AsyncImage(book.coverImagePath, book.title ?: book.displayName,
                                Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                        Text(book.customName ?: book.title ?: book.displayName,
                            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(book.author ?: book.writer.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall)
                        if (progress) {
                            val value = (book.progressPercentage ?: 0f).coerceIn(0f, 100f)
                            LinearProgressIndicator(progress = { value / 100f }, modifier = Modifier.fillMaxWidth())
                            Text(stringResource(R.string.dashboard_progress, value.roundToInt()), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

