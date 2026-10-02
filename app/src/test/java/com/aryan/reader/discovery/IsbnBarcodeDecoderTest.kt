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
import com.google.zxing.oned.EAN13Writer
import org.junit.Assert.*
import org.junit.Test

class IsbnBarcodeDecoderTest {
    @Test fun decodesBothSensorOrientations() {
        val width = 640
        val height = 240
        val code = EAN13Writer().encode("9782864970002", BarcodeFormat.EAN_13, width, height)
        val bytes = ByteArray(width * height) { if (code[it % width, it / width]) 0 else 255.toByte() }
        val decoder = IsbnBarcodeDecoder()
        assertEquals("9782864970002", decoder.decode(bytes, width, height))
        val rotated = ByteArray(bytes.size)
        for (y in 0 until height) for (x in 0 until width) {
            rotated[x * height + (height - y - 1)] = bytes[y * width + x]
        }
        assertEquals("9782864970002", decoder.decode(rotated, height, width))
    }

    @Test fun ignoresNonBookEansAndBlankFrames() {
        val code = EAN13Writer().encode("4006381333931", BarcodeFormat.EAN_13, 640, 240)
        val bytes = ByteArray(640 * 240) { if (code[it % 640, it / 640]) 0 else 255.toByte() }
        assertNull(IsbnBarcodeDecoder().decode(bytes, 640, 240))
        assertNull(IsbnBarcodeDecoder().decode(ByteArray(640 * 240) { 255.toByte() }, 640, 240))
    }
}

