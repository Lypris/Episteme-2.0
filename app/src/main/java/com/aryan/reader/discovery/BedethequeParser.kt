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

import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.text.Normalizer

@Serializable
data class ComicAlbum(
    val bedethequeUrl: String,
    val title: String = "",
    val series: String = "",
    val tome: String = "",
    val writer: String = "",
    val penciller: String = "",
    val inker: String = "",
    val colorist: String = "",
    val letterer: String = "",
    val coverArtist: String = "",
    val publisher: String = "",
    val collection: String = "",
    val date: String = "",
    val isbn: String = "",
    val pages: String = "",
    val format: String = "",
    val genre: String = "",
    val rating: String = "",
    val summary: String = "",
    val price: String = "",
    val coverUrl: String = "",
    val seriesUrl: String = "",
) {
    val authors: String get() = listOf(writer, penciller, colorist).filter(String::isNotBlank).distinct().joinToString(", ")
}

data class ComicSeries(val title: String, val genre: String, val summary: String, val albumsUrl: String)

object BedethequeParser {
    const val BASE = "https://www.bedetheque.com"
    private val albumPath = Regex("/BD-.+-([0-9]+)\\.html")
    private val seriesPath = Regex("/serie-([0-9]+)-BD-(.+)\\.html")
    private val altTitle = Regex("^(.*?)\\s+-([^-]+)-\\s+(.+)$")

    fun albumId(url: String): String? = runCatching { albumPath.matchEntire(URI(url).path)?.groupValues?.get(1) }.getOrNull()
    fun trustedUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "www.bedetheque.com" && uri.userInfo == null && uri.port == -1
    }.getOrDefault(false)

    fun search(html: String, url: String): List<ComicAlbum> {
        val doc = Jsoup.parse(html, url)
        val container = doc.selectFirst(".search-list") ?: return emptyList()
        return hits(container)
    }

    fun albums(html: String, url: String): List<ComicAlbum> {
        val doc = Jsoup.parse(html, url)
        val series = doc.selectFirst("h1 a")?.text().orEmpty()
        return doc.selectFirst(".gallery-couv-large")?.let { hits(it, series) }.orEmpty()
    }

    private fun hits(root: Element, series: String = ""): List<ComicAlbum> = root.select("a[href]").mapNotNull { a ->
        val url = a.absUrl("href").substringBefore('#')
        if (!trustedUrl(url) || albumId(url) == null) return@mapNotNull null
        val alternate = a.attr("title").removePrefix("Voir la fiche Album de ").let { altTitle.matchEntire(it) }
        val text = a.text().trim()
        // Some live title/alt attributes contain unescaped quotes. The visible caption
        // remains valid HTML and includes the complete title and number.
        val caption = a.parent()?.selectFirst(".sous-titre")
        val captionTitle = caption?.clone()?.also { it.select("b, .numa-serie").remove() }
            ?.text()?.trim()?.trimStart('-', ' ')?.takeIf(String::isNotBlank)
        val captionNumber = caption?.selectFirst("b")?.text()?.removePrefix("Tome ")?.trim()
        val title = a.selectFirst(".titre")?.text() ?: captionTitle
            ?: alternate?.groupValues?.get(3)
            ?: text.takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        ComicAlbum(
            bedethequeUrl = url, title = title,
            series = a.selectFirst(".serie")?.text() ?: alternate?.groupValues?.get(1) ?: series,
            tome = a.selectFirst(".num")?.text()?.removePrefix("#")?.let {
                it + a.selectFirst(".numa")?.text().orEmpty()
            } ?: alternate?.groupValues?.get(2) ?: captionNumber.orEmpty(),
            date = a.selectFirst(".dl")?.text().orEmpty(),
            coverUrl = a.attr("rel").takeIf { it.startsWith("https://") }
                ?: a.selectFirst("img")?.absUrl("src").orEmpty(),
        )
    }.distinctBy { albumId(it.bedethequeUrl) }

    fun album(html: String, url: String): ComicAlbum? {
        val doc = Jsoup.parse(html, url)
        if (doc.selectFirst("#IdAlbum") == null && doc.selectFirst("ul.infos-albums") == null) return null
        val rows = doc.select("ul.infos-albums > li").filter { it.selectFirst("label") != null }
        fun value(vararg labels: String): String = rows.firstOrNull { row ->
            fold(row.selectFirst("label")!!.text().trim().trimEnd(':').trim()) in labels
        }?.clone()?.also { it.select("label, script, style").remove() }?.text().orEmpty()
        fun role(vararg labels: String): String = rows.filter { row ->
            fold(row.selectFirst("label")!!.text().trim().trimEnd(':').trim()) in labels
        }.flatMap { row -> row.select("a[href*=auteur-]").map { it.text().trim() } }
            .filter { it.isNotBlank() && !it.startsWith("<") }
            .map { name -> if (", " in name) name.substringAfter(", ") + " " + name.substringBefore(", ") else name }
            .distinct().joinToString(", ")
        val alternate = altTitle.matchEntire(doc.selectFirst("#AltTitle")?.attr("value").orEmpty())
        val seriesLink = doc.select("a[href]").firstOrNull {
            trustedUrl(it.absUrl("href")) && seriesPath.matches(URI(it.absUrl("href")).path)
        }
        val price = doc.selectFirst("#prix_bdfugue")?.attr("value").orEmpty()
        return ComicAlbum(
            bedethequeUrl = url,
            title = value("titre").ifBlank { alternate?.groupValues?.get(3).orEmpty() },
            series = value("serie").ifBlank { alternate?.groupValues?.get(1) ?: seriesLink?.text().orEmpty() },
            tome = value("tome").ifBlank { alternate?.groupValues?.get(2).orEmpty() },
            writer = role("scenario", "storyboard"), penciller = role("dessin"), inker = role("encrage"),
            colorist = role("couleurs"), letterer = role("lettrage"), coverArtist = role("couverture"),
            publisher = value("editeur").ifBlank { microdata(doc, "publisher") },
            collection = value("collection"), date = value("depot legal", "parution"),
            isbn = doc.selectFirst("#EAN")?.attr("value").orEmpty()
                .ifBlank { doc.selectFirst("#EANs")?.attr("value").orEmpty().split(';', ',').first() }
                .ifBlank { value("ean/isbn", "isbn") },
            pages = value("planches"), format = value("format"),
            genre = doc.selectFirst(".style")?.text().orEmpty(),
            rating = microdata(doc, "ratingValue"),
            summary = summary(doc),
            price = if (price.isBlank() || price == "0") value("estimation") else price,
            coverUrl = doc.selectFirst("#Couverture")?.attr("value").orEmpty()
                .ifBlank { doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty() }
                .ifBlank { doc.selectFirst("[itemprop=image]")?.absUrl("src").orEmpty() },
            seriesUrl = seriesLink?.absUrl("href").orEmpty(),
        ).takeIf { it.title.isNotBlank() }
    }

    fun series(html: String, url: String): ComicSeries? {
        val match = seriesPath.matchEntire(runCatching { URI(url).path }.getOrNull().orEmpty()) ?: return null
        val doc = Jsoup.parse(html, url)
        return ComicSeries(
            doc.selectFirst("h1")?.text().orEmpty(),
            doc.selectFirst(".style")?.text().orEmpty(), summary(doc),
            "$BASE/albums-${match.groupValues[1]}-BD-${match.groupValues[2]}.html",
        )
    }

    private fun summary(doc: Document) = doc.selectFirst("meta[name=description]")?.attr("content").orEmpty()
        .replace(Regex("^Tout sur la s[eé]rie.*?:\\s*", RegexOption.IGNORE_CASE), "").trim()
    private fun microdata(doc: Document, name: String): String = doc.selectFirst("[itemprop=$name]")?.let {
        it.attr("content").ifBlank { it.selectFirst("[itemprop=name]")?.text() ?: it.text() }
    }.orEmpty()
    private fun fold(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase()
}
