package ir.matnyaab.semantic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** آزمون کیفیت معنایی روی جمله‌های فارسی پژوهشی (مدل در CI دانلود می‌شود) */
class E5EngineTest {
    private val dir = File(System.getProperty("matnyaab.semantic.dir") ?: "")

    private fun cos(a: FloatArray, b: FloatArray) = a.indices.sumOf { (a[it] * b[it]).toDouble() }

    @Test fun persianConcepts() {
        if (!File(dir, "model/model.onnx").exists()) { println("model not available; skipped"); return }
        E5Engine(dir).use { e ->
            assertEquals(384, e.embedQuery("سلام").size)
            val cases = listOf(
                Triple("خودسازی", "تزکیه نفس و مراقبت از نفس در برابر هواهای نفسانی", "قیمت نفت و نرخ ارز در بازار امروز"),
                Triple("مبارزه با نفس", "جهاد اکبر یعنی غلبه بر تمایلات نفسانی", "مسابقهٔ فوتبال دیشب با نتیجهٔ مساوی تمام شد"),
                Triple("عدالت اجتماعی", "توزیع برابر فرصت‌ها و رفع تبعیض در جامعه", "دستور پخت قورمه‌سبزی با سبزی تازه"),
                Triple("تربیت فرزند", "پرورش اخلاقی کودکان در خانواده", "تعمیر موتور خودرو و تعویض روغن"),
            )
            var ok = 0
            for ((q, rel, unrel) in cases) {
                val qv = e.embedQuery(q)
                val p = e.embedPassages(listOf(rel, unrel))
                val a = cos(qv, p[0]); val b = cos(qv, p[1])
                println("«$q»: related=%.3f unrelated=%.3f".format(a, b))
                if (a > b) ok++
            }
            assertEquals(cases.size, ok, "semantic ranking on Persian samples")
            val t = UnigramTokenizer(File(dir, "model/tokenizer.json")).encode("متن‌یاب برای پژوهشگران", 64)
            assertTrue(t.first() == 0 && t.last() == 2 && t.size > 3)
        }
    }
}
