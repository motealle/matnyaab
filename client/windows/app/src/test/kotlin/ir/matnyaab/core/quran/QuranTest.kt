package ir.matnyaab.core.quran

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuranTest {
    @Test fun dataLoads() {
        assertEquals(6236, Quran.verses.size)
        assertEquals(286, Quran.counts[2]); assertEquals(114, Quran.NAMES.size)
    }

    @Test fun fullVerseWithDiacriticsDifferences() {
        // آیة الکرسی، آغاز آن با اعراب و املای عثمانی
        val t = "خداوند می‌فرماید: «اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ» و این آیه…"
        val h = Matcher.find(t)
        assertTrue(h.any { it.sura == 2 && it.aya == 255 && it.kind == QKind.PARTIAL }, h.toString())
    }

    @Test fun fullShortVerseAndPersianLetters() {
        val t = "چنان‌که آمده است: قل هو الله احد. الله الصمد. لم یلد و لم یولد."
        val h = Matcher.find(t)
        assertTrue(h.any { it.sura == 112 && it.aya == 3 }, h.toString())
    }

    @Test fun references() {
        val t = "بنگرید به سورهٔ بقره، آیهٔ ۳ و نیز (آل عمران: ۱۹۰) و آیه ۵ سوره مائده؛ ص: ۱۲ را هم ببینید."
        val r = Quran.references(t)
        assertTrue(r.any { it.sura == 2 && it.from == 3 }, r.toString())
        assertTrue(r.any { it.sura == 3 && it.from == 190 }, r.toString())
        assertTrue(r.any { it.sura == 5 && it.from == 5 }, r.toString())
        assertEquals(3, r.size, r.toString())
    }

    @Test fun persianProseIsNotQuran() {
        val t = "پژوهشگر باید پیش از نگارش، منابع را با دقت بخواند و فیش‌برداری کند تا در هنگام نوشتن به همهٔ مطالب دسترسی داشته باشد."
        assertTrue(Matcher.all(t).isEmpty())
    }

    @Test fun basmalaHiddenByDefault() {
        assertTrue(Matcher.find("بسم الله الرحمن الرحيم\nمقدمه").isEmpty())
    }
}
