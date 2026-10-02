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
import org.junit.Assert.*
import org.junit.Test

class ComicOwnershipIndexTest {
    private fun book(series: String? = "Buck Danny", number: String? = "1",
        name: String = "book.cbz", id: String = name) = RecentFileItem(
        id, "content://library/Books/BD/Buck%20Danny/$name", FileType.CBZ, name, 1,
        seriesName = series, seriesNumber = number,
    )
    private fun album(series: String = "Buck Danny", number: String = "1", title: String = "Les japs attaquent") =
        ComicAlbum("https://www.bedetheque.com/BD-Test-1.html", title, series, number)

    @Test fun numericPrefixZerosAndAccentsMatch() {
        assertEquals("1", normalizedTome(" T01 "))
        assertEquals("1", normalizedTome("#001"))
        assertEquals(normalizedSeries("L’Épopée - de la BD"), normalizedSeries("l'epopee, de la bd"))
        val index = ComicOwnershipIndex.build(listOf(book("Astérix", "T01")))
        assertTrue(index.isOwned(album("ASTERIX", "1")))
        assertFalse(index.isOwned(album("Astérix", "2")))
        assertFalse(index.isOwned(album("Astérix (Les)", "1")))
    }

    @Test fun suffixesAndSpecialCodesDoNotCollapse() {
        val index = ComicOwnershipIndex.build(listOf(book(number = "032Pub")))
        assertTrue(index.isOwned(album(number = "32pub")))
        assertFalse(index.isOwned(album(number = "32")))
        assertEquals("tl", normalizedTome("TL"))
        assertEquals("hs", normalizedTome("HS"))
        assertEquals("pub", normalizedTome("Pub"))
        assertEquals("hs1a", normalizedTome("HS01A"))
        assertNull(normalizedTome("1-2"))
        assertNull(normalizedTome(""))
        assertNull(normalizedTome("1.5"))
        assertNotEquals(normalizedTome("2"), normalizedTome("2'"))
    }

    @Test fun filenameAndFolderFallbackAreStrict() {
        val full = book(null, null, "Buck Danny - T01 - Les Japs attaquent (Charlier, Hubinon) (Dupuis).cbz")
        assertTrue(ComicOwnershipIndex.build(listOf(full)).isOwned(album()))
        val short = book(null, null, "T02 - Les mystères de Midway.cbz")
        assertTrue(ComicOwnershipIndex.build(listOf(short)).isOwned(album(number = "2")))
        assertFalse(ComicOwnershipIndex.build(listOf(book(null, null, "Buck Danny 01.cbz"))).isOwned(album()))
    }

    @Test fun embeddedMetadataWinsOverFilenameAndIndex() {
        val local = book("Buck Danny Classic", "2", "Buck Danny - T01 - Title.cbz").copy(seriesIndex = 1.0)
        val index = ComicOwnershipIndex.build(listOf(local))
        assertFalse(index.isOwned(album()))
        assertFalse(index.isOwned(album("Buck Danny Classic", "1")))
        assertTrue(index.isOwned(album("Buck Danny Classic", "2")))
        assertFalse(ComicOwnershipIndex.build(listOf(book(number = "1-2").copy(seriesIndex = 1.0))).isOwned(album()))
    }

    @Test fun missingDeletedAndRemoteBooksCannotBeOpened() {
        for (local in listOf(book().copy(isDeleted = true), book().copy(isAvailable = false), book().copy(uriString = null))) {
            assertFalse(ComicOwnershipIndex.build(listOf(local)).isOwned(album()))
        }
    }

    @Test fun genericSpecialCodesRequireSameTitle() {
        val local = book(number = "HS", name = "Buck Danny - HS - Alerte nucléaire (Bergèse) (Dupuis).cbz")
        val index = ComicOwnershipIndex.build(listOf(local))
        assertTrue(index.isOwned(album(number = "HS", title = "Alerte nucléaire")))
        assertFalse(index.isOwned(album(number = "HS", title = "Pilotes de prototypes")))
    }

    @Test fun uniqueVolumesCountOnceAndLocalBookIsReturned() {
        val local = book(id = "first")
        val index = ComicOwnershipIndex.build(listOf(local, book(id = "second"), book(number = "2", id = "two")))
        assertEquals("first", index.matchingBook(album())?.bookId)
        assertEquals(setOf("1", "2"), index.ownedSeries["buck danny"])
        assertEquals(SeriesOwnership(2, 3), index.summary(listOf(album(), album(number = "01"),
            album(number = "2"), album(number = "3"))))
    }

    @Test fun integralLegacySeriesIndexSupportedButFractionsAreNot() {
        assertTrue(ComicOwnershipIndex.build(listOf(book(number = null).copy(seriesIndex = 1.0))).isOwned(album()))
        assertFalse(ComicOwnershipIndex.build(listOf(book(number = null).copy(seriesIndex = 1.5))).isOwned(album()))
        val special = book(number = null, name = "Buck Danny - T32Pub - Title.cbz").copy(seriesIndex = 32.0)
        assertFalse(ComicOwnershipIndex.build(listOf(special)).isOwned(album(number = "32")))
        assertTrue(ComicOwnershipIndex.build(listOf(special)).isOwned(album(number = "32Pub")))
    }
}
