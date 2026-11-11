package ir.matnyaab.semantic

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.google.gson.JsonParser
import ir.matnyaab.core.semantic.SemanticEngine
import java.io.File
import java.nio.LongBuffer
import java.text.Normalizer
import kotlin.math.sqrt

/**
 * توکن‌ساز Unigram (SentencePiece/XLM-R) به زبان کاتلین از روی tokenizer.json؛
 * بدون کتابخانهٔ بومی اضافه. نرمال‌سازی NFKC + Metaspace («▁») + بُرش Viterbi.
 */
class UnigramTokenizer(file: File) {
    private val ids = HashMap<String, Int>(300_000)
    private val scores = HashMap<String, Float>(300_000)
    private var maxLen = 1
    private var minScore = 0f
    val unk: Int
    val bos = 0
    val eos = 2

    init {
        val root = JsonParser.parseString(file.readText()).asJsonObject
        val model = root.getAsJsonObject("model")
        unk = model.get("unk_id")?.asInt ?: 3
        model.getAsJsonArray("vocab").forEachIndexed { i, e ->
            val a = e.asJsonArray
            val p = a[0].asString; val sc = a[1].asFloat
            ids[p] = i; scores[p] = sc
            if (p.length > maxLen) maxLen = p.length
            if (sc < minScore) minScore = sc
        }
    }

    private fun piece(word: String): List<Int> {
        val n = word.length
        val best = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }
        val from = IntArray(n + 1); val tok = IntArray(n + 1)
        best[0] = 0f
        for (i in 0 until n) {
            if (best[i] == Float.NEGATIVE_INFINITY) continue
            var matched = false
            for (l in 1..minOf(maxLen, n - i)) {
                val s = word.substring(i, i + l)
                val id = ids[s] ?: continue
                matched = true
                val v = best[i] + scores[s]!!
                if (v > best[i + l]) { best[i + l] = v; from[i + l] = i; tok[i + l] = id }
            }
            if (!matched || l1Missing(word, i)) {
                val v = best[i] + minScore - 10f
                if (v > best[i + 1]) { best[i + 1] = v; from[i + 1] = i; tok[i + 1] = unk }
            }
        }
        val out = ArrayList<Int>()
        var j = n
        while (j > 0) { out.add(tok[j]); j = from[j] }
        out.reverse()
        // ادغام unkهای پیاپی (مثل SentencePiece)
        val merged = ArrayList<Int>(out.size)
        for (t in out) if (!(t == unk && merged.lastOrNull() == unk)) merged.add(t)
        return merged
    }

    private fun l1Missing(word: String, i: Int) = !ids.containsKey(word.substring(i, i + 1))

    fun encode(text: String, maxTokens: Int): IntArray {
        val norm = Normalizer.normalize(text, Normalizer.Form.NFKC).replace(Regex("[\\u0000-\\u001F]"), " ")
        val out = ArrayList<Int>()
        out.add(bos)
        for (w in norm.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            out.addAll(piece("▁$w"))
            if (out.size >= maxTokens - 1) break
        }
        while (out.size > maxTokens - 1) out.removeAt(out.size - 1)
        out.add(eos)
        return out.toIntArray()
    }
}

/**
 * موتور معنایی بر پایهٔ multilingual-e5-small (ONNX، کوانتیزه) — کاملاً آفلاین.
 * پیشوندهای «query: » و «passage: » طبق راهنمای E5؛ میانگین‌گیری با ماسک و نرمال‌سازی L2.
 */
class E5Engine(private val dir: File) : SemanticEngine {
    override val id = "multilingual-e5-small"
    override val dim = 384
    private val env: OrtEnvironment
    private val session: OrtSession
    private val tok: UnigramTokenizer
    private val maxTokens = 256

    init {
        val native = File(dir, "native")
        if (native.isDirectory) {
            // پیش‌بارگذاری کتابخانه‌های زمان اجرای ویژوال‌سی (اگر روی ویندوز کاربر نباشند)
            for (n in listOf("vcruntime140.dll", "vcruntime140_1.dll", "msvcp140.dll")) {
                val f = File(native, n); if (f.exists()) runCatching { System.load(f.absolutePath) }
            }
            if (File(native, "onnxruntime.dll").exists()) System.setProperty("onnxruntime.native.path", native.absolutePath)
        }
        env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(maxOf(1, Runtime.getRuntime().availableProcessors() / 2))
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        session = env.createSession(File(dir, "model/model.onnx").absolutePath, opts)
        tok = UnigramTokenizer(File(dir, "model/tokenizer.json"))
    }

    override fun embedQuery(text: String): FloatArray = embed(listOf("query: $text"))[0]

    override fun embedPassages(texts: List<String>): List<FloatArray> = embed(texts.map { "passage: $it" })

    @Synchronized
    private fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        val enc = texts.map { tok.encode(it, maxTokens) }
        val len = enc.maxOf { it.size }
        val b = enc.size
        val idsArr = LongArray(b * len) { 1L }       // <pad> = 1
        val mask = LongArray(b * len)
        enc.forEachIndexed { r, e -> e.forEachIndexed { c, v -> idsArr[r * len + c] = v.toLong(); mask[r * len + c] = 1 } }
        val shape = longArrayOf(b.toLong(), len.toLong())
        val inputs = HashMap<String, OnnxTensor>()
        try {
            for (name in session.inputNames) {
                inputs[name] = when (name) {
                    "input_ids" -> OnnxTensor.createTensor(env, LongBuffer.wrap(idsArr), shape)
                    "attention_mask" -> OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape)
                    else -> OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(b * len)), shape)  // token_type_ids
                }
            }
            session.run(inputs).use { res ->
                @Suppress("UNCHECKED_CAST")
                val hidden = res[0].value as Array<Array<FloatArray>>
                return List(b) { r ->
                    val v = FloatArray(hidden[r][0].size)
                    var cnt = 0f
                    for (c in 0 until len) if (mask[r * len + c] == 1L) { val h = hidden[r][c]; for (k in v.indices) v[k] += h[k]; cnt++ }
                    var norm = 0.0
                    for (k in v.indices) { v[k] /= cnt; norm += v[k] * v[k] }
                    val n = sqrt(norm).toFloat().coerceAtLeast(1e-12f)
                    for (k in v.indices) v[k] /= n
                    v
                }
            }
        } finally { inputs.values.forEach { it.close() } }
    }

    override fun close() { runCatching { session.close() } }
}
