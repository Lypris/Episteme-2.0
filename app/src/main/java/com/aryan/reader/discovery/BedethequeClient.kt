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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class BedethequeClient(private val cacheDirectory: File) {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).followRedirects(false).build()
    private val mutex = Mutex()
    private val memory = object : LinkedHashMap<String, String>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 24
    }
    private var lastRequest = 0L

    suspend fun search(query: String): List<ComicAlbum> = withContext(Dispatchers.IO) {
        val isbn = isbnQuery(query)
        val parameters = if (isbn != null) listOf("RechISBN" to isbn)
            else listOf("RechSerie" to query, "RechTitre" to query)
        val hits = mutableListOf<ComicAlbum>()
        for ((key, value) in parameters) {
            val url = "${BedethequeParser.BASE}/search/albums?$key=${URLEncoder.encode(value, "UTF-8")}"
            hits += BedethequeParser.search(fetch(url), url)
        }
        hits.distinctBy { BedethequeParser.albumId(it.bedethequeUrl) }.take(100)
    }

    suspend fun album(url: String): ComicAlbum? = withContext(Dispatchers.IO) { BedethequeParser.album(fetch(url), url) }
    suspend fun series(url: String): ComicSeries? = withContext(Dispatchers.IO) { BedethequeParser.series(fetch(url), url) }
    suspend fun albums(url: String): List<ComicAlbum> = withContext(Dispatchers.IO) { BedethequeParser.albums(fetch(url), url) }

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        require(BedethequeParser.trustedUrl(url))
        mutex.withLock {
            memory[url]?.let { return@withLock it }
            cacheDirectory.mkdirs()
            val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
            val cache = File(cacheDirectory, "$name.html")
            if (cache.exists() && System.currentTimeMillis() - cache.lastModified() < 7 * 24 * 60 * 60 * 1000L) {
                return@withLock cache.readText().also { memory[url] = it }
            }
            val wait = 400 - (System.nanoTime() - lastRequest) / 1_000_000
            if (wait > 0) delay(wait)
            currentCoroutineContext().ensureActive()
            try {
                val request = Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8")
                    .header("Referer", "${BedethequeParser.BASE}/search").build()
                val html = http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("BDtheque HTTP ${response.code} (${response.header("Server")})")
                    response.body?.string() ?: throw IOException("Empty BDtheque response")
                }
                currentCoroutineContext().ensureActive()
                if (!html.contains("<html", ignoreCase = true) || html.contains("cf-chl-")) throw IOException("BDtheque challenge")
                memory[url] = html
                runCatching {
                    cache.writeText(html)
                    cacheDirectory.listFiles()?.sortedByDescending { it.lastModified() }?.drop(64)?.forEach { it.delete() }
                }
                html
            } finally {
                lastRequest = System.nanoTime()
            }
        }
    }
}

internal fun isbnQuery(text: String): String? {
    val compact = text.replace(Regex("[\\s-]"), "").uppercase()
    if (compact.matches(Regex("[0-9]{13}"))) return compact
    if (!compact.matches(Regex("[0-9]{9}[0-9X]"))) return null
    val base = "978" + compact.take(9)
    val sum = base.mapIndexed { index, c -> c.digitToInt() * if (index % 2 == 0) 1 else 3 }.sum()
    return base + ((10 - sum % 10) % 10)
}
