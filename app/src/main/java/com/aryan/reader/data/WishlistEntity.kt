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
package com.aryan.reader.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.aryan.reader.discovery.BedethequeParser
import com.aryan.reader.discovery.ComicAlbum
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "wishlist")
data class WishlistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val series: String,
    val tome: String,
    val authors: String,
    val publisher: String,
    val date: String,
    val isbn: String,
    val coverUrl: String,
    val summary: String,
    val price: String,
    val bedethequeUrl: String,
    val addedAt: Long,
    val metadataJson: String,
) {
    fun toAlbum(): ComicAlbum = runCatching { Json.decodeFromString<ComicAlbum>(metadataJson) }.getOrElse {
        ComicAlbum(bedethequeUrl, title, series, tome, writer = authors, publisher = publisher,
            date = date, isbn = isbn, coverUrl = coverUrl, summary = summary, price = price)
    }

    companion object {
        fun fromAlbum(album: ComicAlbum) = WishlistEntity(
            id = BedethequeParser.albumId(album.bedethequeUrl) ?: album.bedethequeUrl,
            title = album.title, series = album.series, tome = album.tome,
            authors = album.authors, publisher = album.publisher, date = album.date, isbn = album.isbn,
            coverUrl = album.coverUrl, summary = album.summary, price = album.price,
            bedethequeUrl = album.bedethequeUrl, addedAt = System.currentTimeMillis(),
            metadataJson = Json.encodeToString(album),
        )
    }
}

@Dao
interface WishlistDao {
    @Query("SELECT * FROM wishlist ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WishlistEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(item: WishlistEntity)

    @Query("DELETE FROM wishlist WHERE id = :id")
    suspend fun remove(id: String)
}

