package com.imagegrabber.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultFilterTest {
    private fun item(id: String, w: Int, h: Int, alt: String = "", lic: String = "CC BY 4.0") = ImageItem(
        key = "t:$id", source = "t", sourceId = id, thumbUrl = "", largeUrl = "", originalUrl = "",
        pageUrl = "", photographer = "P", photographerUrl = "", license = lic, width = w, height = h, alt = alt
    )

    private val caps = setOf("orientation", "square", "minWidth", "minHeight")
    private val items = listOf(
        item("1", 4000, 3000, "cat on sofa"),
        item("2", 1000, 2000, "dog"),
        item("3", 1000, 1000, "Cat toy", "CC BY-NC-SA 2.0"),
        item("4", 0, 0, "unknown size")
    )

    @Test fun orientationDetection() {
        assertEquals("landscape", ResultFilter.orientationOf(4000, 3000))
        assertEquals("portrait", ResultFilter.orientationOf(1000, 2000))
        assertEquals("square", ResultFilter.orientationOf(1000, 1010))
        assertEquals(null, ResultFilter.orientationOf(0, 0))
    }

    @Test fun orientationFilterKeepsUnknown() {
        val r = ResultFilter.apply(items, SearchFilters(orientation = "portrait"), caps, emptySet())
        assertEquals(listOf("t:2", "t:4"), r.map { it.key })
    }

    @Test fun megapixels() {
        val r = ResultFilter.apply(items, SearchFilters(minMp = 4), caps, emptySet())
        assertEquals(listOf("t:1", "t:4"), r.map { it.key })
    }

    @Test fun commercialOnlyDropsNc() {
        assertTrue(ResultFilter.isNonCommercial("CC BY-NC-SA 2.0"))
        assertFalse(ResultFilter.isNonCommercial("CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/)"))
        val r = ResultFilter.apply(items, SearchFilters(commercialOnly = true), caps, emptySet())
        assertFalse(r.any { it.key == "t:3" })
    }

    @Test fun hideDownloadedAndExclude() {
        val r = ResultFilter.apply(items, SearchFilters(hideDownloaded = true, exclude = "dog, toy"), caps, setOf("t:1"))
        assertEquals(listOf("t:4"), r.map { it.key })
    }

    @Test fun sortBiggestFirst() {
        val r = ResultFilter.apply(items, SearchFilters(sortBy = "big"), caps, emptySet())
        assertEquals("t:1", r.first().key)
        assertEquals("t:4", r.last().key)
    }
}

class CatalogsTest {
    @Test fun extOfHandlesUrls() {
        assertEquals("jpg", ResultFilter.extOf("https://x.com/a/pexels-photo-1.jpeg?auto=compress"))
        assertEquals("png", ResultFilter.extOf("https://upload.wikimedia.org/a/b/File.PNG"))
        assertEquals("jpg", ResultFilter.extOf("https://images.unsplash.com/photo-123?fm=jpg&q=85"))
        assertEquals(null, ResultFilter.extOf("https://images.unsplash.com/photo-123"))
    }

    @Test fun formatFilterKeepsUnknown() {
        fun it(id: String, url: String) = ImageItem("t:$id", "t", id, "", "", url, "", "P", "", "L", 1, 1, "")
        val items = listOf(it("1", "https://a/1.png"), it("2", "https://a/2.jpg"), it("3", "https://a/3"))
        val r = ResultFilter.apply(items, SearchFilters(format = "png"), emptySet(), emptySet())
        assertEquals(listOf("t:1", "t:3"), r.map { x -> x.key })
    }

    @Test fun styleAndBackgroundWords() {
        val f = SearchFilters(style = "3d", background = "transparent")
        assertEquals("3d render transparent background png", Styles.extras(f))
        assertEquals("cat 3d render transparent background png", Styles.build(" cat ", f))
        assertEquals("cat", Styles.build("cat", SearchFilters()))
    }

    @Test fun colorMappings() {
        assertEquals("lilac", ColorCatalog.get("violet")?.pixabay)
        assertEquals("#FFD700", ColorCatalog.get("gold")?.pexels)
        assertEquals("black_and_white", ColorCatalog.get("grayscale")?.unsplash)
        assertEquals(null, ColorCatalog.get("nope"))
        assertTrue(ColorCatalog.all.size >= 25)
        assertEquals(ColorCatalog.all.size, ColorCatalog.all.map { x -> x.id }.toSet().size)
    }
}
