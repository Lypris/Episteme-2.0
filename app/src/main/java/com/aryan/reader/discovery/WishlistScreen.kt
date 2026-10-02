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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aryan.reader.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WishlistScreen(model: DiscoveryViewModel) {
    val items by model.wishlist.collectAsStateWithLifecycle()
    val failed by model.wishlistFailed.collectAsStateWithLifecycle()
    BackHandler { model.showWishlist.value = false }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.search_wishlist)) }, navigationIcon = {
            IconButton(onClick = { model.showWishlist.value = false }) { Icon(Icons.Default.Close, stringResource(R.string.search_close)) }
        })
    }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (failed) item { Text(stringResource(R.string.wishlist_failed), color = MaterialTheme.colorScheme.error) }
            if (items.isEmpty()) item { Text(stringResource(R.string.wishlist_empty)) }
            items(items, key = { it.id }) { entry ->
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { AlbumRow(entry.toAlbum()) { model.openWishlistItem(entry) } }
                    IconButton(onClick = { model.removeWishlist(entry) }) {
                        Icon(Icons.Default.Close, stringResource(R.string.wishlist_remove))
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

