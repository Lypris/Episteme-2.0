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

import org.junit.Assert.*
import org.junit.Test

class BedethequeParserTest {
    @Test fun seriesCaptionRecoversUnescapedQuotesInLiveAttributes() {
        val html = """<h1><a>Buck Danny</a></h1><ul class="gallery-couv-large"><li>
            <a href="/BD-Buck-Danny-Tome-15-Test-32191.html" title="Voir la fiche Album de Buck Danny -15- "NC-22654" ne répond plus">
            <img src="/cover.jpg"></a><div class="sous-titre"><b>Tome 15</b> - &quot;NC-22654&quot; ne répond plus</div></li></ul>"""
        val album = BedethequeParser.albums(html, BedethequeParser.BASE).single()
        assertEquals("Buck Danny", album.series)
        assertEquals("15", album.tome)
        assertEquals("\"NC-22654\" ne répond plus", album.title)
    }
    @Test fun searchPreservesSpecialNumberSuffix() {
        val html = """<ul class="search-list"><li><a href="/BD-Test-123.html">
            <span class="serie">Astérix</span><span class="num">#32</span>
            <span class="numa">Pub</span><span class="titre">Publicité</span></a></li></ul>"""
        assertEquals("32Pub", BedethequeParser.search(html, BedethequeParser.BASE).single().tome)
    }
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/bedetheque/$name.html")).readText()
    private val albumUrl = "https://www.bedetheque.com/BD-Asterix-Tome-25-Le-Grand-Fosse-100.html"

    @Test fun isbnResultsUseCurrentDomAndDeduplicateAlbumIds() {
        val hits = BedethequeParser.search(fixture("search_isbn_asterix25"), BedethequeParser.BASE)
        assertEquals(1, hits.size)
        assertEquals("100", BedethequeParser.albumId(hits.single().bedethequeUrl))
        assertEquals("Astérix", hits.single().series)
        assertEquals("25", hits.single().tome)
        assertTrue(hits.single().coverUrl.contains("Couv_100"))
    }

    @Test fun seriesSearchHasRealTitlesAndCovers() {
        val hits = BedethequeParser.search(fixture("search_buckdanny"), BedethequeParser.BASE)
        assertTrue(hits.size > 20)
        assertTrue(hits.any { it.series.contains("Buck Danny") })
        assertTrue(hits.all { it.title.isNotBlank() && it.coverUrl.startsWith("https://") })
    }

    @Test fun albumMetadataIncludesRolesIsbnPriceAndSeries() {
        val album = requireNotNull(BedethequeParser.album(fixture("album_asterix25"), albumUrl))
        assertEquals("Le Grand Fossé", album.title)
        assertEquals("Astérix", album.series)
        assertEquals("25", album.tome)
        assertEquals("Albert Uderzo", album.writer)
        assertEquals("Albert Uderzo", album.penciller)
        assertEquals("", album.colorist)
        assertEquals("Les Éditions Albert René", album.publisher)
        assertEquals("9782864970002", album.isbn)
        assertEquals("44", album.pages)
        assertEquals("04/1980", album.date)
        assertEquals("de 5 à 10 euros", album.price)
        assertTrue(album.coverUrl.contains("Couv_100"))
        assertEquals("https://www.bedetheque.com/serie-59-BD-Asterix.html", album.seriesUrl)
    }

    @Test fun seriesAndAlbumsUseCurrentDom() {
        val series = requireNotNull(BedethequeParser.series(fixture("series_asterix"),
            "https://www.bedetheque.com/serie-59-BD-Asterix.html"))
        assertEquals("Humour", series.genre)
        assertEquals("https://www.bedetheque.com/albums-59-BD-Asterix.html", series.albumsUrl)
        val albums = BedethequeParser.albums(fixture("albums_asterix"), series.albumsUrl)
        assertTrue(albums.size >= 40)
        assertTrue(albums.any { it.tome == "25" && it.title == "Le Grand Fossé" })
    }

    @Test fun malformedPagesAreEmptyAndExternalLinksAreExcluded() {
        assertNull(BedethequeParser.album("<html>Unavailable</html>", albumUrl))
        assertTrue(BedethequeParser.search("<html>Unavailable</html>", BedethequeParser.BASE).isEmpty())
        assertFalse(BedethequeParser.trustedUrl("https://www.bedetheque.com.evil.test/BD-Test-1.html"))
        assertFalse(BedethequeParser.trustedUrl("http://www.bedetheque.com/BD-Test-1.html"))
    }

    @Test fun tenDigitIsbnIsConvertedToThirteenDigitSearch() {
        assertEquals("9782864970002", isbnQuery("2-86497-000-7"))
        assertEquals("9782864970002", isbnQuery("9782864970002"))
        assertNull(isbnQuery("Buck Danny"))
    }

    @Test fun storyboardMergesWithWriterAndMicrodataCanUseContent() {
        val html = """<input id="IdAlbum" value="1"><input id="AltTitle" value="Series -2- Title">
            <ul class="infos-albums">
            <li><label>Scénario :</label><a href="/auteur-1">Doe, Jane</a></li>
            <li><label>Storyboard :</label><a href="/auteur-2">Smith, John</a></li>
            <li><label>Encrage :</label><a href="/auteur-3">Doe, Jane</a></li></ul>
            <meta itemprop="ratingValue" content="4.5">"""
        val album = requireNotNull(BedethequeParser.album(html, albumUrl))
        assertEquals("Jane Doe, John Smith", album.writer)
        assertEquals("Jane Doe", album.inker)
        assertEquals("4.5", album.rating)
        assertEquals("Title", album.title)
    }
}
