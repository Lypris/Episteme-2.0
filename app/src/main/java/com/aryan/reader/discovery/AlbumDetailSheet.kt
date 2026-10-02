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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aryan.reader.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlbumDetailSheet(model: DiscoveryViewModel, onOpenLocal: (com.aryan.reader.data.RecentFileItem) -> Unit) {
    val album by model.selectedAlbum.collectAsStateWithLifecycle()
    val loading by model.detailLoading.collectAsStateWithLifecycle()
    val failed by model.detailFailed.collectAsStateWithLifecycle()
    val seriesAlbums by model.ownedSeriesAlbums.collectAsStateWithLifecycle()
    val seriesOwnership by model.seriesOwnership.collectAsStateWithLifecycle()
    val localBook by model.selectedLocalBook.collectAsStateWithLifecycle()
    val wishlist by model.wishlist.collectAsStateWithLifecycle()
    val wishlistBusy by model.wishlistBusy.collectAsStateWithLifecycle()
    val wishlistFailed by model.wishlistFailed.collectAsStateWithLifecycle()
    val book = album ?: return
    ModalBottomSheet(onDismissRequest = model::dismissDetail, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.9f), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(listOf(book.series, book.tome).filter(String::isNotBlank).joinToString(" · "),
                        Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = model::dismissDetail) { Icon(Icons.Default.Close, stringResource(R.string.search_close)) }
                }
                Text(book.title, style = MaterialTheme.typography.headlineSmall)
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            localBook?.takeIf { it.album.bedethequeUrl == book.bedethequeUrl }?.localBook?.let { local ->
                item {
                    OwnershipBadge()
                    TextButton(onClick = { model.dismissDetail(); onOpenLocal(local) }) {
                        Text(stringResource(R.string.comic_open_local))
                    }
                }
            }
            if (failed) item {
                Text(stringResource(R.string.search_failed), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { model.selectAlbum(book) }) { Text(stringResource(R.string.search_retry)) }
            }
            item {
                AsyncImage(book.coverUrl, book.title, Modifier.fillMaxWidth().height(260.dp), contentScale = ContentScale.Fit)
            }
            item {
                val saved = wishlist.any { it.id == (BedethequeParser.albumId(book.bedethequeUrl) ?: book.bedethequeUrl) }
                FilledTonalButton(onClick = { model.toggleWishlist(book) }, enabled = !wishlistBusy && !loading) {
                    Icon(Icons.Default.Star, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (saved) R.string.wishlist_remove else R.string.wishlist_add))
                }
                if (wishlistFailed) Text(stringResource(R.string.wishlist_failed), color = MaterialTheme.colorScheme.error)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailField(R.string.comic_writer, book.writer)
                    DetailField(R.string.comic_penciller, book.penciller)
                    DetailField(R.string.comic_inker, book.inker)
                    DetailField(R.string.comic_colorist, book.colorist)
                    DetailField(R.string.comic_letterer, book.letterer)
                    DetailField(R.string.comic_cover_artist, book.coverArtist)
                    DetailField(R.string.comic_publisher, book.publisher)
                    DetailField(R.string.comic_collection, book.collection)
                    DetailField(R.string.comic_date, book.date)
                    DetailField(R.string.comic_isbn, book.isbn)
                    DetailField(R.string.comic_pages, book.pages)
                    DetailField(R.string.comic_format, book.format)
                    DetailField(R.string.comic_genre, book.genre)
                    DetailField(R.string.comic_rating, book.rating)
                    DetailField(R.string.comic_price, book.price)
                }
            }
            if (book.summary.isNotBlank()) item {
                Text(stringResource(R.string.comic_summary), style = MaterialTheme.typography.titleMedium)
                Text(book.summary, style = MaterialTheme.typography.bodyLarge)
            }
            if (book.seriesUrl.isNotBlank()) item {
                TextButton(onClick = model::loadSeries, enabled = !loading) { Text(stringResource(R.string.comic_series_albums)) }
            }
            if (seriesAlbums.isNotEmpty()) item {
                Text(stringResource(R.string.comic_series_owned, seriesOwnership.owned, seriesOwnership.total),
                    style = MaterialTheme.typography.titleMedium)
            }
            items(seriesAlbums, key = { it.album.bedethequeUrl }) { hit ->
                AlbumRow(hit.album, owned = hit.localBook != null) { model.selectAlbum(hit.album) }
            }
        }
    }
}

@Composable
private fun DetailField(label: Int, value: String) {
    if (value.isNotBlank()) {
        Text(stringResource(label) + ": " + value, style = MaterialTheme.typography.bodyMedium)
    }
}
