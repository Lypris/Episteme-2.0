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
import com.aryan.reader.ComicInfo
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
class ComicOwnershipMigrationTest {
    @Test fun migrationBackfillsOnlyComicTextAndPreservesOwnerData() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val name = "comic-migration-${UUID.randomUUID()}"
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_26_27).allowMainThreadQueries().build()
        val original = open()
        for ((id, type) in listOf("comic" to "CBZ", "novel" to "EPUB")) {
            original.openHelper.writableDatabase.execSQL("""
                INSERT INTO recent_files (bookId, type, displayName, timestamp, title,
                    isRecent, isAvailable, lastModifiedTimestamp, isDeleted, isReflowPreferred,
                    fileSize, folderTextMetadataParsed, folderCoverMetadataParsed,
                    sourceFolderUri, coverImagePath, lastPage, progressPercentage,
                    seriesName, originalSeriesName)
                VALUES ('$id', '$type', '$id', 123, 'Keep title', 1, 1, 123, 0, 0, 456,
                    1, 1, 'content://folder', '/cover.jpg', 12, 42.5, 'Edited series', 'Original series')
            """)
        }
        val wish = WishlistEntity.fromAlbum(ComicAlbum("https://www.bedetheque.com/BD-Test-100.html", "Saved"))
        original.wishlistDao().add(wish)
        original.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("ALTER TABLE recent_files DROP COLUMN seriesNumber")
            it.version = 26
            it.execSQL("UPDATE room_master_table SET identity_hash = 'version-26'")
        }
        val db = open()
        try {
            val dao = db.recentFileDao()
            val comic = requireNotNull(dao.getFileByBookId("comic"))
            assertFalse(comic.folderTextMetadataParsed)
            assertTrue(comic.folderCoverMetadataParsed)
            assertEquals("/cover.jpg", comic.coverImagePath)
            assertEquals(12, comic.lastPage)
            assertEquals(42.5f, comic.progressPercentage)
            assertEquals("Keep title", comic.title)
            assertTrue(dao.getFileByBookId("novel")!!.folderTextMetadataParsed)
            assertEquals(listOf("comic"), dao.getFolderBooksNeedingTextMetadata(300).map { it.bookId })
            dao.updateExtractedMetadata("comic", null, null, null, "Embedded series", null,
                null, null, null, null, null, 0, 0, true, true, seriesNumber = "32Pub")
            val saved = dao.getFileByBookId("comic")!!
            assertEquals("Edited series", saved.seriesName)
            assertEquals("32Pub", saved.seriesNumber)
            assertEquals("32Pub", saved.toRecentFileItem().toRecentFileEntity().seriesNumber)
            assertEquals("32Pub", dao.getRecentFiles().first().first { it.bookId == "comic" }.toRecentFileItem().seriesNumber)
            assertEquals(0, dao.countFolderBooksNeedingTextMetadata())
            assertEquals(wish, db.wishlistDao().observeAll().first().single())
            assertEquals(2, dao.count())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun rawNumbersRetainSpecialCodesAndNumericSortIsBounded() {
        assertEquals(1.0, ComicInfo(number = "001").integralSeriesIndex)
        assertEquals(0.0, ComicInfo(number = "0").integralSeriesIndex)
        assertNull(ComicInfo(number = "32Pub").integralSeriesIndex)
        assertNull(ComicInfo(number = "TL").integralSeriesIndex)
        assertNull(ComicInfo(number = "1.5").integralSeriesIndex)
        assertNull(ComicInfo(number = "1000").integralSeriesIndex)
    }
}
