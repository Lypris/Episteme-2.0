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

import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.hasReadingPositionForSync

data class DashboardBooks(
    val continuing: List<RecentFileItem> = emptyList(),
    val suggested: List<RecentFileItem> = emptyList(),
    val added: List<RecentFileItem> = emptyList(),
)

internal fun dashboardBooks(
    books: List<RecentFileItem>,
    addedAt: Map<String, Long>,
): DashboardBooks {
    val available = books.filter { !it.isDeleted && it.isAvailable }
    val read = available.filter { it.hasReadingPositionForSync() }
        .sortedByDescending { maxOf(it.readingPositionModifiedTimestamp, it.timestamp) }
    val continuing = read.filter { (it.progressPercentage ?: 0f) < 100f }.take(12)
    fun authors(book: RecentFileItem) = listOfNotNull(book.author, book.writer, book.penciller)
        .flatMap { it.split(';') }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
    val authorCounts = read.flatMap(::authors).groupingBy { it }.eachCount()
    val recentAuthors = read.take(5).flatMap(::authors).toSet()
    val seriesCounts = read.mapNotNull { it.seriesName?.lowercase()?.takeIf(String::isNotBlank) }
        .groupingBy { it }.eachCount()
    val tagCounts = read.flatMap { it.tags }.groupingBy { it.id }.eachCount()
    val suggested = available.asSequence()
        .filter { !it.hasReadingPositionForSync() }
        .map { book ->
            val score = authors(book).sumOf { (authorCounts[it] ?: 0).coerceAtMost(10) + if (it in recentAuthors) 12 else 0 } +
                (seriesCounts[book.seriesName?.lowercase()] ?: 0).coerceAtMost(10) * 3 +
                book.tags.sumOf { (tagCounts[it.id] ?: 0).coerceAtMost(5) }
            book to score
        }
        .filter { it.second > 0 }
        .sortedWith(compareByDescending<Pair<RecentFileItem, Int>> { it.second }
            .thenBy { it.first.seriesIndex ?: Double.MAX_VALUE }.thenBy { it.first.bookId })
        .take(12).map { it.first }.toList()
    return DashboardBooks(
        continuing, suggested,
        available.sortedByDescending { addedAt[it.bookId] ?: it.timestamp }.take(18),
    )
}

