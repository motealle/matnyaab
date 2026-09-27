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
    private val lblResultCount = Label().apply { style = Theme.FONT }

    // ---------------- پیش‌نمایش
    private val preview = PreviewPane(settings)

    // ---------------- پنل گزینه‌ها (آکاردئون)
    private lateinit var searchPanel: SearchPanel
    private lateinit var scopeTree: ScopeTree
    private lateinit var contentPacks: ContentPacksPanel
    private val accordions = mutableListOf<AccordionButton>()
    private val badge = Label().apply {
        style = "-fx-background-color: red; -fx-text-fill: white; -fx-background-radius: 8px; -fx-padding: 1 6 1 6; -fx-font-size: 10px;"
        isVisible = false
    }

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
        style = Theme.awesome(11) + "-fx-text-fill: red;"
        tooltip = Tooltip("وضعیت موتور استخراج متن")
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
        checkLicenseOnStart()
    }

    fun show() = stage.show()

    // ------------------------------------------------------------------ ساخت رابط

    private fun buildUi() {
        buildTable()

        // ----- پنل راست: گزینه‌ها
        searchPanel = SearchPanel(::quickSearch, ::advancedSearch)
        scopeTree = ScopeTree(indexer, { stage },
            onAddDocument = ::selectDirectory,
            onUpdateIndex = ::showIncrementalDialog,
            onIndexesChanged = { refreshIndexStatus() })
        contentPacks = ContentPacksPanel(settings, { stage },
            onDownloadProgress = ::onDownloadProgress,
            onNewCount = { n -> badge.isVisible = n > 0; badge.text = PersianUtil.toPersianNumbers(n) })

        val accSearch = AccordionButton(0, "جستجو", searchPanel, ::onAccordionOpen)
        val scopeBox = VBox(6.0,
            Button("افزودن سند جدید " + Theme.GLYPH_PLUS).apply {
                style = Theme.primaryButton() + Theme.awesome(11)
                prefHeight = Constants.OPTION_BUTTONS_HEIGHT; maxWidth = Double.MAX_VALUE
                setOnAction { selectDirectory() }
            },
            scopeTree,
        )
        val accScope = AccordionButton(1, "محدوده جستجو", scopeBox, ::onAccordionOpen)
        val accSettings = AccordionButton(2, "تنظیمات و اشتراک", buildSettingsBox(), ::onAccordionOpen)
        val accContents = AccordionButton(3, "بسته های محتوایی", contentPacks, ::onAccordionOpen)
        accordions.addAll(listOf(accSearch, accScope, accSettings, accContents))

        val rightPanel = VBox(8.0).apply {
            padding = Insets(8.0)
            children.addAll(accSearch, accScope, accSettings, StackPane(accContents, badge).also {
                StackPane.setAlignment(badge, Pos.TOP_LEFT)
            })
        }
        accSearch.expand()

        // ----- نوار بالای کادر نتایج: عنوان + تعداد فایل‌ها و مجموع موارد یافت‌شده
        val resultsHeader = HBox(8.0,
            Label("نتایج جستجو").apply { style = Theme.FONT },
            Region().also { HBox.setHgrow(it, Priority.ALWAYS) },
            lblResultCount,
        ).apply {
            nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
            alignment = Pos.CENTER_RIGHT
            padding = Insets(4.0, 8.0, 4.0, 8.0)
            style = "-fx-background-color: ${Constants.SURFACE_COLOR}; -fx-background-radius: 8 8 0 0; -fx-border-color: ${Constants.BORDER_COLOR}; -fx-border-width: 0 0 1 0;"
        }
        val resultsBox = VBox(resultsHeader, table).also { VBox.setVgrow(table, Priority.ALWAYS) }

        // ----- چیدمان سه‌پنلی با نسبت ۲/۳/۲ (چپ به راست: محتوا، فایل‌ها، گزینه‌ها)
        // پنل‌ها به صورت کارت‌های سفید گردگوشه روی زمینهٔ روشن (فلوئنت)
        preview.styleClass.add("card")
        resultsBox.styleClass.add("card")
        val optionsScroll = ScrollPane(rightPanel).apply { isFitToWidth = true; styleClass.add("card") }
        val split = SplitPane(preview, resultsBox, optionsScroll)
        split.nodeOrientation = NodeOrientation.LEFT_TO_RIGHT
        split.setDividerPositions(2.0 / 7.0, 5.0 / 7.0)

        val statusBar = buildStatusBar()
        val titleBar = CustomTitleBar(stage, "${Constants.APP_NAME_PERSIAN} ${Constants.APP_VERSION}") { onClose() }

        val root = BorderPane().apply {
            top = titleBar
            center = StackPane(split, waitOverlay)
            bottom = statusBar
            style = "-fx-background-color: ${Constants.CANVAS_COLOR}; -fx-border-color: ${Constants.BORDER_COLOR};"
        }
        stage.scene = Scene(root, 1200.0, 760.0)
        stage.title = Constants.APP_NAME_PERSIAN
        javaClass.getResource("/css/app.css")?.toExternalForm()?.let { stage.scene.stylesheets.add(it) }
    }

    private fun buildTable() {
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
        val cSize = col("اندازه", 80.0) { PersianUtil.properSize(it.size) }
        val cScore = col("امتیاز", 70.0) { it.score }
        val cTitle = col("عنوان", 160.0) { File(it.path).nameWithoutExtension }
        val cDomain = col("دامنه", 100.0) { it.indexName }
        table.columns.addAll(cFile, cSize, cScore, cTitle, cDomain)

        table.setRowFactory {
            val row = TableRow<SearchHit>()
            row.setOnMouseEntered { if (!row.isEmpty) row.style = "-fx-background-color: ${Constants.TABLE_HOVER_COLOR};" }
            row.setOnMouseExited { row.style = "" }
            row.setOnMouseClicked { e -> if (e.clickCount == 2 && !row.isEmpty) openResultDoc(row.item) }
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
            btn("تنظیمات برنامه") { SettingsDialog(stage, settings).show() },
            btn("اشتراک") { showLicenseDialog() },
            btn("راهنمای برنامه") { preview.showHelp() },
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

        return HBox(12.0, statusGlyph, newsPane, downloadBar, btnUpdate, lblIndexSize).apply {
            nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
            alignment = Pos.CENTER_RIGHT
            padding = Insets(4.0, 10.0, 4.0, 10.0)
            style = "-fx-background-color: ${Constants.SURFACE_COLOR}; -fx-border-color: ${Constants.BORDER_COLOR}; -fx-border-width: 1 0 0 0;"
        }
    }

    // ------------------------------------------------------------------ جستجو

    /** معادل basicSearching */
    private fun quickSearch(text: String) {
        if (!checkLicenseOnSearch()) return
        runSearch { indexer.makeSimpleQuery(text).also { indexer.makeAdvancedQuery(text.split(" ").filter { w -> w.isNotBlank() }, emptyList(), emptyList(), 0) } }
    }

    /** معادل advanceSearching با منطق ستاره‌دار کردن کلمات غیردقیق */
    private fun advancedSearch(p: AdvancedSearchParams) {
        if (!checkLicenseOnSearch()) return
        fun extractWords(s: String) = s.trim().replace(Regex("\\s+"), " ").split(" ").filter { it.isNotBlank() }
        fun star(words: List<String>, accuracy: Boolean) =
            if (accuracy) words else words.map { "*$it*" }

        val allW = star(extractWords(p.allWords), p.allAccuracy)
        val anyW = star(extractWords(p.anyWords), p.anyAccuracy)
        val noneW = star(extractWords(p.noneWords), p.noneAccuracy)
        if (allW.isEmpty() && anyW.isEmpty()) return
        // مطابق نسخه اصلی: اگر کمتر از ۲ کلمه در «همه کلمات» باشد فاصله بی‌معناست
        val slop = if (allW.size < 2) 0 else p.spaceBetween
        runSearch { indexer.makeAdvancedQuery(allW, anyW, noneW, slop) }
    }

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
                    table.items = FXCollections.observableArrayList(hits.sortedByDescending { it.score })
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

    /** معادل check_license_on_search: محدودیت ۲۵ مگابایت نسخه آزمایشی */
    private fun checkLicenseOnSearch(): Boolean {
        if (license.isSubscribed()) return true
        if (indexer.usedSpace() <= Constants.LIMITED_TRIAL_SIZE) return true
        val r = CustomMessageBox.show(stage, Constants.APP_NAME_PERSIAN,
            "اشتراک به اتمام رسیده است!",
            listOf("خرید اشتراک", "حذف فهرست ها", "لغو"))
        when (r) {
            0 -> showLicenseDialog()
            1 -> {
                val c = CustomMessageBox.show(stage, Constants.APP_NAME_PERSIAN,
                    "آیا مطمئن هستید همه فهرست ها حذف شوند؟", listOf("بله", "خیر"))
                if (c == 0) {
                    indexer.allObjects.toList().forEach { indexer.deleteIndexer(it.savePath) }
                    loadExistingIndexers()
                }
            }
        }
        return false
    }

    // ------------------------------------------------------------------ پیش‌نمایش و عملیات فایل

    /** معادل showPreview: هایلایت محتوا + بررسی حذف/تغییر فایل */
    private fun showPreview(hit: SearchHit) {
        val f = File(hit.path)
        val error = when {
            !f.exists() -> "فایل حذف شده است: ${hit.path}"
            f.lastModified() / 1000.0 > hit.time -> "فایل تغییر کرده است"
            else -> null
        }
        val (html, count) = indexer.highlightLastQuery(hit.content)
        preview.showContent(html, count, error)
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
        lblIndexSize.text = "حجم فهرست گیری: $size"
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

    private fun showLicenseDialog() = SubscriptionDialog(stage, settings, license).show()

    /** معادل check_license تایمر اول برنامه: هشدار کمتر از ۷ روز */
    private fun checkLicenseOnStart() {
        Platform.runLater {
            if (license.isSubscribed()) {
                val left = java.time.Duration.between(java.time.LocalDateTime.now(), license.licenseEnd).toDays()
                if (left < 7) CustomMessageBox.show(stage, Constants.APP_NAME_PERSIAN,
                    "از زمان اشتراک ${PersianUtil.toPersianNumbers(left)} روز باقی مانده است!")
            }
        }
    }

    private fun startBackgroundThreads() {
        // وضعیت موتور استخراج متن: با یک پارس کوچک گرم می‌شود و سبز می‌شود (معادل TikaRunThread)
        Thread {
            try {
                val tmp = File.createTempFile("matnyaab", ".txt").apply { writeText("سلام") }
                TikaExtractor.parse(tmp)
                tmp.delete()
                Platform.runLater { statusGlyph.style = Theme.awesome(11) + "-fx-text-fill: green;" }
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
    private fun onClose() {
        StatisticsReporter.report("close_app", license.systemId, license.isSubscribed())
        newsLoader?.stop()
        updateChecker?.stop()
        Platform.exit()
    }
}
