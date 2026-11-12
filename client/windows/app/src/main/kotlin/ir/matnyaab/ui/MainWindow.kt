package ir.matnyaab.ui

import ir.matnyaab.core.*
import ir.matnyaab.util.PersianUtil
import javafx.animation.AnimationTimer
import javafx.application.Platform
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.*
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import javafx.stage.StageStyle
import java.awt.Desktop
import java.io.File
import java.nio.file.Files

/**
 * پنجره اصلی متن‌یاب — پورت Ui_MainWindow در matnyaab.py:
 * سه پنل (نتایج ۳ / پیش‌نمایش ۲ / گزینه‌ها ۲)، نوار عنوان سفارشی، نوار وضعیت با
 * حجم فهرست‌گیری، نوار پیشرفت دانلود، اخبار متحرک و وضعیت موتور استخراج متن
 */
class MainWindow(
    private val stage: Stage,
    private val settings: AppSettingsManager,
    private val indexer: LuceneIndexer,
    private val license: LicenseChecker,
) {
    // ---------------- جدول نتایج
    private val table = TableView<SearchHit>()
    private lateinit var cCount: TableColumn<SearchHit, Int>
    private lateinit var cCover: TableColumn<SearchHit, Int>
    private lateinit var cDate: TableColumn<SearchHit, Any>
    private var sortMode = ir.matnyaab.core.search.SortMode.RELEVANCE
    private val sortBox = ComboBox<String>().apply {
        items.addAll(ir.matnyaab.core.search.SortMode.values().map { it.title })
        tooltip = Tooltip("مرتب‌سازی نتایج؛ «مرتبط‌ترین» تطابق متنی، تکرار، تراکم، پوشش عبارت‌ها و نام فایل را با هم می‌سنجد")
    }
    private var specMode: SearchMode? = null
    private val phraseSummary = Label().apply { styleClass.add("summary-bar"); isWrapText = true; maxWidth = Double.MAX_VALUE; isVisible = false; isManaged = false }
    private val lblResultCount = Label().apply { style = Theme.FONT }

    // ---------------- پیش‌نمایش
    private val preview = PreviewPane(settings)

    // ---------------- پنل گزینه‌ها (آکاردئون)
    private lateinit var searchPanel: SearchPanel
    private lateinit var scopeTree: ScopeTree
    private lateinit var contentPacks: ContentPacksPanel
    private val accordions = mutableListOf<AccordionButton>()
    private val badge = Label().apply {
        style = "-fx-background-color: -mx-danger; -fx-text-fill: white; -fx-background-radius: 8px; -fx-padding: 1 6 1 6; -fx-font-size: 10px;"
        isVisible = false
    }

    // ---------------- اشتراک اعتمادمحور
    internal val subs = SubscriptionManager(settings, license.systemId)
    private val subChip = Button().apply { isFocusTraversable = false }
    private val msgChip = Button().apply { isVisible = false; isManaged = false; isFocusTraversable = false }
    private var gateShown = false

    // ---------------- نوار وضعیت
    private val lblIndexSize = Label()
    private val downloadBar = ProgressBar(0.0).apply {
        prefWidth = 150.0; prefHeight = 20.0; isVisible = false
        tooltip = Tooltip("برای لغو دانلود دوبار کلیک کنید")
    }
    private val newsLabel = Label("")
    private val newsPane = Pane(newsLabel).apply { prefHeight = 22.0; minWidth = 120.0 }
    private var newsEnabled = true
    private var newsText = ""
    private val statusGlyph = Label(Theme.GLYPH_CIRCLE).apply {
        style = Theme.awesome(11) + "-fx-text-fill: -mx-muted;"
        tooltip = Tooltip("موتور استخراج متن در حال آماده شدن است…")
    }
    private val btnUpdate = Button().apply { isVisible = false; isManaged = false }

    private var newsLoader: NewsLoader? = null
    private var updateChecker: UpdateChecker? = null
    private val waitOverlay = WaitOverlay()

    init {
        stage.initStyle(StageStyle.UNDECORATED)
        buildUi()
        loadExistingIndexers()
        importOldWhooshIndexes()
        startBackgroundThreads()
        startBridge()
        startSemantic()
        // کاربر تازه: بخش افزودن پوشه باز شود تا قدم اول روشن باشد
        if (indexer.allObjects.isEmpty()) accordions.getOrNull(1)?.expand()
        if (System.getProperty("matnyaab.shots") != null) subs.declareLocal(subs.config.plans.last())
        refreshSubChip()
        startSubscriptionSync()
    }

    // ------------------------------------------------------------------ اشتراک
    private fun refreshSubChip() {
        val mood = subs.mood()
        subChip.text = SubTexts.chip(subs)
        val (bg, fg) = when (mood) {
            SubscriptionManager.Mood.GOOD -> "rgba(16,124,65,0.14)" to "-mx-success"
            SubscriptionManager.Mood.SOON, SubscriptionManager.Mood.ENDED -> "rgba(196,43,28,0.13)" to "-mx-danger"
            SubscriptionManager.Mood.NONE -> "-mx-accent-soft" to "-mx-accent-text"
        }
        subChip.style = "-fx-background-color: $bg; -fx-text-fill: $fg; -fx-background-radius: 12; -fx-padding: 3 12 3 12; -fx-cursor: hand; -fx-font-weight: bold;"
        subChip.tooltip = Tooltip("اشتراک، روزشمار و تمدید")
        val n = subs.unseenCount()
        msgChip.text = "✉ " + PersianUtil.toPersianNumbers(n)
        msgChip.isVisible = n > 0; msgChip.isManaged = n > 0
        msgChip.style = "-fx-background-color: -mx-accent; -fx-text-fill: white; -fx-background-radius: 12; -fx-padding: 3 10 3 10; -fx-cursor: hand;"
        msgChip.tooltip = Tooltip("پیام‌های تازه از متن‌یاب")
    }

    private fun showSubscription() { SubscriptionDialog(stage, subs) { refreshSubChip() }.show(); refreshSubChip() }

    /** یادآوری مهربان: بدون اشتراک/پایان‌یافته → یک بار هنگام شروع؛ هفتهٔ پایانی → روزی یک بار */
    private fun subscriptionOnStart() {
        if (System.getProperty("matnyaab.shots") != null) return
        val mood = subs.mood()
        when (mood) {
            SubscriptionManager.Mood.NONE, SubscriptionManager.Mood.ENDED -> { gateShown = true; showSubscription() }
            SubscriptionManager.Mood.SOON -> {
                val today = java.time.LocalDate.now().toString()
                if (settings.get("REMINDER_SHOWN") != today) {
                    settings.setValue("REMINDER_SHOWN", today)
                    val (t, b) = SubTexts.headline(subs)
                    val r = CustomMessageBox.show(stage, t, b, listOf("تمدید در سایت", "بعداً"))
                    if (r == 0) try { Desktop.getDesktop().browse(java.net.URI(subs.subscribeUrl())) } catch (e: Exception) {}
                }
            }
            else -> {}
        }
    }

    /** جست‌وجو هرگز قفل نمی‌شود؛ فقط بار اول در هر اجرا، اگر اشتراکی نیست، پنجرهٔ اشتراک یادآوری می‌شود */
    private fun subscriptionGate() {
        val mood = subs.mood()
        if (!gateShown && (mood == SubscriptionManager.Mood.NONE || mood == SubscriptionManager.Mood.ENDED)) {
            gateShown = true; showSubscription()
        }
    }

    private fun startSubscriptionSync() {
        Thread {
            var first = true
            while (true) {
                val fresh = subs.sync()
                Platform.runLater {
                    refreshSubChip()
                    if (first) subscriptionOnStart()
                    fresh.firstOrNull()?.let { n ->
                        if (System.getProperty("matnyaab.shots") == null) {
                            CustomMessageBox.show(stage, n.title, n.body)
                            subs.markAllSeen(); refreshSubChip()
                        }
                    }
                    first = false
                }
                try { Thread.sleep(6 * 3600_000L) } catch (e: InterruptedException) { return@Thread }
            }
        }.apply { isDaemon = true }.start()
    }

    fun show() = stage.show()

    // ---- برای تولید خودکار تصاویر معرفی (حالت --screenshots)
    internal fun demoSearch(text: String) = quickSearch(text)
    internal fun demoPhrases(lines: List<String>) = searchPanel.runPhrases(lines)
    internal fun selectFirstResult() { if (table.items.isNotEmpty()) table.selectionModel.select(0) }
    internal fun openPanel(i: Int) { accordions.getOrNull(i)?.let { if (!it.isOpen) it.expand() } }
    internal fun expandFirstScope() { scopeTree.tree.root.children.firstOrNull()?.isExpanded = true }

    // ------------------------------------------------------------------ ساخت رابط

    private fun buildUi() {
        buildTable()

        // ----- پنل راست: گزینه‌ها
        searchPanel = SearchPanel(::quickSearch, ::advancedSearch, settings, ::specSearch)
        scopeTree = ScopeTree(indexer, { stage },
            onAddDocument = ::selectDirectory,
            onUpdateIndex = ::showIncrementalDialog,
            onIndexesChanged = { refreshIndexStatus() })
        contentPacks = ContentPacksPanel(settings, { stage },
            onDownloadProgress = ::onDownloadProgress,
            onNewCount = { n -> badge.isVisible = n > 0; badge.text = PersianUtil.toPersianNumbers(n) })

        val accSearch = AccordionButton(0, "جستجو", searchPanel, ::onAccordionOpen)
        val scopeBox = VBox(8.0,
            Button(Theme.GLYPH_PLUS + "  افزودن پوشه اسناد").apply {
                style = Theme.addButton()
                prefHeight = Constants.OPTION_HEIGHT; maxWidth = Double.MAX_VALUE
                tooltip = Tooltip("یک پوشه را انتخاب کنید تا محتوای فایل‌هایش جست‌وجوپذیر شود (Ctrl+O)")
                setOnMouseEntered { style = Theme.addButton(hover = true) }
                setOnMouseExited { style = Theme.addButton() }
                setOnAction { selectDirectory() }
            },
            scopeTree,
        )
        val accScope = AccordionButton(1, "محدوده جستجو", scopeBox, ::onAccordionOpen)
        val accSettings = AccordionButton(2, "تنظیمات و اشتراک", buildSettingsBox(), ::onAccordionOpen)
        val accContents = AccordionButton(3, "بسته‌های محتوایی", contentPacks, ::onAccordionOpen)
        accordions.addAll(listOf(accSearch, accScope, accSettings, accContents))

        val rightPanel = VBox(8.0).apply {
            padding = Insets(8.0)
            children.addAll(accSearch, accScope, accSettings, StackPane(accContents, badge).also {
                StackPane.setAlignment(badge, Pos.TOP_LEFT)
            })
        }
        accSearch.expand()
        table.placeholder = Label("هنوز جست‌وجویی انجام نشده است.\nیک پوشه اضافه کنید و سپس واژه‌ای را جست‌وجو کنید.").apply {
            styleClass.add("empty-hint")
        }

        // ----- نوار بالای کادر نتایج: عنوان + تعداد فایل‌ها و مجموع موارد یافت‌شده
        val resultsHeader = HBox(8.0,
            Label("نتایج جست‌وجو").apply { styleClass.add("section-title") },
            Region().also { HBox.setHgrow(it, Priority.ALWAYS) },
            lblResultCount,
            sortBox,
            Button("⭳ خروجی").apply {
                style = Theme.addButton(); tooltip = Tooltip("ذخیرهٔ فهرست نتایج در فایل CSV (قابل بازکردن در اکسل)")
                setOnAction { exportResults() }
            },
        ).apply {
            nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
            alignment = Pos.CENTER_RIGHT
            padding = Insets(4.0, 8.0, 4.0, 8.0)
            style = "-fx-background-color: ${Constants.SURFACE_COLOR}; -fx-background-radius: 8 8 0 0; -fx-border-color: ${Constants.BORDER_COLOR}; -fx-border-width: 0 0 1 0;"
        }
        phraseSummary.style = Theme.FONT
        val resultsBox = VBox(resultsHeader, phraseSummary, table).also { VBox.setVgrow(table, Priority.ALWAYS) }

        // ----- چیدمان سه‌پنلی با نسبت ۲/۳/۲ (چپ به راست: محتوا، فایل‌ها، گزینه‌ها)
        // پنل‌ها به صورت کارت‌های سفید گردگوشه روی زمینهٔ روشن (فلوئنت)
        preview.styleClass.add("card")
        resultsBox.styleClass.add("card")
        val optionsScroll = ScrollPane(rightPanel).apply { isFitToWidth = true; styleClass.add("card") }
        val split = SplitPane(preview, resultsBox, optionsScroll)
        split.nodeOrientation = NodeOrientation.LEFT_TO_RIGHT
        split.setDividerPositions(2.0 / 7.0, 5.0 / 7.0)

        val statusBar = buildStatusBar()
        val titleBar = CustomTitleBar(stage, "${Constants.APP_NAME_PERSIAN} ۳") { onClose() }

        val root = BorderPane().apply {
            top = javafx.scene.layout.VBox(titleBar, buildToolbar())
            center = StackPane(split, waitOverlay)
            bottom = statusBar
            style = "-fx-background-color: ${Constants.CANVAS_COLOR}; -fx-border-color: ${Constants.BORDER_COLOR};"
        }
        stage.scene = Scene(root, 1240.0, 780.0)
        stage.title = Constants.APP_NAME_PERSIAN
        stage.minWidth = 900.0; stage.minHeight = 560.0
        Theme.install(stage.scene, stage)
        // میان‌برها: Ctrl+F جست‌وجو، Ctrl+O افزودن پوشه
        stage.scene.accelerators[javafx.scene.input.KeyCombination.keyCombination("Shortcut+F")] = Runnable {
            accordions.getOrNull(0)?.let { if (!it.isOpen) it.expand() }
            searchPanel.focusQuick()
        }
        stage.scene.accelerators[javafx.scene.input.KeyCombination.keyCombination("Shortcut+O")] = Runnable { selectDirectory() }
        stage.scene.accelerators[javafx.scene.input.KeyCombination.keyCombination("Shortcut+D")] = Runnable { quickFish(preview.selectedText()) }
        preview.onFish = { quickFish(it) }
        preview.btnFish.isVisible = flag("FF_RESEARCH"); preview.btnFish.isManaged = preview.btnFish.isVisible
    }

    private fun statusFlash(msg: String) {
        val old = lblIndexSize.text
        lblIndexSize.text = msg
        javafx.animation.PauseTransition(javafx.util.Duration.seconds(3.0)).apply { setOnFinished { if (lblIndexSize.text == msg) lblIndexSize.text = old } }.play()
    }

    private fun flag(k: String) = settings.get(k) != "0"

    private fun research(tab: String) = ResearchWindow.show(stage, settings, indexer, tab)

    /** نوار ابزار بالای برنامه: کارهای اصلی پژوهشی در یک نگاه */
    private fun buildToolbar(): javafx.scene.layout.HBox {
        fun tool(glyph: String, text: String, tip: String, a: () -> Unit) = Button(text).apply {
            styleClass.add("tool-btn"); style = Theme.FONT
            graphic = Label(glyph).apply { styleClass.add("tool-glyph") }
            tooltip = Tooltip(tip).apply { style = Theme.FONT }
            setOnAction { a() }
        }
        fun sep() = Region().apply { styleClass.add("tool-sep") }
        val items = mutableListOf<javafx.scene.Node>(
            tool("⌕", "جست‌وجو", "جست‌وجو در کتابخانهٔ شما (Ctrl+F)") { accordions.getOrNull(0)?.let { if (!it.isOpen) it.expand() }; searchPanel.focusQuick() },
            tool("＋", "افزودن پوشه", "افزودن پوشهٔ اسناد به کتابخانه (Ctrl+O)") { selectDirectory() },
        )
        if (flag("FF_RESEARCH")) items += listOf(sep(),
            tool("🗂", "فیش‌ها", "فیش‌ها و پروژه‌های پژوهشی؛ در پیش‌نمایش متن را انتخاب و Ctrl+D بزنید") { research("fish") },
            tool("📘", "ساخت کتاب", "از فیش‌ها یک فایل Word صفحه‌آرایی‌شده با فهرست و پانویس بسازید") { research("book") })
        if (flag("FF_SOURCE")) items += tool("⧉", "منبع‌یاب", "منبع یک نقل‌قول و همانندجویی متن با کتابخانهٔ شما") { research("source") }
        if (flag("FF_AI")) items += listOf(sep(), tool("✦", "دستیار هوشمند", "مأموریت‌های پژوهشی چندمرحله‌ای با هوش مصنوعیِ دلخواه شما و خروجی Word/Markdown") { research("ai") })
        if (flag("FF_SEMANTIC")) items += tool("≈", "جست‌وجوی مفهومی", "جست‌وجو بر پایهٔ معنا (بستهٔ اختیاری آفلاین)") { openSemantic() }
        items += listOf(Region().also { HBox.setHgrow(it, Priority.ALWAYS) },
            tool("W", "افزونهٔ ورد", "متن‌یاب درون Microsoft Word: فیش، استناد، قالب پایان‌نامه، نامهٔ اداری") { runCatching { Desktop.getDesktop().browse(java.net.URI(Constants.BASE_URL + "/#word")) } },
            tool("?", "چه کارهایی می‌توانم بکنم؟", "سناریوهای کاربردی متن‌یاب") { ScenariosDialog.show(stage) { research(it) } })
        return HBox(4.0, *items.toTypedArray()).apply { styleClass.add("app-toolbar"); alignment = Pos.CENTER_RIGHT; nodeOrientation = NodeOrientation.RIGHT_TO_LEFT }
    }

    /** فیش سریع از متن انتخاب‌شده در پیش‌نمایش؛ منبع و جای دقیق خودکار ثبت می‌شود */
    private fun quickFish(selected: String) {
        if (!flag("FF_RESEARCH")) return
        val hit = previewHit
        if (selected.isBlank() || hit == null) {
            Alert(Alert.AlertType.INFORMATION, "ابتدا در پیش‌نمایش یک سند، بخشی از متن را انتخاب کنید؛ سپس «＋ فیش» یا Ctrl+D را بزنید.", ButtonType.OK)
                .apply { initOwner(stage); headerText = null; dialogPane.nodeOrientation = NodeOrientation.RIGHT_TO_LEFT }.showAndWait(); return
        }
        val store = ir.matnyaab.core.research.ResearchStore
        val project = ComboBox<String>().apply { isEditable = true; items.setAll(store.projects().map { it.name }); value = settings.get("LAST_PROJECT").takeIf { it.isNotBlank() } ?: items.firstOrNull(); maxWidth = Double.MAX_VALUE }
        val topic = ComboBox<String>().apply { isEditable = true; maxWidth = Double.MAX_VALUE; value = settings.get("LAST_TOPIC") }
        fun topics() { topic.items.setAll(store.project(project.editor.text ?: "")?.topics ?: emptyList()) }
        project.setOnAction { topics() }; topics()
        val title = TextField(selected.split(Regex("\\s+")).take(8).joinToString(" "))
        val tags = TextField().apply { promptText = "برچسب‌ها با ویرگول (اختیاری)" }
        val note = TextArea().apply { prefRowCount = 3; isWrapText = true; promptText = "برداشت یا یادداشت شما (اختیاری)" }
        val text = TextArea(selected).apply { prefRowCount = 5; isWrapText = true }
        val grid = javafx.scene.layout.GridPane().apply {
            hgap = 8.0; vgap = 8.0; var r = 0
            fun row(l: String, n: javafx.scene.Node) { add(Label(l).apply { style = Theme.FONT }, 0, r); add(n, 1, r); javafx.scene.layout.GridPane.setHgrow(n, Priority.ALWAYS); r++ }
            row("پروژه:", project); row("سرفصل:", topic); row("عنوان:", title); row("متن:", text); row("یادداشت:", note); row("برچسب:", tags)
            add(Label("منبع: ${File(hit.path).name}").apply { styleClass.add("muted"); style = Theme.FONT }, 1, r)
        }
        val dlg = Dialog<ButtonType>().apply {
            initOwner(stage); title = "فیش تازه"; headerText = null
            dialogPane.content = grid.apply { prefWidth = 520.0 }; dialogPane.nodeOrientation = NodeOrientation.RIGHT_TO_LEFT; dialogPane.style = Theme.FONT
            dialogPane.buttonTypes.addAll(ButtonType("ذخیرهٔ فیش", ButtonBar.ButtonData.OK_DONE), ButtonType("انصراف", ButtonBar.ButtonData.CANCEL_CLOSE))
        }
        if (dlg.showAndWait().orElse(null)?.buttonData != ButtonBar.ButtonData.OK_DONE) return
        val pName = (project.editor.text ?: "").trim().ifBlank { store.DEFAULT_PROJECT }
        val tName = (topic.editor.text ?: "").trim()
        val p = store.ensureProject(pName)
        if (tName.isNotBlank() && tName !in p.topics) store.updateProject(p.copy(topics = p.topics + tName))
        val start = hit.content.indexOf(text.text.trim().take(60))
        store.add(ir.matnyaab.core.research.Fish(project = pName, topic = tName, title = title.text.trim(), text = text.text.trim(), note = note.text.trim(),
            tags = tags.text.split(Regex("[،,]")).map { it.trim() }.filter { it.isNotEmpty() },
            sourcePath = hit.path, sourceName = File(hit.path).nameWithoutExtension, collection = hit.indexName,
            start = start, end = if (start >= 0) start + text.text.trim().length else -1))
        settings.setValue("LAST_PROJECT", pName); settings.setValue("LAST_TOPIC", tName)
        statusFlash("✓ فیش در «$pName» ذخیره شد")
    }

    private fun applySort(i: Int) {
        sortMode = ir.matnyaab.core.search.SortMode.values()[i.coerceIn(0, ir.matnyaab.core.search.SortMode.values().size - 1)]
        settings.setValue("RESULT_SORT", sortMode.name)
        cDate.isVisible = sortMode == ir.matnyaab.core.search.SortMode.NEWEST || sortMode == ir.matnyaab.core.search.SortMode.OLDEST
        table.sortOrder.clear()
        if (table.items.isNotEmpty()) {
            val sel = table.selectionModel.selectedItem
            table.items = FXCollections.observableArrayList(ir.matnyaab.core.search.Ranking.sort(table.items.toList(), sortMode))
            if (sel != null) table.selectionModel.select(sel)
        }
    }

    private fun buildTable() {
        sortMode = runCatching { ir.matnyaab.core.search.SortMode.valueOf(settings.get("RESULT_SORT")) }.getOrDefault(ir.matnyaab.core.search.SortMode.RELEVANCE)
        sortBox.selectionModel.select(sortMode.ordinal)
        sortBox.style = Theme.FONT
        sortBox.selectionModel.selectedIndexProperty().addListener { _, _, i -> if (i.toInt() >= 0) applySort(i.toInt()) }
        table.nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
        table.style = Theme.FONT
        table.selectionModel.selectionMode = SelectionMode.SINGLE
        table.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN

        fun col(title: String, w: Double, value: (SearchHit) -> Any): TableColumn<SearchHit, Any> =
            TableColumn<SearchHit, Any>(title).apply {
                prefWidth = w
                setCellValueFactory { SimpleObjectProperty(value(it.value)) }
            }

        // ستون‌ها مطابق نسخه اصلی: فایل / اندازه / امتیاز / عنوان / دامنه
        val cFile = col("فایل", 180.0) { File(it.path).name }
        val cSize = col("اندازه", 80.0) { PersianUtil.toPersianNumbers(PersianUtil.properSize(it.size)) }
        // ستون «ارتباط»: رتبهٔ ترکیبی ۰ تا ۱۰۰ با نوار کوچک (برای جست‌وجوی قدیمی همان امتیاز)
        val cScore = TableColumn<SearchHit, Int>("ارتباط").apply {
            prefWidth = 78.0
            setCellValueFactory { SimpleObjectProperty(if (it.value.relevance >= 0) it.value.relevance else it.value.score) }
            setCellFactory { object : TableCell<SearchHit, Int>() {
                private val bar = ProgressBar().apply { prefWidth = 52.0; prefHeight = 9.0; styleClass.add("rel-bar") }
                override fun updateItem(v: Int?, empty: Boolean) {
                    super.updateItem(v, empty)
                    val h = tableRow?.item
                    if (empty || v == null || h == null) { graphic = null; text = null; return }
                    if (h.relevance >= 0) { bar.progress = v / 100.0; graphic = bar; text = null; tooltip = Tooltip("ارتباط " + PersianUtil.toPersianNumbers(v) + " از ۱۰۰") }
                    else { graphic = null; text = PersianUtil.toPersianNumbers(v) }
                }
            } }
        }
        cDate = col("تاریخ", 90.0) { if (it.time > 0) PersianUtil.toJalali(java.time.LocalDateTime.ofEpochSecond(it.time.toLong(), 0, java.time.ZoneOffset.UTC)) else "" }.apply { isVisible = sortMode == ir.matnyaab.core.search.SortMode.NEWEST || sortMode == ir.matnyaab.core.search.SortMode.OLDEST }
        val cTitle = col("عنوان", 160.0) { File(it.path).nameWithoutExtension }
        val cDomain = col("دامنه", 100.0) { it.indexName }
        // ستون‌های جست‌وجوی نسل دوم: تعداد موارد و پوشش عبارت‌ها (با مرتب‌سازی عددی)
        cCount = TableColumn<SearchHit, Int>("تعداد").apply {
            prefWidth = 62.0; isVisible = false
            setCellValueFactory { SimpleObjectProperty(it.value.matches) }
            setCellFactory { object : TableCell<SearchHit, Int>() {
                override fun updateItem(v: Int?, empty: Boolean) {
                    super.updateItem(v, empty)
                    val h = tableRow?.item
                    text = if (empty || v == null || v < 0) "" else if (v == 0 && h != null && h.semantic >= 0) "مفهومی" else PersianUtil.toPersianNumbers(v)
                    style = if (text == "مفهومی") "-fx-text-fill: -mx-accent-text; -fx-font-size: 11px;" else ""
                }
            } }
        }
        cCover = TableColumn<SearchHit, Int>("عبارت‌ها").apply {
            prefWidth = 72.0; isVisible = false
            setCellValueFactory { SimpleObjectProperty(it.value.perPhrase.count { c -> c > 0 }) }
            setCellFactory { object : TableCell<SearchHit, Int>() {
                override fun updateItem(v: Int?, empty: Boolean) {
                    super.updateItem(v, empty)
                    val h = tableRow?.item
                    text = if (empty || v == null || h == null) "" else PersianUtil.toPersianNumbers("$v/${h.perPhrase.size}")
                }
            } }
        }
        table.columns.addAll(cFile, cScore, cCount, cCover, cDate, cSize, cTitle, cDomain)

        table.setRowFactory {
            val row = TableRow<SearchHit>()
            row.setOnMouseEntered { if (!row.isEmpty) row.style = "-fx-background-color: ${Constants.TABLE_HOVER_COLOR};" }
            row.setOnMouseExited { row.style = "" }
            row.setOnMouseClicked { e -> if (e.clickCount == 2 && !row.isEmpty) openResultDoc(row.item) }
            row.itemProperty().addListener { _, _, h ->
                row.tooltip = if (h != null && h.relevance >= 0) Tooltip(
                    "ارتباط " + PersianUtil.toPersianNumbers(h.relevance) + " · " + PersianUtil.toPersianNumbers(h.matches) + " مورد · تراکم " +
                        PersianUtil.toPersianNumbers(String.format(java.util.Locale.US, "%.1f", h.density)) + " در هر هزار واژه") else null
            }
            row
        }
        table.selectionModel.selectedItemProperty().addListener { _, _, hit -> if (hit != null) showPreview(hit) }

        // منوی راست‌کلیک: باز کردن فایل / بازکردن پوشه / کپی فایل
        val miOpen = MenuItem("باز کردن فایل").apply { setOnAction { table.selectionModel.selectedItem?.let { openResultDoc(it) } } }
        val miFolder = MenuItem("بازکردن پوشه").apply { setOnAction { table.selectionModel.selectedItem?.let { openFolder(it) } } }
        val miCopy = MenuItem("کپی فایل").apply { setOnAction { table.selectionModel.selectedItem?.let { copyFile(it) } } }
        val menu = ContextMenu(miOpen, miFolder, miCopy)
        menu.setOnShowing {
            val exists = table.selectionModel.selectedItem?.let { File(it.path).exists() } ?: false
            miOpen.isDisable = !exists; miFolder.isDisable = !exists; miCopy.isDisable = !exists
        }
        table.contextMenu = menu
    }

    private fun buildSettingsBox(): VBox {
        fun btn(text: String, action: () -> Unit) = Button(text).apply {
            style = Theme.primaryButton(); prefHeight = Constants.OPTION_BUTTONS_HEIGHT; maxWidth = Double.MAX_VALUE
            setOnMouseEntered { style = Theme.primaryButtonHover() }
            setOnMouseExited { style = Theme.primaryButton() }
            setOnAction { action() }
        }
        return VBox(6.0,
            btn("تنظیمات و پوستهٔ رنگی") { SettingsDialog(stage, settings).show() },
            btn("جست‌وجوی معنایی (اختیاری)") { openSemantic() },
            btn("راهنمای برنامه") { preview.showHelp() },
            Button("★  اشتراک و تمدید").apply {
                style = Theme.addButton(); prefHeight = Constants.OPTION_BUTTONS_HEIGHT; maxWidth = Double.MAX_VALUE
                setOnMouseEntered { style = Theme.addButton(hover = true) }
                setOnMouseExited { style = Theme.addButton() }
                setOnAction { showSubscription() }
            },
            btn("پشتیبانی و پیشنهاد قابلیت") { try { Desktop.getDesktop().browse(java.net.URI(subs.config.contactUrl)) } catch (e: Exception) {} },
        ).apply { padding = Insets(8.0) }
    }

    private fun buildStatusBar(): HBox {
        lblIndexSize.style = Theme.FONT
        refreshIndexStatus()

        downloadBar.setOnMouseClicked { e ->
            if (e.clickCount == 2) { contentPacks.cancelDownload(); onDownloadProgress(-1) }
        }

        // اخبار متحرک (معادل QTimer ۲۰ میلی‌ثانیه)
        newsLabel.style = "${Theme.FONT} -fx-font-size: ${Constants.FONT_SIZE_SMALL}px;"
        var x = 0.0
        val timer = object : AnimationTimer() {
            private var last = 0L
            override fun handle(now: Long) {
                if (now - last < 20_000_000) return
                last = now
                if (!newsEnabled || newsText.isBlank()) return
                x -= 1.0
                if (x < -newsLabel.width) x = newsPane.width
                newsLabel.layoutX = x
            }
        }
        timer.start()
        newsPane.clip = javafx.scene.shape.Rectangle().also { r ->
            newsPane.widthProperty().addListener { _, _, w -> r.width = w.toDouble() }
            newsPane.heightProperty().addListener { _, _, h -> r.height = h.toDouble() }
        }
        val newsMenu = ContextMenu(CheckMenuItem("اخبار و اطلاعیه ها").apply {
            isSelected = true
            setOnAction { newsEnabled = isSelected; newsLabel.isVisible = isSelected }
        })
        newsPane.setOnContextMenuRequested { e -> newsMenu.show(newsPane, e.screenX, e.screenY) }
        newsPane.setOnMouseClicked { e -> if (e.clickCount == 2) { newsEnabled = !newsEnabled; newsLabel.isVisible = newsEnabled } }
        HBox.setHgrow(newsPane, Priority.ALWAYS)

        subChip.setOnAction { showSubscription() }
        msgChip.setOnAction { MessagesDialog.show(stage, subs); refreshSubChip() }
        return HBox(12.0, subChip, msgChip, statusGlyph, newsPane, downloadBar, btnUpdate, lblIndexSize).apply {
            nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
            alignment = Pos.CENTER_RIGHT
            padding = Insets(4.0, 10.0, 4.0, 10.0)
            style = "-fx-background-color: ${Constants.SURFACE_COLOR}; -fx-border-color: ${Constants.BORDER_COLOR}; -fx-border-width: 1 0 0 0;"
        }
    }

    // ------------------------------------------------------------------ جستجو

    /** معادل basicSearching */
    private fun quickSearch(text: String) {
        subscriptionGate()
        setSpecMode(null, null)
        runSearch { indexer.makeSimpleQuery(text).also { indexer.makeAdvancedQuery(text.split(" ").filter { w -> w.isNotBlank() }, emptyList(), emptyList(), 0) } }
    }

    /** معادل advanceSearching با منطق ستاره‌دار کردن کلمات غیردقیق */
    private fun advancedSearch(p: AdvancedSearchParams) {
        subscriptionGate()
        fun extractWords(s: String) = s.trim().replace(Regex("\\s+"), " ").split(" ").filter { it.isNotBlank() }
        fun star(words: List<String>, accuracy: Boolean) =
            if (accuracy) words else words.map { "*$it*" }

        val allW = star(extractWords(p.allWords), p.allAccuracy)
        val anyW = star(extractWords(p.anyWords), p.anyAccuracy)
        val noneW = star(extractWords(p.noneWords), p.noneAccuracy)
        if (allW.isEmpty() && anyW.isEmpty()) return
        // مطابق نسخه اصلی: اگر کمتر از ۲ کلمه در «همه کلمات» باشد فاصله بی‌معناست
        val slop = if (allW.size < 2) 0 else p.spaceBetween
        setSpecMode(null, null)
        runSearch { indexer.makeAdvancedQuery(allW, anyW, noneW, slop) }
    }

    /** جست‌وجوی نسل دوم (واژه/عبارت، چند واژه، چند عبارت) */
    private fun specSearch(spec: ir.matnyaab.core.search.SearchSpec, mode: SearchMode) {
        subscriptionGate()
        waitOverlay.showOverlay()
        val (selected, masked) = scopeTree.getSelectedDocs()
        Thread {
            try {
                val hits = indexer.searchSpec(spec, selected, masked)
                val phrases = indexer.lastEngine?.let { e -> (0 until e.phraseCount).map { e.phrase(it) } } ?: spec.cleanPhrases
                val totals = IntArray(phrases.size).also { t -> hits.forEach { h -> h.perPhrase.forEachIndexed { i, c -> t[i] += c } } }
                Platform.runLater {
                    setSpecMode(mode, if (mode != SearchMode.PHRASE && phrases.size > 1)
                        phrases.indices.joinToString("   ·   ") { i -> "«${phrases[i]}» ${PersianUtil.toPersianNumbers(totals[i])}" } else null)
                    table.items = FXCollections.observableArrayList(ir.matnyaab.core.search.Ranking.sort(hits, sortMode))
                    lblResultCount.text = PersianUtil.toPersianNumbers(hits.size) + " فایل — " +
                        PersianUtil.toPersianNumbers(hits.sumOf { it.matches }) + " مورد"
                    preview.clear(); preview.setSummary(null)
                    waitOverlay.hideOverlay()
                }
            } catch (e: Exception) {
                Platform.runLater {
                    waitOverlay.hideOverlay()
                    CustomMessageBox.show(stage, "خطا", "خطا در جستجو: ${e.message}")
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun setSpecMode(mode: SearchMode?, summary: String?) {
        specMode = mode
        cCount.isVisible = mode != null
        cCover.isVisible = mode != null && mode != SearchMode.PHRASE && summary != null
        phraseSummary.text = summary ?: ""
        phraseSummary.isVisible = summary != null; phraseSummary.isManaged = summary != null
        if (mode == null) preview.setSummary(null)
    }

    /** اجرای جست‌وجو از بیرون (پل افزونهٔ ورد) */
    internal fun externalSearch(text: String) = searchPanel.runPhrase(text)
    fun externalSearchLater(text: String) = Platform.runLater { externalSearch(text) }

    private fun runSearch(queryBuilder: () -> org.apache.lucene.search.Query) {
        waitOverlay.showOverlay()
        val (selected, masked) = scopeTree.getSelectedDocs()
        Thread {
            try {
                val query = queryBuilder()
                val hits = indexer.search(query, selected, masked)
                // مجموع کل موارد یافت‌شده در همه فایل‌ها (روی ترد پس‌زمینه محاسبه می‌شود)
                val totalMatches = hits.sumOf { indexer.countMatches(it.content) }
                Platform.runLater {
                    table.items = FXCollections.observableArrayList(ir.matnyaab.core.search.Ranking.sort(hits, sortMode))
                    lblResultCount.text = PersianUtil.toPersianNumbers(hits.size) + " فایل — " +
                        PersianUtil.toPersianNumbers(totalMatches) + " مورد"
                    preview.clear()
                    waitOverlay.hideOverlay()
                }
            } catch (e: Exception) {
                Platform.runLater {
                    waitOverlay.hideOverlay()
                    CustomMessageBox.show(stage, "خطا", "خطا در جستجو: ${e.message}")
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun exportResults() {
        if (table.items.isEmpty()) { CustomMessageBox.show(stage, "خروجی نتایج", "ابتدا جست‌وجو کنید تا نتیجه‌ای برای ذخیره باشد."); return }
        val f = javafx.stage.FileChooser().apply {
            title = "ذخیرهٔ نتایج"; initialFileName = "matnyaab-results.csv"
            extensionFilters.add(javafx.stage.FileChooser.ExtensionFilter("CSV", "*.csv"))
        }.showSaveDialog(stage) ?: return
        fun q(v: Any) = "\"" + v.toString().replace("\"", "\"\"") + "\""
        val phrases = indexer.lastEngine?.takeIf { specMode != null }?.let { e -> (0 until e.phraseCount).map { e.phrase(it) } } ?: emptyList()
        val lines = listOf((listOf("فایل", "مسیر", "اندازه (بایت)", "امتیاز", "مجموعه", "تعداد موارد", "ارتباط (۰ تا ۱۰۰)", "تراکم در هزار واژه", "تاریخ فایل") + phrases).joinToString(",") { q(it) }) +
            table.items.map { h -> (listOf(File(h.path).name, h.path, h.size, h.score, h.indexName, h.matches.coerceAtLeast(0), h.relevance.coerceAtLeast(0), String.format(java.util.Locale.US, "%.2f", h.density),
                if (h.time > 0) PersianUtil.toJalali(java.time.LocalDateTime.ofEpochSecond(h.time.toLong(), 0, java.time.ZoneOffset.UTC)) else "") + h.perPhrase).joinToString(",") { q(it) } }
        f.writeText("\uFEFF" + lines.joinToString("\r\n"), Charsets.UTF_8)
        CustomMessageBox.show(stage, "خروجی نتایج", "${PersianUtil.toPersianNumbers(table.items.size)} نتیجه ذخیره شد.")
    }

    // ------------------------------------------------------------------ پیش‌نمایش و عملیات فایل

    /** معادل showPreview: هایلایت محتوا + بررسی حذف/تغییر فایل */
    private var previewHit: SearchHit? = null

    private fun showPreview(hit: SearchHit) {
        previewHit = hit
        val f = File(hit.path)
        val error = when {
            !f.exists() -> "فایل حذف شده است: ${hit.path}"
            f.lastModified() / 1000.0 > hit.time -> "فایل تغییر کرده است"
            else -> null
        }
        val (html, count) = if (hit.matches == 0 && hit.chunkStart >= 0) indexer.highlightRange(hit.content, hit.chunkStart, hit.chunkEnd) to 0
            else indexer.highlightLastQuery(hit.content)
        preview.showContent(html, count, error)
        if (hit.matches == 0 && hit.chunkStart >= 0) {
            preview.setSummary("نتیجهٔ مفهومی: واژه‌های جست‌وجو در این سند نیامده‌اند، ولی این بخش از نظر معنا نزدیک است (شباهت " +
                PersianUtil.toPersianNumbers((hit.semantic * 100).toInt()) + "٪).")
            return
        }
        val engine = indexer.lastEngine
        preview.setSummary(if (specMode != null && engine != null && hit.perPhrase.size > 1)
            "این فایل: " + PersianUtil.toPersianNumbers(hit.perPhrase.count { it > 0 }) + " از " + PersianUtil.toPersianNumbers(hit.perPhrase.size) + " — " +
                hit.perPhrase.indices.filter { hit.perPhrase[it] > 0 }.joinToString(" · ") { "«${engine.phrase(it)}» ${PersianUtil.toPersianNumbers(hit.perPhrase[it])}" }
        else null)
    }

    /** معادل openResultDoc (os.startfile) */
    private fun openResultDoc(hit: SearchHit) {
        val f = File(hit.path)
        if (!f.exists()) { CustomMessageBox.show(stage, "خطا", "فایل حذف شده است: ${hit.path}"); return }
        try { Desktop.getDesktop().open(f) } catch (e: Exception) {
            CustomMessageBox.show(stage, "خطا", "امکان باز کردن فایل وجود ندارد.")
        }
    }

    /** معادل explorer /select */
    private fun openFolder(hit: SearchHit) {
        try {
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("win")) Runtime.getRuntime().exec(arrayOf("explorer", "/select,", hit.path))
            else Desktop.getDesktop().open(File(hit.path).parentFile)
        } catch (e: Exception) { /* silent */ }
    }

    private fun copyFile(hit: SearchHit) {
        val content = ClipboardContent()
        content.putFiles(listOf(File(hit.path)))
        Clipboard.getSystemClipboard().setContent(content)
    }

    // ------------------------------------------------------------------ فهرست‌ها

    /** معادل selectDirectory + scanDirectoryDialog */
    private fun selectDirectory() {
        val dir = DirectoryChooser().apply { title = "انتخاب پوشه" }.showDialog(stage) ?: return
        ScanDirectoryDialog(stage, dir.absolutePath, indexer, license) {
            loadExistingIndexers()
        }.show()
    }

    private fun showIncrementalDialog(single: IndexerInfo?) {
        val targets = single?.let { listOf(it) } ?: indexer.allObjects.toList()
        if (targets.isEmpty()) return
        IncrementalIndexingDialog(stage, targets, indexer, license) {
            loadExistingIndexers()
        }.show()
    }

    /** معادل loadExistingdIndexers: خواندن همه پوشه‌های فهرست از روی fileInfo.json */
    private fun openSemantic() {
        SemanticDialog(stage, settings, { indexer.allObjects.toList() }) { searchPanel.refreshSemantic() }.show()
        searchPanel.refreshSemantic()
    }

    /** راه‌اندازی/همگام‌سازی پس‌زمینهٔ بردارها (فقط اگر کاربر جست‌وجوی معنایی را روشن کرده باشد) */
    private fun startSemantic() {
        if (System.getProperty("matnyaab.shots") == "1") return
        ir.matnyaab.core.semantic.SemanticService.listener = { Platform.runLater { searchPanel.refreshSemantic() } }
        ir.matnyaab.core.semantic.SemanticService.start(settings) { indexer.allObjects.toList() }
        searchPanel.refreshSemantic()
    }

    private fun loadExistingIndexers() {
        indexer.allObjects.clear()
        val root = Constants.INDEX_DIR
        Files.createDirectories(root)
        Files.list(root).use { stream ->
            stream.filter { Files.isDirectory(it) }.forEach { dir ->
                if (WhooshIndexImporter.isWhooshIndex(dir)) return@forEach // در importOldWhooshIndexes مدیریت می‌شود
                val info = indexer.readFileInfo(dir)
                if (info != null && info["name"] != null && info["path"] != null) {
                    indexer.loadIndexer(info["name"]!!, dir.toString(), info["path"]!!)
                } else {
                    val r = CustomMessageBox.show(stage, "فهرست",
                        "خطا در بارگذاری فهرست: ${dir.fileName}", listOf("حذف", "ادامه"))
                    if (r == 0) dir.toFile().deleteRecursively()
                }
            }
        }
        scopeTree.reload()
        refreshIndexStatus()
        ir.matnyaab.core.semantic.SemanticService.start(settings) { indexer.allObjects.toList() }
    }

    /**
     * مهاجرت از فهرست‌های قدیمی ووش: به جای مبدل باینری پرخطا، پوشه‌های قبلی
     * به صورت خودکار با Lucene دوباره فهرست می‌شوند (با تایید کاربر).
     */
    private fun importOldWhooshIndexes() {
        val old = WhooshIndexImporter.findOldIndexes(indexer, Constants.INDEX_DIR)
        if (old.isEmpty()) return
        val names = old.joinToString("، ") { it.indexName }
        val r = CustomMessageBox.show(stage, "ارتقاء فهرست ها",
            "فهرست های نسخه قبلی برنامه پیدا شد ($names). برای استفاده در نسخه جدید لازم است دوباره فهرست شوند. این کار اکنون انجام شود؟",
            listOf("بله", "بعدا"))
        if (r != 0) return
        for (o in old) {
            WhooshIndexImporter.clearOldIndexFiles(o.dir)
            if (File(o.rootPath).isDirectory) {
                ScanDirectoryDialog(stage, o.rootPath, indexer, license) { loadExistingIndexers() }.show()
                o.dir.toFile().deleteRecursively()
            } else {
                o.dir.toFile().deleteRecursively()
            }
        }
        loadExistingIndexers()
    }

    /** معادل setIndexedSizeStatus */
    private fun refreshIndexStatus() {
        val size = PersianUtil.toPersianNumbers(PersianUtil.humanFileSize(indexer.usedSpace()))
        val files = PersianUtil.toPersianNumbers(indexer.allObjects.size)
        lblIndexSize.text = "$files مجموعه · حجم اسناد فهرست‌شده: $size"
    }

    // ------------------------------------------------------------------ آکاردئون و بسته‌ها

    /** معادل onlyOneOptionOpen: فقط یک گزینه باز می‌ماند؛ باز شدن بسته‌ها بازدید ثبت می‌کند */
    private fun onAccordionOpen(id: Int) {
        accordions.filter { it.optionId != id && it.isOpen }.forEach { it.collapse() }
        if (id == 3) { contentPacks.refresh(); contentPacks.markVisited() }
    }

    private fun onDownloadProgress(percent: Int) {
        if (percent < 0) { downloadBar.isVisible = false; return }
        downloadBar.isVisible = true
        downloadBar.progress = percent / 100.0
    }

    // ------------------------------------------------------------------ اشتراک، اخبار، بروزرسانی

    private fun startBackgroundThreads() {
        // وضعیت موتور استخراج متن: با یک پارس کوچک گرم می‌شود و سبز می‌شود (معادل TikaRunThread)
        Thread {
            try {
                val tmp = File.createTempFile("matnyaab", ".txt").apply { writeText("سلام") }
                TikaExtractor.parse(tmp)
                tmp.delete()
                Platform.runLater {
                    statusGlyph.style = Theme.awesome(11) + "-fx-text-fill: -mx-success;"
                    statusGlyph.tooltip = Tooltip("موتور استخراج متن آماده است")
                }
            } catch (e: Exception) { /* قرمز می‌ماند */ }
        }.apply { isDaemon = true }.start()

        newsLoader = NewsLoader { news ->
            Platform.runLater {
                newsText = news.joinToString("     ***     ") { "${it.first}: ${it.second.replace(Regex("<[^>]*>"), "")}" }
                newsLabel.text = newsText
            }
        }.also { it.start() }

        updateChecker = UpdateChecker { version ->
            Platform.runLater {
                btnUpdate.text = "دریافت نسخه $version"
                btnUpdate.style = "${Theme.FONT} -fx-background-color: ${Constants.UPDATE_BUTTON_COLOR}; -fx-background-radius: 4px; -fx-cursor: hand;"
                btnUpdate.isVisible = true; btnUpdate.isManaged = true
                btnUpdate.setOnAction { downloadUpdate(version) }
            }
        }.also { it.start() }
    }

    /** معادل DownloadUpdate + finishDownloadUpdate */
    private fun downloadUpdate(version: String) {
        val outFile = Constants.APP_SETTINGS_DIR.resolve("MATNYAAB_${Constants.APP_ARCH}_setup.exe").toFile()
        val tmp = File(outFile.absolutePath + ".tmp")
        val dl = Downloader(Constants.APP_UPDATE_DOWNLOAD_URL, tmp,
            onProgress = { p -> Platform.runLater { onDownloadProgress(p) } },
            onFinish = { code ->
                Platform.runLater {
                    onDownloadProgress(-1)
                    if (code == 0) {
                        tmp.copyTo(outFile, overwrite = true); tmp.delete()
                        val r = CustomMessageBox.show(stage, "بروزرسانی",
                            "برنامه بسته خواهد شد و نصب نسخه جدید آغاز می شود. ادامه می دهید؟", listOf("بله", "خیر"))
                        if (r == 0) {
                            try { Runtime.getRuntime().exec(arrayOf(outFile.absolutePath)) } catch (e: Exception) {}
                            onClose()
                        }
                    } else if (code == 1) CustomMessageBox.show(stage, "خطا", "خطا در دانلود بروزرسانی!")
                }
            })
        dl.start()
    }

    /** معادل closeEvent */
    // ------------------------------------------------------------------ پل افزونهٔ ورد

    private var bridge: ir.matnyaab.core.bridge.LocalBridge? = null

    private fun startBridge() {
        if (settings.get("FF_WORD_BRIDGE") == "0" || System.getProperty("matnyaab.shots") == "1") return
        Thread {
            runCatching {
                ir.matnyaab.core.bridge.LocalBridge.stopHeadless()
                val b = ir.matnyaab.core.bridge.LocalBridge(settings, indexer, "gui", object : ir.matnyaab.core.bridge.LocalBridge.Host {
                    override fun show(query: String?) = Platform.runLater {
                        stage.isIconified = false; stage.show(); stage.toFront(); stage.requestFocus()
                        if (!query.isNullOrBlank()) externalSearch(query)
                    }
                    override fun collectionsChanged() = Platform.runLater { loadExistingIndexers(); refreshIndexStatus() }
                    override fun addFolder(path: String): Boolean {
                        Platform.runLater {
                            stage.isIconified = false; stage.toFront()
                            ScanDirectoryDialog(stage, path, indexer, license) { loadExistingIndexers() }.show()
                        }
                        return true
                    }
                })
                b.start(); bridge = b
            }.onFailure { System.err.println("bridge: ${it.message}") }
        }.apply { isDaemon = true }.start()
    }

    private fun onClose() {
        runCatching { bridge?.stop() }
        StatisticsReporter.report("close_app", license.systemId, license.isSubscribed())
        newsLoader?.stop()
        updateChecker?.stop()
        Platform.exit()
    }
}
