package com.aryan.reader

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Xml
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveException
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/** shiroikuma-custom: ComicRack's ComicInfo.xml metadata for comic archives
 *  (CBZ/CBR/CB7/CBT), read live from the file. */
data class ComicInfo(
    val title: String? = null,
    val series: String? = null,
    val number: String? = null,
    val year: String? = null,
    val month: String? = null,
    val day: String? = null,
    val writer: String? = null,
    val penciller: String? = null,
    val colorist: String? = null,
    val genre: String? = null,
    val format: String? = null,
    val ageRating: String? = null,
    val pageCount: String? = null,
    val publisher: String? = null,
    val languageISO: String? = null,
    val summary: String? = null,
    val characters: String? = null,
    val teams: String? = null,
    val locations: String? = null
) {
    /** Numeric sorting only; special codes remain available in Number verbatim. */
    val integralSeriesIndex: Double?
        get() = number?.trim()?.takeIf { it.matches(Regex("[0-9]+")) }
            ?.toIntOrNull()?.takeIf { it in 0 until 1000 }?.toDouble()

    /** Publication date rebuilt from Year/Month/Day as an ISO string. */
    val publicationDate: String?
        get() = when {
            year.isNullOrBlank() -> null
            month.isNullOrBlank() -> year
            day.isNullOrBlank() -> "$year-$month"
            else -> "$year-$month-$day"
        }

    val hasAny: Boolean
        get() = listOf(
            title, series, writer, penciller, colorist, genre, format,
            ageRating, pageCount, publisher, languageISO, summary
        ).any { !it.isNullOrBlank() }
}

/** shiroikuma-custom: reads ComicInfo.xml from a comic archive (ZIP-based CBZ/CB7/CBT
 *  via ZipInputStream, RAR-based CBR via libarchive) and parses its fields. */
object ComicInfoExtractor {

    fun extract(uri: Uri, context: Context): ComicInfo? {
        return runCatching {
            // Local SAF documents are seekable. Read the ZIP directory instead of inflating
            // every page when ComicInfo.xml sits at the end of a large archive.
            val direct = runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    java.util.zip.ZipFile("/proc/self/fd/${descriptor.fd}").use { zip ->
                        val entry = zip.entries().asSequence().firstOrNull {
                            !it.isDirectory && it.name.equals("ComicInfo.xml", ignoreCase = true)
                        }
                        entry?.let { zip.getInputStream(it).use { stream ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (output.size() < 64 * 1024) {
                                val count = stream.read(buffer, 0, minOf(buffer.size, 64 * 1024 - output.size()))
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                            output.toString(Charsets.UTF_8.name())
                        } } ?: ""
                    }
                }
            }.getOrNull()
            if (direct != null) return direct.takeIf { it.isNotBlank() }?.let(::parse)
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val xml = input.use { readFromZip(it) ?: readFromRar(uri, context) } ?: return null
            parse(xml)
        }.getOrNull()
    }

    private fun readFromZip(input: java.io.InputStream): String? {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return null
                try {
                    if (!entry.isDirectory && entry.name.equals("ComicInfo.xml", ignoreCase = true)) {
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (total < 64 * 1024) {
                            val count = zip.read(buffer, 0, minOf(buffer.size, 64 * 1024 - total))
                            if (count <= 0) break
                            out.write(buffer, 0, count)
                            total += count
                        }
                        return out.toString(Charsets.UTF_8.name())
                    }
                } finally {
                    zip.closeEntry()
                }
            }
        }
    }

    private fun readFromRar(uri: Uri, context: Context): String? {
        val cacheFile = File(context.cacheDir, "temp_comic_${UUID.randomUUID()}.cbr")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            var archive = 0L
            try {
                archive = Archive.readNew()
                Archive.readSupportFilterAll(archive)
                Archive.readSupportFormatAll(archive)
                Archive.readOpenFileName(archive, cacheFile.absolutePath.toByteArray(), 10240)
                while (true) {
                    val entry = try {
                        Archive.readNextHeader(archive)
                    } catch (e: ArchiveException) {
                        if (e.code == Archive.ERRNO_EOF) break
                        throw e
                    }
                    if (entry == 0L) break
                    val path = ArchiveEntry.pathnameUtf8(entry)
                    if (path != null && path.equals("ComicInfo.xml", ignoreCase = true)) {
                        val extractedFile = File(context.cacheDir, "comicinfo_${UUID.randomUUID()}.xml")
                        try {
                            var pfd: ParcelFileDescriptor? = null
                            try {
                                pfd = ParcelFileDescriptor.open(
                                    extractedFile,
                                    ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
                                )
                                Archive.readDataIntoFd(archive, pfd.fd)
                            } finally {
                                pfd?.close()
                            }
                            return extractedFile.readText(Charsets.UTF_8).takeIf { it.length <= 64 * 1024 }
                        } finally {
                            runCatching { extractedFile.delete() }
                        }
                    } else {
                        Archive.readDataSkip(archive)
                    }
                }
            } finally {
                if (archive != 0L) Archive.readFree(archive)
            }
        } catch (e: Exception) {
            Timber.tag("ComicInfoExtractor").d(e, "Failed to read ComicInfo.xml from ${cacheFile.name}")
        } finally {
            runCatching { cacheFile.delete() }
        }
        return null
    }

    private fun parse(xml: String): ComicInfo {
        val fields = mutableMapOf<String, String>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(xml.reader())
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val name = parser.name
                    if (name != null && name != "ComicInfo" && name != "Pages" && name != "Page") {
                        val text = runCatching { parser.nextText() }.getOrNull()?.trim()
                        if (!text.isNullOrEmpty()) fields[name] = text
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            // Malformed ComicInfo.xml — return whatever was collected.
        }
        return ComicInfo(
            title = fields["Title"],
            series = fields["Series"],
            number = fields["Number"],
            year = fields["Year"],
            month = fields["Month"],
            day = fields["Day"],
            writer = fields["Writer"],
            penciller = fields["Penciller"],
            colorist = fields["Colorist"],
            genre = fields["Genre"],
            format = fields["Format"],
            ageRating = fields["AgeRating"],
            pageCount = fields["PageCount"],
            publisher = fields["Publisher"],
            languageISO = fields["LanguageISO"],
            summary = fields["Summary"],
            characters = fields["Characters"],
            teams = fields["Teams"],
            locations = fields["Locations"]
        )
    }
}
