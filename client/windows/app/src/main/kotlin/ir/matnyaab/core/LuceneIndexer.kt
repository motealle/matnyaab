package ir.matnyaab.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.LowerCaseFilter
import org.apache.lucene.analysis.Tokenizer
import org.apache.lucene.analysis.pattern.PatternTokenizer
import org.apache.lucene.document.*
import org.apache.lucene.index.*
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.*
import org.apache.lucene.queries.spans.*
import org.apache.lucene.store.FSDirectory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.random.Random

/**
 * پورت دقیق whooshIndexer بر پایه Apache Lucene 9.
 *
 * طرح (Schema) معادل:
 *  - name: TEXT stored
 *  - path: ID stored unique
 *  - size: NUMERIC int64 stored
 *  - time: STORED
 *  - content: TEXT stored با آنالایزر معادل RegexTokenizer(r'[@#ًٌٍَُِّْ\w+]*')
 */
class LuceneIndexer(private val settings: AppSettingsManager) {

    companion object {
        /** همان الگوی توکن‌ساز نسخه ووش: حروف کلمه + اعراب عربی + @ #  */
        val TOKEN_PATTERN: Pattern = Pattern.compile("[@#a-zA-Z0-9\u0600-\u06FF]+")
        val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    }

    /** آنالایزر معادل analysis.RegexTokenizer + LowercaseFilter */
    class ArabicRegexAnalyzer : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val tokenizer: Tokenizer = PatternTokenizer(TOKEN_PATTERN, 0)
            return TokenStreamComponents(tokenizer, LowerCaseFilter(tokenizer))
        }
    }

    val analyzer = ArabicRegexAnalyzer()

    /** معادل allObjects: لیست فهرست‌های باز */
    val allObjects = mutableListOf<IndexerInfo>()

    /** نتایج آخرین جستجو — معادل searchResult */
    val searchResult = mutableListOf<SearchHit>()

    var lastQueryWords: List<String> = emptyList(); private set
    var lastQuerySlop: Int = 0; private set
    var lastAllWords: List<String> = emptyList(); private set
    var lastAnyWords: List<String> = emptyList(); private set

    // ------------------------------------------------------------------ ساخت و بارگذاری

    /** معادل makeIndexDir: نام پوشه = basename_عددتصادفی */
    fun makeIndexDir(rootPath: String): Path {
        val baseName = File(rootPath).name
        val dir = Constants.INDEX_DIR.resolve(baseName + "_" + Random.nextInt(0, 1_000_000))
        Files.createDirectories(dir)
        return dir
    }

    /** معادل createIndexer + loadIndexer */
    fun loadIndexer(indexName: String, savePath: String, rootPath: String) {
        allObjects.removeAll { it.savePath == savePath }
        allObjects.add(IndexerInfo(indexName, rootPath, savePath))
    }

    fun findBySavePath(savePath: String): IndexerInfo? = allObjects.find { it.savePath == savePath }

    /** معادل deleteIndexer: پاک‌سازی و حذف پوشه فهرست */
    fun deleteIndexer(savePath: String) {
        allObjects.removeAll { it.savePath == savePath }
        val dir = File(savePath)
        if (dir.exists()) dir.deleteRecursively()
    }

    fun openWriter(savePath: Path): IndexWriter {
        val cfg = IndexWriterConfig(analyzer)
        cfg.ramBufferSizeMB = 2048.0 // معادل writer(limitmb=2048)
        return IndexWriter(FSDirectory.open(savePath), cfg)
    }

    /** معادل indexDocument: افزودن سند به فهرست */
    fun addDocument(writer: IndexWriter, name: String, path: String, size: Long, time: Double, content: String) {
        val doc = Document()
        doc.add(TextField("name", name, Field.Store.YES))
        doc.add(StringField("path", path, Field.Store.YES))
        doc.add(NumericDocValuesField("size", size))
        doc.add(StoredField("size", size))
        doc.add(StoredField("time", time))
        doc.add(TextField("content", content, Field.Store.YES))
        writer.updateDocument(Term("path", path), doc)
    }

    fun deleteByPath(writer: IndexWriter, path: String) {
        writer.deleteDocuments(Term("path", path))
    }

    // ------------------------------------------------------------------ fileInfo.json / file_list.txt

    fun saveFileInfo(savePath: Path, indexName: String, rootPath: String) {
        val map = mapOf("name" to indexName, "path" to rootPath)
        Files.writeString(savePath.resolve("fileInfo.json"), gson.toJson(map), Charsets.UTF_8)
    }

    fun readFileInfo(savePath: Path): Map<String, String>? = try {
        val type = object : TypeToken<Map<String, String>>() {}.type
        gson.fromJson(Files.readString(savePath.resolve("fileInfo.json"), Charsets.UTF_8), type)
    } catch (e: Exception) { null }

    fun saveFileList(savePath: Path, files: List<FileEntry>) {
        val sb = StringBuilder()
        for (f in files) sb.append(gson.toJson(listOf(f.filename, f.size, f.time))).append('\n')
        Files.writeString(savePath.resolve("file_list.txt"), sb.toString(), Charsets.UTF_8)
    }

    fun readFileList(savePath: Path): List<FileEntry> {
        val file = savePath.resolve("file_list.txt")
        if (!Files.exists(file)) return emptyList()
        val out = mutableListOf<FileEntry>()
        for (line in Files.readAllLines(file, Charsets.UTF_8)) {
            if (line.isBlank()) continue
            try {
                val arr = gson.fromJson(line, List::class.java)
                out.add(FileEntry(arr[0] as String, (arr[1] as Number).toLong(), (arr[2] as Number).toDouble()))
            } catch (e: Exception) { /* خط خراب را رد می‌کنیم */ }
        }
        return out
    }

    /** مجموع حجم فایل‌های فهرست‌شده — معادل UsedSpace */
    fun usedSpace(): Long {
        var total = 0L
        for (obj in allObjects)
            for (f in readFileList(Path.of(obj.savePath))) total += f.size
        return total
    }

    // ------------------------------------------------------------------ ساخت کوئری

    /** معادل makeSimpleQuery: QueryParser روی فیلد content */
    fun makeSimpleQuery(text: String): Query {
        val parser = QueryParser("content", analyzer)
        parser.allowLeadingWildcard = true
        return try { parser.parse(QueryParser.escape(text).let { if (text.any { c -> c == '*' || c == '?' }) text else it }) }
        catch (e: Exception) { parser.parse(QueryParser.escape(text)) }
    }

    private fun termOrWildcard(word: String): Query {
        val w = word.lowercase()
        return if (w.contains('*') || w.contains('?')) WildcardQuery(Term("content", w))
        else TermQuery(Term("content", w))
    }

    private fun spanTermOrWildcard(word: String): SpanQuery {
        val w = word.lowercase()
        return if (w.contains('*') || w.contains('?'))
            SpanMultiTermQueryWrapper(WildcardQuery(Term("content", w)))
        else SpanTermQuery(Term("content", w))
    }

    /**
     * معادل makeAdvancedQuery:
     *  - slop==0: And(همه کلمات) + Not(هیچ‌کدام) + Or(یکی از کلمات)
     *  - slop>0: SpanNear بین کلمات «همه کلمات» با فاصله حداکثر slop
     */
    fun makeAdvancedQuery(allWords: List<String>, anyWords: List<String>, noneWords: List<String>, slop: Int): Query {
        lastQueryWords = allWords + anyWords
        lastQuerySlop = slop
        lastAllWords = allWords
        lastAnyWords = anyWords
        val builder = BooleanQuery.Builder()
        if (slop == 0) {
            for (w in allWords) builder.add(termOrWildcard(w), BooleanClause.Occur.MUST)
        } else if (allWords.isNotEmpty()) {
            val spans = allWords.map { spanTermOrWildcard(it) }.toTypedArray()
            // inOrder=true: کلمات باید به همان ترتیب تایپ‌شده در متن بیایند (هماهنگ با هایلایتر)
            val near: Query = if (spans.size == 1) spans[0] else SpanNearQuery(spans, slop, true)
            builder.add(near, BooleanClause.Occur.MUST)
        }
        if (anyWords.isNotEmpty()) {
            val orB = BooleanQuery.Builder()
            for (w in anyWords) orB.add(termOrWildcard(w), BooleanClause.Occur.SHOULD)
            builder.add(orB.build(), BooleanClause.Occur.MUST)
        }
        for (w in noneWords) builder.add(termOrWildcard(w), BooleanClause.Occur.MUST_NOT)
        if (allWords.isEmpty() && anyWords.isEmpty())
            builder.add(MatchAllDocsQuery(), BooleanClause.Occur.MUST)
        return builder.build()
    }

    // ------------------------------------------------------------------ جستجو

    /**
     * معادل search: روی همه فهرست‌های فعال با maskedPath (مسیرهای حذف‌شده از محدوده)
     * امتیاز = int(score*100)
     */
    fun search(query: Query, selectedObjects: List<IndexerInfo>, maskedPaths: Set<String>): List<SearchHit> {
        searchResult.clear()
        for (obj in selectedObjects) {
            try {
                FSDirectory.open(Path.of(obj.savePath)).use { dir ->
                    DirectoryReader.open(dir).use { reader ->
                        val searcher = IndexSearcher(reader)
                        val top = searcher.search(query, Integer.MAX_VALUE)
                        for (sd in top.scoreDocs) {
                            val doc = searcher.storedFields().document(sd.doc)
                            val path = doc.get("path") ?: continue
                            if (path in maskedPaths) continue
                            searchResult.add(
                                SearchHit(
                                    indexName = obj.indexName,
                                    path = path,
                                    name = doc.get("name") ?: File(path).name,
                                    score = (sd.score * 100).toInt(),
                                    size = doc.get("size")?.toLongOrNull() ?: 0L,
                                    time = doc.get("time")?.toDoubleOrNull() ?: 0.0,
                                    content = doc.get("content") ?: "",
                                )
                            )
                        }
                    }
                }
            } catch (e: IndexNotFoundException) { /* فهرست خالی */ }
            catch (e: Exception) { /* فهرست خراب — رد می‌شود */ }
        }
        return searchResult
    }

    // ------------------------------------------------------------------ هایلایت

    /**
     * یافتن بازه‌های هایلایت — هماهنگ کامل با منطق کوئری:
     *  - slop==0: هر کلمه («همه کلمات» + «یکی از کلمات») جداگانه هایلایت می‌شود
     *  - slop>0: «همه کلمات» به صورت عبارت مرتب با بودجه کل فاصله = slop
     *            (دقیقا مطابق SpanNearQuery(inOrder=true)) و «یکی از کلمات» جداگانه
     */
    private fun findMatchRanges(content: String, allWords: List<String>, anyWords: List<String>, slop: Int): Pair<List<IntRange>, Int> {
        if (allWords.isEmpty() && anyWords.isEmpty()) return Pair(emptyList(), 0)
        val tokenRe = Regex("[@#a-zA-Z0-9\u0600-\u06FF]+")
        data class Tok(val start: Int, val end: Int, val text: String)
        val tokens = tokenRe.findAll(content).map { Tok(it.range.first, it.range.last + 1, it.value.lowercase()) }.toList()

        val ranges = mutableListOf<IntRange>()
        var count = 0

        val phrase = if (slop > 0 && allWords.size >= 2) allWords else emptyList()
        val singleWords = if (phrase.isEmpty()) allWords + anyWords else anyWords

        // هایلایت تک‌کلمه‌ای
        if (singleWords.isNotEmpty()) {
            val singlePatterns = singleWords.map { wordToRegex(it) }
            for (t in tokens) if (singlePatterns.any { it.matches(t.text) }) { ranges.add(t.start until t.end); count++ }
        }

        // هایلایت عبارت مرتب: بودجه کل فاصله بین کلمات = slop (هماهنگ با SpanNearQuery(inOrder=true))
        if (phrase.isNotEmpty()) {
            val phrasePatterns = phrase.map { wordToRegex(it) }
            var i = 0
            while (i < tokens.size) {
                if (phrasePatterns[0].matches(tokens[i].text)) {
                    var cur = i; var ok = true; var budget = slop
                    val matchIdx = mutableListOf(i)
                    for (w in 1 until phrase.size) {
                        var found = -1
                        var j = cur + 1
                        while (j < tokens.size && (j - cur - 1) <= budget) {
                            if (phrasePatterns[w].matches(tokens[j].text)) { found = j; break }
                            j++
                        }
                        if (found < 0) { ok = false; break }
                        budget -= (found - cur - 1)
                        matchIdx.add(found); cur = found
                    }
                    if (ok) {
                        ranges.add(tokens[matchIdx.first()].start until tokens[matchIdx.last()].end)
                        count++
                        i = matchIdx.last() + 1
                        continue
                    }
                }
                i++
            }
        }
        ranges.sortBy { it.first }
        return Pair(ranges, count)
    }

    /** هایلایت بر اساس آخرین کوئری اجراشده — جایگزین fastHighlight (هماهنگ کامل با کوئری) */
    fun highlightLastQuery(content: String): Pair<String, Int> {
        val (ranges, count) = findMatchRanges(content, lastAllWords, lastAnyWords, lastQuerySlop)
        val sb = StringBuilder()
        var pos = 0
        for (r in ranges) {
            if (r.first < pos) continue
            sb.append(escapeHtml(content.substring(pos, r.first)))
            sb.append("<span style=\" background-color:${Constants.GLOBAL_SEARCH_HIGHLIGHT_COLOR};\">")
            sb.append(escapeHtml(content.substring(r.first, r.last + 1)))
            sb.append("</span>")
            pos = r.last + 1
        }
        sb.append(escapeHtml(content.substring(pos)))
        return Pair(sb.toString(), count)
    }

    /** فقط شمارش موارد یافت‌شده در یک متن (برای مجموع کل نتایج در نوار بالای جدول) */
    fun countMatches(content: String): Int =
        findMatchRanges(content, lastAllWords, lastAnyWords, lastQuerySlop).second

    private fun wordToRegex(word: String): Regex {
        val w = word.lowercase()
        val sb = StringBuilder()
        for (c in w) {
            when (c) {
                '*' -> sb.append(".*")
                '?' -> sb.append('.')
                else -> sb.append(Regex.escape(c.toString()))
            }
        }
        return Regex(sb.toString())
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
