package ir.matnyaab.ui

import ir.matnyaab.core.AppSettingsManager
import ir.matnyaab.core.LuceneIndexer
import ir.matnyaab.core.TikaExtractor
import ir.matnyaab.core.quran.Matcher
import ir.matnyaab.core.quran.QHit
import ir.matnyaab.core.quran.QKind
import ir.matnyaab.core.quran.Quran
import ir.matnyaab.core.quran.Scanner
import ir.matnyaab.core.research.DocxWriter
import ir.matnyaab.core.research.ReportWriter
import ir.matnyaab.core.research.XlsxWriter
import ir.matnyaab.util.PersianUtil
import javafx.application.Platform
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.stage.FileChooser
import javafx.stage.Stage
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * کشف‌الآیات: یک آیه در کل کتابخانه، آیات یک سند، و فهرست و بسامد آیات کل کتابخانه.
 * نقل کامل، نقل بخشی، ارجاع شماره‌ای، ترجمه و اشارهٔ مفهومی جدا نمایش داده می‌شوند و هر یافته اطمینان و متن شاهد دارد.
 */
class QuranWindow(private val owner: Stage, private val settings: AppSettingsManager, private val indexer: LuceneIndexer,
                  private val openAt: (path: String, start: Int, end: Int, note: String) -> Unit) {

    companion object {
        private var open: QuranWindow? = null
        fun show(owner: Stage, settings: AppSettingsManager, indexer: LuceneIndexer, openAt: (String, Int, Int, String) -> Unit, file: File? = null) {
            val w = open ?: QuranWindow(owner, settings, indexer, openAt).also { open = it }
            w.stage.show(); w.stage.toFront()
            if (file != null) w.analyzeFile(file)
        }
        private fun fa(n: Int) = PersianUtil.toPersianNumbers(n)
    }

    /** یک ردیف جدول یافته‌ها */
    data class Row(val path: String, val name: String, val hit: QHit)

    val stage = Stage().apply { initOwner(owner); title = "کشف‌الآیات"; width = 1100.0; height = 740.0 }
    private val tabs = TabPane().apply { tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE; styleClass.add("research-tabs") }
    private val tVerse = Tab("آیه در کتابخانه"); private val tDoc = Tab("آیات یک سند"); private val tAll = Tab("کل کتابخانه")
    private val basmala = CheckBox("«بسم الله…» آغاز سندها را هم نشان بده")
    private val kinds = QKind.values().associateWith { k -> CheckBox(k.title).apply { isSelected = true } }
    private var archive: List<Scanner.DocHits>? = null
    private val stop = AtomicBoolean(false)

    init {
        tVerse.content = verseTab(); tDoc.content = docTab(); tAll.content = allTab()
        tabs.tabs.addAll(tVerse, tDoc, tAll)
        val addTr = Button("ترجمه‌ها…").apply { style = Theme.addButton(); setOnAction { translations() } }
        val filters = HBox(10.0, Label("نمایش:").apply { styleClass.add("muted") }, *kinds.values.toTypedArray(), Region().also { HBox.setHgrow(it, Priority.ALWAYS) }, basmala, addTr)
            .apply { padding = Insets(8.0, 12.0, 8.0, 12.0); alignment = Pos.CENTER_RIGHT }
        val credit = Label("متن قرآن: پروژهٔ Tanzil (tanzil.net) — ترجمه‌ها: پرونده‌هایی که خودتان افزوده‌اید").apply { styleClass.add("muted"); style = Theme.FONT + "-fx-font-size: 11px;"; padding = Insets(4.0, 12.0, 6.0, 12.0) }
        val root = BorderPane(tabs).apply { top = filters; bottom = credit; nodeOrientation = NodeOrientation.RIGHT_TO_LEFT; style = Theme.FONT }
        stage.scene = Scene(root)
        Theme.install(stage.scene, stage)
        stage.setOnHidden { stop.set(true); open = null }
        basmala.setOnAction { archive = null }
    }

    /** ترجمه‌های فارسی برای تشخیص «ترجمهٔ آیه»: کاربر پروندهٔ متنی Tanzil را از tanzil.net/trans دریافت و این‌جا می‌افزاید */
    private fun translations() {
        val have = Quran.translationFiles()
        val msg = Label((if (have.isEmpty()) "هنوز ترجمه‌ای نیفزوده‌اید." else "ترجمه‌های افزوده: " + have.joinToString("، ") { it.nameWithoutExtension }) +
            "\n\nبرای تشخیص ترجمهٔ آیات در اسناد، ترجمهٔ دلخواه (مثلاً مکارم شیرازی، انصاریان، فولادوند یا الهی قمشه‌ای) را از سایت Tanzil با قالب «Text (with aya numbers)» دریافت کنید و این‌جا بیفزایید. " +
            "می‌توانید چند ترجمه بیفزایید؛ هر کدام جداگانه بررسی می‌شود. استفاده از هر ترجمه تابع شرایط ناشر آن است.").apply { isWrapText = true; maxWidth = 480.0 }
        val link = Hyperlink("tanzil.net/trans").apply { setOnAction { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI("https://tanzil.net/trans/")) } } }
        val add = Button("افزودن پروندهٔ ترجمه…").apply { style = Theme.primaryButton(); setOnAction {
            FileChooser().apply { extensionFilters.add(FileChooser.ExtensionFilter("ترجمهٔ Tanzil", "*.txt")) }.showOpenDialog(stage)?.let { f ->
                val err = runCatching { Quran.importTranslation(f) }.getOrElse { it.message }
                CustomMessageBox.show(stage, "ترجمه‌ها", err ?: "ترجمهٔ «${f.nameWithoutExtension}» افزوده شد. برای به‌کارگیری در کل کتابخانه، «بررسی دوباره» را بزنید.")
                archive = null
            } } }
        val d = Dialog<ButtonType>().apply { initOwner(stage); title = "ترجمه‌های قرآن"; headerText = null
            dialogPane.content = VBox(10.0, msg, link, add).apply { padding = Insets(10.0) }; dialogPane.nodeOrientation = NodeOrientation.RIGHT_TO_LEFT; dialogPane.style = Theme.FONT
            dialogPane.buttonTypes.add(ButtonType("بستن", ButtonBar.ButtonData.CANCEL_CLOSE)) }
        d.showAndWait()
    }

    private fun bg(work: () -> Unit) = Thread { work() }.apply { isDaemon = true }.start()
    private fun shown(h: QHit) = kinds[h.kind]?.isSelected != false

    // ---------------------------------------------------------------- جدول مشترک یافته‌ها

    private fun hitTable(showDoc: Boolean): TableView<Row> {
        val t = TableView<Row>()
        fun <T> col(title: String, w: Double, f: (Row) -> T) = TableColumn<Row, T>(title).apply { prefWidth = w; setCellValueFactory { SimpleObjectProperty(f(it.value)) } }
        if (showDoc) t.columns.add(col("سند", 200.0) { it.name })
        t.columns.addAll(
            col("آیه", 110.0) { Quran.label(it.hit.sura, it.hit.aya) },
            col("نوع", 100.0) { it.hit.kind.title },
            col("اطمینان", 70.0) { fa(it.hit.confidence) + "٪" },
            col("متن شاهد", 520.0) { it.hit.witness.replace('\n', ' ').take(300) + if (it.hit.alternatives > 0) "  (همین متن در ${fa(it.hit.alternatives)} آیهٔ دیگر هم هست)" else "" },
        )
        t.placeholder = Label("یافته‌ای نیست").apply { styleClass.add("muted") }
        t.setRowFactory { TableRow<Row>().apply { setOnMouseClicked { e -> if (e.clickCount == 2 && item != null) item.let { r ->
            openAt(r.path, r.hit.start, r.hit.end, "کشف‌الآیات: ${Quran.label(r.hit.sura, r.hit.aya)} — ${r.hit.kind.title} (اطمینان ${fa(r.hit.confidence)}٪)")
        } } } }
        return t
    }

    private fun exportRows(rows: List<Row>, title: String, xlsx: Boolean) {
        if (rows.isEmpty()) return
        val out = FileChooser().apply { initialDirectory = ResearchWindow.docsDir(); initialFileName = ResearchWindow.safeName(title) + if (xlsx) ".xlsx" else ".docx"
            extensionFilters.add(if (xlsx) FileChooser.ExtensionFilter("Excel", "*.xlsx") else FileChooser.ExtensionFilter("Word", "*.docx")) }.showSaveDialog(stage) ?: return
        runCatching {
            if (xlsx) {
                val x = XlsxWriter("کشف‌الآیات").header(listOf("سند", "مسیر", "سوره", "آیه", "نام سوره", "نوع", "اطمینان", "متن آیه", "متن شاهد"), listOf(26, 36, 7, 7, 12, 12, 9, 50, 60))
                rows.forEach { r -> x.row(listOf(XlsxWriter.Cell.Text(r.name), XlsxWriter.Cell.Text(r.path), XlsxWriter.Cell.Num(r.hit.sura.toDouble()), XlsxWriter.Cell.Num(r.hit.aya.toDouble()),
                    XlsxWriter.Cell.Text(Quran.NAMES[r.hit.sura - 1]), XlsxWriter.Cell.Text(r.hit.kind.title), XlsxWriter.Cell.Num(r.hit.confidence.toDouble()),
                    XlsxWriter.Cell.Text(Quran.verse(r.hit.sura, r.hit.aya)?.text ?: ""), XlsxWriter.Cell.Text(r.hit.witness))) }
                x.save(out)
            } else {
                val w = ReportWriter(out, DocxWriter.Layout())
                w.title(title); w.subtitle("${fa(rows.size)} یافته · متن قرآن از پروژهٔ Tanzil")
                rows.groupBy { it.hit.key }.forEach { (_, rs) ->
                    val h = rs.first().hit
                    w.heading(2, "سورهٔ " + Quran.label(h.sura, h.aya))
                    w.quote(Quran.verse(h.sura, h.aya)?.text ?: "")
                    rs.forEach { r -> w.note("${r.name} · ${r.hit.kind.title} · اطمینان ${fa(r.hit.confidence)}٪"); w.para(r.hit.witness) }
                }
                w.save()
            }
            java.awt.Desktop.getDesktop().open(out)
        }.onFailure { CustomMessageBox.show(stage, "کشف‌الآیات", "ذخیره نشد: ${it.message}") }
    }

    /** کل کتابخانه را (با حافظهٔ نهان) پیمایش کن؛ پیشرفت در نوار و کنار ساعت */
    private fun ensureArchive(bar: ProgressBar, status: Label, then: (List<Scanner.DocHits>) -> Unit) {
        archive?.let { then(it); return }
        stop.set(false); bar.isVisible = true; bar.progress = ProgressBar.INDETERMINATE_PROGRESS
        status.text = "در حال بررسی کتابخانه… (بار نخست کمی طول می‌کشد؛ دفعه‌های بعد فوری است)"
        val cols = indexer.allObjects.toList()
        bg {
            val r = runCatching { Scanner.scan(cols, basmala.isSelected, stop) { d, t, n ->
                Platform.runLater { bar.progress = if (t > 0) d.toDouble() / t else -1.0; status.text = "بررسی ${fa(d)} از ${fa(t)} سند · $n" }
                TrayProgress.update(if (t > 0) d.toDouble() / t else -1.0, n, "کشف‌الآیات")
            } }
            Platform.runLater {
                bar.isVisible = false
                r.onSuccess { archive = it; then(it) }.onFailure { status.text = "خطا: ${it.message}" }
            }
        }
    }

    // ---------------------------------------------------------------- ۱. یک آیه در کتابخانه

    private fun verseTab(): Node {
        val sura = ComboBox<String>().apply { items.setAll(Quran.NAMES.mapIndexed { i, n -> "${fa(i + 1)}. $n" }); selectionModel.select(1); prefWidth = 180.0 }
        val aya = Spinner<Int>(1, 286, 255).apply { isEditable = true; prefWidth = 90.0 }
        sura.setOnAction { (aya.valueFactory as SpinnerValueFactory.IntegerSpinnerValueFactory).max = Quran.counts[sura.selectionModel.selectedIndex + 1] }
        val text = TextArea().apply { promptText = "یا متن عربی آیه (کامل یا بخشی) را این‌جا بچسبانید…"; prefRowCount = 2; isWrapText = true }
        val verseView = Label().apply { isWrapText = true; style = Theme.FONT + "-fx-font-size: 17px;" }
        val trView = Label().apply { isWrapText = true; styleClass.add("muted") }
        val allusion = CheckBox("اشاره‌های مفهومی را هم بیاب (با بستهٔ جست‌وجوی معنایی)").apply {
            isDisable = !ir.matnyaab.core.semantic.SemanticService.usable(settings)
            tooltip = Tooltip("متن‌هایی که مضمون آیه را بدون عبارت عربی آن آورده‌اند؛ اطمینان این دسته کمتر است و جدا نمایش داده می‌شود.")
        }
        val bar = ProgressBar().apply { maxWidth = Double.MAX_VALUE; isVisible = false }
        val status = Label().apply { styleClass.add("muted") }
        val table = hitTable(true)
        var current: Pair<Int, Int>? = null

        fun showVerse(s: Int, a: Int) {
            current = s to a
            verseView.text = "﴿" + (Quran.verse(s, a)?.text ?: "") + "﴾  — " + Quran.label(s, a)
            trView.text = Quran.translationFiles().firstOrNull()?.let { Quran.loadTranslation(it)["$s:$a"] } ?: ""
        }
        fun search() {
            val pasted = text.text.trim()
            if (pasted.isNotEmpty()) {
                val m = Matcher.find(pasted, true, false).maxByOrNull { it.confidence }
                if (m == null) { status.text = "این متن با آیه‌ای تطبیق داده نشد؛ دست‌کم چهار واژهٔ پیاپی از آیه لازم است."; return }
                sura.selectionModel.select(m.sura - 1); aya.valueFactory.value = m.aya
            }
            val s = sura.selectionModel.selectedIndex + 1; val a = aya.value
            showVerse(s, a)
            ensureArchive(bar, status) { list ->
                val rows = list.flatMap { d -> d.hits.filter { it.sura == s && (it.aya == a || (it.kind == QKind.REFERENCE && a in it.aya..it.ayaTo)) && shown(it) }.map { Row(d.path, d.name, it) } }
                table.items.setAll(rows)
                status.text = "${fa(rows.map { it.path }.distinct().size)} سند · ${fa(rows.size)} موضع"
                if (allusion.isSelected && !allusion.isDisable && kinds[QKind.ALLUSION]!!.isSelected) {
                    val q = (trView.text.ifBlank { Quran.verse(s, a)?.text ?: "" })
                    val have = rows.map { it.path }.toSet()
                    bg {
                        val sem = runCatching { ir.matnyaab.core.semantic.SemanticService.search(q, indexer.allObjects.toList()) }.getOrDefault(emptyList())
                            .filter { it.path !in have && it.similarity >= 0.84 }.take(30)
                        Platform.runLater {
                            table.items.addAll(sem.map { h -> Row(h.path, File(h.path).name, QHit(s, a, QKind.ALLUSION, ir.matnyaab.core.search.Ranking.semanticScore(h.similarity).coerceAtMost(80), maxOf(0, h.chunkStart), maxOf(0, h.chunkEnd), "بخش هم‌مضمون (برای دیدن، دوبار کلیک کنید)")) })
                            status.text += " · ${fa(sem.size)} اشارهٔ مفهومی احتمالی"
                        }
                    }
                }
            }
        }
        val go = Button("جست‌وجوی این آیه در کتابخانه").apply { style = Theme.primaryButton(); setOnAction { search() } }
        val exportW = Button("خروجی Word").apply { style = Theme.addButton(); setOnAction { exportRows(table.items.toList(), "کشف‌الآیات " + (current?.let { Quran.label(it.first, it.second) } ?: ""), false) } }
        val exportX = Button("خروجی Excel").apply { style = Theme.addButton(); setOnAction { exportRows(table.items.toList(), "کشف‌الآیات " + (current?.let { Quran.label(it.first, it.second) } ?: ""), true) } }
        showVerse(2, 255)
        return VBox(10.0,
            HBox(8.0, Label("سوره:"), sura, Label("آیه:"), aya, Region().also { HBox.setHgrow(it, Priority.ALWAYS) }, allusion).apply { alignment = Pos.CENTER_RIGHT },
            text, VBox(4.0, verseView, trView).apply { styleClass.add("card") },
            HBox(8.0, go, exportW, exportX, status).apply { alignment = Pos.CENTER_RIGHT }, bar, table,
        ).apply { padding = Insets(12.0); VBox.setVgrow(table, Priority.ALWAYS) }
    }

    // ---------------------------------------------------------------- ۲. آیات یک سند

    private val docTable = hitTable(false)
    private val docStatus = Label().apply { styleClass.add("muted") }
    private var docFile: File? = null

    fun analyzeFile(f: File) {
        tabs.selectionModel.select(tDoc)
        docFile = f; docStatus.text = "در حال بررسی «${f.name}»…"
        bg {
            val content = Scanner.content(indexer.allObjects.toList(), f.absolutePath) ?: runCatching { TikaExtractor.parse(f).content }.getOrNull() ?: ""
            val hits = Matcher.all(content, basmala.isSelected)
            Platform.runLater {
                docTable.items.setAll(hits.filter { shown(it) }.map { Row(f.absolutePath, f.name, it) })
                val n = hits.map { it.key }.distinct().size
                docStatus.text = if (hits.isEmpty()) "آیه‌ای در «${f.name}» پیدا نشد." else "«${f.name}»: ${fa(n)} آیه در ${fa(hits.size)} موضع"
            }
        }
    }

    private fun docTab(): Node {
        val pick = Button("انتخاب سند…").apply { style = Theme.primaryButton(); setOnAction {
            FileChooser().apply { extensionFilters.add(FileChooser.ExtensionFilter("اسناد", "*.docx", "*.doc", "*.pdf", "*.txt", "*.rtf", "*.odt", "*.epub", "*.htm", "*.html")) }
                .showOpenDialog(stage)?.let { analyzeFile(it) } } }
        val ew = Button("خروجی Word").apply { style = Theme.addButton(); setOnAction { exportRows(docTable.items.toList(), "آیات " + (docFile?.nameWithoutExtension ?: ""), false) } }
        val ex = Button("خروجی Excel").apply { style = Theme.addButton(); setOnAction { exportRows(docTable.items.toList(), "آیات " + (docFile?.nameWithoutExtension ?: ""), true) } }
        return VBox(10.0, HBox(8.0, pick, ew, ex, docStatus).apply { alignment = Pos.CENTER_RIGHT },
            Label("نکته: در پیش‌نمایش هر نتیجه هم می‌توانید از منوی کلیک راست «کشف‌الآیات این سند» را بزنید.").apply { styleClass.add("muted") }, docTable)
            .apply { padding = Insets(12.0); VBox.setVgrow(docTable, Priority.ALWAYS) }
    }

    // ---------------------------------------------------------------- ۳. کل کتابخانه

    private fun allTab(): Node {
        val bar = ProgressBar().apply { maxWidth = Double.MAX_VALUE; isVisible = false }
        val status = Label().apply { styleClass.add("muted") }
        val freq = TableView<Triple<String, Int, Int>>()
        fun <T> fcol(t: String, w: Double, f: (Triple<String, Int, Int>) -> T) = TableColumn<Triple<String, Int, Int>, T>(t).apply { prefWidth = w; setCellValueFactory { SimpleObjectProperty(f(it.value)) } }
        freq.columns.addAll(
            fcol("آیه", 120.0) { val k = it.first.split(':'); Quran.label(k[0].toInt(), k[1].toInt()) },
            fcol("سندها", 70.0) { it.second }, fcol("رخداد", 70.0) { it.third },
            fcol("متن آیه", 600.0) { val k = it.first.split(':'); Quran.verse(k[0].toInt(), k[1].toInt())?.text?.take(160) ?: "" })
        val detail = hitTable(true)
        freq.selectionModel.selectedItemProperty().addListener { _, _, t ->
            val list = archive ?: return@addListener
            detail.items.setAll(if (t == null) emptyList() else list.flatMap { d -> d.hits.filter { it.key == t.first && shown(it) }.map { Row(d.path, d.name, it) } })
        }
        fun run() = ensureArchive(bar, status) { list ->
            val filtered = list.map { it.copy(hits = it.hits.filter { h -> shown(h) }) }.filter { it.hits.isNotEmpty() }
            freq.items.setAll(Scanner.frequency(filtered))
            status.text = "${fa(freq.items.size)} آیهٔ مختلف در ${fa(filtered.size)} سند · ${fa(filtered.sumOf { it.hits.size })} موضع"
        }
        val go = Button("فهرست همهٔ آیات کتابخانه").apply { style = Theme.primaryButton(); setOnAction { run() } }
        val refresh = Button("بررسی دوباره").apply { style = Theme.addButton(); setOnAction { archive = null; run() } }
        val park = Button("⤓ کنار ساعت").apply { style = Theme.addButton(); isVisible = TrayProgress.supported; isManaged = isVisible
            setOnAction { TrayProgress.park(listOf(owner, stage), "کشف‌الآیات") } }
        val ex = Button("خروجی Excel همه").apply { style = Theme.addButton(); setOnAction {
            val list = archive ?: return@setOnAction
            exportRows(list.flatMap { d -> d.hits.filter { shown(it) }.map { Row(d.path, d.name, it) } }, "کشف‌الآیات کتابخانه", true) } }
        val ew = Button("خروجی Word همه").apply { style = Theme.addButton(); setOnAction {
            val list = archive ?: return@setOnAction
            exportRows(list.flatMap { d -> d.hits.filter { shown(it) }.map { Row(d.path, d.name, it) } }.sortedBy { it.hit.sura * 1000 + it.hit.aya }, "کشف‌الآیات کتابخانه", false) } }
        val split = SplitPane(freq, detail).apply { orientation = javafx.geometry.Orientation.VERTICAL; setDividerPositions(0.5) }
        return VBox(10.0, HBox(8.0, go, refresh, park, ew, ex, status).apply { alignment = Pos.CENTER_RIGHT }, bar, split)
            .apply { padding = Insets(12.0); VBox.setVgrow(split, Priority.ALWAYS) }
    }
}
