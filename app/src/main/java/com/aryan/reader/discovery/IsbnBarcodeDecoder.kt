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

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

internal class IsbnBarcodeDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.EAN_13), DecodeHintType.TRY_HARDER to true))
    }

    fun decode(bytes: ByteArray, width: Int, height: Int): String? {
        fun read(data: ByteArray, w: Int, h: Int): String? = try {
            val source = PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
                .takeIf { it.length == 13 && (it.startsWith("978") || it.startsWith("979")) }
        } catch (_: ReaderException) { null }
        finally { reader.reset() }
        read(bytes, width, height)?.let { return it }
        // Camera sensor orientation differs across devices; ZXing's YUV source cannot rotate.
        val rotated = ByteArray(bytes.size)
        for (y in 0 until height) for (x in 0 until width) {
            rotated[(width - x - 1) * height + y] = bytes[y * width + x]
        }
        return read(rotated, height, width)
    }
}

