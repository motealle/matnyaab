package ir.matnyaab.ui

import ir.matnyaab.core.AppSettingsManager
import ir.matnyaab.core.research.DocxWriter
import ir.matnyaab.core.whisper.Whisper
import ir.matnyaab.util.PersianUtil
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Stage
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * «گفتار به متن» با بستهٔ اختیاری Whisper: آشنایی (تور سه‌گامی) ← نصب بسته و مدل ← رونویسی صف فایل‌ها ← افزودن به کتابخانه.
 * همه‌چیز آفلاین؛ هشدارهای زمان و کیفیت پیش از شروع گفته می‌شود.
 */
class WhisperDialog private constructor(private val owner: Stage, private val settings: AppSettingsManager, private val addFolder: (File) -> Unit) {
    companion object {
        private var open: WhisperDialog? = null
        fun show(owner: Stage, settings: AppSettingsManager, addFolder: (File) -> Unit) {
            val w = open ?: WhisperDialog(owner, settings, addFolder).also { open = it }
            w.stage.show(); w.stage.toFront()
        }
        private fun fa(n: Long) = PersianUtil.toPersianNumbers(n)
        private fun mb(b: Long) = fa(b / 1_048_576) + " مگابایت"
    }

    val stage = Stage().apply { initOwner(owner); title = "گفتار به متن (Whisper)"; width = 860.0; height = 700.0 }
    private val body = StackPane()
    private val stop = AtomicBoolean(false)
    private var release: Whisper.Release? = null

    init {
        val root = BorderPane(body).apply { nodeOrientation = NodeOrientation.RIGHT_TO_LEFT; style = Theme.FONT }
        stage.scene = Scene(root)
        Theme.install(stage.scene, stage)
        stage.setOnHidden { stop.set(true); open = null }
        if (settings.get("WHISPER_TOUR") != "1") tour(0) else main()
    }

    private fun lbl(t: String, cls: String? = null) = Label(t).apply { isWrapText = true; cls?.let { styleClass.add(it) } }
    private fun card(vararg n: Node) = VBox(8.0, *n).apply { styleClass.add("card") }
    private fun primary(t: String, a: () -> Unit) = Button(t).apply { style = Theme.primaryButton(); setOnAction { a() } }
    private fun secondary(t: String, a: () -> Unit) = Button(t).apply { style = Theme.addButton(); setOnAction { a() } }
    private fun page(vararg n: Node) = ScrollPane(VBox(12.0, *n).apply { padding = Insets(16.0) }).apply { isFitToWidth = true }

    // ---------------------------------------------------------------- تور آشنایی

    private fun tour(i: Int) {
        val pages = listOf(
            Triple("🎙", "سخنرانی‌ها و درس‌ها را قابل جست‌وجو کنید",
                "فایل‌های صوتی و تصویری (MP3، WAV، M4A، MP4 و…) به متن فارسی با زمان‌بندی تبدیل می‌شوند. متن‌ها مثل بقیهٔ اسناد در متن‌یاب جست‌وجو، فیش‌برداری و نقل می‌شوند و زمان هر جمله کنارش می‌آید تا به صدای اصلی برگردید."),
            Triple("⚠", "پیش از شروع بدانید",
                Whisper.REQUIREMENTS.joinToString("\n") { "• $it" }),
            Triple("✓", "سه گام ساده",
                "۱. بسته و یک مدل را یک‌بار دریافت کنید (اگر اینترنت قطع شد، از همان‌جا ادامه می‌یابد).\n۲. فایل‌ها یا پوشهٔ صوتی را بیفزایید و «شروع» را بزنید؛ می‌توانید برنامه را کنار ساعت ببرید.\n۳. «افزودن رونویسی‌ها به کتابخانه» را بزنید تا جست‌وجوپذیر شوند."),
        )
        val (icon, t, d) = pages[i]
        val dots = HBox(6.0, *pages.indices.map { k -> Label(if (k == i) "●" else "○") }.toTypedArray()).apply { alignment = Pos.CENTER }
        val next = if (i < pages.lastIndex) primary("بعدی") { tour(i + 1) } else primary("شروع کنیم") { settings.setValue("WHISPER_TOUR", "1"); main() }
        val skip = secondary("رد کردن") { settings.setValue("WHISPER_TOUR", "1"); main() }
        body.children.setAll(VBox(18.0, Label(icon).apply { style = "-fx-font-size: 46px;" }, lbl(t, "section-title").apply { style = "-fx-font-size: 20px;" },
            lbl(d).apply { maxWidth = 620.0; style = "-fx-font-size: 14px; -fx-line-spacing: 4;" }, dots, HBox(10.0, next, skip).apply { alignment = Pos.CENTER })
            .apply { alignment = Pos.CENTER; padding = Insets(30.0) })
    }

    // ---------------------------------------------------------------- صفحهٔ اصلی

    private val status = lbl("", "muted")
    private val bar = ProgressBar(0.0).apply { maxWidth = Double.MAX_VALUE; isVisible = false }
    private val modelBox = ComboBox<File>().apply { prefWidth = 320.0 }
    private val files = ListView<File>()
    private val lang = ComboBox<String>().apply { items.setAll("فارسی", "عربی", "انگلیسی", "تشخیص خودکار"); selectionModel.select(0) }
    private val outDir = TextField(File(ResearchWindow.docsDir(), "رونویسی").absolutePath)
    private val docx = CheckBox("نسخهٔ Word هم ساخته شود").apply { isSelected = true }
    private val log = TextArea().apply { isEditable = false; isWrapText = true; prefRowCount = 6; styleClass.add("log-area") }

    private fun main() {
        modelBox.setCellFactory { object : ListCell<File>() { override fun updateItem(f: File?, e: Boolean) { super.updateItem(f, e); text = f?.let { label(it) } } } }
        modelBox.buttonCell = modelBox.cellFactory.call(null)
        files.setCellFactory { object : ListCell<File>() { override fun updateItem(f: File?, e: Boolean) { super.updateItem(f, e); text = f?.let { it.name + "   ·   " + mb(it.length()) } } } }
        files.placeholder = lbl("فایل‌های صوتی یا تصویری را این‌جا بکشید یا با دکمه‌ها بیفزایید.", "muted")
        files.setOnDragOver { e -> if (e.dragboard.hasFiles()) e.acceptTransferModes(*javafx.scene.input.TransferMode.COPY_OR_MOVE) }
        files.setOnDragDropped { e -> addFiles(e.dragboard.files); e.isDropCompleted = true }
        refresh()
    }

    private fun label(f: File) = when {
        f.name.contains("turbo") || f.name.contains("large") -> "دقیق (پیشنهادی برای فارسی) — "
        f.name.contains("small") -> "متعادل — "
        f.name.contains("base") || f.name.contains("tiny") -> "سریع — "
        else -> ""
    } + f.name + " (" + mb(f.length()) + ")"

    private fun refresh() {
        val installed = Whisper.installed()
        val models = Whisper.models()
        modelBox.items.setAll(models); if (modelBox.value !in models) modelBox.value = models.lastOrNull()
        val setup = card(lbl("۱. آماده‌سازی (یک‌بار)", "card-title"),
            lbl(if (installed) "✓ بستهٔ گفتار به متن نصب است." else "بستهٔ گفتار به متن هنوز نصب نیست.", if (installed) null else "muted"),
            HBox(8.0, *listOfNotNull(
                if (!installed) primary("دریافت و نصب بسته") { installPack() } else null,
                secondary("دریافت مدل…") { pickModel() },
                secondary("افزودن مدل از رایانه…") { importModel() },
                if (installed) secondary("حذف بسته و مدل‌ها") { if (confirm("بسته و همهٔ مدل‌ها حذف شوند؟")) { Whisper.uninstall(); refresh() } } else null,
            ).toTypedArray()).apply { alignment = Pos.CENTER_RIGHT },
            HBox(8.0, lbl("مدل:"), modelBox, Hyperlink("پیش‌نیازها").apply { setOnAction { tour(1) } }).apply { alignment = Pos.CENTER_RIGHT })
        val queue = card(lbl("۲. فایل‌ها", "card-title"),
            HBox(8.0, secondary("افزودن فایل…") {
                FileChooser().apply { extensionFilters.add(FileChooser.ExtensionFilter("صدا و فیلم", Whisper.AUDIO.map { "*.$it" })) }.showOpenMultipleDialog(stage)?.let { addFiles(it) }
            }, secondary("افزودن پوشه…") {
                DirectoryChooser().showDialog(stage)?.let { d -> addFiles(d.walkTopDown().filter { it.isFile && it.extension.lowercase() in Whisper.AUDIO }.toList()) }
            }, secondary("پاک کردن فهرست") { files.items.clear() }).apply { alignment = Pos.CENTER_RIGHT },
            files.apply { prefHeight = 150.0 },
            HBox(8.0, lbl("زبان:"), lang, docx).apply { alignment = Pos.CENTER_RIGHT },
            HBox(8.0, lbl("ذخیره در:"), outDir.also { HBox.setHgrow(it, Priority.ALWAYS) }, secondary("انتخاب…") { DirectoryChooser().showDialog(stage)?.let { outDir.text = it.absolutePath } }).apply { alignment = Pos.CENTER_RIGHT })
        val runCard = card(lbl("۳. رونویسی", "card-title"),
            HBox(8.0, primary("شروع رونویسی") { start() }.apply { isDisable = !installed },
                secondary("توقف") { stop.set(true) },
                secondary("⤓ کنار ساعت") { TrayProgress.park(listOf(owner, stage), "رونویسی صدا") }.apply { isVisible = TrayProgress.supported; isManaged = isVisible },
                secondary("افزودن رونویسی‌ها به کتابخانه") { addFolder(File(outDir.text).also { it.mkdirs() }) }).apply { alignment = Pos.CENTER_RIGHT },
            bar, status, log)
        body.children.setAll(page(setup, queue, runCard))
    }

    private fun addFiles(list: List<File>) {
        val ok = list.filter { it.isFile && it.extension.lowercase() in Whisper.AUDIO && it !in files.items }
        files.items.addAll(ok)
        if (ok.size < list.size) status.text = "${fa((list.size - ok.size).toLong())} فایل که صوتی/تصویری نبود کنار گذاشته شد."
    }

    private fun confirm(msg: String) = Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.YES, ButtonType.NO).apply { initOwner(stage); headerText = null; dialogPane.nodeOrientation = NodeOrientation.RIGHT_TO_LEFT }
        .showAndWait().orElse(ButtonType.NO) == ButtonType.YES

    private fun bg(work: () -> Unit) = Thread { work() }.apply { isDaemon = true }.start()

    private fun withRelease(then: (Whisper.Release) -> Unit) {
        release?.let { then(it); return }
        status.text = "در حال گرفتن مشخصات بسته از سایت…"
        bg { val r = Whisper.latest(); Platform.runLater { if (r == null) status.text = "مشخصات بسته دریافت نشد؛ اتصال اینترنت را بررسی کنید." else { release = r; status.text = ""; then(r) } } }
    }

    private fun downloading(title: String, work: ((Long, Long) -> Unit) -> Unit) {
        stop.set(false); bar.isVisible = true; bar.progress = 0.0; status.text = title
        bg {
            val r = runCatching { work { d, t -> Platform.runLater { bar.progress = if (t > 0) d.toDouble() / t else -1.0; status.text = "$title  ${mb(d)} از ${mb(t)}" }; TrayProgress.update(if (t > 0) d.toDouble() / t else -1.0, title) } }
            Platform.runLater {
                TrayProgress.finish(r.getOrNull()?.let { "$title انجام شد." }); bar.isVisible = false
                status.text = r.fold({ "✓ انجام شد." }, { "✗ ${it.message}" }); refresh()
            }
        }
    }

    private fun installPack() = withRelease { rel ->
        if (!confirm("بستهٔ گفتار به متن (${mb(rel.size)}) دریافت و نصب شود؟")) return@withRelease
        downloading("دریافت بستهٔ گفتار به متن") { p ->
            val zip = File(Whisper.dir, "pack.zip")
            Whisper.download(rel.url, rel.sha256, rel.size, zip, p) { stop.get() }
            Whisper.installPack(zip); zip.delete()
        }
    }

    private fun pickModel() = withRelease { rel ->
        if (rel.models.isEmpty()) { status.text = "مدلی روی سایت معرفی نشده است."; return@withRelease }
        val names = rel.models.map { "${it.title} — ${mb(it.size)}" + if (it.desc.isNotBlank()) " — ${it.desc}" else "" }
        val pick = ChoiceDialog(names.last(), names).apply {
            initOwner(stage); title = "دریافت مدل"; headerText = "مدل «دقیق» برای فارسی بهتر است ولی بزرگ‌تر و کندتر است."; contentText = "مدل:"
            dialogPane.nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
        }.showAndWait().orElse(null) ?: return@withRelease
        val m = rel.models[names.indexOf(pick)]
        downloading("دریافت مدل «${m.title}»") { p -> Whisper.download(m.url, m.sha256, m.size, File(Whisper.modelsDir, m.file), p) { stop.get() } }
    }

    private fun importModel() {
        val f = FileChooser().apply { extensionFilters.add(FileChooser.ExtensionFilter("مدل whisper.cpp (ggml)", "*.bin")) }.showOpenDialog(stage) ?: return
        if (f.length() < 10_000_000) { status.text = "این فایل مدل whisper نیست."; return }
        downloading("کپی مدل") { p -> f.inputStream().use { i -> File(Whisper.modelsDir, f.name.replace(Regex("[^A-Za-z0-9._-]"), "_")).outputStream().use { o ->
            val b = ByteArray(1 shl 16); var d = 0L; while (true) { val n = i.read(b); if (n < 0) break; o.write(b, 0, n); d += n; p(d, f.length()) } } } }
    }

    private fun start() {
        val model = modelBox.value ?: run { status.text = "ابتدا یک مدل دریافت کنید."; return }
        val queue = files.items.toList().ifEmpty { status.text = "فایلی برای رونویسی نیفزوده‌اید."; return }
        val out = File(outDir.text.trim()).also { it.mkdirs() }
        val language = when (lang.selectionModel.selectedIndex) { 1 -> "ar"; 2 -> "en"; 3 -> "auto"; else -> "fa" }
        stop.set(false); bar.isVisible = true; log.clear()
        bg {
            var ok = 0
            queue.forEachIndexed { i, f ->
                if (stop.get()) return@forEachIndexed
                Platform.runLater { status.text = "رونویسی ${fa(i + 1L)} از ${fa(queue.size.toLong())}: ${f.name}" }
                val r = runCatching {
                    val segs = Whisper.transcribe(f, model, language, { v ->
                        val all = (i + v) / queue.size
                        Platform.runLater { bar.progress = all }; TrayProgress.update(all, f.name, "رونویسی صدا")
                    }) { stop.get() }
                    val base = File(out, f.nameWithoutExtension)
                    val lines = segs.map { "[${Whisper.stamp(it.from)}] ${it.text}" }
                    File(base.path + ".txt").writeText("رونویسی خودکار «${f.name}» — متن‌یاب\nمنبع: ${f.absolutePath}\n\n" + lines.joinToString("\n"), Charsets.UTF_8)
                    if (docx.isSelected) DocxWriter().apply {
                        title(f.nameWithoutExtension); subtitle("رونویسی خودکار — " + f.absolutePath)
                        note("این متن با مدل گفتار به متن ساخته شده و ممکن است خطا داشته باشد؛ برای نقل دقیق، با صدای اصلی تطبیق دهید.")
                        segs.forEach { s -> para("[${Whisper.stamp(s.from)}]  ${s.text}") }
                    }.save(File(base.path + ".docx"))
                    segs.size
                }
                Platform.runLater { log.appendText(r.fold({ "✓ ${f.name}: ${fa(it.toLong())} جمله\n" }, { "✗ ${f.name}: ${it.message}\n" })) }
                if (r.isSuccess) ok++
            }
            Platform.runLater {
                TrayProgress.finish("رونویسی ${fa(ok.toLong())} فایل تمام شد.")
                bar.isVisible = false
                status.text = "${fa(ok.toLong())} از ${fa(queue.size.toLong())} فایل رونویسی شد. برای جست‌وجوپذیر شدن، «افزودن رونویسی‌ها به کتابخانه» را بزنید."
            }
        }
    }
}
