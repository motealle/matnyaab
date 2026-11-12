package ir.matnyaab.core.research

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * نویسندهٔ سبک Excel (xlsx) بدون وابستگی: یک برگهٔ راست‌به‌چپ، سرستون پررنگ و ثابت، شکستن خط،
 * و متن غنی در خانه‌ها (بخش‌های منطبق، پررنگ و قرمز).
 */
class XlsxWriter(private val sheetName: String = "نتایج") {
    /** یک خانه: متن ساده، عدد، یا متن با بخش‌های برجسته */
    sealed class Cell {
        data class Text(val v: String) : Cell()
        data class Num(val v: Double) : Cell()
        data class Rich(val v: String, val marks: List<IntRange>) : Cell()
    }

    private val rows = mutableListOf<List<Cell>>()
    private var widths: List<Int> = emptyList()

    fun header(cols: List<String>, widths: List<Int>) = apply { rows.add(0, cols.map { Cell.Text(it) }); this.widths = widths }
    fun row(cells: List<Cell>) = apply { rows.add(cells) }

    private fun esc(s: String) = s.replace(BAD_XML, "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .let { if (it.length > 32000) it.take(32000) + "…" else it }

    private fun col(i: Int): String { var n = i + 1; val sb = StringBuilder(); while (n > 0) { val r = (n - 1) % 26; sb.insert(0, ('A' + r)); n = (n - 1) / 26 }; return sb.toString() }

    private fun cellXml(c: Cell, ref: String, head: Boolean): String = when (c) {
        is Cell.Num -> "<c r=\"$ref\" s=\"2\"><v>${c.v}</v></c>"
        is Cell.Text -> "<c r=\"$ref\" t=\"inlineStr\" s=\"${if (head) 1 else 2}\"><is><t xml:space=\"preserve\">${esc(c.v)}</t></is></c>"
        is Cell.Rich -> {
            val t = c.v.take(32000); val sb = StringBuilder(); var pos = 0
            fun plain(s: String) { if (s.isNotEmpty()) sb.append("<r><t xml:space=\"preserve\">${esc(s)}</t></r>") }
            for (m in c.marks.sortedBy { it.first }) {
                val a = m.first.coerceIn(pos, t.length); val b = (m.last + 1).coerceIn(a, t.length)
                plain(t.substring(pos, a))
                if (b > a) sb.append("<r><rPr><b/><color rgb=\"FFC00000\"/></rPr><t xml:space=\"preserve\">${esc(t.substring(a, b))}</t></r>")
                pos = b
            }
            plain(t.substring(pos))
            "<c r=\"$ref\" t=\"inlineStr\" s=\"2\"><is>$sb</is></c>"
        }
    }

    fun save(file: File) {
        file.parentFile?.mkdirs()
        val sheet = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView rightToLeft="1" workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        if (widths.isNotEmpty()) sheet.append("<cols>").append(widths.mapIndexed { i, w -> "<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>" }.joinToString("")).append("</cols>")
        sheet.append("<sheetData>")
        rows.forEachIndexed { r, cells -> sheet.append("<row r=\"${r + 1}\">").append(cells.mapIndexed { i, c -> cellXml(c, col(i) + (r + 1), r == 0) }.joinToString("")).append("</row>") }
        sheet.append("</sheetData>")
        if (rows.size > 1 && rows[0].isNotEmpty()) sheet.append("<autoFilter ref=\"A1:${col(rows[0].size - 1)}${rows.size}\"/>")
        sheet.append("</worksheet>")
        val styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Tahoma"/></font><font><b/><sz val="11"/><name val="Tahoma"/></font></fonts>
<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFE8EEF7"/></patternFill></fill></fills>
<borders count="1"><border/></borders><cellStyleXfs count="1"><xf/></cellStyleXfs>
<cellXfs count="3"><xf/><xf fontId="1" fillId="2" applyFont="1" applyFill="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1" readingOrder="2"/></xf><xf applyAlignment="1"><alignment vertical="top" wrapText="1" readingOrder="2"/></xf></cellXfs></styleSheet>"""
        ZipOutputStream(file.outputStream()).use { z ->
            fun put(n: String, s: String) { z.putNextEntry(ZipEntry(n)); z.write(s.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="${esc(sheetName.take(31))}" sheetId="1" r:id="rId1"/></sheets>${if (rows.size > 1) "<definedNames><definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"0\" hidden=\"1\">'${esc(sheetName.take(31))}'!\$A\$1:\$${col(rows[0].size - 1)}\$${rows.size}</definedName></definedNames>" else ""}</workbook>""")
            put("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            put("xl/styles.xml", styles)
            put("xl/worksheets/sheet1.xml", sheet.toString())
        }
    }
}
