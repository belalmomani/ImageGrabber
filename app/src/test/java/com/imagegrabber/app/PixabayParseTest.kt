package com.imagegrabber.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixabayParseTest {
    private val source = PixabaySource { "k" }

    private val sample = """
    {"total":100,"totalHits":100,"hits":[
      {"id":7,"pageURL":"https://pixabay.com/photos/x-7/","tags":"cat, pet","webformatURL":"https://w/7.jpg",
       "largeImageURL":"https://l/7.jpg","imageWidth":4000,"imageHeight":3000,"user":"Bob","user_id":55},
      {"id":8,"largeImageURL":"http://insecure/8.jpg"},
      {"id":9}
    ]}
    """.trimIndent()

    @Test fun parsesValidHitsOnly() {
        val r = source.parse(sample, 1, 30, 99)
        assertEquals(1, r.items.size)
        val p = r.items[0]
        assertEquals("pixabay:7", p.key)
        assertEquals("https://w/7.jpg", p.thumbUrl)
        assertEquals("https://l/7.jpg", p.originalUrl)
        assertEquals("Bob", p.photographer)
        assertEquals("https://pixabay.com/users/Bob-55/", p.photographerUrl)
        assertTrue(r.hasNext)
        assertEquals(99, r.remaining)
    }

    @Test fun lastPageHasNoNext() {
        val r = source.parse("""{"total":10,"totalHits":10,"hits":[]}""", 1, 30, null)
        assertFalse(r.hasNext)
    }

    @Test(expected = ApiException::class)
    fun invalidJsonThrows() {
        source.parse("oops", 1, 30, null)
    }
}
