package com.forgeport.android.gallery

internal fun galleryDownloadPageCount(total: Int, percent: Int): Int {
    require(total in 1..10000 && percent in 1..100) { "Invalid gallery download selection." }
    // Round up so even a small selection of a short gallery includes one page.
    return (total * percent + 99) / 100
}
