package com.forgeport.android.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GalleryDownloadSelectionTest {
    @Test fun roundsUpAndBoundsTheSelectedPrefix() {
        assertEquals(1, galleryDownloadPageCount(1, 1))
        assertEquals(1, galleryDownloadPageCount(3, 1))
        assertEquals(2, galleryDownloadPageCount(3, 50))
        assertEquals(38, galleryDownloadPageCount(76, 50))
        assertEquals(77, galleryDownloadPageCount(101, 76))
        assertEquals(10000, galleryDownloadPageCount(10000, 100))
        for (total in listOf(1, 3, 76, 101, 10000)) {
            for (percent in 1..100) {
                val target = galleryDownloadPageCount(total, percent)
                assertEquals(true, target in 1..total)
                assertEquals(true, target * 100 >= total * percent)
                assertEquals(true, (target - 1) * 100 < total * percent)
            }
        }
        for ((total, percent) in listOf(0 to 100, 10001 to 100, 100 to 0, 100 to 101)) {
            assertThrows(IllegalArgumentException::class.java) { galleryDownloadPageCount(total, percent) }
        }
    }
}
