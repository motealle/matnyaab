package ir.matnyaab.ui

import ir.matnyaab.core.*
import ir.matnyaab.util.PersianUtil
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tooltip
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.Window
import java.awt.Desktop
import java.io.File

/**
 * معادل ContentPacks + CustomContentWidget — لیست بسته‌های محتوایی سرور:
 * هر ردیف: تصویر جلد ۴۸×۴۸، عنوان (قرمز اگر جدید)، اندازه فارسی، دکمه دانلود و دکمه بازکردن پوشه
 */
class ContentPacksPanel(
    private val settings: AppSettingsManager,
    private val ownerWindow: () -> Window?,
    private val onDownloadProgress: (Int) -> Unit,
    private val onNewCount: (Int) -> Unit,
) : VBox(6.0) {

    private val listBox = VBox(4.0)
    private var downloader: Downloader? = null

    init {
        nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
        padding = Insets(8.0)
        style = "-fx-background-color: white; -fx-border-color: ${Constants.BORDER_COLOR}; -fx-border-radius: 6px;"
        val scroll = ScrollPane(listBox).apply {
            isFitToWidth = true
            prefHeight = 300.0
            style = "-fx-background-color: transparent;"
        }
        children.add(scroll)
    }

    /** دریافت لیست از سرور در ترد جداگانه */
    fun refresh() {
        Thread {
            try {
                val items = ContentService.fetchContents()
                val lastVisit = settings.get("LAST_VISIT")
                val newCount = items.count { it.dateAdded > lastVisit }
                Platform.runLater {
                    onNewCount(newCount)
                    listBox.children.clear()
                    for (c in items) listBox.children.add(row(c, c.dateAdded > lastVisit))
                }
            } catch (e: Exception) { /* عدم دسترسی به سرور */ }
        }.apply { isDaemon = true }.start()
    }

    /** ثبت زمان بازدید (معادل LAST_VISIT جلالی) */
    fun markVisited() {
        settings.setValue("LAST_VISIT", PersianUtil.nowJalaliWithTime())
        onNewCount(0)
    }

    private fun row(c: ContentService.ContentInfo, isNew: Boolean): HBox {
        val cover = ImageView().apply { fitWidth = 48.0; fitHeight = 48.0; isPreserveRatio = true }
        Thread {
            val f = Constants.CONTENTS_DIR.resolve("cover_${c.id}.img").toFile()
            if (!f.exists()) ContentService.downloadCover(c.id, f)
            if (f.exists()) Platform.runLater { try { cover.image = Image(f.toURI().toString(), 48.0, 48.0, true, true) } catch (e: Exception) {} }
        }.apply { isDaemon = true }.start()

        val lblTitle = Label(c.title).apply {
            style = "${Theme.FONT} -fx-font-size: ${Constants.FONT_SIZE_NORMAL}px;" +
                if (isNew) " -fx-text-fill: red;" else ""
            tooltip = Tooltip(c.title)
            maxWidth = 170.0
        }
        val lblSize = Label(PersianUtil.toPersianNumbers(PersianUtil.humanFileSize(c.filesize))).apply {
            style = "${Theme.FONT} -fx-font-size: ${Constants.FONT_SIZE_SMALL}px; -fx-text-fill: #555;"
        }

        val savedPath = settings.get("contents/${c.id}")
        val btnDownload = Button(Theme.GLYPH_DOWNLOAD).apply {
            style = Theme.awesome(13) + Theme.primaryButton()
            tooltip = Tooltip("دانلود بسته")
            setOnAction { download(c) }
        }
        val btnExplore = Button(Theme.GLYPH_FOLDER_OPEN).apply {
            style = Theme.awesome(13) + Theme.primaryButton()
            tooltip = Tooltip("نمایش فایل دانلود شده")
            isDisable = savedPath.isBlank() || !File(savedPath).exists()
            setOnAction { explore(savedPath) }
        }

        val spacer = Region().also { HBox.setHgrow(it, Priority.ALWAYS) }
        return HBox(8.0, cover, VBox(2.0, lblTitle, lblSize), spacer, btnDownload, btnExplore).apply {
            alignment = Pos.CENTER_RIGHT
            padding = Insets(4.0)
            style = "-fx-border-color: ${Constants.BORDER_COLOR}; -fx-border-width: 0 0 1 0;"
        }
    }

    private fun download(c: ContentService.ContentInfo) {
        val chooser = DirectoryChooser().apply { title = "انتخاب پوشه ذخیره" }
        val dir = chooser.showDialog(ownerWindow()) ?: return
        val outFile = File(dir, c.packageFile.substringAfterLast('/'))
        downloader?.stop()
        downloader = Downloader(
            Constants.DOWNLOAD_CONTENT_URL + c.id, outFile,
            onProgress = { p -> Platform.runLater { onDownloadProgress(p) } },
            onFinish = { code ->
                Platform.runLater {
                    onDownloadProgress(-1)
                    when (code) {
                        0 -> {
                            settings.setValue("contents/${c.id}", outFile.absolutePath)
                            CustomMessageBox.show(ownerWindow(), "دانلود", "دانلود بسته «${c.title}» با موفقیت انجام شد.")
                            refresh()
                        }
                        1 -> CustomMessageBox.show(ownerWindow(), "خطا", "خطا در دانلود بسته!")
                    }
                }
            },
        ).also { it.start() }
    }

    fun cancelDownload() { downloader?.stop() }

    private fun explore(path: String) {
        try {
            val f = File(path)
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("win")) Runtime.getRuntime().exec(arrayOf("explorer", "/select,", f.absolutePath))
            else Desktop.getDesktop().open(f.parentFile)
        } catch (e: Exception) { /* silent */ }
    }
}
