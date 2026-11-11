package ir.matnyaab.core.search

import ir.matnyaab.core.AppSettingsManager
import ir.matnyaab.core.LuceneIndexer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** سناریوهای پیشنهادی مشتری (سند «قابلیت‌های جستجوی پایه») */
class MatchEngineTest {

    private fun n(spec: SearchSpec, text: String) = MatchEngine(spec).evaluate(text).total

    @Test fun wordCount() {
        val t = "نفس اماره، نفس لوامه و نفسِ مطمئنه؛ تهذیب نفس. نفسانی"
        assertEquals(4, n(SearchSpec(listOf("نفس")), t))
    }

    @Test fun phraseAndPunctuationInsensitive() {
        val s = SearchSpec(listOf("جهاد نفس"))
        listOf("جهاد نفس", "جهاد، نفس", "جهاد؛ نفس", "جهاد: نفس", "جهاد (نفس)", "جهاد ـ نفس", "جهاد  نفس")
            .forEach { assertEquals(1, n(s, "متن $it متن"), it) }
        assertEquals(0, n(s, "جهاد اکبر و نفس"))
    }

    @Test fun strictExact() {
        val s = SearchSpec(listOf("مبارزه با نفس"), strict = true)
        assertEquals(1, n(s, "مبارزه با نفس یک ضرورت است."))
        assertEquals(0, n(s, "مبارزه بانفس را باید"))
        assertEquals(0, n(s, "مبارزه‌با نفس برای کنترل"))
        assertEquals(0, n(s, "مبارزه، با نفس"))
    }

    @Test fun spacingModes() {
        val zw = "تهذیب‌نفس"; val wide = "تهذیب   نفس"
        assertEquals(0, n(SearchSpec(listOf("تهذیب نفس"), spacing = Spacing.EXACT, ignorePunct = false), zw))
        assertEquals(0, n(SearchSpec(listOf("تهذیب نفس"), spacing = Spacing.EXACT, ignorePunct = false), wide))
        assertEquals(1, n(SearchSpec(listOf("تهذیب نفس"), spacing = Spacing.COLLAPSE, ignorePunct = false), wide))
        assertEquals(0, n(SearchSpec(listOf("تهذیب نفس"), spacing = Spacing.COLLAPSE, ignorePunct = false), zw))
        assertEquals(1, n(SearchSpec(listOf("تهذیب نفس"), spacing = Spacing.UNIFY, ignorePunct = false), zw))
    }

    @Test fun partial() {
        val t = "مجاهد مجاهدت مجاهدین مجاهدانه جهاد"
        assertEquals(4, n(SearchSpec(listOf("مجاهد"), match = MatchType.PARTIAL), t))
        assertEquals(1, n(SearchSpec(listOf("مجاهد")), t))
        assertEquals(3, n(SearchSpec(listOf("خودساز"), match = MatchType.PARTIAL), "خودسازی خودسازندگی خودساخته خودسازی"))
    }

    @Test fun lettersAndDiacritics() {
        assertEquals(2, n(SearchSpec(listOf("تقوا")), "تقوا و تَقوا"))
        assertEquals(1, n(SearchSpec(listOf("کتاب")), "كتاب"))
        assertEquals(0, n(SearchSpec(listOf("کتاب"), strict = true), "كتاب"))
    }

    @Test fun multiWordAllAny() {
        val words = listOf("جهاد", "نفس", "تقوا", "خودسازی")
        val onlyTaqwa = "سخن از تقوا است"
        assertFalse(MatchEngine(SearchSpec(words, Combine.ALL)).evaluate(onlyTaqwa).ok)
        assertTrue(MatchEngine(SearchSpec(words, Combine.ANY)).evaluate(onlyTaqwa).ok)
        assertTrue(MatchEngine(SearchSpec(words, Combine.ALL)).evaluate("جهاد با نفس، تقوا و خودسازی").ok)
        assertFalse(MatchEngine(SearchSpec(listOf("نفس"), exclude = listOf("تقوا"))).evaluate("نفس و تقوا").ok)
    }

    @Test fun multiPhrase() {
        val e = MatchEngine(SearchSpec(MatchEngine.splitLines("1: تهذیب نفس\n2: جهاد اکبر\n3: مبارزه با نفس\n4: تزکیه نفس\n5: مجاهده با نفس"), Combine.ANY))
        val r = e.evaluate("تهذیب نفس و جهاد اکبر؛ باز هم تهذیب نفس و مجاهده با نفس")
        assertEquals(listOf(2, 1, 0, 0, 1), r.perPhrase.toList())
        assertEquals(3, r.perPhrase.count { it > 0 })
        assertEquals("تهذیب نفس", e.phrase(0))
    }

    private fun ok(expr: String, text: String) = MatchEngine(SearchSpec(emptyList(), expression = expr)).evaluate(text).ok

    @Test fun booleanOperators() {
        assertTrue(ok("جهاد AND نفس", "جهاد ... و در جای دیگر نفس"))
        assertFalse(ok("جهاد AND نفس", "فقط جهاد"))
        assertTrue(ok("جهاد OR مبارزه", "مبارزه"))
        assertFalse(ok("جهاد NOT نظامی", "جهاد نظامی"))
        assertTrue(ok("جهاد NOT نظامی", "جهاد اکبر"))
        val e = "(جهاد OR مبارزه) AND نفس NOT نظامی"
        assertTrue(ok(e, "مبارزه با نفس"))
        assertFalse(ok(e, "مبارزه نظامی و نفس"))
        assertFalse(ok(e, "جهاد اکبر"))
        assertTrue(ok("«جهاد اکبر» || تقوا", "تقوا"))
        assertTrue(ok("تهذیب نفس AND تقوا", "تهذیب نفس و تقوا"))
        assertFalse(ok("تهذیب نفس AND تقوا", "تهذیب و تقوا و نفس"))
    }

    @Test fun proximity() {
        assertTrue(ok("جهاد NEAR/5 نفس", "جهاد یک دو سه چهار پنج نفس"))
        assertFalse(ok("جهاد NEAR/5 نفس", "جهاد یک دو سه چهار پنج شش نفس"))
        assertTrue(ok("جهاد NEAR/۲ نفس", "نفس یک دو جهاد"))
        assertTrue(ok("جهاد ~0 نفس", "جهاد، نفس"))
    }

    @Test fun booleanErrors() {
        listOf("", "(جهاد OR نفس", "جهاد AND", "NOT نظامی").forEach { q ->
            assertTrue(runCatching { BoolQuery.parse(q) }.exceptionOrNull() is BoolParseException, q)
        }
    }

    @Test fun luceneCandidatesFindPunctuationGluedTokens() {
        val home = Files.createTempDirectory("mx-test")
        System.setProperty("matnyaab.home", home.toString())
        val idx = LuceneIndexer(AppSettingsManager())
        val dir = idx.makeIndexDir(home.resolve("lib").toString())
        idx.openWriter(dir).use { w ->
            idx.addDocument(w, "a.txt", "/a.txt", 10, 0.0, "سخن از جهاد، نفس و تقوا")
            idx.addDocument(w, "b.txt", "/b.txt", 10, 0.0, "مبارزه با نفس یک ضرورت است")
            idx.addDocument(w, "c.txt", "/c.txt", 10, 0.0, "متنی دیگر بی‌ربط")
        }
        idx.loadIndexer("lib", dir.toString(), "/")
        val hits = idx.searchSpec(SearchSpec(listOf("جهاد نفس", "مبارزه با نفس"), Combine.ANY), idx.allObjects, emptySet())
        assertEquals(setOf("/a.txt", "/b.txt"), hits.map { it.path }.toSet())
        assertTrue(hits.all { it.matches == 1 })
        val (html, count) = idx.highlightLastQuery("جهاد، نفس")
        assertEquals(1, count); assertTrue(html.contains("<span"))
        val b = idx.searchSpec(SearchSpec(emptyList(), expression = "(جهاد OR مبارزه) AND نفس NOT تقوا"), idx.allObjects, emptySet())
        assertEquals(listOf("/b.txt"), b.map { it.path })
    }
}
