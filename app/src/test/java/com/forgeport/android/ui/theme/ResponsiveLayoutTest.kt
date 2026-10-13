package com.forgeport.android.ui.theme

import org.junit.Assert.*
import org.junit.Test

class ResponsiveLayoutTest {
    @Test fun narrowPhonesAndLargeTextUseOneCoverColumn() {
        assertEquals(1, contentColumns(296f, 1f))
        assertEquals(2, contentColumns(336f, 1f))
        assertEquals(1, contentColumns(336f, 2f))
    }

    @Test fun tabletsAddColumnsWithoutMakingCoversTiny() {
        assertEquals(4, contentColumns(816f, 1f))
        assertEquals(6, contentColumns(1176f, 1f))
        assertEquals(3, contentColumns(1176f, 2f))
    }

    @Test fun widthAndFontScaleSweepNeverOverflowsMultiColumnRows() {
        for (width in 240..1200 step 8) for (scale in listOf(0.85f, 1f, 1.3f, 1.5f, 2f)) {
            val columns = contentColumns(width.toFloat(), scale)
            assertTrue(columns in 1..6)
            if (columns > 1) assertTrue(columns * 156f * scale.coerceAtLeast(1f) + (columns - 1) * 12f <= width)
        }
    }

    @Test fun galleryHeadersStackWhenTitleWouldBeSqueezed() {
        assertFalse(sideBySideGalleryHeader(280f, 1f))
        assertTrue(sideBySideGalleryHeader(320f, 1f))
        assertFalse(sideBySideGalleryHeader(320f, 1.5f))
        assertTrue(sideBySideGalleryHeader(700f, 2f))
    }
}
