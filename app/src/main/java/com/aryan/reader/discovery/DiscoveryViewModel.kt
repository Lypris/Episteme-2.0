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

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.AppDatabase
import com.aryan.reader.data.WishlistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.text.Normalizer

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class DiscoveryViewModel(application: Application) : AndroidViewModel(application) {
    val query = MutableStateFlow("")
    val showWishlist = MutableStateFlow(false)
    val showScanner = MutableStateFlow(false)
    private val books = MutableStateFlow<List<RecentFileItem>>(emptyList())
    private val connectivity = application.getSystemService(ConnectivityManager::class.java)
    private val client = BedethequeClient(java.io.File(application.cacheDir, "bedetheque"))
    private val wishlistDao = AppDatabase.getDatabase(application).wishlistDao()
    val wishlist = wishlistDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val wishlistBusy = MutableStateFlow(false)
    val wishlistFailed = MutableStateFlow(false)
    private val retry = MutableStateFlow(0)
    val onlineTab = MutableStateFlow(false)
    val selectedAlbum = MutableStateFlow<ComicAlbum?>(null)
    val detailLoading = MutableStateFlow(false)
    val detailFailed = MutableStateFlow(false)
    val seriesAlbums = MutableStateFlow<List<ComicAlbum>>(emptyList())
    private var detailJob: Job? = null
    private var lastAutomaticIsbn: String? = null
    private val ownership = books.map(ComicOwnershipIndex::build).flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ComicOwnershipIndex.Empty)
    internal val selectedLocalBook = combine(ownership, selectedAlbum) { index, album ->
        album?.let { OwnedAlbum(it, index.matchingBook(it)) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    internal val ownedSeriesAlbums = combine(ownership, seriesAlbums) { index, albums ->
        albums.map { OwnedAlbum(it, index.matchingBook(it)) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    internal val seriesOwnership = combine(ownership, seriesAlbums) { index, albums -> index.summary(albums) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SeriesOwnership())

    val online = callbackFlow {
        fun connected(): Boolean = connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } == true
        fun publish() { trySend(connected()) }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publish()
            override fun onLost(network: Network) = publish()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = publish()
        }
        connectivity.registerDefaultNetworkCallback(callback)
        publish()
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val debouncedQuery = query.debounce(300).map(String::trim).distinctUntilChanged()
    val remoteResults = combine(debouncedQuery, online, retry) { text, connected, _ -> text to connected }
        .flatMapLatest { (text, connected) ->
            flow {
                if (text.isBlank() || !connected) { emit(OnlineResults()); return@flow }
                emit(OnlineResults(loading = true))
                try {
                    val albums = client.search(text)
                    emit(OnlineResults(albums = albums))
                    val isbn = isbnQuery(text)
                    if (isbn != null && isbn != lastAutomaticIsbn && query.value.trim() == text) {
                        onlineTab.value = true
                        albums.firstOrNull()?.let { lastAutomaticIsbn = isbn; selectAlbum(it) }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    timber.log.Timber.tag("BDtheque").w(error, "Search failed")
                    emit(OnlineResults(failed = true))
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OnlineResults())
    val localResults = combine(books, debouncedQuery) { library, text ->
        val words = normalized(text).split(' ').filter(String::isNotBlank)
        if (words.isEmpty()) emptyList() else library.asSequence().filter { book ->
            val haystack = normalized(listOfNotNull(book.title, book.customName, book.displayName,
                book.author, book.writer, book.penciller, book.seriesName).joinToString(" "))
            !book.isDeleted && words.all { it in haystack }
        }.take(100).toList()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    internal val ownedSearchAlbums = combine(ownership, remoteResults) { index, results ->
        results.albums.map { OwnedAlbum(it, index.matchingBook(it)) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateBooks(value: List<RecentFileItem>) { books.value = value }
    fun toggleWishlist(album: ComicAlbum) {
        if (wishlistBusy.value) return
        val id = BedethequeParser.albumId(album.bedethequeUrl) ?: album.bedethequeUrl
        val exists = wishlist.value.any { it.id == id }
        wishlistBusy.value = true
        viewModelScope.launch {
            try {
                wishlistFailed.value = false
                if (exists) wishlistDao.remove(id) else wishlistDao.add(WishlistEntity.fromAlbum(album))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { wishlistFailed.value = true }
            finally { wishlistBusy.value = false }
        }
    }
    fun removeWishlist(item: WishlistEntity) {
        viewModelScope.launch {
            try { wishlistDao.remove(item.id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { wishlistFailed.value = true }
        }
    }
    fun openWishlistItem(item: WishlistEntity) {
        detailJob?.cancel()
        detailLoading.value = false
        detailFailed.value = false
        seriesAlbums.value = emptyList()
        selectedAlbum.value = item.toAlbum()
    }
    fun retrySearch() { retry.value++ }
    fun scannedIsbn(isbn: String) {
        lastAutomaticIsbn = null
        showScanner.value = false
        onlineTab.value = true
        query.value = isbn
        retrySearch()
    }
    fun selectAlbum(hit: ComicAlbum) {
        detailJob?.cancel()
        selectedAlbum.value = hit
        seriesAlbums.value = emptyList()
        detailFailed.value = false
        detailJob = viewModelScope.launch {
            detailLoading.value = true
            try {
                val album = client.album(hit.bedethequeUrl)
                if (album == null) { detailFailed.value = true; return@launch }
                selectedAlbum.value = album
                if (album.seriesUrl.isNotBlank()) {
                    val series = client.series(album.seriesUrl)
                    if (series != null) selectedAlbum.value = album.copy(
                        genre = album.genre.ifBlank { series.genre },
                        summary = album.summary.ifBlank { series.summary },
                    )
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                timber.log.Timber.tag("BDtheque").w(error, "Album failed")
                detailFailed.value = true
            }
            finally { detailLoading.value = false }
        }
    }
    fun loadSeries() {
        val album = selectedAlbum.value ?: return
        if (album.seriesUrl.isBlank()) return
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            detailLoading.value = true
            detailFailed.value = false
            try {
                val series = client.series(album.seriesUrl)
                if (series != null) seriesAlbums.value = client.albums(series.albumsUrl)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { detailFailed.value = true }
            finally { detailLoading.value = false }
        }
    }
    fun dismissDetail() { detailJob?.cancel(); selectedAlbum.value = null }
    fun open(action: String) {
        showWishlist.value = action == "wishlist"
        showScanner.value = action == "scan"
    }
}

data class OnlineResults(val albums: List<ComicAlbum> = emptyList(), val loading: Boolean = false, val failed: Boolean = false)

internal fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase()
