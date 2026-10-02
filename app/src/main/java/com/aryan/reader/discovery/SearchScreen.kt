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
package com.aryan.reader.discovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aryan.reader.MainViewModel
import com.aryan.reader.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(mainViewModel: MainViewModel, model: DiscoveryViewModel) {
    val showScanner by model.showScanner.collectAsStateWithLifecycle()
    if (showScanner) {
        BarcodeScanner(onDismiss = { model.showScanner.value = false }, onIsbn = model::scannedIsbn)
    }
    val showWishlist by model.showWishlist.collectAsStateWithLifecycle()
    if (showWishlist) {
        WishlistScreen(model)
        AlbumDetailSheet(model, mainViewModel::onRecentFileClicked)
        return
    }
    val query by model.query.collectAsStateWithLifecycle()
    val local by model.localResults.collectAsStateWithLifecycle()
    val online by model.online.collectAsStateWithLifecycle()
    val remote by model.remoteResults.collectAsStateWithLifecycle()
    val ownedAlbums by model.ownedSearchAlbums.collectAsStateWithLifecycle()
    val onlineTab by model.onlineTab.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_search)) }, actions = {
                IconButton(onClick = { model.showWishlist.value = true }) {
                    Icon(Icons.Default.Star, stringResource(R.string.search_wishlist))
                }
            })
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(query, { model.query.value = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.search_hint)) }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { model.query.value = "" }) {
                            Icon(Icons.Default.Close, stringResource(R.string.search_clear))
                        }
                    })
            }
            item { TextButton(onClick = { model.showScanner.value = true }) { Text(stringResource(R.string.search_scan)) } }
            item {
                TabRow(selectedTabIndex = if (onlineTab) 1 else 0) {
                    Tab(selected = !onlineTab, onClick = { model.onlineTab.value = false },
                        text = { Text(stringResource(R.string.search_local) + " (${local.size})") })
                    Tab(selected = onlineTab, onClick = { model.onlineTab.value = true },
                        text = { Text(stringResource(R.string.search_bedetheque) + " (${remote.albums.size})") })
                }
            }
            if (!online) item { Text(stringResource(R.string.search_offline), color = MaterialTheme.colorScheme.error) }
            if (!onlineTab && query.isNotBlank() && local.isEmpty()) item { Text(stringResource(R.string.search_no_results)) }
            items(if (onlineTab) emptyList() else local, key = { it.bookId }) { book ->
                Row(Modifier.fillMaxWidth().clickable { mainViewModel.onRecentFileClicked(book) }.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(book.coverImagePath, null, Modifier.size(60.dp, 84.dp), contentScale = ContentScale.Fit)
                    Column(Modifier.weight(1f)) {
                        Text(book.customName ?: book.title ?: book.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium)
                        Text(listOfNotNull(book.seriesName, book.author).joinToString(" · "), maxLines = 2,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (onlineTab) {
                if (remote.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (remote.failed) item {
                    Text(stringResource(R.string.search_failed))
                    TextButton(onClick = model::retrySearch) { Text(stringResource(R.string.search_retry)) }
                }
                if (online && query.isNotBlank() && !remote.loading && !remote.failed && remote.albums.isEmpty()) {
                    item { Text(stringResource(R.string.search_no_results)) }
                }
                items(ownedAlbums, key = { it.album.bedethequeUrl }) { hit ->
                    AlbumRow(hit.album, owned = hit.localBook != null) { model.selectAlbum(hit.album) }
                }
            }
        }
    }
    AlbumDetailSheet(model, mainViewModel::onRecentFileClicked)
}

@Composable
internal fun AlbumRow(album: ComicAlbum, owned: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AsyncImage(album.coverUrl, null, Modifier.size(80.dp, 112.dp), contentScale = ContentScale.Fit)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(listOf(album.series, album.tome).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.labelLarge)
            Text(album.title, style = MaterialTheme.typography.titleMedium)
            if (owned) OwnershipBadge()
            if (album.authors.isNotBlank()) Text(album.authors, style = MaterialTheme.typography.bodyMedium)
            Text(listOf(album.publisher, album.date).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun OwnershipBadge() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Check, null, Modifier.size(16.dp))
            Text(stringResource(R.string.comic_owned), style = MaterialTheme.typography.labelMedium)
        }
    }
}
