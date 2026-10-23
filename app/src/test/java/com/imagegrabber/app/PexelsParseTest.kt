package com.imagegrabber.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PexelsParseTest {
    private val sample = """
    {"total_results":2,"next_page":"https://api.pexels.com/next",
     "photos":[
      {"id":1,"width":100,"height":200,"url":"https://p/1","photographer":"A","photographer_url":"https://a","alt":"x",
       "src":{"original":"https://o/1.jpg","large2x":"https://l/1.jpg","medium":"https://m/1.jpg"}},
      {"id":2,"src":{"original":"http://insecure/2.jpg"}},
      {"id":3}
     ]}
    """.trimIndent()

    @Test fun parsesValidPhotosOnly() {
        val r = PexelsSource.parse(sample, 5)
        assertEquals(1, r.items.size)
        val p = r.items[0]
        assertEquals("pexels:1", p.key)
        assertEquals("https://m/1.jpg", p.thumbUrl)
        assertEquals("https://l/1.jpg", p.largeUrl)
        assertEquals("A", p.photographer)
        assertTrue(r.hasNext)
        assertEquals(5, r.remaining)
        assertEquals(2, r.total)
    }

    @Test fun noNextPage() {
        val r = PexelsSource.parse("""{"total_results":0,"photos":[]}""", null)
        assertFalse(r.hasNext)
        assertTrue(r.items.isEmpty())
    }

    @Test(expected = ApiException::class)
    fun invalidJsonThrows() {
        PexelsSource.parse("not json", null)
    }

    @Test(expected = ApiException::class)
    fun missingPhotosThrows() {
        PexelsSource.parse("""{"total_results":0}""", null)
    }
}
