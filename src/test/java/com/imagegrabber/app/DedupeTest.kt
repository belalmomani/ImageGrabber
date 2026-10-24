package com.imagegrabber.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class DedupeTest {
    private fun randomLum(seed: Long): IntArray {
        val r = Random(seed)
        return IntArray(72) { r.nextInt(256) }
    }

    private fun item(id: String, source: String, w: Int, h: Int) = ImageItem(
        key = "$source:$id", source = source, sourceId = id, thumbUrl = "", largeUrl = "", originalUrl = "",
        pageUrl = "", photographer = "P", photographerUrl = "", license = "L", width = w, height = h, alt = ""
    )

    // ----- البصمة -----
    @Test fun brightnessShiftKeepsHash() {
        val lum = randomLum(1)
        val shifted = IntArray(72) { lum[it] + 25 }
        assertEquals(DHash.fromLum(lum), DHash.fromLum(shifted))
    }

    @Test fun smallNoiseGivesSmallDistance() {
        val lum = randomLum(2)
        val noisy = IntArray(72) { lum[it] + if (it % 9 == 0) 3 else 0 }
        assertTrue(DHash.distance(DHash.fromLum(lum), DHash.fromLum(noisy)) <= DupGroups.SIM_T)
    }

    @Test fun differentImagesAreFar() {
        val d = DHash.distance(DHash.fromLum(randomLum(10)), DHash.fromLum(randomLum(99)))
        assertTrue(d > DupGroups.SIM_T)
    }

    @Test fun flatImagesAreUnreliable() {
        assertFalse(DHash.reliable(DHash.fromLum(IntArray(72) { 128 })))
        assertTrue(DHash.reliable(DHash.fromLum(randomLum(5))))
    }

    // ----- التجميع -----
    @Test fun sameImageAcrossSourcesGroupsAsDuplicate() {
        val h = DHash.fromLum(randomLum(7))
        val near = h xor 0b11L // اختلاف بتين (نسخة معاد ضغطها)
        val other = DHash.fromLum(randomLum(123))
        val g = DupGroups.group(
            listOf(
                FpEntry("pexels:1", h, 4000, 3000),
                FpEntry("pixabay:9", near, 1280, 960),
                FpEntry("unsplash:z", other, 4000, 3000)
            )
        )
        assertEquals(g.dup["pexels:1"], g.dup["pixabay:9"])
        assertNull(g.dup["unsplash:z"])
        assertEquals(2, g.dupSize[g.dup["pexels:1"]!!])
    }

    @Test fun similarButNotDuplicateStaysSeparate() {
        val h = DHash.fromLum(randomLum(8))
        val similar = h xor 0b1111111L // 7 بتات: مشابه وليس مطابقًا
        val g = DupGroups.group(listOf(FpEntry("a:1", h, 100, 100), FpEntry("b:2", similar, 100, 100)))
        assertNull(g.dup["a:1"])
        assertEquals(g.sim["a:1"], g.sim["b:2"])
    }

    @Test fun differentAspectIsNotDuplicate() {
        val h = DHash.fromLum(randomLum(9))
        val g = DupGroups.group(listOf(FpEntry("a:1", h, 4000, 3000), FpEntry("b:2", h, 3000, 4000)))
        assertNull(g.dup["a:1"])
        assertNotEquals(null, g.sim["a:1"])
    }

    // ----- أوضاع العرض -----
    private fun ctxFor(items: List<ImageItem>, hashes: Map<String, Long>, preferred: Set<String> = emptySet()) =
        Dedupe.build(items, hashes, emptyList(), preferred, 100)

    @Test fun hideKeepsLargestAsRepresentative() {
        val h = DHash.fromLum(randomLum(3))
        val items = listOf(item("1", "pixabay", 1280, 960), item("2", "pexels", 4000, 3000), item("3", "unsplash", 500, 500))
        val hashes = mapOf("pixabay:1" to h, "pexels:2" to (h xor 1L), "unsplash:3" to DHash.fromLum(randomLum(500)))
        val r = Dedupe.apply(items, "hide", ctxFor(items, hashes), emptySet())
        assertEquals(listOf("pexels:2", "unsplash:3"), r.map { it.key }.sorted())
    }

    @Test fun preferredCopyWinsOverLargest() {
        val h = DHash.fromLum(randomLum(4))
        val items = listOf(item("1", "pixabay", 1280, 960), item("2", "pexels", 4000, 3000))
        val hashes = mapOf("pixabay:1" to h, "pexels:2" to h)
        val r = Dedupe.apply(items, "hide", ctxFor(items, hashes, setOf("pixabay:1")), emptySet())
        assertEquals(listOf("pixabay:1"), r.map { it.key })
    }

    @Test fun reviewShowsOnlyDuplicates() {
        val h = DHash.fromLum(randomLum(6))
        val items = listOf(item("1", "a", 10, 10), item("2", "b", 10, 10), item("3", "c", 10, 10))
        val hashes = mapOf("a:1" to h, "b:2" to h, "c:3" to DHash.fromLum(randomLum(700)))
        val r = Dedupe.apply(items, "review", ctxFor(items, hashes), emptySet())
        assertEquals(setOf("a:1", "b:2"), r.map { it.key }.toSet())
    }

    @Test fun libraryMatchIsHiddenButDownloadedItemItselfIsKept() {
        val h = DHash.fromLum(randomLum(11))
        val items = listOf(item("1", "pexels", 10, 10))
        val lib = listOf(FingerprintEntity("pixabay:77", h, null, true))
        val ctx = Dedupe.build(items, mapOf("pexels:1" to h), lib, emptySet(), 100)
        assertTrue("pexels:1" in ctx.inLibrary)
        assertTrue(Dedupe.apply(items, "hide", ctx, emptySet()).isEmpty())
        assertEquals(1, Dedupe.apply(items, "hide", ctx, setOf("pexels:1")).size)
    }

    @Test fun unanalyzedItemsAreKept() {
        val items = listOf(item("1", "a", 10, 10), item("2", "b", 10, 10))
        val r = Dedupe.apply(items, "hide", ctxFor(items, emptyMap()), emptySet())
        assertEquals(2, r.size)
    }

    @Test fun filterOffLeavesListUntouched() {
        val items = listOf(item("1", "a", 10, 10))
        val r = ResultFilter.apply(items, SearchFilters(), emptySet(), emptySet(), null)
        assertEquals(items, r)
    }

    @Test fun serverPartIgnoresClientOnlyFields() {
        val a = SearchFilters(color = "red")
        assertEquals(a.serverPart(), a.copy(dedupe = "hide", minMp = 4, exclude = "x").serverPart())
        assertNotEquals(a.serverPart(), a.copy(color = "blue").serverPart())
    }
}
