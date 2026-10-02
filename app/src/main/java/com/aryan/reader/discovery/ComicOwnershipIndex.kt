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

import com.aryan.reader.FileType
import com.aryan.reader.data.RecentFileItem
import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale

internal fun normalizedSeries(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

internal fun normalizedTome(value: String): String? {
    val code = value.trim().lowercase(Locale.ROOT).removePrefix("#")
        .replace(Regex("^t\\s*(?=[0-9])"), "").replace(Regex("\\s+"), "")
    val match = Regex("^([0-9]+)([a-z]*|[']*)$").matchEntire(code)
    if (match != null) {
        val number = match.groupValues[1].toIntOrNull() ?: return null
        return number.toString() + match.groupValues[2]
    }
    return code.takeIf { it.matches(Regex("[a-z]+[0-9]*[a-z]*")) }
        ?.replace(Regex("[0-9]+")) { it.value.trimStart('0').ifEmpty { "0" } }
}

internal data class ComicKey(val series: String, val tome: String)
internal data class FilenameComic(val series: String, val tome: String, val title: String)
internal data class SeriesOwnership(val owned: Int = 0, val total: Int = 0)
internal data class OwnedAlbum(val album: ComicAlbum, val localBook: RecentFileItem?)

internal class ComicOwnershipIndex private constructor(
    private val byKey: Map<ComicKey, List<RecentFileItem>>,
    private val titles: Map<String, String>,
) {
    val ownedSeries: Map<String, Set<String>> = byKey.keys.groupBy { it.series }
        .mapValues { (_, keys) -> keys.mapTo(mutableSetOf()) { it.tome } }

    fun matchingBook(album: ComicAlbum): RecentFileItem? {
        val key = key(album.series, album.tome) ?: return null
        val candidates = byKey[key].orEmpty()
        // HS/TL/Pub can identify several different books within one series.
        if (key.tome.all(Char::isLetter)) {
            val title = normalizedSeries(album.title)
            return candidates.firstOrNull { title.isNotEmpty() && titles[it.bookId] == title }
        }
        return candidates.firstOrNull()
    }

    fun isOwned(album: ComicAlbum): Boolean = matchingBook(album) != null

    fun summary(albums: List<ComicAlbum>): SeriesOwnership {
        val volumes = albums.mapNotNull { album -> key(album.series, album.tome)?.let { it to album } }
            .groupBy({ it.first }, { it.second })
        return SeriesOwnership(volumes.count { (_, editions) -> editions.any(::isOwned) }, volumes.size)
    }

    companion object {
        private val comicTypes = setOf(FileType.CBZ, FileType.CBR, FileType.CB7, FileType.CBT)
        private val fullName = Regex("^(.+?)\\s+-\\s+(T?[0-9]+[A-Za-z]*|HS|TL|Pub)\\s+-\\s+(.+)$", RegexOption.IGNORE_CASE)
        private val shortName = Regex("^(T[0-9]+[A-Za-z]*|HS|TL|Pub)\\s+-\\s+(.+)$", RegexOption.IGNORE_CASE)
        val Empty = ComicOwnershipIndex(emptyMap(), emptyMap())

        private fun key(series: String, tome: String): ComicKey? {
            val name = normalizedSeries(series).takeIf(String::isNotBlank) ?: return null
            return normalizedTome(tome)?.let { ComicKey(name, it) }
        }

        internal fun filename(book: RecentFileItem): FilenameComic? {
            if (book.type !in comicTypes) return null
            val name = book.displayName.replace(Regex("\\.(cbz|cbr|cb7|cbt)$", RegexOption.IGNORE_CASE), "")
            fullName.matchEntire(name)?.let {
                return FilenameComic(it.groupValues[1], it.groupValues[2], cleanTitle(it.groupValues[3]))
            }
            val short = shortName.matchEntire(name) ?: return null
            val path = runCatching { URLDecoder.decode(book.uriString.orEmpty().replace("+", "%2B"), "UTF-8") }.getOrNull()
                ?: return null
            val folder = path.substringBeforeLast('/', "").substringAfterLast('/').substringAfterLast(':')
            if (normalizedSeries(folder) in setOf("", "bd", "books", "comics", "hors serie", "integrales")) return null
            return FilenameComic(folder, short.groupValues[1], cleanTitle(short.groupValues[2]))
        }

        private fun cleanTitle(value: String): String = value.replace(Regex("(\\s+\\([^()]*\\))+$"), "").trim()

        fun build(books: List<RecentFileItem>): ComicOwnershipIndex {
            val index = mutableMapOf<ComicKey, MutableList<RecentFileItem>>()
            val titles = mutableMapOf<String, String>()
            books.asSequence().filter { !it.isDeleted && it.isAvailable && !it.uriString.isNullOrBlank() }
                .sortedWith(compareByDescending<RecentFileItem> { it.readingPositionModifiedTimestamp }.thenBy { it.bookId })
                .forEach { book ->
                    val fallback = filename(book)
                    val series = book.seriesName?.takeIf(String::isNotBlank) ?: fallback?.series ?: return@forEach
                    val number = book.seriesNumber?.takeIf(String::isNotBlank)
                        ?: fallback?.takeIf { normalizedSeries(it.series) == normalizedSeries(series) }?.tome
                        ?: book.seriesIndex?.takeIf { it.isFinite() && it >= 0 && it < 1000 && it % 1.0 == 0.0 }?.toInt()?.toString()
                        ?: return@forEach
                    val key = key(series, number) ?: return@forEach
                    index.getOrPut(key) { mutableListOf() }.add(book)
                    val title = book.title?.takeIf { it.isNotBlank() && it != book.displayName }
                        ?: fallback?.title.orEmpty()
                    titles[book.bookId] = normalizedSeries(title)
                }
            return ComicOwnershipIndex(index, titles)
        }
    }
}
