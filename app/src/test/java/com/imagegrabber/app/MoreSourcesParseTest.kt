package com.imagegrabber.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreSourcesParseTest {

    @Test fun unsplashParsesAndHandlesNullAlt() {
        val body = """
        {"total":50,"total_pages":5,"results":[
          {"id":"abc","width":4000,"height":3000,"alt_description":null,"description":"A cat",
           "urls":{"full":"https://i/full","regular":"https://i/reg","small":"https://i/small"},
           "links":{"html":"https://u/p/abc","download_location":"https://api.unsplash.com/photos/abc/download"},
           "user":{"name":"Sam","links":{"html":"https://u/@sam"}}},
          {"id":"bad","urls":{"full":"http://insecure"}}
        ]}
        """.trimIndent()
        val r = UnsplashSource.parse(body, 1, 49)
        assertEquals(1, r.items.size)
        val p = r.items[0]
        assertEquals("unsplash:abc", p.key)
        assertEquals("A cat", p.alt)
        assertEquals("https://api.unsplash.com/photos/abc/download", p.extra)
        assertTrue(p.pageUrl.contains("utm_source=ImageGrabber"))
        assertTrue(r.hasNext)
        assertEquals(49, r.remaining)
    }

    @Test fun flickrParsesLicenseAndFailure() {
        val body = """
        {"stat":"ok","photos":{"page":1,"pages":3,"total":"250","photo":[
          {"id":"11","owner":"o@N0","ownername":"Lee","license":"4","title":"T",
           "url_m":"https://m/11.jpg","url_l":"https://l/11.jpg","width_o":3000,"height_o":2000}
        ]}}
        """.trimIndent()
        val r = FlickrSource.parse(body, null)
        assertEquals(1, r.items.size)
        assertEquals(250, r.total)
        assertTrue(r.hasNext)
        assertTrue(r.items[0].license.contains("CC BY 2.0"))
        assertEquals("https://www.flickr.com/photos/o@N0/11", r.items[0].pageUrl)
    }

    @Test fun flickrInvalidKeyMapsTo401() {
        try {
            FlickrSource.parse("""{"stat":"fail","code":100,"message":"Invalid API Key"}""", null)
            org.junit.Assert.fail("expected")
        } catch (e: ApiException) {
            assertEquals(401, e.code)
        }
    }

    @Test fun openverseParsesLicense() {
        val body = """
        {"result_count":10,"page_count":2,"results":[
          {"id":"x1","url":"https://o/1.jpg","thumbnail":"https://t/1","creator":"Ann","license":"by","license_version":"4.0",
           "license_url":"https://cc/by/4.0","foreign_landing_url":"https://f/1","source":"flickr","width":10,"height":20},
          {"id":"x2","url":"http://insecure/2.jpg"}
        ]}
        """.trimIndent()
        val r = OpenverseSource.parse(body, 1, null)
        assertEquals(1, r.items.size)
        assertTrue(r.items[0].license.contains("CC BY 4.0"))
        assertTrue(r.hasNext)
    }

    @Test fun commonsParsesSortsAndFilters() {
        val body = """
        {"continue":{"gsroffset":30},"query":{"searchinfo":{"totalhits":77},"pages":[
          {"pageid":2,"index":2,"title":"File:Second.png","imageinfo":[{"url":"https://c/2.png","thumburl":"https://c/thumb/a/ab/2.png/1600px-2.png","mime":"image/png","width":5,"height":5,"descriptionurl":"https://c/File:Second.png","extmetadata":{"LicenseShortName":{"value":"CC BY-SA 4.0"},"Artist":{"value":"<a href=\"x\">Zed</a>"}}}]},
          {"pageid":1,"index":1,"title":"File:First.jpg","imageinfo":[{"url":"https://c/1.jpg","mime":"image/jpeg","width":9,"height":9,"descriptionurl":"https://c/File:First.jpg"}]},
          {"pageid":3,"index":3,"title":"File:Doc.pdf","imageinfo":[{"url":"https://c/3.pdf","mime":"application/pdf"}]}
        ]}}
        """.trimIndent()
        val r = CommonsSource.parse(body, null)
        assertEquals(2, r.items.size)
        assertEquals("commons:1", r.items[0].key)
        assertEquals("Zed", r.items[1].photographer)
        assertTrue(r.items[1].license.contains("CC BY-SA 4.0"))
        assertEquals("https://c/thumb/a/ab/2.png/480px-2.png", r.items[1].thumbUrl)
        assertEquals(77, r.total)
        assertTrue(r.hasNext)
    }

    @Test fun commonsEmptyResult() {
        val r = CommonsSource.parse("""{"batchcomplete":true}""", null)
        assertTrue(r.items.isEmpty())
        assertFalse(r.hasNext)
    }

    @Test fun filterCountRespectsCaps() {
        val f = SearchFilters(orientation = "landscape", color = "red", category = "nature", editorsChoice = true)
        assertEquals(2, f.activeCount(setOf("orientation", "color")))
        assertEquals(4, f.activeCount(SourceCatalog.byId("pixabay").caps))
        assertEquals(0, f.activeCount(emptySet()))
    }
}
