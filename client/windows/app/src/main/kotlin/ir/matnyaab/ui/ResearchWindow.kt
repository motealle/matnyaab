package ir.matnyaab.ui

import ir.matnyaab.core.AppSettingsManager
import ir.matnyaab.core.LuceneIndexer
import ir.matnyaab.core.TikaExtractor
import ir.matnyaab.core.ai.AiClient
import ir.matnyaab.core.ai.AiConfig
import ir.matnyaab.core.ai.MissionRunner
import ir.matnyaab.core.ai.MissionSpec
import ir.matnyaab.core.ai.Scenario
import ir.matnyaab.core.research.DocxWriter
import ir.matnyaab.core.research.Fish
import ir.matnyaab.core.research.ReportWriter
import ir.matnyaab.core.research.ResearchStore
import ir.matnyaab.core.research.SourceFinder
import ir.matnyaab.util.PersianUtil
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.stage.FileChooser
import javafx.stage.Stage
import java.awt.Desktop
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/**
 * «میز پژوهش» متن‌یاب: فیش‌ها و پروژه‌ها، دستیار هوشمند (مأموریت‌های چندمرحله‌ای)، ساخت کتاب، منبع‌یاب و همانندجویی.
 * هر زبانه پرچم قابلیت خودش را دارد و بدون هوش مصنوعی هم بیشتر امکانات کار می‌کند.
 */
class ResearchWindow(private val owner: Stage, private val settings: AppSettingsManager, private val indexer: LuceneIndexer) {

    companion object {
        private var open: ResearchWindow? = null
        fun show(owner: Stage, settings: AppSettingsManager, indexer: LuceneIndexer, tab: String) {
            val w = open ?: ResearchWindow(owner, settings, indexer).also { open = it }
            w.select(tab)
            w.stage.show(); w.stage.toFront()
        }
        fun docsDir(): File = File(System.getProperty("user.home"), "Documents" + File.separator + "Matnyaab").also { it.mkdirs() }
        fun safeName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), " ").trim().take(60).ifBlank { "خروجی" }
    }

    val stage = Stage().apply { initOwner(owner); title = "میز پژوهش متن‌یاب"; width = 1120.0; height = 760.0 }
    private val tabs = TabPane().apply { styleClass.add("research-tabs"); tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE }
    private val tFish = Tab("🗂  فیش‌ها و پروژه‌ها")
    private val tAi = Tab("✦  دستیار هوشمند")
    private val tBook = Tab("📘  ساخت کتاب")
    private val tSource = Tab("⌕  منبع‌یاب و همانندجویی")

    init {
        tFish.content = FishTab().root
        tAi.content = AssistantTab().root
        tBook.content = BookTab().root
        tSource.content = SourceTab().root
        tabs.tabs.addAll(tFish, tAi, tBook, tSource)
        if (settings.get("FF_AI") == "0") tabs.tabs.remove(tAi)
        if (settings.get("FF_SOURCE") == "0") tabs.tabs.remove(tSource)
        val root = BorderPane(tabs).apply { nodeOrientation = NodeOrientation.RIGHT_TO_LEFT; style = Theme.FONT }
        stage.scene = Scene(root)
        Theme.install(stage.scene, stage)
        stage.setOnHidden { ResearchStore.listener = null; open = null }
    }

    fun select(id: String) = tabs.selectionModel.select(when (id) { "ai" -> tAi; "book" -> tBook; "source" -> tSource; else -> tFish }.takeIf { it in tabs.tabs } ?: tFish)

    // ---------------------------------------------------------------- ابزار مشترک

    private fun label(t: String, cls: String? = null) = Label(t).apply { isWrapText = true; style = Theme.FONT; cls?.let { styleClass.add(it) } }
    private fun primary(t: String, a: () -> Unit) = Button(t).apply { style = Theme.primaryButton(); setOnMouseEntered { style = Theme.primaryButtonHover() }; setOnMouseExited { style = Theme.primaryButton() }; setOnAction { a() } }
    private fun secondary(t: String, a: () -> Unit) = Button(t).apply { style = Theme.addButton(); setOnAction { a() } }
    private fun card(vararg n: Node) = VBox(8.0, *n).apply { styleClass.add("card") }
    private fun info(msg: String) = Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).apply { initOwner(stage); headerText = null; dialogPane.nodeOrientation = NodeOrientation.RIGHT_TO_LEFT }.showAndWait()
    private fun confirm(msg: String) = Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.YES, ButtonType.NO).apply { initOwner(stage); headerText = null; dialogPane.nodeOrientation = NodeOrientation.RIGHT_TO_LEFT }.showAndWait().orElse(ButtonType.NO) == ButtonType.YES
    private fun openFile(f: File) = runCatching { Desktop.getDesktop().open(f) }
    private fun date(ms: Long) = PersianUtil.toJalali(LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())).substringBefore(' ')
    private fun bg(work: () -> Unit) = Thread { work() }.apply { isDaemon = true }.start()

    private fun projectCombo(): ComboBox<String> = ComboBox<String>().apply {
        isEditable = true; prefWidth = 260.0
        items.setAll(ResearchStore.projects().map { it.name }); value = items.firstOrNull()
    }

    // ================================================================ فیش‌ها

    private inner class FishTab {
        val project = projectCombo()
        val topics = ListView<String>()
        val search = TextField().apply { promptText = "جست‌وجو در فیش‌ها (عنوان، متن، یادداشت، برچسب، کد)" }
        val list = ListView<Fish>()
        val title = TextField(); val topic = ComboBox<String>().apply { isEditable = true; maxWidth = Double.MAX_VALUE }
        val tags = TextField().apply { promptText = "برچسب‌ها با ویرگول" }
        val note = TextArea().apply { promptText = "یادداشت و برداشت شما"; prefRowCount = 4; isWrapText = true }
        val text = TextArea().apply { isEditable = false; isWrapText = true; prefRowCount = 9 }
        val source = Hyperlink()
        val count = Label()
        var current: Fish? = null
        val root: Node

        init {
            topics.setOnMouseClicked { refreshList() }
            search.textProperty().addListener { _ -> refreshList() }
            project.setOnAction { refreshTopics(); refreshList() }
            list.setCellFactory {
                object : ListCell<Fish>() {
                    override fun updateItem(f: Fish?, empty: Boolean) {
                        super.updateItem(f, empty)
                        if (empty || f == null) { graphic = null; text = null; return }
                        graphic = VBox(2.0, Label(f.title).apply { styleClass.add("card-title"); style = Theme.FONT },
                            Label(f.text.take(140).replace('\n', ' ') + if (f.text.length > 140) "…" else "").apply { isWrapText = true; style = Theme.FONT + "-fx-font-size: 12px;"; maxWidth = 420.0 },
                            Label((if (f.topic.isNotBlank()) "§ ${f.topic}   ·   " else "") + f.sourceName + "   ·   " + date(f.created) + if (f.origin == "mission") "   ·   دستیار" else if (f.origin == "word") "   ·   ورد" else "")
                                .apply { styleClass.add("muted"); style = Theme.FONT + "-fx-font-size: 11px;" })
                        text = null
                    }
                }
            }
            list.selectionModel.selectionMode = SelectionMode.MULTIPLE
            list.selectionModel.selectedItemProperty().addListener { _, _, f -> show(f) }
            source.setOnAction { current?.let { f -> runCatching { Desktop.getDesktop().open(File(f.sourcePath)) } } }

            val top = HBox(8.0, label("پروژه:"), project,
                secondary("پروژهٔ تازه") { newProject() },
                secondary("تغییر نام") { renameProject() },
                secondary("حذف پروژه") { deleteProject() },
                Region().also { HBox.setHgrow(it, Priority.ALWAYS) },
                secondary("خروجی Word") { export("docx") },
                secondary("خروجی Markdown") { export("md") },
                primary("ساخت کتاب از این پروژه") { select("book") },
            ).apply { alignment = Pos.CENTER_RIGHT; padding = Insets(10.0) }

            val left = VBox(6.0, label("سرفصل‌ها", "section-title"), topics, label("فیش‌ها را با کشیدن سرفصل یا از ویرایشگر کنار، دسته‌بندی کنید.", "muted"))
                .apply { padding = Insets(0.0, 10.0, 10.0, 10.0); prefWidth = 210.0; VBox.setVgrow(topics, Priority.ALWAYS) }
            val center = VBox(6.0, search, count, list).apply { padding = Insets(0.0, 10.0, 10.0, 0.0); VBox.setVgrow(list, Priority.ALWAYS) }
            val save = primary("ذخیره") { saveCurrent() }
            val del = secondary("حذف فیش(ها)") { deleteSelected() }
            val toWord = secondary("کپی با منبع") { current?.let { f -> copy("«${f.text}» (${f.sourceName})") } }
            val editor = VBox(6.0, label("عنوان"), title, label("سرفصل / فصل"), topic, label("برچسب‌ها"), tags, label("یادداشت"), note,
                label("متن فیش (عین منبع)"), text, HBox(6.0, label("منبع:"), source).apply { alignment = Pos.CENTER_RIGHT },
                HBox(8.0, save, toWord, del).apply { alignment = Pos.CENTER_RIGHT })
                .apply { padding = Insets(0.0, 0.0, 10.0, 10.0); prefWidth = 380.0 }
            val emptyHint = card(label("هنوز فیشی ندارید", "card-title"),
                label("در پیش‌نمایش نتایج، متن را انتخاب و «＋ فیش» (یا Ctrl+D) را بزنید؛ منبع و جای دقیق خودکار ثبت می‌شود. " +
                    "یا از «دستیار هوشمند» بخواهید از منابع شما فیش‌برداری موضوعی کند."))
            val body = SplitPane(left, center, ScrollPane(editor).apply { isFitToWidth = true }).apply { setDividerPositions(0.2, 0.62) }
            root = BorderPane(StackPane(body, emptyHint.apply { maxWidth = 460.0; maxHeight = 140.0; isVisible = false })).apply { top = top }
            ResearchStore.listener = { Platform.runLater { refreshTopics(); refreshList(); emptyHint.isVisible = ResearchStore.fishes().isEmpty() } }
            refreshTopics(); refreshList()
            emptyHint.isVisible = ResearchStore.fishes().isEmpty()
        }

        fun refreshTopics() {
            val p = ResearchStore.project(project.value ?: "")
            val sel = topics.selectionModel.selectedItem
            topics.items.setAll(listOf("همهٔ فیش‌ها") + (p?.topics ?: emptyList()) + listOf("بدون سرفصل"))
            topics.selectionModel.select(sel?.takeIf { it in topics.items } ?: "همهٔ فیش‌ها")
            topic.items.setAll(p?.topics ?: emptyList())
        }

        fun refreshList() {
            val t = topics.selectionModel.selectedItem ?: "همهٔ فیش‌ها"
            val all = ResearchStore.fishes(project.value ?: "")
            val f = ResearchStore.filter(all.filter { t == "همهٔ فیش‌ها" || (t == "بدون سرفصل" && it.topic.isBlank()) || it.topic == t }, search.text)
            list.items.setAll(f.sortedByDescending { it.created })
            count.text = "${PersianUtil.toPersianNumbers(f.size)} فیش" + if (f.size != all.size) " از ${PersianUtil.toPersianNumbers(all.size)}" else ""
            count.style = Theme.FONT; count.styleClass.setAll("muted")
        }

        fun show(f: Fish?) {
            current = f
            title.text = f?.title ?: ""; topic.value = f?.topic ?: ""; tags.text = f?.tags?.joinToString("، ") ?: ""
            note.text = f?.note ?: ""; text.text = (f?.text ?: "") + if (f != null && f.codes.isNotEmpty()) "\n\nکدها: " + f.codes.joinToString("، ") else ""
            source.text = f?.let { it.sourceName.ifBlank { "—" } } ?: ""
        }

        fun saveCurrent() {
            val f = current ?: return
            val t = (topic.editor?.text ?: topic.value ?: "").trim()
            ResearchStore.update(f.copy(title = title.text.trim(), topic = t, tags = tags.text.split(Regex("[،,]")).map { it.trim() }.filter { it.isNotEmpty() }, note = note.text))
            ResearchStore.project(f.project)?.let { p -> if (t.isNotBlank() && t !in p.topics) ResearchStore.updateProject(p.copy(topics = p.topics + t)) }
        }

        fun deleteSelected() {
            val sel = list.selectionModel.selectedItems.toList()
            if (sel.isEmpty() || !confirm("${PersianUtil.toPersianNumbers(sel.size)} فیش حذف شود؟")) return
            ResearchStore.delete(sel.map { it.id })
        }

        fun copy(s: String) { javafx.scene.input.Clipboard.getSystemClipboard().setContent(javafx.scene.input.ClipboardContent().apply { putString(s) }) }

        fun newProject() {
            val n = TextInputDialog().apply { initOwner(stage); headerText = null; contentText = "نام پروژه یا کتاب:"; dialogPane.nodeOrientation = NodeOrientation.RIGHT_TO_LEFT }.showAndWait().orElse("").trim()
            if (n.isEmpty()) return
            ResearchStore.ensureProject(n); project.items.setAll(ResearchStore.projects().map { it.name }); project.value = n
        }

        fun renameProject() {
            val p = ResearchStore.project(project.value ?: "") ?: return
            val n = TextInputDialog(p.name).apply { initOwner(stage); headerText = null; contentText = "نام تازه:"; dialogPane.nodeOrientation = NodeOrientation.RIGHT_TO_LEFT }.showAndWait().orElse("").trim()
            if (n.isEmpty() || n == p.name) return
            ResearchStore.updateProject(p.copy(name = n)); project.items.setAll(ResearchStore.projects().map { it.name }); project.value = n
        }

        fun deleteProject() {
            val p = ResearchStore.project(project.value ?: "") ?: return
            if (!confirm("پروژهٔ «${p.name}» حذف شود؟ فیش‌هایش به «${ResearchStore.DEFAULT_PROJECT}» منتقل می‌شوند.")) return
            ResearchStore.deleteProject(p, false); project.items.setAll(ResearchStore.projects().map { it.name }); project.value = project.items.firstOrNull()
        }

        fun export(fmt: String) {
            val name = project.value ?: return
            val fishes = list.items.toList().ifEmpty { return }
            val out = File(docsDir(), safeName(name) + " - فیش‌ها.$fmt")
            val w = ReportWriter(out)
            w.title(name); w.subtitle("فیش‌های پژوهش — " + PersianUtil.toPersianNumbers(fishes.size) + " فیش")
            fishes.groupBy { it.topic.ifBlank { "بدون سرفصل" } }.forEach { (t, fs) ->
                w.heading(1, t)
                fs.forEach { f ->
                    w.heading(3, f.title); w.quote(f.text, f.sourceName + " — " + f.sourcePath)
                    if (f.note.isNotBlank()) w.note("یادداشت: " + f.note)
                    if (f.codes.isNotEmpty() || f.tags.isNotEmpty()) w.note((f.codes + f.tags).joinToString("، "))
                }
            }
            w.save(); openFile(out)
        }
    }

    // ================================================================ دستیار هوشمند

    private inner class AssistantTab {
        val root: Node
        val instruction = TextArea().apply {
            promptText = "به دستیار بگویید چه کند؛ مثلاً:\nبا روش گراندد تئوری، همهٔ فیش‌های موضوع «توکل» را از مجموعهٔ «منابع اخلاق» با عنوان استخراج و در Documents\\Matnyaab\\توکل.docx ذخیره کن."
            prefRowCount = 3; isWrapText = true
        }
        val scenario = ComboBox<Scenario>().apply { items.setAll(*Scenario.values()); value = Scenario.FISH; maxWidth = Double.MAX_VALUE }
        val desc = label("", "muted")
        val topic = TextField().apply { promptText = "موضوع یا پرسش؛ مثلاً: خودسازی و مبارزه با نفس" }
        val goal = TextField().apply { promptText = "هدف یا روش (اختیاری)؛ مثلاً: فصل دوم پایان‌نامه" }
        val extra = TextField().apply { promptText = "دستور تکمیلی (اختیاری)؛ مثلاً: فقط دیدگاه‌های قرن اخیر" }
        val sources = MenuButton("همهٔ مجموعه‌ها")
        val project = projectCombo()
        val chapter = ComboBox<String>().apply { isEditable = true; promptText = "همهٔ فصل‌ها"; prefWidth = 220.0 }
        val format = ComboBox<String>().apply { items.setAll("Word (docx)", "Markdown (md)"); value = items[0] }
        val output = TextField()
        val maxDocs = Spinner<Int>(5, 80, 30, 5).apply { prefWidth = 90.0 }
        val provider = ComboBox<String>()
        val estimate = label("", "muted")
        val bar = ProgressBar(0.0).apply { maxWidth = Double.MAX_VALUE }
        val log = TextArea().apply { isEditable = false; isWrapText = true; prefRowCount = 9; styleClass.add("log-area") }
        val run = primary("اجرای مأموریت") { start() }
        val cancel = secondary("توقف") { stop.set(true) }
        val openOut = secondary("بازکردن فایل خروجی") { runCatching { openFile(File(output.text)) } }
        val stop = AtomicBoolean(false)
        val setup = card(label("دستیار هوشمند را با کلید خودتان روشن کنید", "card-title"),
            label("دستیار متن‌یاب با سرویس هوش مصنوعیِ دلخواه شما کار می‌کند (OpenAI، Claude، Gemini، درگاه‌های ایرانی مثل AvalAI و GapGPT، OpenRouter با مدل‌های رایگان، یا Ollama کاملاً آفلاین). " +
                "جست‌وجو و راستی‌آزمایی روی رایانهٔ شما انجام می‌شود و فقط بخش‌های لازم برای سرویس فرستاده می‌شود."),
            HBox(8.0, primary("افزودن سرویس هوش مصنوعی") { AiSettingsDialog(stage).show(); refreshProviders() },
                Hyperlink("از کجا کلید بگیرم؟").apply { setOnAction { runCatching { Desktop.getDesktop().browse(java.net.URI(ir.matnyaab.core.Constants.BASE_URL + "/#ai")) } } }).apply { alignment = Pos.CENTER_RIGHT })

        init {
            scenario.setCellFactory { object : ListCell<Scenario>() { override fun updateItem(s: Scenario?, e: Boolean) { super.updateItem(s, e); text = s?.title } } }
            scenario.buttonCell = scenario.cellFactory.call(null)
            scenario.valueProperty().addListener { _ -> sync() }
            topic.textProperty().addListener { _ -> autoOutput(); sync() }
            format.setOnAction { autoOutput() }
            maxDocs.valueProperty().addListener { _ -> sync() }
            project.setOnAction { chapter.items.setAll(ResearchStore.project(project.value ?: "")?.topics ?: emptyList()) }
            buildSources()
            refreshProviders()
            provider.setOnAction { AiConfig.load().providers.getOrNull(provider.selectionModel.selectedIndex)?.let { AiConfig.setActive(it.Id) } }

            val examples = FlowPane(6.0, 6.0).apply {
                listOf(
                    "فیش‌برداری موضوعی «توکل» از همهٔ منابع با عنوان و سرفصل",
                    "کدگذاری گراندد تئوری دربارهٔ «تربیت فرزند» برای فصل چهارم پایان‌نامه",
                    "پیشینهٔ پژوهش «عدالت اجتماعی» با ارجاع دقیق",
                    "دیدگاه‌های مطرح‌شده دربارهٔ «جهاد اکبر» را دسته‌بندی و مقایسه کن",
                    "طرح یک کتاب دربارهٔ «اخلاق خانواده» از منابع من",
                ).forEach { ex -> children.add(Button(ex).apply { styleClass.add("chip"); style = Theme.FONT + "-fx-font-size: 12px;"; setOnAction { instruction.text = ex } }) }
            }
            val plan = primary("برنامه‌ریزی از روی دستور") { plan() }
            val freeCard = card(label("۱. بگویید چه می‌خواهید (یا مستقیم فرم زیر را پر کنید)", "card-title"), instruction, examples, HBox(8.0, plan).apply { alignment = Pos.CENTER_RIGHT })
            val browse = secondary("انتخاب…") {
                val fc = FileChooser().apply { initialDirectory = docsDir(); initialFileName = File(output.text).name
                    extensionFilters.addAll(FileChooser.ExtensionFilter("Word", "*.docx"), FileChooser.ExtensionFilter("Markdown", "*.md")) }
                fc.showSaveDialog(stage)?.let { output.text = it.absolutePath }
            }
            val grid = GridPane().apply {
                hgap = 10.0; vgap = 8.0
                columnConstraints.addAll(ColumnConstraints(110.0), ColumnConstraints().apply { hgrow = Priority.ALWAYS })
                var r = 0
                fun row(l: String, n: Node) { add(label(l), 0, r); add(n, 1, r); r++ }
                row("کار:", VBox(4.0, scenario, desc))
                row("موضوع / پرسش:", topic)
                row("هدف / روش:", goal)
                row("منابع:", HBox(8.0, sources, label("حداکثر سند:"), maxDocs).apply { alignment = Pos.CENTER_RIGHT })
                row("پروژه:", HBox(8.0, project, label("فصل:"), chapter).apply { alignment = Pos.CENTER_RIGHT })
                row("دستور تکمیلی:", extra)
                row("خروجی:", HBox(8.0, format, output.also { HBox.setHgrow(it, Priority.ALWAYS) }, browse).apply { alignment = Pos.CENTER_RIGHT })
                row("سرویس:", HBox(8.0, provider, secondary("تنظیمات هوش مصنوعی") { AiSettingsDialog(stage).show(); refreshProviders() }).apply { alignment = Pos.CENTER_RIGHT })
            }
            val formCard = card(label("۲. مأموریت را بازبینی و اجرا کنید", "card-title"), grid, estimate, HBox(8.0, run, cancel, openOut).apply { alignment = Pos.CENTER_RIGHT })
            val runCard = card(label("۳. پیشرفت", "card-title"), bar, log)
            cancel.isDisable = true; openOut.isDisable = true
            val content = VBox(12.0, setup, freeCard, formCard, runCard).apply { padding = Insets(14.0) }
            root = ScrollPane(content).apply { isFitToWidth = true }
            autoOutput(); sync()
        }

        fun buildSources() {
            sources.items.clear()
            indexer.allObjects.forEach { c -> sources.items.add(CheckMenuItem(c.indexName).apply { setOnAction { updateSourcesLabel() } }) }
            updateSourcesLabel()
        }

        fun selectedSources() = sources.items.filterIsInstance<CheckMenuItem>().filter { it.isSelected }.map { it.text }
        fun updateSourcesLabel() { val s = selectedSources(); sources.text = if (s.isEmpty()) "همهٔ مجموعه‌ها" else s.joinToString("، ") }

        fun refreshProviders() {
            val d = AiConfig.load()
            provider.items.setAll(d.providers.map { "${it.Title} · ${it.Model}" })
            provider.selectionModel.select(maxOf(0, d.providers.indexOfFirst { it.Id == d.activeId }))
            setup.isVisible = d.providers.isEmpty(); setup.isManaged = setup.isVisible
            sync()
        }

        fun autoOutput() {
            val ext = if (format.value.startsWith("Word")) "docx" else "md"
            val name = safeName(topic.text.ifBlank { chapter.value ?: project.value ?: "خروجی دستیار" })
            val cur = output.text
            if (cur.isBlank() || cur.startsWith(docsDir().absolutePath)) output.text = File(docsDir(), "$name.$ext").absolutePath
            else if (!cur.endsWith(".$ext")) output.text = cur.substringBeforeLast('.') + ".$ext"
        }

        fun spec() = MissionSpec(scenario.value, topic.text.trim(), goal.text.trim(), selectedSources(),
            (project.editor?.text ?: project.value ?: "").trim(), (chapter.editor?.text ?: chapter.value ?: "").trim(),
            File(output.text.trim()), maxDocs.value, extra.text.trim())

        fun sync() {
            val s = scenario.value
            desc.text = s.desc
            topic.isDisable = !s.needsTopic
            val p = AiConfig.active()
            run.isDisable = p == null || (s.needsTopic && topic.text.isBlank())
            estimate.text = if (p == null) "" else "برآورد: حدود ${PersianUtil.toPersianNumbers(MissionRunner(indexer, settings, p, {}, {}, { false }).estimate(spec()))} درخواست به ${p.Title}؛ هزینه بر عهدهٔ حساب شما در همان سرویس است."
        }

        fun plan() {
            val p = AiConfig.active() ?: run { AiSettingsDialog(stage).show(); refreshProviders(); return }
            val text = instruction.text.trim().ifEmpty { return }
            log.text = "در حال برنامه‌ریزی…\n"
            bg {
                val r = runCatching { MissionRunner.route(p, text, indexer.allObjects.map { it.indexName }, ResearchStore.projects().map { it.name }) }
                Platform.runLater {
                    val o = r.getOrNull()
                    if (o == null) { log.appendText("برنامه‌ریزی نشد: ${r.exceptionOrNull()?.message ?: "پاسخ نامفهوم"} — فرم را دستی پر کنید.\n"); return@runLater }
                    runCatching { Scenario.valueOf(o.get("scenario").asString) }.getOrNull()?.let { scenario.value = it }
                    o.get("topic")?.asString?.let { topic.text = it }
                    o.get("goal")?.asString?.let { goal.text = it }
                    o.get("extra")?.asString?.let { extra.text = it }
                    o.get("project")?.asString?.takeIf { it.isNotBlank() }?.let { project.value = it }
                    o.get("chapter")?.asString?.takeIf { it.isNotBlank() }?.let { chapter.value = it }
                    val cols = o.getAsJsonArray("collections")?.map { it.asString } ?: emptyList()
                    sources.items.filterIsInstance<CheckMenuItem>().forEach { it.isSelected = it.text in cols }; updateSourcesLabel()
                    if (o.get("format")?.asString == "md") format.value = "Markdown (md)" else format.value = "Word (docx)"
                    o.get("output")?.asString?.takeIf { it.isNotBlank() }?.let { var f = it; if (!f.contains(':') && !f.startsWith("\\")) f = File(docsDir(), f).absolutePath
                        val ext = if (format.value.startsWith("Word")) ".docx" else ".md"; output.text = if (f.endsWith(".docx") || f.endsWith(".md")) f else f.trimEnd('\\', '/') + (if (File(f).isDirectory) File.separator + safeName(topic.text) else "") + ext }
                    log.appendText("✓ برنامه آماده است؛ فرم را بازبینی و «اجرای مأموریت» را بزنید.\n")
                    sync()
                }
            }
        }

        fun start() {
            val p = AiConfig.active() ?: return
            val spec = spec()
            if (spec.output.exists() && !confirm("فایل «${spec.output.name}» وجود دارد. جایگزین شود؟")) return
            stop.set(false); run.isDisable = true; cancel.isDisable = false; openOut.isDisable = true
            log.text = ""; bar.progress = 0.0
            bg {
                val r = runCatching {
                    MissionRunner(indexer, settings, p, { m -> Platform.runLater { log.appendText(m + "\n") } }, { v -> Platform.runLater { bar.progress = v } }, { stop.get() })
                        .run(spec, indexer.allObjects.toList())
                }
                Platform.runLater {
                    run.isDisable = false; cancel.isDisable = true
                    r.onSuccess { res ->
                        log.appendText("\n✓ انجام شد: ${res.file.absolutePath}\n   ${PersianUtil.toPersianNumbers(res.fishes)} فیش از ${PersianUtil.toPersianNumbers(res.sources)} سند · ${PersianUtil.toPersianNumbers(res.calls)} درخواست به سرویس\n")
                        openOut.isDisable = false; openFile(res.file)
                    }.onFailure { e -> log.appendText("\n✗ ${e.message ?: e.javaClass.simpleName}\n") }
                }
            }
        }
    }

    // ================================================================ ساخت کتاب

    private inner class BookTab {
        val root: Node
        val project = projectCombo()
        val chapters = ListView<String>()
        val bookTitle = TextField().apply { promptText = "عنوان کتاب" }
        val author = TextField().apply { promptText = "نویسنده / گردآورنده" }
        val paper = ComboBox<String>().apply { items.setAll(DocxWriter.Layout.PAPERS.keys); value = "وزیری" }
        val font = ComboBox<String>().apply { items.setAll("B Nazanin", "B Lotus", "IRNazanin", "Vazirmatn", "Sahel", "Tahoma"); value = "B Lotus"; isEditable = true }
        val size = Spinner<Int>(10, 18, 13).apply { prefWidth = 80.0 }
        val toc = CheckBox("فهرست مطالب").apply { isSelected = true }
        val notes = CheckBox("یادداشت‌های پژوهشگر زیر هر فیش").apply { isSelected = true }
        val bib = CheckBox("فهرست منابع پایانی").apply { isSelected = true }
        val stats = label("", "muted")

        init {
            project.setOnAction { load() }
            fun move(d: Int) {
                val i = chapters.selectionModel.selectedIndex; val j = i + d
                if (i < 0 || j !in chapters.items.indices) return
                val it = chapters.items.removeAt(i); chapters.items.add(j, it); chapters.selectionModel.select(j); persist()
            }
            val chapButtons = HBox(6.0, secondary("▲") { move(-1) }, secondary("▼") { move(1) },
                secondary("فصل تازه") {
                    val n = TextInputDialog().apply { initOwner(stage); headerText = null; contentText = "عنوان فصل:"; dialogPane.nodeOrientation = NodeOrientation.RIGHT_TO_LEFT }.showAndWait().orElse("").trim()
                    if (n.isNotEmpty() && n !in chapters.items) { chapters.items.add(n); persist() }
                },
                secondary("تغییر نام") {
                    val i = chapters.selectionModel.selectedIndex; if (i < 0) return@secondary
                    val old = chapters.items[i]
                    val n = TextInputDialog(old).apply { initOwner(stage); headerText = null; contentText = "عنوان تازه:"; dialogPane.nodeOrientation = NodeOrientation.RIGHT_TO_LEFT }.showAndWait().orElse("").trim()
                    if (n.isEmpty() || n == old) return@secondary
                    chapters.items[i] = n; persist()
                    ResearchStore.fishes(project.value).filter { it.topic == old }.forEach { ResearchStore.update(it.copy(topic = n)) }
                })
            val left = card(label("فصل‌ها به ترتیب کتاب", "card-title"), label("ترتیب را با ▲▼ بچینید؛ فیش‌های هر سرفصل زیر همان فصل می‌آیند.", "muted"), chapters, chapButtons, stats)
                .apply { VBox.setVgrow(chapters, Priority.ALWAYS) }
            val grid = GridPane().apply {
                hgap = 10.0; vgap = 8.0
                var r = 0
                fun row(l: String, n: Node) { add(label(l), 0, r); add(n, 1, r); r++ }
                row("پروژه:", project); row("عنوان:", bookTitle); row("نویسنده:", author)
                row("قطع:", paper); row("قلم:", HBox(8.0, font, label("اندازه:"), size).apply { alignment = Pos.CENTER_RIGHT })
                row("بخش‌ها:", VBox(4.0, toc, notes, bib))
            }
            val right = card(label("قالب و خروجی", "card-title"), grid,
                label("خروجی یک فایل Word صفحه‌آرایی‌شده است: صفحهٔ عنوان، فهرست مطالب خودکار، فصل‌ها با سبک «عنوان ۱»، نقل‌قول‌ها با پانویس منبع، شمارهٔ صفحه و فهرست منابع. " +
                    "در ورد با افزونهٔ متن‌یاب می‌توانید قالب ناشر یا دانشگاه را هم یک‌جا اعمال کنید.", "muted"),
                HBox(8.0, primary("ساخت فایل Word کتاب") { build() },
                    secondary("نگارش پیوستهٔ فصل‌ها با دستیار…") { select("ai") }).apply { alignment = Pos.CENTER_RIGHT })
            root = HBox(14.0, left.apply { prefWidth = 420.0 }, right.apply { HBox.setHgrow(this, Priority.ALWAYS) }).apply { padding = Insets(14.0) }
            load()
        }

        fun load() {
            val p = ResearchStore.project(project.value ?: "") ?: return
            val fs = ResearchStore.fishes(p.name)
            val extra = fs.map { it.topic }.filter { it.isNotBlank() && it !in p.topics }.distinct()
            chapters.items.setAll(p.topics + extra)
            bookTitle.text = bookTitle.text.ifBlank { p.name }
            stats.text = "${PersianUtil.toPersianNumbers(fs.size)} فیش · ${PersianUtil.toPersianNumbers(chapters.items.size)} فصل · ${PersianUtil.toPersianNumbers(fs.count { it.topic.isBlank() })} فیش بی‌فصل (در «سایر» می‌آیند)"
        }

        fun persist() { ResearchStore.project(project.value ?: "")?.let { ResearchStore.updateProject(it.copy(topics = chapters.items.toList())) } }

        fun build() {
            val p = ResearchStore.project(project.value ?: "") ?: return
            val fs = ResearchStore.fishes(p.name)
            if (fs.isEmpty()) { info("این پروژه هنوز فیشی ندارد."); return }
            val layout = DocxWriter.Layout.paper(paper.value, DocxWriter.Layout(fontFa = font.editor.text.ifBlank { "B Lotus" }, size = size.value.toDouble(), headingFont = font.editor.text.ifBlank { "B Lotus" }))
            val out = File(docsDir(), safeName(bookTitle.text.ifBlank { p.name }) + ".docx")
            val w = DocxWriter(layout)
            w.title(bookTitle.text.ifBlank { p.name })
            if (author.text.isNotBlank()) w.subtitle(author.text)
            if (p.goal.isNotBlank()) w.subtitle(p.goal)
            w.pageBreak()
            if (toc.isSelected) w.toc()
            val order = chapters.items.toList() + listOf("")
            order.forEachIndexed { i, ch ->
                val list = fs.filter { (if (ch.isEmpty()) it.topic.isBlank() || it.topic !in chapters.items else it.topic == ch) }
                if (list.isEmpty()) return@forEachIndexed
                w.heading(1, if (ch.isEmpty()) "سایر" else "فصل ${PersianUtil.toPersianNumbers(i + 1)}: $ch")
                list.sortedBy { it.created }.forEach { f ->
                    w.heading(2, f.title)
                    w.quote(f.text, f.sourceName.ifBlank { "منبع نامشخص" })
                    if (notes.isSelected && f.note.isNotBlank()) w.para(f.note)
                }
                w.pageBreak()
            }
            if (bib.isSelected) { w.heading(1, "منابع"); fs.map { it.sourceName }.filter { it.isNotBlank() }.distinct().sorted().forEach { w.para(it) } }
            runCatching { w.save(out) }.onSuccess { openFile(out) }.onFailure { info("ذخیره نشد: ${it.message}\nاگر فایل در ورد باز است، آن را ببندید.") }
        }
    }

    // ================================================================ منبع‌یاب و همانندجویی

    private inner class SourceTab {
        val root: Node
        val input = TextArea().apply { promptText = "متن یا نقل‌قول را این‌جا بچسبانید، یا یک فایل Word/PDF انتخاب کنید…"; isWrapText = true; prefRowCount = 8 }
        val results = TableView<Array<String>>()
        val summary = label("", "card-title")
        val bar = ProgressBar(0.0).apply { maxWidth = Double.MAX_VALUE; isVisible = false }
        var rows = listOf<Pair<String, ir.matnyaab.core.research.SourceMatch?>>()

        init {
            fun col(t: String, i: Int, w: Double) = TableColumn<Array<String>, String>(t).apply { prefWidth = w; setCellValueFactory { SimpleStringProperty(it.value[i]) } }
            results.columns.addAll(col("متن شما", 0, 380.0), col("منبع", 1, 220.0), col("شباهت", 2, 70.0), col("متن منبع", 3, 360.0))
            results.placeholder = label("نتیجه‌ها این‌جا نمایش داده می‌شوند.", "muted")
            results.setRowFactory { TableRow<Array<String>>().apply { setOnMouseClicked { e -> if (e.clickCount == 2 && item != null && item[4].isNotBlank()) runCatching { Desktop.getDesktop().open(File(item[4])) } } } }
            val pick = secondary("انتخاب فایل…") {
                FileChooser().apply { extensionFilters.add(FileChooser.ExtensionFilter("اسناد", "*.docx", "*.doc", "*.pdf", "*.txt", "*.rtf", "*.odt")) }.showOpenDialog(stage)?.let { f ->
                    bg { val t = runCatching { TikaExtractor.parse(f).content ?: "" }.getOrDefault(""); Platform.runLater { input.text = t } }
                }
            }
            val one = primary("این متن از کجاست؟") { findOne() }
            val all = secondary("همانندجویی کل متن") { checkAll() }
            val report = secondary("گزارش Word") { report() }
            root = VBox(10.0,
                card(label("منبع هر نقل‌قول را در کتابخانهٔ خودتان پیدا کنید", "card-title"),
                    label("«این متن از کجاست؟»: سند و جای دقیق یک نقل‌قول را حتی با تغییرهای جزئی پیدا می‌کند. «همانندجویی»: متن را جمله‌به‌جمله با کتابخانه مقایسه می‌کند تا پیش از تحویل (مثلاً به سامانهٔ همانندجو) بخش‌های بدون ارجاع را ببینید و اصلاح کنید.", "muted"),
                    input, HBox(8.0, one, all, pick, report).apply { alignment = Pos.CENTER_RIGHT }, bar),
                summary, results).apply { padding = Insets(14.0); VBox.setVgrow(results, Priority.ALWAYS) }
        }

        fun show(list: List<Pair<String, ir.matnyaab.core.research.SourceMatch?>>) {
            rows = list
            results.items = FXCollections.observableArrayList(list.map { (s, m) ->
                arrayOf(s.take(300), m?.name ?: "—", m?.let { PersianUtil.toPersianNumbers((it.similarity * 100).toInt()) + "٪" } ?: "", m?.excerpt?.take(400)?.replace('\n', ' ') ?: "", m?.path ?: "")
            })
        }

        fun findOne() {
            val t = input.text.trim().ifEmpty { return }
            summary.text = "در حال جست‌وجو…"
            bg {
                val r = SourceFinder.find(indexer, indexer.allObjects.toList(), t.take(3000), 5)
                Platform.runLater {
                    summary.text = if (r.isEmpty()) "منبعی برای این متن در کتابخانه پیدا نشد." else "${PersianUtil.toPersianNumbers(r.size)} منبع محتمل (بیشترین شباهت ${PersianUtil.toPersianNumbers((r.first().similarity * 100).toInt())}٪). برای بازکردن سند دوبار کلیک کنید."
                    show(r.map { t.take(300) to it })
                }
            }
        }

        fun checkAll() {
            val t = input.text.trim().ifEmpty { return }
            bar.isVisible = true; bar.progress = 0.0; summary.text = "در حال مقایسه…"
            bg {
                val r = SourceFinder.check(indexer, indexer.allObjects.toList(), t) { i, n -> Platform.runLater { bar.progress = i.toDouble() / n; summary.text = "مقایسهٔ جملهٔ ${PersianUtil.toPersianNumbers(i)} از ${PersianUtil.toPersianNumbers(n)}…" } }
                Platform.runLater {
                    bar.isVisible = false
                    val matched = r.count { it.match != null }
                    summary.text = if (r.isEmpty()) "جملهٔ کافی (دست‌کم ۸ واژه) برای بررسی نیست." else
                        "${PersianUtil.toPersianNumbers(if (r.isEmpty()) 0 else matched * 100 / r.size)}٪ جمله‌ها (${PersianUtil.toPersianNumbers(matched)} از ${PersianUtil.toPersianNumbers(r.size)}) با منابع کتابخانه همانندند. ارجاع این بخش‌ها را بررسی کنید."
                    show(r.sortedByDescending { it.match?.similarity ?: 0.0 }.map { it.sentence to it.match })
                }
            }
        }

        fun report() {
            if (rows.isEmpty()) return
            val out = File(docsDir(), "گزارش منبع‌یابی ${PersianUtil.toJalali(LocalDateTime.now()).replace('/', '-').replace(':', '-')}.docx")
            val w = ReportWriter(out)
            w.title("گزارش منبع‌یابی و همانندجویی"); w.subtitle(summary.text)
            w.table(listOf("متن شما", "منبع", "شباهت"), rows.map { (s, m) -> listOf(s.take(400), m?.name ?: "—", m?.let { PersianUtil.toPersianNumbers((it.similarity * 100).toInt()) + "٪" } ?: "") })
            rows.filter { it.second != null }.forEach { (s, m) -> w.heading(3, m!!.name); w.para(s); w.quote(m.excerpt.take(1200), m.path) }
            w.save(); openFile(out)
        }
    }
}
