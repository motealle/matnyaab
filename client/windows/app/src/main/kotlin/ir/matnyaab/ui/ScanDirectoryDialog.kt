package ir.matnyaab.ui

import ir.matnyaab.core.*
import ir.matnyaab.util.PersianUtil
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.layout.*
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.stage.Window
import java.io.File

/**
 * دیالوگ «افزودن پوشه» (فهرست‌گیری) — طراحی فشردهٔ متن‌یاب ۲:
 * نام دسته، انتخاب نوع اسناد با چند گزینهٔ ساده (به‌جای فهرست بلند پسوندها)،
 * زیرپوشه‌ها، تنظیمات پیشرفتهٔ تاشو، دکمهٔ شروع درست زیر گزینه‌ها،
 * و بخش پیشرفت (درصد، شمارش، زمان باقیمانده، خطاها) که فقط هنگام کار ظاهر می‌شود.
 * هیچ محدودیت حجمی یا اشتراکی وجود ندارد.
 */
class ScanDirectoryDialog(
    owner: Window?,
    private val rootPath: String,
    private val indexer: LuceneIndexer,
    @Suppress("UNUSED_PARAMETER") license: LicenseChecker? = null,
    private val onFinished: () -> Unit,
) {
    /** گروه‌های نوع سند؛ هر گروه چند پسوند را پوشش می‌دهد */
    data class FormatGroup(val title: String, val exts: List<String>, val default: Boolean)

    companion object {
        val GROUPS = listOf(
            FormatGroup("Word و متن", listOf(".doc", ".docx", ".rtf", ".odt", ".ott", ".txt"), true),
            FormatGroup("PDF", listOf(".pdf"), true),
            FormatGroup("پاورپوینت", listOf(".ppt", ".pptx", ".odp"), true),
            FormatGroup("OneNote", listOf(".one"), true),
            FormatGroup("اکسل", listOf(".xls", ".xlsx", ".ods", ".csv"), false),
            FormatGroup("وب، XML و کتاب الکترونیک", listOf(".htm", ".html", ".xhtml", ".xml", ".epub"), false),
        )
    }

    private val stage = Stage(StageStyle.UNDECORATED)
    private val txtCatName = TextField(File(rootPath).name)
    private val groupChecks = GROUPS.map { g ->
        CheckBox(g.title).apply {
            isSelected = g.default
            styleClass.add("chip")
            tooltip = Tooltip(g.exts.joinToString("  "))
        }
    }
    private val chkSubFolders = CheckBox("زیرپوشه‌ها هم فهرست شوند").apply { isSelected = true }
    private val chkAllFormats = CheckBox("همهٔ فایل‌ها با هر پسوندی (کندتر)")
    private val txtMinSize = TextField("1").apply { prefColumnCount = 6 }
    private val txtMaxSize = TextField("200").apply { prefColumnCount = 6 }
    private val units = listOf("بایت", "کیلوبایت", "مگابایت")
    private val cmbMinUnit = ComboBox(FXCollections.observableArrayList(units)).apply { selectionModel.select(0) }
    private val cmbMaxUnit = ComboBox(FXCollections.observableArrayList(units)).apply { selectionModel.select(2) }

    private val progress = ProgressBar(0.0).apply { maxWidth = Double.MAX_VALUE; prefHeight = 10.0 }
    private val lblProgress = Label("در حال شمارش فایل‌ها…")
    private val lblEta = Label("").apply { styleClass.add("muted") }
    private val lblCurrent = Label("").apply {
        styleClass.add("muted"); maxWidth = Double.MAX_VALUE
        textOverrun = OverrunStyle.LEADING_ELLIPSIS
        style = "-fx-font-size: ${Constants.FONT_SIZE_SMALL}px;"
    }
    private val tblErrors = TableView<ErrorRow>().apply {
        val c1 = TableColumn<ErrorRow, String>("فایل").apply { cellValueFactory = PropertyValueFactory("file"); prefWidth = 170.0 }
        val c2 = TableColumn<ErrorRow, String>("مسیر").apply { cellValueFactory = PropertyValueFactory("path"); prefWidth = 200.0 }
        val c3 = TableColumn<ErrorRow, String>("علت").apply { cellValueFactory = PropertyValueFactory("error"); prefWidth = 140.0 }
        columns.addAll(c1, c2, c3)
        prefHeight = 130.0
        placeholder = Label("همهٔ فایل‌ها بدون مشکل خوانده شدند.")
    }
    private val errorsPane = TitledPane("فایل‌هایی که خوانده نشدند (۰)", tblErrors).apply {
        isExpanded = false; isVisible = false; isManaged = false
    }
    private val ownerStage = owner as? Stage
    private val btnPark = Button("⤓ ادامه در پس‌زمینه (کنار ساعت)").apply {
        style = Theme.addButton(); isVisible = TrayProgress.supported; isManaged = isVisible
        tooltip = javafx.scene.control.Tooltip("پنجره‌ها پنهان می‌شوند و پیشرفت را با نماد متن‌یاب کنار ساعت ویندوز می‌بینید؛ در پایان برنامه خودکار برمی‌گردد.")
        setOnAction { TrayProgress.park(listOfNotNull(ownerStage, stage), "فهرست‌سازی «${txtCatName.text.trim()}»") }
    }
    private val progressBox = VBox(6.0, lblProgress, progress, lblEta, lblCurrent, btnPark, errorsPane).apply {
        padding = Insets(12.0)
        styleClass.add("card")
        isVisible = false; isManaged = false
    }
    private val btnStart = Button()
    private val service = IndexingService(indexer)
    private var running = false
    private var startMs = 0L
    private var errorCount = 0

    class ErrorRow(val file: String, val path: String, val error: String)

    init {
        stage.initModality(Modality.APPLICATION_MODAL)
        owner?.let { stage.initOwner(it) }

        chkAllFormats.setOnAction { groupChecks.forEach { it.isDisable = chkAllFormats.isSelected } }
        styleStart(false)
        btnStart.setOnAction { if (running) cancel() else start() }

        val lblFolder = Label(rootPath).apply {
            style = "-fx-font-weight: bold;"
            textOverrun = OverrunStyle.LEADING_ELLIPSIS
            maxWidth = Double.MAX_VALUE
            tooltip = Tooltip(rootPath)
        }
        val folderRow = VBox(2.0, Label("پوشهٔ انتخاب‌شده").apply { styleClass.add("muted") }, lblFolder)

        val nameRow = VBox(4.0, Label("نام این مجموعه در متن‌یاب").apply { styleClass.add("section-title") }, txtCatName)

        val chips = FlowPane(8.0, 8.0).apply { children.addAll(groupChecks) }
        val typesRow = VBox(6.0, Label("چه نوع اسنادی جست‌وجوپذیر شوند؟").apply { styleClass.add("section-title") }, chips)

        val advGrid = GridPane().apply {
            hgap = 8.0; vgap = 8.0
            add(Label("حداقل اندازهٔ فایل:"), 0, 0); add(txtMinSize, 1, 0); add(cmbMinUnit, 2, 0)
            add(Label("حداکثر اندازهٔ فایل:"), 0, 1); add(txtMaxSize, 1, 1); add(cmbMaxUnit, 2, 1)
            add(chkAllFormats, 0, 2, 3, 1)
        }
        val advanced = TitledPane("تنظیمات بیشتر", advGrid).apply { isExpanded = false }

        val content = VBox(14.0,
            folderRow, nameRow, typesRow, chkSubFolders, advanced, btnStart, progressBox,
        ).apply {
            padding = Insets(18.0)
            style = "-fx-background-color: ${Constants.OPTION_BACKGROUND_COLOR};"
        }

        val root = BorderPane().apply {
            nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
            top = CustomTitleBar(stage, "افزودن پوشه به متن‌یاب", showMaximize = false) { if (!running) stage.close() }
            center = ScrollPane(content).apply { isFitToWidth = true; hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER }
            style = "-fx-border-color: ${Constants.BORDER_COLOR}; -fx-background-color: ${Constants.OPTION_BACKGROUND_COLOR};"
        }
        stage.scene = Scene(root, Constants.FEHRESTGIRI_WINDOW_WIDTH, Constants.FEHRESTGIRI_WINDOW_HEIGHT)
        Theme.install(stage.scene, stage)
        Platform.runLater { txtCatName.requestFocus(); txtCatName.selectAll() }
    }

    private fun styleStart(cancelMode: Boolean) {
        btnStart.text = if (cancelMode) "توقف فهرست‌گیری" else "شروع فهرست‌گیری"
        btnStart.style = if (cancelMode) Theme.dangerButton() else Theme.successButton()
        btnStart.prefHeight = Constants.FISH_BARDARI_BUTTON
        btnStart.maxWidth = Double.MAX_VALUE
        btnStart.isDefaultButton = !cancelMode
    }

    private fun unitFactor(u: String): Long = when (u) {
        "کیلوبایت" -> 1024L
        "مگابایت" -> 1024L * 1024L
        else -> 1L
    }

    private fun fa(n: Number) = PersianUtil.toPersianNumbers(n.toString())

    private fun formatEta(secs: Long): String = when {
        secs < 60 -> "کمتر از یک دقیقه"
        secs < 3600 -> "حدود ${fa(secs / 60 + 1)} دقیقه"
        else -> "حدود ${fa(secs / 3600)} ساعت و ${fa((secs % 3600) / 60)} دقیقه"
    }

    private fun start() {
        val name = txtCatName.text.trim()
        if (name.isBlank()) { CustomMessageBox.show(stage, "افزودن پوشه", "یک نام برای این مجموعه وارد کنید."); return }
        val formats: Set<String> = if (chkAllFormats.isSelected) emptySet()
            else GROUPS.zip(groupChecks).filter { it.second.isSelected }.flatMap { it.first.exts }.toSet()
        if (formats.isEmpty() && !chkAllFormats.isSelected) {
            CustomMessageBox.show(stage, "افزودن پوشه", "دست‌کم یک نوع سند را انتخاب کنید."); return
        }
        val minV = (txtMinSize.text.trim().toLongOrNull() ?: 1L) * unitFactor(cmbMinUnit.value)
        val maxV = (txtMaxSize.text.trim().toLongOrNull() ?: 200L) * unitFactor(cmbMaxUnit.value)
        if (minV < 1 || minV > maxV) {
            CustomMessageBox.show(stage, "افزودن پوشه", "محدودهٔ اندازهٔ فایل معتبر نیست؛ حداقل باید از حداکثر کوچک‌تر باشد.")
            return
        }

        running = true
        styleStart(true)
        errorCount = 0
        tblErrors.items.clear()
        progress.progress = ProgressBar.INDETERMINATE_PROGRESS
        lblProgress.text = "در حال شمارش فایل‌ها…"
        lblEta.text = ""; lblCurrent.text = ""
        progressBox.isVisible = true; progressBox.isManaged = true
        startMs = System.currentTimeMillis()

        Thread {
            service.indexDirectory(name, rootPath, formats, minV, maxV, chkSubFolders.isSelected, object : IndexingService.Callbacks {
                override fun onFileStart(file: File) { Platform.runLater { lblCurrent.text = file.absolutePath } }
                override fun onFileError(file: File, error: String) {
                    Platform.runLater {
                        errorCount++
                        tblErrors.items.add(ErrorRow(file.name, file.parent ?: "", error))
                        errorsPane.text = "فایل‌هایی که خوانده نشدند (${fa(errorCount)})"
                        errorsPane.isVisible = true; errorsPane.isManaged = true
                    }
                }
                override fun onProgress(done: Int, total: Int) {
                    Platform.runLater {
                        progress.progress = if (total == 0) 1.0 else done.toDouble() / total
                        val pct = if (total == 0) 100 else done * 100 / total
                        TrayProgress.update(progress.progress, "${fa(done)} از ${fa(total)} فایل")
                        lblProgress.text = "${fa(done)} از ${fa(total)} فایل (${fa(pct)}٪)"
                        if (done > 3 && total > done) {
                            val elapsed = (System.currentTimeMillis() - startMs) / 1000.0
                            val remain = (elapsed / done * (total - done)).toLong()
                            lblEta.text = "زمان تقریبی باقیمانده: " + formatEta(remain)
                        } else lblEta.text = ""
                    }
                }
                override fun onFinish(err: Int) {
                    Platform.runLater {
                        TrayProgress.finish(if (err == 0) "فهرست‌سازی «$name» تمام شد و آمادهٔ جست‌وجو است." else null)
                        running = false; styleStart(false)
                        when (err) {
                            0 -> {
                                val extra = if (errorCount > 0) "\n${fa(errorCount)} فایل قابل خواندن نبود و کنار گذاشته شد." else ""
                                CustomMessageBox.show(stage, "افزودن پوشه",
                                    "«$name» آمادهٔ جست‌وجو است.$extra")
                                onFinished(); stage.close()
                            }
                            2 -> {
                                progressBox.isVisible = false; progressBox.isManaged = false
                                CustomMessageBox.show(stage, "افزودن پوشه", "فهرست‌گیری متوقف شد و تغییری ذخیره نشد.")
                            }
                            else -> CustomMessageBox.show(stage, "خطا",
                                "فهرست‌گیری کامل نشد. اگر پوشه روی حافظهٔ جانبی یا شبکه است، اتصال آن را بررسی کنید و دوباره تلاش کنید.")
                        }
                    }
                }
            })
        }.apply { isDaemon = true }.start()
    }

    private fun cancel() { service.cancelled = true }

    fun show() = ParkableModal.showAndWait(stage)
    internal fun showNonBlocking(): Stage { stage.show(); return stage }
}
