package ir.matnyaab.core.research

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * سازندهٔ سبک فایل Word (docx) بدون کتابخانهٔ سنگین: سبک‌های استاندارد (عنوان ۱ تا ۳، نقل‌قول، پانویس)،
 * راست‌به‌چپ، پانویس واقعی ورد، فهرست مطالب خودکار و شمارهٔ صفحه. خروجی با قالب‌های افزونهٔ ورد هم سازگار است.
 */
/** نویسه‌هایی که در XML مجاز نیستند (مثل \\f جداکنندهٔ صفحه) */
val BAD_XML = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\uFFFE\\uFFFF]")

class DocxWriter(private val layout: Layout = Layout()) {

    data class Layout(
        val rtl: Boolean = true,
        val fontFa: String = "B Nazanin",
        val fontEn: String = "Times New Roman",
        val size: Double = 13.0,
        val lineSpacing: Double = 1.3,
        val pageW: Double = 21.0, val pageH: Double = 29.7,
        val marginTop: Double = 2.5, val marginBottom: Double = 2.5, val marginRight: Double = 2.5, val marginLeft: Double = 2.5,
        val headingFont: String = "B Titr",
        val firstIndent: Double = 0.5,
    ) {
        companion object {
            val PAPERS = linkedMapOf("A4" to (21.0 to 29.7), "وزیری" to (16.5 to 23.5), "رقعی" to (14.5 to 21.5), "A5" to (14.8 to 21.0))
            fun paper(name: String, base: Layout = Layout()): Layout {
                val (w, h) = PAPERS[name] ?: (21.0 to 29.7)
                val small = w < 18
                return base.copy(pageW = w, pageH = h, marginTop = if (small) 2.0 else 2.5, marginBottom = if (small) 2.0 else 2.5,
                    marginRight = if (small) 2.0 else 2.5, marginLeft = if (small) 1.7 else 2.5, size = if (small) minOf(base.size, 12.0) else base.size)
            }
        }
    }

    private val body = StringBuilder()
    private val notes = mutableListOf<String>()

    private fun esc(s: String) = s.replace(BAD_XML, "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")

    private fun twip(cm: Double) = (cm * 567).toInt()

    private val latinRun = Regex("[A-Za-z][A-Za-z0-9 .,:;()'\"/&%+\\-]*[A-Za-z0-9.)]|[A-Za-z]")

    /** متن را به تکه‌های راست‌به‌چپ و لاتین تقسیم می‌کند تا هر کدام درست نمایش داده شود */
    private fun runs(text: String, extra: String = ""): String {
        if (!layout.rtl) return run(text, false, extra)
        val sb = StringBuilder(); var pos = 0
        for (m in latinRun.findAll(text)) {
            if (m.range.first > pos) sb.append(run(text.substring(pos, m.range.first), true, extra))
            sb.append(run(m.value, false, extra)); pos = m.range.last + 1
        }
        if (pos < text.length) sb.append(run(text.substring(pos), true, extra))
        return sb.toString()
    }

    private fun run(t: String, rtl: Boolean, extra: String): String {
        if (t.isEmpty()) return ""
        val lines = t.split('\n')
        val rpr = "<w:rPr>$extra${if (rtl) "<w:rtl/>" else ""}</w:rPr>"
        return lines.mapIndexed { i, l -> (if (i > 0) "<w:r>$rpr<w:br/></w:r>" else "") + "<w:r>$rpr<w:t xml:space=\"preserve\">${esc(l)}</w:t></w:r>" }.joinToString("")
    }

    private fun ppr(style: String?, jc: String? = null, extra: String = ""): String =
        "<w:pPr>${style?.let { "<w:pStyle w:val=\"$it\"/>" } ?: ""}${if (layout.rtl) "<w:bidi/>" else ""}${jc?.let { "<w:jc w:val=\"$it\"/>" } ?: ""}$extra</w:pPr>"

    private fun noteRef(text: String?): String {
        if (text.isNullOrBlank()) return ""
        notes.add(text)
        return "<w:r><w:rPr><w:rStyle w:val=\"FootnoteReference\"/></w:rPr><w:footnoteReference w:id=\"${notes.size}\"/></w:r>"
    }

    fun title(t: String) = apply { body.append("<w:p>${ppr("Title", "center")}${runs(t)}</w:p>") }
    fun subtitle(t: String) = apply { body.append("<w:p>${ppr("Subtitle", "center")}${runs(t)}</w:p>") }
    fun heading(level: Int, t: String) = apply { body.append("<w:p>${ppr("Heading${level.coerceIn(1, 3)}")}${runs(t)}</w:p>") }
    fun para(t: String, footnote: String? = null) = apply { body.append("<w:p>${ppr(null, "both")}${runs(t)}${noteRef(footnote)}</w:p>") }
    fun quote(t: String, footnote: String? = null) = apply { body.append("<w:p>${ppr("Quote", "both")}${runs(t)}${noteRef(footnote)}</w:p>") }
    fun note(t: String) = apply { body.append("<w:p>${ppr("Note")}${runs(t)}</w:p>") }
    fun bullet(t: String, footnote: String? = null) = apply { body.append("<w:p>${ppr("ListBullet")}${runs("•  $t")}${noteRef(footnote)}</w:p>") }
    fun meta(label: String, value: String) = apply {
        body.append("<w:p>${ppr("Note")}${runs("$label: ", "<w:b/><w:bCs/>")}${runs(value)}</w:p>")
    }
    /** بند با بخش‌های برجسته (عبارت‌های منطبق): زرد و پررنگ */
    fun marked(t: String, marks: List<IntRange>, styleId: String? = "Quote", footnote: String? = null) = apply {
        val sb = StringBuilder(); var pos = 0
        for (r in marks.sortedBy { it.first }) {
            val a = r.first.coerceIn(pos, t.length); val b = (r.last + 1).coerceIn(a, t.length)
            if (a > pos) sb.append(runs(t.substring(pos, a)))
            if (b > a) sb.append(runs(t.substring(a, b), "<w:b/><w:bCs/><w:highlight w:val=\"yellow\"/>"))
            pos = b
        }
        if (pos < t.length) sb.append(runs(t.substring(pos)))
        body.append("<w:p>${ppr(styleId, "both")}$sb${noteRef(footnote)}</w:p>")
    }

    fun pageBreak() = apply { body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>") }
    fun spacer() = apply { body.append("<w:p/>") }

    /** جدول ساده (برای کدگذاری و جمع‌بندی) */
    fun table(header: List<String>, rows: List<List<String>>) = apply {
        fun cell(t: String, head: Boolean) = "<w:tc><w:tcPr><w:tcW w:w=\"0\" w:type=\"auto\"/></w:tcPr><w:p>${ppr(null)}${runs(t, if (head) "<w:b/><w:bCs/>" else "")}</w:p></w:tc>"
        body.append("<w:tbl><w:tblPr><w:tblStyle w:val=\"TableGrid\"/>${if (layout.rtl) "<w:bidiVisual/>" else ""}<w:tblW w:w=\"5000\" w:type=\"pct\"/></w:tblPr>")
        body.append("<w:tr>${header.joinToString("") { cell(it, true) }}</w:tr>")
        rows.forEach { r -> body.append("<w:tr>${r.joinToString("") { cell(it, false) }}</w:tr>") }
        body.append("</w:tbl><w:p/>")
    }

    /** فهرست مطالب خودکار (ورد هنگام بازکردن به‌روزش می‌کند) */
    fun toc(label: String = "فهرست مطالب") = apply {
        heading(1, label)
        body.append("<w:p>${ppr(null)}<w:r><w:fldChar w:fldCharType=\"begin\" w:dirty=\"true\"/></w:r><w:r><w:instrText xml:space=\"preserve\"> TOC \\o \"1-3\" \\h \\z \\u </w:instrText></w:r><w:r><w:fldChar w:fldCharType=\"separate\"/></w:r>${runs("برای ساخت فهرست مطالب، روی این‌جا کلیک راست و «به‌روزرسانی فیلد» را بزنید.")}<w:r><w:fldChar w:fldCharType=\"end\"/></w:r></w:p>")
        pageBreak()
    }

    // ---------------------------------------------------------------- بسته‌بندی

    private fun rfonts(fa: String = layout.fontFa) = "<w:rFonts w:ascii=\"${esc(layout.fontEn)}\" w:hAnsi=\"${esc(layout.fontEn)}\" w:cs=\"${esc(fa)}\"/>"
    private fun sz(pt: Double) = "<w:sz w:val=\"${(pt * 2).toInt()}\"/><w:szCs w:val=\"${(pt * 2).toInt()}\"/>"

    private fun styles(): String {
        val bidi = if (layout.rtl) "<w:bidi/>" else ""
        val line = (240 * layout.lineSpacing).toInt()
        fun h(id: String, name: String, lvl: Int, pt: Double, center: Boolean = false) =
            "<w:style w:type=\"paragraph\" w:styleId=\"$id\"><w:name w:val=\"$name\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:uiPriority w:val=\"9\"/><w:qFormat/>" +
                "<w:pPr><w:keepNext/>$bidi<w:spacing w:before=\"360\" w:after=\"120\"/>${if (center) "<w:jc w:val=\"center\"/>" else ""}<w:ind w:firstLine=\"0\"/><w:outlineLvl w:val=\"$lvl\"/></w:pPr>" +
                "<w:rPr>${rfonts(layout.headingFont)}<w:b/><w:bCs/>${sz(pt)}</w:rPr></w:style>"
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:docDefaults><w:rPrDefault><w:rPr>${rfonts()}${sz(layout.size)}<w:lang w:val="en-US" w:bidi="fa-IR"/></w:rPr></w:rPrDefault>
<w:pPrDefault><w:pPr>$bidi<w:spacing w:after="120" w:line="$line" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/><w:pPr>$bidi<w:ind w:firstLine="${twip(layout.firstIndent)}"/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:qFormat/><w:pPr>$bidi<w:jc w:val="center"/><w:spacing w:before="1200" w:after="480"/><w:ind w:firstLine="0"/></w:pPr><w:rPr>${rfonts(layout.headingFont)}<w:b/><w:bCs/>${sz(26.0)}</w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Subtitle"><w:name w:val="Subtitle"/><w:basedOn w:val="Normal"/><w:qFormat/><w:pPr>$bidi<w:jc w:val="center"/><w:ind w:firstLine="0"/></w:pPr><w:rPr>${sz(layout.size + 2)}<w:color w:val="555555"/></w:rPr></w:style>
${h("Heading1", "heading 1", 0, layout.size + 5, center = true)}
${h("Heading2", "heading 2", 1, layout.size + 3)}
${h("Heading3", "heading 3", 2, layout.size + 1)}
<w:style w:type="paragraph" w:styleId="Quote"><w:name w:val="Quote"/><w:basedOn w:val="Normal"/><w:qFormat/><w:pPr>$bidi<w:ind w:left="567" w:right="567" w:firstLine="0"/><w:spacing w:before="60" w:after="160"/></w:pPr><w:rPr>${sz(layout.size - 1)}<w:color w:val="1F3A5F"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Note"><w:name w:val="Note"/><w:basedOn w:val="Normal"/><w:pPr>$bidi<w:ind w:firstLine="0"/><w:spacing w:after="60"/></w:pPr><w:rPr>${sz(layout.size - 2)}<w:color w:val="555555"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="ListBullet"><w:name w:val="List Bullet"/><w:basedOn w:val="Normal"/><w:pPr>$bidi<w:ind w:firstLine="0" w:right="284"/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="FootnoteText"><w:name w:val="footnote text"/><w:basedOn w:val="Normal"/><w:pPr>$bidi<w:spacing w:after="0" w:line="240" w:lineRule="auto"/><w:ind w:firstLine="0"/></w:pPr><w:rPr>${sz(layout.size - 3)}</w:rPr></w:style>
<w:style w:type="character" w:styleId="FootnoteReference"><w:name w:val="footnote reference"/><w:rPr><w:vertAlign w:val="superscript"/></w:rPr></w:style>
<w:style w:type="table" w:styleId="TableGrid"><w:name w:val="Table Grid"/><w:tblPr><w:tblBorders><w:top w:val="single" w:sz="4" w:color="A0A8B5"/><w:left w:val="single" w:sz="4" w:color="A0A8B5"/><w:bottom w:val="single" w:sz="4" w:color="A0A8B5"/><w:right w:val="single" w:sz="4" w:color="A0A8B5"/><w:insideH w:val="single" w:sz="4" w:color="A0A8B5"/><w:insideV w:val="single" w:sz="4" w:color="A0A8B5"/></w:tblBorders><w:tblCellMar><w:left w:w="100" w:type="dxa"/><w:right w:w="100" w:type="dxa"/></w:tblCellMar></w:tblPr></w:style>
</w:styles>"""
    }

    private fun footnotes(): String {
        val bidi = if (layout.rtl) "<w:bidi/>" else ""
        val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:footnotes xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:footnote w:type="separator" w:id="-1"><w:p><w:pPr>$bidi<w:spacing w:after="0" w:line="240" w:lineRule="auto"/></w:pPr><w:r><w:separator/></w:r></w:p></w:footnote>
<w:footnote w:type="continuationSeparator" w:id="0"><w:p><w:pPr>$bidi<w:spacing w:after="0" w:line="240" w:lineRule="auto"/></w:pPr><w:r><w:continuationSeparator/></w:r></w:p></w:footnote>
""")
        notes.forEachIndexed { i, n ->
            sb.append("<w:footnote w:id=\"${i + 1}\"><w:p><w:pPr><w:pStyle w:val=\"FootnoteText\"/>$bidi</w:pPr><w:r><w:rPr><w:rStyle w:val=\"FootnoteReference\"/></w:rPr><w:footnoteRef/></w:r>${runs(" $n")}</w:p></w:footnote>\n")
        }
        return sb.append("</w:footnotes>").toString()
    }

    private fun footer() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:pPr>${if (layout.rtl) "<w:bidi/>" else ""}<w:jc w:val="center"/></w:pPr><w:r><w:fldChar w:fldCharType="begin"/></w:r><w:r><w:instrText xml:space="preserve"> PAGE </w:instrText></w:r><w:r><w:fldChar w:fldCharType="separate"/></w:r><w:r><w:t>1</w:t></w:r><w:r><w:fldChar w:fldCharType="end"/></w:r></w:p></w:ftr>"""

    fun save(file: File) {
        file.parentFile?.mkdirs()
        val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        val sect = "<w:sectPr><w:footerReference w:type=\"default\" r:id=\"rIdFtr\"/><w:footnotePr><w:numFmt w:val=\"decimal\"/></w:footnotePr>" +
            "<w:pgSz w:w=\"${twip(layout.pageW)}\" w:h=\"${twip(layout.pageH)}\"/>" +
            "<w:pgMar w:top=\"${twip(layout.marginTop)}\" w:right=\"${twip(layout.marginRight)}\" w:bottom=\"${twip(layout.marginBottom)}\" w:left=\"${twip(layout.marginLeft)}\" w:header=\"567\" w:footer=\"567\" w:gutter=\"0\"/>" +
            (if (layout.rtl) "<w:bidi/>" else "") + "</w:sectPr>"
        val doc = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="$W" xmlns:r="$R"><w:body>$body$sect</w:body></w:document>"""
        val parts = linkedMapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
<Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/>
<Override PartName="/word/footnotes.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml"/>
<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/></Relationships>""",
            "docProps/core.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:creator>متن‌یاب</dc:creator></cp:coreProperties>""",
            "word/_rels/document.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rIdStyles" Type="$R/styles" Target="styles.xml"/><Relationship Id="rIdSettings" Type="$R/settings" Target="settings.xml"/><Relationship Id="rIdFn" Type="$R/footnotes" Target="footnotes.xml"/><Relationship Id="rIdFtr" Type="$R/footer" Target="footer1.xml"/></Relationships>""",
            "word/settings.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:settings xmlns:w="$W"><w:footnotePr><w:footnote w:id="-1"/><w:footnote w:id="0"/></w:footnotePr><w:compat><w:compatSetting w:name="compatibilityMode" w:uri="http://schemas.microsoft.com/office/word" w:val="15"/></w:compat><w:themeFontLang w:val="en-US" w:bidi="fa-IR"/></w:settings>""",
            "word/styles.xml" to styles(),
            "word/document.xml" to doc,
            "word/footnotes.xml" to footnotes(),
            "word/footer1.xml" to footer(),
        )
        val tmp = File(file.absolutePath + ".part")
        ZipOutputStream(tmp.outputStream()).use { z ->
            for ((name, content) in parts) { z.putNextEntry(ZipEntry(name)); z.write(content.toByteArray(Charsets.UTF_8)); z.closeEntry() }
        }
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) { tmp.copyTo(file, true); tmp.delete() }
    }
}

/** خروجی Markdown هم‌ارز (برای Obsidian، گیت‌هاب و…) */
class MarkdownWriter {
    private val sb = StringBuilder()
    private val notes = mutableListOf<String>()
    private fun ref(n: String?): String { if (n.isNullOrBlank()) return ""; notes.add(n); return "[^${notes.size}]" }
    fun title(t: String) = apply { sb.append("# ").append(t).append("\n\n") }
    fun subtitle(t: String) = apply { sb.append("_").append(t).append("_\n\n") }
    fun heading(level: Int, t: String) = apply { sb.append("#".repeat(level.coerceIn(1, 3) + 1)).append(' ').append(t).append("\n\n") }
    fun para(t: String, footnote: String? = null) = apply { sb.append(t).append(ref(footnote)).append("\n\n") }
    fun quote(t: String, footnote: String? = null) = apply { sb.append("> ").append(t.replace("\n", "\n> ")).append(ref(footnote)).append("\n\n") }
    fun note(t: String) = apply { sb.append("_").append(t).append("_\n\n") }
    fun bullet(t: String, footnote: String? = null) = apply { sb.append("- ").append(t).append(ref(footnote)).append("\n") }
    fun marked(t: String, marks: List<IntRange>, footnote: String? = null) = apply {
        val o = StringBuilder(); var pos = 0
        for (r in marks.sortedBy { it.first }) { val a = r.first.coerceIn(pos, t.length); val b = (r.last + 1).coerceIn(a, t.length); o.append(t, pos, a).append("**").append(t, a, b).append("**"); pos = b }
        o.append(t.substring(pos)); quote(o.toString(), footnote)
    }
    fun meta(label: String, value: String) = apply { sb.append("**").append(label).append(":** ").append(value).append("  \n") }
    fun table(header: List<String>, rows: List<List<String>>) = apply {
        fun c(s: String) = s.replace("|", "\\|").replace("\n", " ")
        sb.append("| ").append(header.joinToString(" | ") { c(it) }).append(" |\n|").append(header.joinToString("|") { "---" }).append("|\n")
        rows.forEach { r -> sb.append("| ").append(r.joinToString(" | ") { c(it) }).append(" |\n") }
        sb.append("\n")
    }
    fun save(file: File) {
        file.parentFile?.mkdirs()
        val out = StringBuilder(sb)
        if (notes.isNotEmpty()) { out.append("\n---\n\n"); notes.forEachIndexed { i, n -> out.append("[^${i + 1}]: ").append(n).append("\n") } }
        file.writeText(out.toString(), Charsets.UTF_8)
    }
}

/** رابط مشترک برای نوشتن یک خروجی پژوهشی در Word یا Markdown */
class ReportWriter(file: File, layout: DocxWriter.Layout = DocxWriter.Layout()) {
    val target = file
    private val md = file.name.endsWith(".md", true)
    private val d = if (md) null else DocxWriter(layout)
    private val m = if (md) MarkdownWriter() else null
    fun title(t: String) = apply { d?.title(t); m?.title(t) }
    fun subtitle(t: String) = apply { d?.subtitle(t); m?.subtitle(t) }
    fun heading(l: Int, t: String) = apply { d?.heading(l, t); m?.heading(l, t) }
    fun para(t: String, fn: String? = null) = apply { d?.para(t, fn); m?.para(t, fn) }
    fun marked(t: String, marks: List<IntRange>, fn: String? = null) = apply { d?.marked(t, marks, "Quote", fn); m?.marked(t, marks, fn) }
    fun quote(t: String, fn: String? = null) = apply { d?.quote(t, fn); m?.quote(t, fn) }
    fun note(t: String) = apply { d?.note(t); m?.note(t) }
    fun bullet(t: String, fn: String? = null) = apply { d?.bullet(t, fn); m?.bullet(t, fn) }
    fun meta(l: String, v: String) = apply { d?.meta(l, v); m?.meta(l, v) }
    fun table(h: List<String>, r: List<List<String>>) = apply { d?.table(h, r); m?.table(h, r) }
    fun toc() = apply { d?.toc() }
    fun pageBreak() = apply { d?.pageBreak() }
    fun save() { d?.save(target); m?.save(target) }
}
