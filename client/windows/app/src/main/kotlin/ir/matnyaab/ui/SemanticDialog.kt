package ir.matnyaab.ui

import ir.matnyaab.core.AppSettingsManager
import ir.matnyaab.core.IndexerInfo
import ir.matnyaab.core.semantic.SemanticPack
import ir.matnyaab.core.semantic.SemanticService
import ir.matnyaab.util.PersianUtil
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * «جست‌وجوی معنایی (اختیاری)»: توضیح، پیشنهادهای سخت‌افزاری، دانلود بسته با پیشرفت،
 * وضعیت آماده‌سازی و حذف. نسخهٔ پایه بدون این بسته کامل و سبک است.
 */
class SemanticDialog(owner: Stage, private val settings: AppSettingsManager, private val cols: () -> List<IndexerInfo>, private val onChange: () -> Unit) {
    private val stage = Stage().apply { initOwner(owner); initModality(Modality.WINDOW_MODAL); title = "جست‌وجوی معنایی (اختیاری)" }
    private val status = Label().apply { isWrapText = true }
    private val bar = ProgressBar(0.0).apply { maxWidth = Double.MAX_VALUE; isVisible = false; isManaged = false }
    private val enable = CheckBox("فعال‌سازی جست‌وجوی معنایی")
    private val pause = Button()
    private val remove = Button("حذف بسته و بردارها")
    private val cancelled = AtomicBoolean(false)
    private var downloading = false

    private fun lbl(t: String, bold: Boolean = false, muted: Boolean = false) = Label(t).apply {
        isWrapText = true
        style = Theme.FONT + (if (bold) " -fx-font-weight: bold; -fx-font-size: 15px;" else "") + (if (muted) " -fx-opacity: 0.8;" else "")
    }

    fun show() {
        val req = VBox(4.0, *SemanticPack.REQUIREMENTS.map { lbl("•  $it", muted = true) }.toTypedArray())
        val body = VBox(12.0,
            lbl("جست‌وجو بر پایهٔ مفهوم، نه فقط واژه", bold = true),
            lbl("با این قابلیت، جست‌وجوی «خودسازی» متن‌هایی را هم پیدا می‌کند که از «تهذیب نفس»، «مراقبت از نفس» یا «غلبه بر تمایلات نفسانی» سخن گفته‌اند، حتی اگر واژهٔ «خودسازی» در آن‌ها نیامده باشد. نتیجه‌های مفهومی کنار نتیجه‌های دقیق می‌آیند، با نشان «مفهومی» و بخشِ مرتبطِ برجسته."),
            lbl("پیش از فعال‌سازی بدانید:", bold = false),
            req,
            enable, bar, status,
            HBox(8.0, pause, remove).apply { alignment = Pos.CENTER_RIGHT },
            lbl("نکته: کیفیت درک مفهومی مدل‌ها روی همهٔ متن‌ها یکسان نیست؛ نتیجه‌های مفهومی را مکمل جست‌وجوی دقیق بدانید.", muted = true),
        ).apply { padding = Insets(18.0); nodeOrientation = NodeOrientation.RIGHT_TO_LEFT }
        enable.style = Theme.FONT + " -fx-font-weight: bold;"
        listOf(pause, remove, status).forEach { it.style = Theme.FONT }
        enable.isSelected = SemanticPack.enabled(settings) && SemanticPack.installed()
        enable.setOnAction { if (enable.isSelected) turnOn() else turnOff() }
        pause.setOnAction {
            if (SemanticService.status.startsWith("آماده‌سازی")) SemanticService.stop()
            else SemanticService.start(settings, cols)
            refresh()
        }
        remove.setOnAction {
            val ok = Alert(Alert.AlertType.CONFIRMATION, "بستهٔ معنایی و بردارهای ساخته‌شده حذف شوند؟ فهرست‌های اصلی و جست‌وجوی عادی دست نمی‌خورند.", ButtonType.YES, ButtonType.NO)
                .apply { initOwner(stage); headerText = null }.showAndWait().orElse(ButtonType.NO) == ButtonType.YES
            if (ok) Thread {
                SemanticService.shutdown()
                ir.matnyaab.core.semantic.SemanticIndex.deleteAll(cols())
                SemanticPack.uninstall()
                settings.setValue("FF_SEMANTIC", "0")
                Platform.runLater { enable.isSelected = false; refresh(); onChange() }
            }.start()
        }
        val prev = SemanticService.listener
        SemanticService.listener = { prev?.invoke(); Platform.runLater { refresh() } }
        stage.setOnHidden { SemanticService.listener = prev; cancelled.set(true) }
        refresh()
        stage.scene = Scene(ScrollPane(body).apply { isFitToWidth = true }, 560.0, 620.0)
        Theme.install(stage.scene, stage)
        stage.showAndWait()
    }

    private fun refresh() {
        val installed = SemanticPack.installed()
        val on = SemanticPack.enabled(settings) && installed
        pause.isVisible = on; pause.isManaged = on
        remove.isVisible = installed; remove.isManaged = installed
        pause.text = if (SemanticService.status.startsWith("آماده‌سازی")) "توقف آماده‌سازی" else "ادامه / به‌روزرسانی آماده‌سازی"
        if (downloading) return
        bar.isVisible = on && SemanticService.total > 0 && SemanticService.percent() < 100; bar.isManaged = bar.isVisible
        bar.progress = SemanticService.percent() / 100.0
        status.text = when {
            !installed -> "بسته نصب نیست. با زدن تیک بالا، دانلود و نصب آغاز می‌شود."
            !on -> "بسته نصب است (نسخهٔ ${SemanticPack.installedVersion()}) ولی خاموش است."
            SemanticService.status.startsWith("خطا") -> SemanticService.status
            SemanticService.total > 0 -> "${SemanticService.status}: ${PersianUtil.toPersianNumbers(SemanticService.done)} از ${PersianUtil.toPersianNumbers(SemanticService.total)} سند (${PersianUtil.toPersianNumbers(SemanticService.percent())}٪)" +
                if (SemanticService.percent() in 1..99) " — جست‌وجوی مفهومی از همین حالا روی اسناد آماده کار می‌کند." else ""
            else -> SemanticService.status.ifBlank { "آماده" }
        }
    }

    private fun turnOn() {
        if (SemanticPack.installed()) { settings.setValue("FF_SEMANTIC", "1"); SemanticService.start(settings, cols); onChange(); refresh(); return }
        downloading = true; cancelled.set(false)
        enable.isDisable = true; bar.isVisible = true; bar.isManaged = true; bar.progress = -1.0
        status.text = "در حال یافتن بسته…"
        Thread {
            try {
                val rel = SemanticPack.latest() ?: throw IllegalStateException("بسته در دسترس نیست؛ اتصال اینترنت را بررسی کنید.")
                SemanticPack.download(rel, { done, total ->
                    Platform.runLater {
                        bar.progress = if (total > 0) done.toDouble() / total else -1.0
                        status.text = "دریافت بسته: ${PersianUtil.toPersianNumbers(done / 1_048_576)} از ${PersianUtil.toPersianNumbers(total / 1_048_576)} مگابایت"
                    }
                }, { cancelled.get() })
                settings.setValue("FF_SEMANTIC", "1")
                Platform.runLater { downloading = false; enable.isDisable = false; SemanticService.start(settings, cols); onChange(); refresh() }
            } catch (e: Exception) {
                Platform.runLater {
                    downloading = false; enable.isDisable = false; enable.isSelected = false
                    bar.isVisible = false; bar.isManaged = false
                    status.text = "نصب انجام نشد: ${e.message}  — دوباره تلاش کنید؛ برنامه بدون این بسته کامل کار می‌کند."
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun turnOff() {
        settings.setValue("FF_SEMANTIC", "0")
        Thread { SemanticService.shutdown(); Platform.runLater { refresh(); onChange() } }.start()
    }
}
