package ir.matnyaab.ui

import ir.matnyaab.util.PersianUtil
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.stage.WindowEvent
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

/**
 * کار طولانی (فهرست‌سازی، آماده‌سازی معنایی، رونویسی صدا…) کنار ساعت ویندوز:
 * پنجره‌ها پنهان می‌شوند، نماد متن‌یاب با حلقهٔ پیشرفت در ناحیهٔ اعلان می‌نشیند،
 * با کلیک روی آن کارت کوچک پیشرفت باز می‌شود و در پایان، برنامه خودکار برمی‌گردد.
 * اگر سیستم ناحیهٔ اعلان نداشته باشد، هیچ کاری نمی‌کند (supported = false).
 */
object TrayProgress {
    val supported: Boolean by lazy { runCatching { !java.awt.GraphicsEnvironment.isHeadless() && SystemTray.isSupported() }.getOrDefault(false) }

    var parked = false; private set
    private var icon: TrayIcon? = null
    private var windows: List<Stage> = emptyList()
    private var title = ""; private var fraction = -1.0; private var detail = ""
    private var lastDraw = 0L; private var spin = 0
    private var card: Stage? = null
    private var cardBar: ProgressBar? = null; private var cardPct: Label? = null; private var cardDetail: Label? = null; private var cardTitle: Label? = null

    /** پنجره‌ها را پنهان و نماد پیشرفت را کنار ساعت نشان بده. main نخست، سپس پنجرهٔ کار. */
    fun park(windows: List<Stage>, title: String): Boolean {
        if (!supported || parked) return false
        this.title = title
        val ok = runCatching {
            val ti = TrayIcon(draw(), tip()).apply {
                isImageAutoSize = true
                addActionListener { Platform.runLater { restore() } }                  // دوبار کلیک
                addMouseListener(object : java.awt.event.MouseAdapter() {
                    override fun mouseClicked(e: java.awt.event.MouseEvent) { if (e.clickCount == 1 && e.button == java.awt.event.MouseEvent.BUTTON1) Platform.runLater { toggleCard() } }
                })
                popupMenu = java.awt.PopupMenu().apply { add(java.awt.MenuItem("Show Matnyaab").apply { addActionListener { Platform.runLater { restore() } } }) }
            }
            SystemTray.getSystemTray().add(ti); icon = ti
        }.isSuccess
        if (!ok) return false
        parked = true
        Platform.setImplicitExit(false)
        this.windows = windows
        windows.forEach { it.hide() }
        runCatching { icon?.displayMessage("متن‌یاب", "$title در پس‌زمینه ادامه دارد. برای دیدن پیشرفت روی نماد کلیک کنید.", TrayIcon.MessageType.INFO) }
        return true
    }

    /** به‌روزرسانی پیشرفت (۰..۱، یا منفی برای نامعلوم). از هر رشته‌ای قابل فراخوانی است. */
    fun update(fraction: Double, detail: String = "", title: String? = null) {
        this.fraction = fraction; this.detail = detail; title?.let { this.title = it }
        val now = System.currentTimeMillis()
        if (now - lastDraw < 400) return
        lastDraw = now
        icon?.let { ti -> runCatching { spin = (spin + 30) % 360; ti.image = draw(); ti.toolTip = tip() } }
        Platform.runLater { syncCard() }
    }

    /** پایان کار: اگر پنجره‌ها پنهان‌اند برمی‌گردند و اعلان پایان نشان داده می‌شود. باید روی رشتهٔ JavaFX صدا زده شود. */
    fun finish(message: String? = null) {
        val ti = icon
        if (parked) restore()
        if (ti != null) {
            if (message != null) runCatching { ti.displayMessage("متن‌یاب", message, TrayIcon.MessageType.INFO) }
            // نماد کمی بماند تا اعلان دیده شود
            Thread { Thread.sleep(if (message != null) 6000 else 0); runCatching { SystemTray.getSystemTray().remove(ti) } }.apply { isDaemon = true }.start()
        }
        icon = null; fraction = -1.0; detail = ""
    }

    private fun restore() {
        if (!parked) return
        parked = false
        card?.hide()
        windows.forEach { w -> runCatching { w.show(); w.isIconified = false; w.toFront() } }
        windows = emptyList()
        Platform.setImplicitExit(true)
    }

    private fun tip(): String {
        val p = if (fraction >= 0) " ${(fraction * 100).toInt()}%" else ""
        return ("Matnyaab — $title$p").take(120)
    }

    /** حلقهٔ پیشرفت روی زمینهٔ گرد؛ برای پیشرفت نامعلوم، کمانی چرخان */
    private fun draw(): BufferedImage {
        val s = 32
        val img = BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x1F, 0x3A, 0x68); g.fillOval(1, 1, s - 2, s - 2)
        g.stroke = BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.color = Color(255, 255, 255, 60); g.drawOval(5, 5, s - 10, s - 10)
        g.color = Color(0x5B, 0xC0, 0xFF)
        if (fraction >= 0) g.drawArc(5, 5, s - 10, s - 10, 90, -(fraction.coerceIn(0.0, 1.0) * 360).toInt().coerceAtLeast(8))
        else g.drawArc(5, 5, s - 10, s - 10, 90 - spin, -100)
        // ذره‌بین کوچک نشان متن‌یاب
        g.color = Color.WHITE; g.stroke = BasicStroke(2.2f)
        g.drawOval(11, 10, 8, 8); g.drawLine(18, 17, 21, 20)
        g.dispose()
        return img
    }

    private fun toggleCard() {
        val c = card ?: buildCard().also { card = it }
        if (c.isShowing) { c.hide(); return }
        syncCard()
        val b = Screen.getPrimary().visualBounds
        c.show()
        c.x = b.maxX - c.width - 12; c.y = b.maxY - c.height - 12
    }

    private fun buildCard(): Stage {
        val st = Stage(StageStyle.UTILITY).apply { isAlwaysOnTop = true; isResizable = false; title = "متن‌یاب" }
        cardTitle = Label().apply { styleClass.add("card-title") }
        cardBar = ProgressBar(0.0).apply { prefWidth = 300.0; prefHeight = 12.0 }
        cardPct = Label().apply { styleClass.add("card-title") }
        cardDetail = Label().apply { styleClass.add("muted"); maxWidth = 300.0; isWrapText = false; textOverrun = javafx.scene.control.OverrunStyle.LEADING_ELLIPSIS }
        val show = Button("نمایش متن‌یاب").apply { style = Theme.primaryButton(); setOnAction { restore() } }
        val hide = Button("بستن کارت").apply { style = Theme.addButton(); setOnAction { st.hide() } }
        val root = VBox(8.0, cardTitle, HBox(8.0, cardBar, cardPct).apply { alignment = Pos.CENTER_RIGHT }, cardDetail,
            HBox(8.0, show, hide).apply { alignment = Pos.CENTER_RIGHT })
            .apply { padding = Insets(14.0); nodeOrientation = NodeOrientation.RIGHT_TO_LEFT; style = Theme.FONT }
        st.scene = Scene(root)
        Theme.install(st.scene, st)
        st.addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST) { it.consume(); st.hide() }
        return st
    }

    private fun syncCard() {
        cardTitle?.text = title
        cardBar?.progress = if (fraction >= 0) fraction else ProgressBar.INDETERMINATE_PROGRESS
        cardPct?.text = if (fraction >= 0) PersianUtil.toPersianNumbers((fraction * 100).toInt()) + "٪" else ""
        cardDetail?.text = detail
    }
}

/**
 * نمایش پنجرهٔ مودال با حلقهٔ رویداد تودرتو که با پنهان‌شدن موقت (رفتن به کنار ساعت) پایان نمی‌یابد؛
 * فقط وقتی پنجره واقعاً بسته شود (در حالت پارک‌نشده) برمی‌گردد. رفتار بیرونی همان showAndWait است.
 */
object ParkableModal {
    fun showAndWait(stage: Stage) {
        val key = Any()
        var done = false
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN) {
            if (!TrayProgress.parked && !done) { done = true; Platform.exitNestedEventLoop(key, null) }
        }
        stage.show()
        if (!done) Platform.enterNestedEventLoop(key)
    }
}
