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

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.aryan.reader.discovery.ComicAlbum
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class WishlistMigrationTest {
    @Test fun upgradeFrom25PreservesLibraryAndWishlistSurvivesReopen() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val name = "wishlist-migration-${UUID.randomUUID()}"
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_25_26, AppDatabase.MIGRATION_26_27).allowMainThreadQueries().build()
        val original = open()
        original.openHelper.writableDatabase.execSQL("""
            INSERT INTO recent_files (
                bookId, uriString, type, displayName, timestamp, title, author,
                isRecent, isAvailable, lastModifiedTimestamp, isDeleted, isReflowPreferred,
                fileSize, folderTextMetadataParsed, lastPositionCfi, progressPercentage
            ) VALUES ('owner-book', 'content://library/book', 'EPUB', 'Owner book', 123,
                'Keep this title', 'Keep this author', 1, 1, 123, 0, 0, 456, 1,
                'epubcfi(/6/2!/4/2:12)', 42.5)
        """)
        original.close()
        // The pre-wishlist schema is the current schema with only the new table absent.
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE wishlist")
            it.execSQL("ALTER TABLE recent_files DROP COLUMN seriesNumber")
            it.version = 25
            it.execSQL("UPDATE room_master_table SET identity_hash = 'version-25-before-wishlist'")
        }
        val migrated = open()
        val book = requireNotNull(migrated.recentFileDao().getFileByBookId("owner-book"))
        assertEquals("Keep this title", book.title)
        assertEquals("epubcfi(/6/2!/4/2:12)", book.lastPositionCfi)
        assertEquals(42.5f, book.progressPercentage)
        assertEquals(1, migrated.recentFileDao().count())
        val album = ComicAlbum("https://www.bedetheque.com/BD-Test-100.html", title = "Wishlist title",
            writer = "Author", colorist = "Colorist", genre = "Humour", isbn = "9782864970002")
        val entry = WishlistEntity.fromAlbum(album)
        migrated.wishlistDao().add(entry)
        migrated.wishlistDao().add(entry.copy(addedAt = entry.addedAt + 1))
        migrated.close()

        val reopened = open()
        try {
            val saved = reopened.wishlistDao().observeAll().first().single()
            assertEquals(entry.addedAt, saved.addedAt)
            assertEquals(album, saved.toAlbum())
            assertEquals(1, reopened.recentFileDao().count())
            reopened.wishlistDao().remove(saved.id)
            assertTrue(reopened.wishlistDao().observeAll().first().isEmpty())
            assertEquals(1, reopened.recentFileDao().count())
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }
}
