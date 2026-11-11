package ir.matnyaab.core.search

/**
 * زبان پرس‌وجوی منطقی متن‌یاب:
 *   (جهاد OR مبارزه) AND نفس NOT نظامی
 *   جهاد NEAR/5 نفس            (حداکثر ۵ واژه فاصله، هر ترتیبی)
 * - عملگرها: AND، OR، NOT، NEAR/n (حروف بزرگ یا کوچک) و معادل‌های && ، || ، ! و ~n
 * - واژه‌های پشت‌سرهمِ بدون عملگر یک «عبارت» حساب می‌شوند؛ گیومه ("…" یا «…») هم پذیرفته است.
 * - اولویت: NOT یکانی > NEAR > AND/NOT دوتایی > OR
 */
sealed class BoolNode {
    data class Leaf(val phrase: String) : BoolNode()
    data class And(val a: BoolNode, val b: BoolNode) : BoolNode()
    data class Or(val a: BoolNode, val b: BoolNode) : BoolNode()
    data class Not(val a: BoolNode) : BoolNode()
    data class Near(val a: BoolNode, val b: BoolNode, val dist: Int) : BoolNode()

    fun leaves(): List<String> = when (this) {
        is Leaf -> listOf(phrase)
        is And -> a.leaves() + b.leaves()
        is Or -> a.leaves() + b.leaves()
        is Not -> a.leaves()
        is Near -> a.leaves() + b.leaves()
    }

    /** واژه‌هایی که فقط زیر NOT آمده‌اند در برجسته‌سازی/شمارش لحاظ نمی‌شوند */
    fun positiveLeaves(neg: Boolean = false): List<String> = when (this) {
        is Leaf -> if (neg) emptyList() else listOf(phrase)
        is And -> a.positiveLeaves(neg) + b.positiveLeaves(neg)
        is Or -> a.positiveLeaves(neg) + b.positiveLeaves(neg)
        is Not -> a.positiveLeaves(!neg)
        is Near -> a.positiveLeaves(neg) + b.positiveLeaves(neg)
    }
}

class BoolParseException(msg: String) : Exception(msg)

object BoolQuery {
    private sealed class Tok {
        data class Word(val text: String, val quoted: Boolean) : Tok()
        object LP : Tok(); object RP : Tok(); object AND : Tok(); object OR : Tok(); object NOT : Tok()
        data class NEAR(val n: Int) : Tok()
    }

    private fun digits(s: String) = s.map { c -> when (c) { in '۰'..'۹' -> '0' + (c - '۰'); in '٠'..'٩' -> '0' + (c - '٠'); else -> c } }.joinToString("")

    /** آیا متن ورودی عملگر منطقی دارد؟ (برای پیشنهاد خودکار حالت منطقی) */
    fun looksBoolean(s: String): Boolean =
        Regex("(^|[\\s(])(AND|OR|NOT|NEAR/[0-9۰-۹]+)(?=$|[\\s)])|&&|\\|\\||~[0-9۰-۹]+|[()]").containsMatchIn(s)

    private fun lex(src: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        val s = src
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c == '(' -> { out.add(Tok.LP); i++ }
                c == ')' -> { out.add(Tok.RP); i++ }
                c == '"' || c == '«' -> {
                    val close = if (c == '"') '"' else '»'
                    val j = s.indexOf(close, i + 1).let { if (it < 0) s.length else it }
                    out.add(Tok.Word(s.substring(i + 1, j).trim(), true)); i = j + 1
                }
                s.startsWith("&&", i) -> { out.add(Tok.AND); i += 2 }
                s.startsWith("||", i) -> { out.add(Tok.OR); i += 2 }
                c == '!' -> { out.add(Tok.NOT); i++ }
                c == '~' -> {
                    var j = i + 1; while (j < s.length && (s[j].isDigit())) j++
                    out.add(Tok.NEAR(digits(s.substring(i + 1, j)).toIntOrNull() ?: 5)); i = j
                }
                else -> {
                    var j = i
                    while (j < s.length && !s[j].isWhitespace() && s[j] !in "()\"«»!~" && !s.startsWith("&&", j) && !s.startsWith("||", j)) j++
                    val w = s.substring(i, j)
                    val up = w.uppercase()
                    when {
                        up == "AND" -> out.add(Tok.AND)
                        up == "OR" -> out.add(Tok.OR)
                        up == "NOT" -> out.add(Tok.NOT)
                        up.startsWith("NEAR") -> out.add(Tok.NEAR(digits(w.substringAfter('/', "5")).toIntOrNull() ?: 5))
                        else -> out.add(Tok.Word(w, false))
                    }
                    i = j
                }
            }
        }
        // واژه‌های بی‌گیومهٔ پشت‌سرهم ⇐ یک عبارت
        val merged = mutableListOf<Tok>()
        for (t in out) {
            val last = merged.lastOrNull()
            if (t is Tok.Word && !t.quoted && last is Tok.Word && !last.quoted) merged[merged.size - 1] = Tok.Word(last.text + " " + t.text, false)
            else merged.add(t)
        }
        return merged.filter { it !is Tok.Word || it.text.isNotBlank() }
    }

    fun parse(src: String): BoolNode {
        val toks = lex(src)
        if (toks.isEmpty()) throw BoolParseException("عبارتی برای جست‌وجو ننوشته‌اید.")
        var p = 0
        fun peek() = toks.getOrNull(p)

        lateinit var orExpr: () -> BoolNode
        fun primary(): BoolNode = when (val t = peek()) {
            is Tok.LP -> { p++; val e = orExpr(); if (peek() !is Tok.RP) throw BoolParseException("پرانتز بسته «)» جا افتاده است."); p++; e }
            is Tok.Word -> { p++; BoolNode.Leaf(t.text) }
            null -> throw BoolParseException("عبارت ناتمام است؛ پس از عملگر، واژه‌ای بنویسید.")
            else -> throw BoolParseException("پیش از عملگر، واژه یا عبارتی لازم است.")
        }
        fun unary(): BoolNode = if (peek() is Tok.NOT) { p++; BoolNode.Not(unary()) } else primary()
        fun near(): BoolNode {
            var l = unary()
            while (peek() is Tok.NEAR) { val n = (peek() as Tok.NEAR).n; p++; l = BoolNode.Near(l, unary(), n) }
            return l
        }
        fun and(): BoolNode {
            var l = near()
            while (true) {
                when (peek()) {
                    is Tok.AND -> { p++; l = BoolNode.And(l, near()) }
                    is Tok.NOT -> { p++; l = BoolNode.And(l, BoolNode.Not(near())) }
                    is Tok.LP, is Tok.Word -> l = BoolNode.And(l, near())
                    else -> return l
                }
            }
        }
        orExpr = {
            var l = and()
            while (peek() is Tok.OR) { p++; l = BoolNode.Or(l, and()) }
            l
        }
        val r = orExpr()
        if (p < toks.size) throw BoolParseException("پرانتز باز «(» جا افتاده یا عملگری اضافه است.")
        if (r.positiveLeaves().isEmpty()) throw BoolParseException("دست‌کم یک واژهٔ مثبت لازم است؛ فقط NOT کافی نیست.")
        return r
    }
}
