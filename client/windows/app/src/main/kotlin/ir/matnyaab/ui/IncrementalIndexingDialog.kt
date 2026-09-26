package ir.matnyaab.ui

import ir.matnyaab.core.*
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.layout.BorderPane
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.stage.Window
import java.io.File

/**
 * معادل IncrementalIndexingDlg — به‌روزرسانی فهرست‌ها:
 * جدول لاگ با ستون‌های فایل/مسیر/عمل + دکمه لغو
 */
class IncrementalIndexingDialog(
    owner: Window?,
    private val targets: List<IndexerInfo>,
    private val indexer: LuceneIndexer,
    private val license: LicenseChecker,
    private val onFinished: () -> Unit,
) {
    private val stage = Stage(StageStyle.UNDECORATED)
    private val tblLog = TableView<LogRow>()
    private val btnCancel = Button("لغو")
    private val service = IndexingService(indexer, license)

    // یادداشت: نیازی به گترهای دستی نیست — کاتلین برای propertyهای public
    // به‌طور خودکار گترهای هم‌نام جاوابین (getFile/getPath/getAction) می‌سازد
    // که PropertyValueFactory از طریق آن‌ها خوانده می‌شود.
    class LogRow(val file: String, val path: String, val action: String)

    init {
        stage.initModality(Modality.APPLICATION_MODAL)
        owner?.let { stage.initOwner(it) }

        val c1 = TableColumn<LogRow, String>("فایل").apply { cellValueFactory = PropertyValueFactory("file"); prefWidth = 150.0 }
        val c2 = TableColumn<LogRow, String>("مسیر").apply { cellValueFactory = PropertyValueFactory("path"); prefWidth = 260.0 }
        val c3 = TableColumn<LogRow, String>("عمل").apply { cellValueFactory = PropertyValueFactory("action"); prefWidth = 140.0 }
        tblLog.columns.addAll(c1, c2, c3)
        tblLog.prefHeight = 320.0

        btnCancel.style = "${Theme.FONT} -fx-background-color: #c42b1c; -fx-text-fill: white; -fx-background-radius: 4px;"
        btnCancel.prefHeight = Constants.OPTION_BUTTONS_HEIGHT
        btnCancel.maxWidth = Double.MAX_VALUE
        btnCancel.setOnAction { service.cancelled = true }

        val content = VBox(10.0, tblLog, btnCancel).apply {
            padding = Insets(14.0); style = "-fx-background-color: white;"
        }
        val root = BorderPane().apply {
            nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
            top = CustomTitleBar(stage, "به روز رسانی فهرست ها", showMaximize = false)
            center = content
            style = "-fx-border-color: ${Constants.BORDER_COLOR};"
        }
        stage.scene = Scene(root, 620.0, 440.0)
    }

    fun show() {
        Thread {
            var idx = 0
            fun runNext() {
                if (idx >= targets.size) {
                    Platform.runLater { onFinished(); stage.close() }
                    return
                }
                val info = targets[idx]; idx++
                service.incrementalUpdate(info, object : IndexingService.Callbacks {
                    override fun onLog(message: String) {
                        Platform.runLater {
                            val parts = message.split(": ", limit = 2)
                            if (parts.size == 2) tblLog.items.add(0, LogRow(File(parts[1]).name, parts[1], parts[0]))
                            else tblLog.items.add(0, LogRow("", info.rootPath, message))
                        }
                    }
                    override fun onFinish(err: Int) {
                        if (err == 1) Platform.runLater {
                            CustomMessageBox.show(stage, Constants.APP_NAME_PERSIAN, "اشتراک به اتمام رسیده است!")
                        }
                        if (err == 2) { Platform.runLater { onFinished(); stage.close() }; return }
                        runNext()
                    }
                })
            }
            runNext()
        }.apply { isDaemon = true }.start()
        stage.showAndWait()
    }
}
