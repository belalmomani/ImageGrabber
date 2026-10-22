package com.imagegrabber.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {
    private fun entity(photographer: String, status: String = Status.DONE) = ImageEntity(
        imageKey = "pexels:1", source = "pexels", sourceId = "1", searchQuery = "قطط",
        thumbUrl = "t", largeUrl = "l", originalUrl = "https://o/1.jpg", pageUrl = "https://p/1",
        photographer = photographer, photographerUrl = "https://a", license = "Pexels",
        width = 10, height = 10, alt = "", status = status
    )

    @Test fun startsWithBomAndHasHeader() {
        val csv = Csv.build(emptyList())
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("اسم البحث"))
        assertTrue(csv.contains("حالة التنزيل"))
    }

    @Test fun escapesQuotes() {
        val csv = Csv.build(listOf(entity("A \"B\" C")))
        assertTrue(csv.contains("\"A \"\"B\"\" C\""))
    }

    @Test fun guardsAgainstFormulaInjection() {
        val csv = Csv.build(listOf(entity("=cmd")))
        assertTrue(csv.contains("\"'=cmd\""))
    }

    @Test fun rowCountMatches() {
        val csv = Csv.build(listOf(entity("a"), entity("b")))
        assertEquals(3, csv.trim().split("\r\n").size)
    }

    @Test fun statusLabels() {
        assertEquals("تم الحفظ", Status.label(Status.DONE))
        assertEquals("فشل", Status.label(Status.FAILED))
        assertEquals("لم يُنزَّل", Status.label("???"))
    }
}
