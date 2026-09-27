package ir.matnyaab.ui

import ir.matnyaab.core.*
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
 * معادل scanDirectoryDialog — دیالوگ «فهرست‌گیری»:
 * نام دسته، حداقل/حداکثر اندازه (بایت/کیلوبایت/مگابایت)، لیست فرمت‌ها با منوی راست‌کلیک،
 * چک‌باکس «همه فایل‌ها و فرمت‌ها»، زیرپوشه‌ها، لاگ فایل‌ها و جدول خطاها، دکمه سبز شروع/قرمز لغو
 */
class ScanDirectoryDialog(
    owner: Window?,
    private val rootPath: String,
    private val indexer: LuceneIndexer,
    private val license: LicenseChecker,
    private val onFinished: () -> Unit,
) {
    private val stage = Stage(StageStyle.UNDECORATED)
    private val txtCatName = TextField(File(rootPath).name).apply { style = Theme.FONT }
    private val txtMinSize = TextField("1")
    private val txtMaxSize = TextField("100")
    private val units = listOf("بایت", "کیلوبایت", "مگابایت")
    private val cmbMinUnit = ComboBox(FXCollections.observableArrayList(units)).apply { selectionModel.select(0) }
    private val cmbMaxUnit = ComboBox(FXCollections.observableArrayList(units)).apply { selectionModel.select(2) }
    private val lstFormats = ListView<String>().apply {
        items.addAll(IndexingService.ALL_FILE_FORMATS)
        selectionModel.selectionMode = SelectionMode.MULTIPLE
        IndexingService.IMPORTANT_FORMATS.forEach { selectionModel.select(items.indexOf(it)) }
        prefHeight = 150.0
    }
    private val chkAllFormats = CheckBox("همه فایل ها و فرمت ها")
    private val chkSubFolders = CheckBox("زیر پوشه ها هم فهرست شوند").apply { isSelected = true }
    private val lstLog = ListView<String>().apply { prefHeight = 110.0 }
    private val tblErrors = TableView<ErrorRow>().apply {
        val c1 = TableColumn<ErrorRow, String>("فایل").apply { cellValueFactory = PropertyValueFactory("file"); prefWidth = 170.0 }
        val c2 = TableColumn<ErrorRow, String>("مسیر").apply { cellValueFactory = PropertyValueFactory("path"); prefWidth = 230.0 }
        val c3 = TableColumn<ErrorRow, String>("خطا").apply { cellValueFactory = PropertyValueFactory("error"); prefWidth = 150.0 }
        columns.addAll(c1, c2, c3)
        prefHeight = 110.0
    }
    private val progress = ProgressBar(0.0).apply { maxWidth = Double.MAX_VALUE }
    private val lblProgress = Label("").apply { style = Theme.FONT }
    private val btnStart = Button("شروع فهرست گیری")
    private val service = IndexingService(indexer, license)
    private var running = false

    // یادداشت: نیازی به گترهای دستی نیست — کاتلین برای propertyهای public
    // به‌طور خودکار گترهای هم‌نام جاوابین (getFile/getPath/getError) می‌سازد
    // که PropertyValueFactory از طریق آن‌ها خوانده می‌شود.
    class ErrorRow(val file: String, val path: String, val error: String)

    init {
        stage.initModality(Modality.APPLICATION_MODAL)
        owner?.let { stage.initOwner(it) }

        chkAllFormats.setOnAction { lstFormats.isDisable = chkAllFormats.isSelected }
        val menu = ContextMenu(
            MenuItem("پاک کردن انتخاب").apply { setOnAction { lstFormats.selectionModel.clearSelection() } },
            MenuItem("انتخاب همه").apply { setOnAction { lstFormats.selectionModel.selectAll() } },
            MenuItem("انتخاب متداول").apply {
                setOnAction {
                    lstFormats.selectionModel.clearSelection()
                    IndexingService.IMPORTANT_FORMATS.forEach { f -> lstFormats.selectionModel.select(lstFormats.items.indexOf(f)) }
                }
            },
        )
        lstFormats.contextMenu = menu

        styleStart(false)
        btnStart.setOnAction { if (running) cancel() else start() }

        val grid = GridPane().apply {
            hgap = 8.0; vgap = 8.0
            add(Label("نام دسته:").styled(), 0, 0); add(txtCatName, 1, 0, 3, 1)
            add(Label("حداقل اندازه فایل:").styled(), 0, 1); add(txtMinSize, 1, 1); add(cmbMinUnit, 2, 1)
            add(Label("حداکثر اندازه فایل:").styled(), 0, 2); add(txtMaxSize, 1, 2); add(cmbMaxUnit, 2, 2)
        }

        val content = VBox(8.0,
            Label("پوشه: $rootPath").styled(),
            grid,
            Label("فرمت فایل ها:").styled(), lstFormats, chkAllFormats, chkSubFolders,
            Label("فایل ها:").styled(), lstLog,
            Label("خطاها:").styled(), tblErrors,
            lblProgress, progress, btnStart,
        ).apply { padding = Insets(14.0); style = "-fx-background-color: white;" }

        val root = BorderPane().apply {
            nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
            top = CustomTitleBar(stage, "فهرست گیری", showMaximize = false) { if (!running) stage.close() }
            center = ScrollPane(content).apply { isFitToWidth = true }
            style = "-fx-border-color: ${Constants.BORDER_COLOR};"
        }
        stage.scene = Scene(root, Constants.FEHRESTGIRI_WINDOW_WIDTH, Constants.FEHRESTGIRI_WINDOW_HEIGHT)
    }

    private fun Label.styled(): Label { style = "${Theme.FONT} -fx-font-size: ${Constants.FONT_SIZE_NORMAL}px;"; return this }

    private fun styleStart(cancelMode: Boolean) {
        btnStart.text = if (cancelMode) "لغو فهرست گیری" else "شروع فهرست گیری"
        btnStart.style = "${Theme.FONT} -fx-text-fill: white; -fx-background-radius: 4px; -fx-cursor: hand;" +
            if (cancelMode) "-fx-background-color: #FE0001;" else "-fx-background-color: #156608;"
        btnStart.prefHeight = Constants.OPTION_BUTTONS_HEIGHT
        btnStart.maxWidth = Double.MAX_VALUE
    }

    private fun unitFactor(u: String): Long = when (u) {
        "کیلوبایت" -> 1024L
        "مگابایت" -> 1024L * 1024L
        else -> 1L
    }

    /** معادل validate + startOperation */
    private fun start() {
        val name = txtCatName.text.trim()
        if (name.isBlank()) { CustomMessageBox.show(stage, "خطا", "نام دسته را وارد کنید."); return }
        val formats: Set<String> = if (chkAllFormats.isSelected) emptySet()
            else lstFormats.selectionModel.selectedItems.toSet()
        if (formats.isEmpty() && !chkAllFormats.isSelected) {
            CustomMessageBox.show(stage, "خطا", "حداقل یک فرمت را انتخاب کنید."); return
        }
        val minV = (txtMinSize.text.toLongOrNull() ?: 0) * unitFactor(cmbMinUnit.value)
        val maxV = (txtMaxSize.text.toLongOrNull() ?: 0) * unitFactor(cmbMaxUnit.value)
        if (minV < 1) { CustomMessageBox.show(stage, "خطا", "حداقل اندازه باید حداقل یک بایت باشد."); return }
        if (minV > maxV) { CustomMessageBox.show(stage, "خطا", "حداقل اندازه نباید از حداکثر بیشتر باشد."); return }

        // بررسی محدودیت نسخه آزمایشی پیش از شروع
        if (!license.isSubscribed() && indexer.usedSpace() >= Constants.LIMITED_TRIAL_SIZE) {
            CustomMessageBox.show(stage, Constants.APP_NAME_PERSIAN, "اشتراک به اتمام رسیده است!")
            return
        }

        running = true
        styleStart(true)
        lstLog.items.clear(); tblErrors.items.clear()

        Thread {
            service.indexDirectory(name, rootPath, formats, minV, maxV, chkSubFolders.isSelected, object : IndexingService.Callbacks {
                override fun onFileStart(file: File) { Platform.runLater { lstLog.items.add(0, file.absolutePath) } }
                override fun onFileError(file: File, error: String) {
                    Platform.runLater { tblErrors.items.add(ErrorRow(file.name, file.parent ?: "", error)) }
                }
                override fun onProgress(done: Int, total: Int) {
                    Platform.runLater {
                        progress.progress = if (total == 0) 1.0 else done.toDouble() / total
                        lblProgress.text = "فهرست‌شده: " + ir.matnyaab.util.PersianUtil.toPersianNumbers(done) +
                            " از " + ir.matnyaab.util.PersianUtil.toPersianNumbers(total) +
                            " فایل — باقیمانده: " + ir.matnyaab.util.PersianUtil.toPersianNumbers(total - done)
                    }
                }
                override fun onLog(message: String) { Platform.runLater { lstLog.items.add(0, message) } }
                override fun onFinish(err: Int) {
                    Platform.runLater {
                        running = false; styleStart(false)
                        when (err) {
                            0 -> { CustomMessageBox.show(stage, "فهرست گیری", "فهرست گیری با موفقیت به پایان رسید."); onFinished(); stage.close() }
                            1 -> { CustomMessageBox.show(stage, Constants.APP_NAME_PERSIAN, "اشتراک به اتمام رسیده است!"); onFinished(); stage.close() }
                            2 -> CustomMessageBox.show(stage, "فهرست گیری", "فهرست گیری لغو شد.")
                            else -> CustomMessageBox.show(stage, "خطا", "خطا در فهرست گیری!")
                        }
                    }
                }
            })
        }.apply { isDaemon = true }.start()
    }

    private fun cancel() { service.cancelled = true }

    fun show() = stage.showAndWait()
}
