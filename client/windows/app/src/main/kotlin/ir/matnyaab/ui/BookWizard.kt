package ir.matnyaab.ui

import ir.matnyaab.core.AppSettingsManager
import ir.matnyaab.core.LuceneIndexer
import ir.matnyaab.core.ai.AiConfig
import ir.matnyaab.core.ai.BookRunner
import ir.matnyaab.core.ai.BookSpec
import ir.matnyaab.core.ai.Outline
import ir.matnyaab.core.ai.PromptTemplate
import ir.matnyaab.core.ai.PromptTemplates
import ir.matnyaab.core.research.DocxWriter
import ir.matnyaab.core.research.ResearchStore
import ir.matnyaab.util.PersianUtil
import javafx.application.Platform
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
 * جادوگر «کتاب با هوش مصنوعی» در پنج گام روشن: موضوع ← سبک ← صفحه‌آرایی ← طرح ← ساخت.
 * هر گام فقط چند انتخاب دارد و پیش‌فرض‌ها خوب‌اند؛ کاربر می‌تواند از گام دوم تا آخر فقط «بعدی» بزند.
 */
class BookWizard(private val owner: Stage, private val settings: AppSettingsManager, private val indexer: LuceneIndexer) {

    companion object {
        fun show(owner: Stage, settings: AppSettingsManager, indexer: LuceneIndexer, project: String? = null) =
            BookWizard(owner, settings, indexer).apply { project?.let { fromFishes.isSelected = true; this.project.value = it } }.stage.show()
        private val LAYOUTS = listOf(
            Triple("کتاب وزیری (لوتوس ۱۳)", "وزیری", DocxWriter.Layout(fontFa = "B Lotus", headingFont = "B Lotus", size = 13.0, lineSpacing = 1.25)),
            Triple("رقعی خوش‌خوان (نازنین ۱۲)", "رقعی", DocxWriter.Layout(fontFa = "B Nazanin", headingFont = "B Titr", size = 12.0, lineSpacing = 1.3)),
            Triple("پایان‌نامه A4 (نازنین ۱۳، فاصلهٔ ۱٫۵)", "A4", DocxWriter.Layout(fontFa = "B Nazanin", headingFont = "B Titr", size = 13.0, lineSpacing = 1.5)),
            Triple("A5 جیبی (میترا ۱۱)", "A5", DocxWriter.Layout(fontFa = "B Mitra", headingFont = "B Mitra", size = 11.0, lineSpacing = 1.2)),
        )
    }

    val stage = Stage().apply { initOwner(owner); title = "کتاب با هوش مصنوعی"; width = 880.0; height = 720.0 }
    private var step = 0
    private val steps = listOf("موضوع", "سبک نگارش", "صفحه‌آرایی", "طرح کتاب", "ساخت")
    private val stepBar = HBox(6.0).apply { alignment = Pos.CENTER; padding = Insets(12.0) }
    private val body = StackPane()
    private val back = Button("‹ قبلی").apply { style = Theme.addButton() }
    private val next = Button("بعدی ›").apply { style = Theme.primaryButton() }

    // گام ۱
    private val title = TextField().apply { promptText = "مثلاً: خودسازی در آموزه‌های اسلامی" }
    private val keywords = TextField().apply { promptText = "کلیدواژه‌ها با ویرگول؛ مثلاً: تهذیب نفس، جهاد اکبر، مراقبه" }
    private val audience = TextField().apply { promptText = "مخاطب (اختیاری)؛ مثلاً: دانشجویان، عموم مردم، طلاب" }
    private val author = TextField().apply { promptText = "نام نویسنده یا گردآورنده (اختیاری)" }
    private val fromLibrary = RadioButton("از کتابخانهٔ من: دستیار خودش جست‌وجو و فیش‌برداری می‌کند")
    val fromFishes = RadioButton("از فیش‌های یک پروژه: فیش‌ها را به متن روان یا پیوسته تبدیل می‌کند")
    val project = ComboBox<String>().apply { isEditable = true; prefWidth = 260.0; items.setAll(ResearchStore.projects().map { it.name }) }
    private val sources = MenuButton("همهٔ مجموعه‌ها")

    // گام ۲
    private val tplGroup = ToggleGroup()
    private val tplBox = VBox(6.0)
    private val length = ComboBox<String>().apply { items.setAll("کوتاه (حدود ۲۵۰ واژه در هر بخش)", "متوسط (حدود ۴۵۰ واژه)", "بلند (حدود ۸۰۰ واژه)"); selectionModel.select(1) }
    private val extra = TextArea().apply { promptText = "دستور ویژه به دستیار (اختیاری)؛ مثلاً: از روایات نهج‌البلاغه بیشتر استفاده کن؛ هر فصل با یک داستان کوتاه شروع شود."; prefRowCount = 3; isWrapText = true }

    // گام ۳
    private val layoutPreset = ComboBox<String>().apply { items.setAll(LAYOUTS.map { it.first }); selectionModel.select(0) }
    private val paper = ComboBox<String>().apply { items.setAll(DocxWriter.Layout.PAPERS.keys) }
    private val font = ComboBox<String>().apply { isEditable = true; items.setAll("B Lotus", "B Nazanin", "B Mitra", "B Zar", "IRNazanin", "IRLotus", "Vazirmatn", "Sahel") }
    private val headFont = ComboBox<String>().apply { isEditable = true; items.setAll("B Titr", "B Lotus", "B Nazanin", "IRTitr", "Vazirmatn") }
    private val size = Spinner<Double>(9.0, 18.0, 13.0, 0.5).apply { prefWidth = 90.0 }
    private val spacing = Spinner<Double>(1.0, 2.0, 1.25, 0.05).apply { prefWidth = 90.0 }

    // گام ۴
    private val outline = TextArea().apply { isWrapText = true; promptText = "# فصل نخست\n- بخش اول\n- بخش دوم\n# فصل دوم\n- …" }
    private val estimate = Label().apply { styleClass.add("muted"); isWrapText = true }

    // گام ۵
    private val output = TextField()
    private val log = TextArea().apply { isEditable = false; isWrapText = true; styleClass.add("log-area") }
    private val bar = ProgressBar(0.0).apply { maxWidth = Double.MAX_VALUE }
    private val run = Button("ساخت کتاب").apply { style = Theme.primaryButton() }
    private val stop = Button("توقف").apply { style = Theme.addButton(); isDisable = true }
    private val park = Button("⤓ کنار ساعت").apply { style = Theme.addButton(); isVisible = TrayProgress.supported; isManaged = isVisible }
    private val openOut = Button("بازکردن کتاب").apply { style = Theme.addButton(); isDisable = true }
    private val stopFlag = AtomicBoolean(false)

    init {
        ToggleGroup().also { g -> fromLibrary.toggleGroup = g; fromFishes.toggleGroup = g }
        fromLibrary.isSelected = true
        project.value = project.items.firstOrNull() ?: ResearchStore.DEFAULT_PROJECT
        indexer.allObjects.forEach { c -> sources.items.add(CheckMenuItem(c.indexName).apply { setOnAction { syncSources() } }) }
        layoutPreset.setOnAction { applyPreset() }; applyPreset()
        refreshTemplates()
        back.setOnAction { go(step - 1) }
        next.setOnAction { go(step + 1) }
        run.setOnAction { start() }
        stop.setOnAction { stopFlag.set(true) }
        park.setOnAction { TrayProgress.park(listOf(owner, stage), "ساخت کتاب «${title.text.trim()}»") }
        openOut.setOnAction { runCatching { java.awt.Desktop.getDesktop().open(File(output.text)) } }
        val nav = HBox(8.0, back, Region().also { HBox.setHgrow(it, Priority.ALWAYS) }, next).apply { padding = Insets(10.0, 14.0, 14.0, 14.0) }
        val root = BorderPane(body).apply { top = stepBar; bottom = nav; nodeOrientation = NodeOrientation.RIGHT_TO_LEFT; style = Theme.FONT }
        stage.scene = Scene(root)
        Theme.install(stage.scene, stage)
        go(0)
    }

    private fun lbl(t: String, cls: String? = null) = Label(t).apply { isWrapText = true; cls?.let { styleClass.add(it) } }
    private fun card(vararg n: Node) = VBox(8.0, *n).apply { styleClass.add("card") }
    private fun page(vararg n: Node): Node = ScrollPane(VBox(12.0, *n).apply { padding = Insets(14.0) }).apply { isFitToWidth = true }
    private fun fa(n: Int) = PersianUtil.toPersianNumbers(n)

    private fun syncSources() { val s = sources.items.filterIsInstance<CheckMenuItem>().filter { it.isSelected }.map { it.text }; sources.text = if (s.isEmpty()) "همهٔ مجموعه‌ها" else s.joinToString("، ") }

    private fun applyPreset() {
        val (_, p, l) = LAYOUTS[maxOf(0, layoutPreset.selectionModel.selectedIndex)]
        paper.value = p; font.value = l.fontFa; headFont.value = l.headingFont; size.valueFactory.value = l.size; spacing.valueFactory.value = l.lineSpacing
    }

    private fun refreshTemplates() {
        val sel = (tplGroup.selectedToggle?.userData as? PromptTemplate)?.id ?: settings.get("BOOK_TEMPLATE").ifBlank { "academic" }
        tplBox.children.clear()
        PromptTemplates.all().forEach { t ->
            val rb = RadioButton(t.title).apply { toggleGroup = tplGroup; userData = t; isSelected = t.id == sel; styleClass.add("card-title") }
            tplBox.children.add(VBox(2.0, rb, lbl(t.desc, "muted").apply { padding = Insets(0.0, 26.0, 4.0, 0.0) }))
        }
        if (tplGroup.selectedToggle == null) (tplBox.children.firstOrNull() as? VBox)?.children?.firstOrNull()?.let { (it as RadioButton).isSelected = true }
    }

    private fun template() = tplGroup.selectedToggle?.userData as? PromptTemplate ?: PromptTemplates.DEFAULTS.first()

    private fun layout() = DocxWriter.Layout.paper(paper.value ?: "وزیری", DocxWriter.Layout(fontFa = font.editor.text.ifBlank { "B Lotus" }, headingFont = headFont.editor.text.ifBlank { "B Titr" },
        size = size.value, lineSpacing = spacing.value))

    private fun spec(o: Outline?) = BookSpec(
        title = title.text.trim(), keywords = keywords.text.split(Regex("[،,]")).map { it.trim() }.filter { it.isNotEmpty() }, audience = audience.text.trim(),
        template = template(), layout = layout(), author = author.text.trim(), source = if (fromFishes.isSelected) "FROM_FISHES" else "FROM_LIBRARY",
        project = (project.editor.text ?: "").trim().ifBlank { title.text.trim().take(60) },
        collections = sources.items.filterIsInstance<CheckMenuItem>().filter { it.isSelected }.map { it.text },
        outline = o, sectionWords = when (length.selectionModel.selectedIndex) { 0 -> 250; 2 -> 800; else -> 450 },
        extra = extra.text.trim(), output = File(output.text.trim().ifBlank { File(ResearchWindow.docsDir(), ResearchWindow.safeName(title.text) + ".docx").absolutePath }),
    )

    private fun runner(logTo: (String) -> Unit = { m -> Platform.runLater { log.appendText(m + "\n") } }) = AiConfig.active()?.let { p ->
        BookRunner(indexer, settings, p, logTo, { v -> Platform.runLater { bar.progress = v }; TrayProgress.update(v, "", null) }, { stopFlag.get() })
    }

    // ---------------------------------------------------------------- گام‌ها

    private fun go(i: Int) {
        if (i > step && !validate()) return
        step = i.coerceIn(0, steps.lastIndex)
        stepBar.children.setAll(steps.mapIndexed { k, s -> Label("${fa(k + 1)}. $s").apply {
            styleClass.add("badge"); style = if (k == step) "-fx-background-color: -mx-accent; -fx-text-fill: white;" else if (k < step) "" else "-fx-opacity: 0.55;"
        } })
        back.isDisable = step == 0
        next.isVisible = step < steps.lastIndex; next.isManaged = next.isVisible
        body.children.setAll(when (step) { 0 -> topicPage(); 1 -> stylePage(); 2 -> layoutPage(); 3 -> outlinePage(); else -> buildPage() })
    }

    private fun validate(): Boolean = when (step) {
        0 -> if (title.text.isBlank()) { CustomMessageBox.show(stage, "کتاب", "عنوان کتاب را بنویسید."); false }
             else if (fromFishes.isSelected && ResearchStore.fishes((project.editor.text ?: "").trim()).isEmpty()) { CustomMessageBox.show(stage, "کتاب", "این پروژه هنوز فیشی ندارد. «از کتابخانهٔ من» را انتخاب کنید یا ابتدا فیش بزنید."); false }
             else true
        3 -> if (Outline.parse(outline.text).chapters.isEmpty()) { CustomMessageBox.show(stage, "کتاب", "طرح کتاب خالی است؛ «پیشنهاد طرح» را بزنید یا فصل‌ها را بنویسید."); false } else true
        else -> true
    }

    private fun topicPage(): Node {
        val noAi = if (AiConfig.active() == null) card(lbl("برای این جادوگر یک سرویس هوش مصنوعی لازم است", "card-title"),
            lbl("کلید سرویس دلخواهتان را بیفزایید (درگاه‌های ایرانی، OpenRouter رایگان یا Ollama آفلاین). ساخت کتاب از فیش‌ها بدون هوش مصنوعی هم در «میز پژوهش ← ساخت کتاب» ممکن است."),
            Button("افزودن سرویس هوش مصنوعی").apply { style = Theme.primaryButton(); setOnAction { AiSettingsDialog(stage).show(); go(0) } }) else null
        val grid = GridPane().apply {
            hgap = 10.0; vgap = 8.0; columnConstraints.addAll(ColumnConstraints(110.0), ColumnConstraints().apply { hgrow = Priority.ALWAYS })
            var r = 0; fun row(l: String, n: Node) { add(lbl(l), 0, r); add(n, 1, r); r++ }
            row("عنوان کتاب:", title); row("کلیدواژه‌ها:", keywords); row("مخاطب:", audience); row("نویسنده:", author)
        }
        val src = card(lbl("مواد کتاب از کجا بیاید؟", "card-title"), fromLibrary, HBox(8.0, lbl("منابع:"), sources).apply { alignment = Pos.CENTER_RIGHT; padding = Insets(0.0, 26.0, 0.0, 0.0) },
            fromFishes, HBox(8.0, lbl("پروژه:"), project).apply { alignment = Pos.CENTER_RIGHT; padding = Insets(0.0, 26.0, 0.0, 0.0) },
            lbl("در هر دو حالت، نقل‌قول‌ها پیش از ثبت با متن اصلی سنجیده می‌شوند و هر بند پانویس منبع دارد.", "muted"))
        return page(*listOfNotNull(noAi, card(lbl("کتاب دربارهٔ چیست؟", "card-title"), grid), src).toTypedArray())
    }

    private fun stylePage(): Node {
        val manage = Button("قالب‌های من…").apply { style = Theme.addButton(); setOnAction { TemplateEditor.show(stage, template()) { refreshTemplates() } } }
        return page(card(lbl("سبک نگارش کتاب", "card-title"), tplBox, HBox(8.0, manage, lbl("قالب‌ها پرامپت‌های ساختاری‌اند؛ می‌توانید یکی را تکثیر و به سلیقهٔ خود ویرایش کنید.", "muted")).apply { alignment = Pos.CENTER_RIGHT }),
            card(lbl("اندازه و دستور ویژه", "card-title"), HBox(8.0, lbl("حجم هر بخش:"), length).apply { alignment = Pos.CENTER_RIGHT }, extra))
    }

    private fun layoutPage(): Node {
        val grid = GridPane().apply {
            hgap = 10.0; vgap = 8.0; var r = 0
            fun row(l: String, n: Node) { add(lbl(l), 0, r); add(n, 1, r); r++ }
            row("الگوی آماده:", layoutPreset); row("قطع:", paper); row("قلم متن:", font); row("قلم عنوان‌ها:", headFont)
            row("اندازهٔ قلم:", size); row("فاصلهٔ سطرها:", spacing)
        }
        return page(card(lbl("صفحه‌آرایی فایل Word", "card-title"), grid,
            lbl("خروجی: صفحهٔ عنوان، فهرست مطالب خودکار، فصل‌ها با سبک عنوان، پانویس منابع، شمارهٔ صفحه و فهرست منابع؛ تنظیم زبان فارسی و راست‌به‌چپ. " +
                "اگر قلمی روی رایانه نصب نباشد، Word قلم جایگزین نشان می‌دهد. با افزونهٔ ورد متن‌یاب می‌توانید قالب ناشر یا دانشگاه را هم اعمال کنید.", "muted")))
    }

    private fun outlinePage(): Node {
        val suggest = Button("پیشنهاد طرح با هوش مصنوعی").apply { style = Theme.primaryButton() }
        val chapters = Spinner<Int>(2, 20, 6).apply { prefWidth = 80.0 }
        val fromProject = Button("فصل‌های پروژه").apply { style = Theme.addButton(); isVisible = fromFishes.isSelected; isManaged = isVisible
            setOnAction { val p = ResearchStore.project((project.editor.text ?: "").trim()); outline.text = Outline(p?.topics?.map { Outline.Chapter(it, emptyList()) } ?: emptyList()).toText(); syncEstimate() } }
        suggest.setOnAction {
            val r = runner { } ?: run { CustomMessageBox.show(stage, "کتاب", "ابتدا یک سرویس هوش مصنوعی بیفزایید."); return@setOnAction }
            suggest.isDisable = true; estimate.text = "در حال طراحی فصل‌ها…"
            Thread {
                val o = runCatching { r.proposeOutline(spec(null), chapters.value) }
                Platform.runLater { suggest.isDisable = false; o.onSuccess { outline.text = it.toText(); syncEstimate() }.onFailure { estimate.text = "✗ ${it.message}" } }
            }.apply { isDaemon = true }.start()
        }
        outline.textProperty().addListener { _ -> syncEstimate() }
        if (outline.text.isBlank() && fromFishes.isSelected) fromProject.fire()
        syncEstimate()
        return page(card(lbl("طرح کتاب را ببینید و ویرایش کنید", "card-title"),
            lbl("هر سطری که با # شروع شود یک فصل است و سطرهای - بخش‌های آن فصل. ترتیب، حذف و افزودن کاملاً در اختیار شماست.", "muted"),
            HBox(8.0, suggest, lbl("فصل:"), chapters, fromProject).apply { alignment = Pos.CENTER_RIGHT }, outline.apply { prefRowCount = 18 }, estimate))
    }

    private fun syncEstimate() {
        val o = Outline.parse(outline.text)
        val p = AiConfig.active()
        val n = runner { }?.estimate(spec(o), o) ?: 0
        estimate.text = "${fa(o.chapters.size)} فصل · ${fa(o.chapters.sumOf { maxOf(1, it.sections.size) })} بخش" +
            if (p != null) " · برآورد حدود ${fa(n)} درخواست به ${p.Title} (هزینه با حساب شما در همان سرویس)" else ""
    }

    private fun buildPage(): Node {
        if (output.text.isBlank()) output.text = File(ResearchWindow.docsDir(), ResearchWindow.safeName(title.text) + ".docx").absolutePath
        val browse = Button("انتخاب…").apply { style = Theme.addButton(); setOnAction {
            FileChooser().apply { initialDirectory = ResearchWindow.docsDir(); initialFileName = File(output.text).name; extensionFilters.add(FileChooser.ExtensionFilter("Word", "*.docx")) }
                .showSaveDialog(stage)?.let { output.text = it.absolutePath } } }
        val t = template()
        return page(card(lbl("خلاصه", "card-title"),
            lbl("«${title.text.trim()}» · سبک «${t.title}» · ${paper.value} · ${font.editor.text} ${size.value} · " + (if (fromFishes.isSelected) "از فیش‌های «${project.editor.text}»" else "از کتابخانه (${sources.text})")),
            HBox(8.0, lbl("ذخیره در:"), output.also { HBox.setHgrow(it, Priority.ALWAYS) }, browse).apply { alignment = Pos.CENTER_RIGHT },
            HBox(8.0, run, stop, park, openOut).apply { alignment = Pos.CENTER_RIGHT }, bar, log.apply { prefRowCount = 14 },
            lbl("می‌توانید در میانهٔ کار «توقف» بزنید؛ اجرای دوباره از همان‌جا ادامه می‌دهد و بخش‌های نوشته‌شده دوباره هزینه ندارند.", "muted")))
    }

    private fun start() {
        val o = Outline.parse(outline.text)
        val s = spec(o)
        if (s.output.exists() && !confirm("فایل «${s.output.name}» وجود دارد. جایگزین شود؟")) return
        settings.setValue("BOOK_TEMPLATE", s.template.id)
        val r = runner() ?: return
        stopFlag.set(false); run.isDisable = true; stop.isDisable = false; openOut.isDisable = true; back.isDisable = true
        log.clear(); bar.progress = 0.0
        val cols = indexer.allObjects.toList()
        Thread {
            val res = runCatching { r.run(s, cols) }
            Platform.runLater {
                TrayProgress.finish(res.getOrNull()?.let { "کتاب «${s.title}» ساخته شد." })
                run.isDisable = false; stop.isDisable = true; back.isDisable = false
                res.onSuccess { m ->
                    log.appendText("\n✓ کتاب ساخته شد: ${m.file.absolutePath}\n   ${fa(m.fishes)} فیش از ${fa(m.sources)} سند · ${fa(m.calls)} درخواست\n")
                    openOut.isDisable = false; runCatching { java.awt.Desktop.getDesktop().open(m.file) }
                }.onFailure { e -> log.appendText("\n✗ ${e.message ?: e.javaClass.simpleName}\n" + if (e is InterruptedException) "برای ادامه دوباره «ساخت کتاب» را بزنید.\n" else "") }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun confirm(msg: String) = Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.YES, ButtonType.NO).apply { initOwner(stage); headerText = null; dialogPane.nodeOrientation = NodeOrientation.RIGHT_TO_LEFT }
        .showAndWait().orElse(ButtonType.NO) == ButtonType.YES
}

/** ویرایش قالب‌های نگارش (پرامپت‌های ساختاری): تکثیر، ویرایش، حذف */
object TemplateEditor {
    fun show(owner: Stage, base: PromptTemplate, onChange: () -> Unit) {
        val st = Stage().apply { initOwner(owner); title = "قالب‌های نگارش" }
        val list = ListView<PromptTemplate>()
        val name = TextField(); val desc = TextField(); val styleText = TextArea().apply { prefRowCount = 6; isWrapText = true }
        val mode = ComboBox<String>().apply { items.setAll("متن پیوسته با پانویس", "پیوند فیش‌ها (متن فیش عیناً)") }
        val intro = CheckBox("پیشگفتار"); val sum = CheckBox("خلاصهٔ هر فصل"); val q = CheckBox("پرسش‌های پایان فصل"); val concl = CheckBox("سخن پایانی")
        var cur: PromptTemplate? = null
        fun refresh(select: String?) {
            list.items.setAll(PromptTemplates.all())
            list.selectionModel.select(list.items.indexOfFirst { it.id == select }.coerceAtLeast(0))
        }
        list.setCellFactory { object : ListCell<PromptTemplate>() { override fun updateItem(t: PromptTemplate?, e: Boolean) { super.updateItem(t, e); text = t?.let { it.title + if (it.builtIn) "  (پیش‌فرض)" else "" } } } }
        list.selectionModel.selectedItemProperty().addListener { _, _, t ->
            cur = t ?: return@addListener
            name.text = t.title; desc.text = t.desc; styleText.text = t.style; mode.selectionModel.select(if (t.mode == "BRIDGE") 1 else 0)
            intro.isSelected = t.intro; sum.isSelected = t.chapterSummary; q.isSelected = t.questions; concl.isSelected = t.conclusion
            listOf(name, desc, styleText).forEach { it.isEditable = !t.builtIn }; listOf(mode, intro, sum, q, concl).forEach { it.isDisable = t.builtIn }
        }
        fun edited(id: String) = PromptTemplate(id, name.text.trim().ifBlank { "قالب من" }, desc.text.trim(), styleText.text.trim(), if (mode.selectionModel.selectedIndex == 1) "BRIDGE" else "WOVEN",
            intro.isSelected, sum.isSelected, q.isSelected, concl.isSelected, false)
        val dup = Button("تکثیر برای ویرایش").apply { style = Theme.primaryButton(); setOnAction {
            val c = cur ?: return@setOnAction; val id = "u" + System.currentTimeMillis()
            PromptTemplates.upsert(c.copy(id = id, title = c.title + " (نسخهٔ من)", builtIn = false)); refresh(id); onChange() } }
        val save = Button("ذخیره").apply { style = Theme.addButton(); setOnAction { val c = cur ?: return@setOnAction; if (c.builtIn) return@setOnAction; PromptTemplates.upsert(edited(c.id)); refresh(c.id); onChange() } }
        val del = Button("حذف").apply { style = Theme.addButton(); setOnAction { val c = cur ?: return@setOnAction; if (c.builtIn) return@setOnAction; PromptTemplates.delete(c.id); refresh(null); onChange() } }
        val form = VBox(6.0, Label("نام"), name, Label("توضیح کوتاه"), desc, Label("سبک و لحن (دستور به هوش مصنوعی؛ متغیرها: {title} {keywords} {audience})"), styleText,
            Label("شیوهٔ نوشتن"), mode, HBox(10.0, intro, sum, q, concl), HBox(8.0, dup, save, del).apply { alignment = Pos.CENTER_RIGHT },
            Label("قالب‌های پیش‌فرض فقط‌خواندنی‌اند؛ برای تغییر، تکثیرشان کنید.").apply { styleClass.add("muted") }).apply { padding = Insets(0.0, 0.0, 0.0, 10.0) }
        val root = HBox(10.0, list.apply { prefWidth = 230.0 }, form.also { HBox.setHgrow(it, Priority.ALWAYS) })
            .apply { padding = Insets(12.0); nodeOrientation = NodeOrientation.RIGHT_TO_LEFT; style = Theme.FONT }
        st.scene = Scene(root, 780.0, 520.0)
        Theme.install(st.scene, st)
        refresh(base.id)
        st.show()
    }
}
