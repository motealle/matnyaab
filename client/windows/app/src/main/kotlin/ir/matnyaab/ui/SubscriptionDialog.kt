package ir.matnyaab.ui

import ir.matnyaab.core.Constants
import ir.matnyaab.core.SubscriptionManager
import ir.matnyaab.core.SubscriptionManager.Kind
import ir.matnyaab.core.SubscriptionManager.Mood
import ir.matnyaab.util.PersianUtil
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.*
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.stage.Window
import java.awt.Desktop
import java.net.URI

/** متن‌های صمیمی و مهربان وضعیت اشتراک */
object SubTexts {
    fun fa(n: Any) = PersianUtil.toPersianNumbers(n)

    private fun org(m: SubscriptionManager) = m.state.org.takeIf { it.isNotBlank() }

    fun chip(m: SubscriptionManager): String {
        val s = m.state
        val g = org(m)?.let { "گروهی «$it» · " } ?: ""
        return when {
            s.kind == Kind.NONE -> "🌱 هنوز اشتراکی ثبت نکرده‌اید · بزنید تا با هم شروع کنیم"
            !s.active -> "🍂 دورهٔ اشتراک تمام شد · تمدید"
            s.kind == Kind.TRIAL -> "☕ مهمان ما هستید · ${fa(s.daysLeft)} روز مانده"
            m.mood(s) == Mood.SOON -> "⏳ ${g}${fa(s.daysLeft)} روز تا تمدید"
            else -> "✔ ${g}اشتراک ${s.planTitle} · روز ${fa(s.dayNumber)} از ${fa(s.daysTotal)}"
        }
    }

    fun headline(m: SubscriptionManager): Pair<String, String> {
        val s = m.state
        val g = org(m)
        return when {
            s.kind == Kind.NONE -> "سلام، خوش آمدید! 👋" to
                "خوشحالیم که متن‌یاب را انتخاب کردید. اشتراک ما بر پایهٔ اعتماد است: پلن را خودتان انتخاب می‌کنید و کد اشتراک همان لحظه تقدیم‌تان می‌شود."
            !s.active && g != null -> "دورهٔ اشتراک گروهی «$g» تمام شد" to
                "اگر زحمتی نیست، به مسئول اشتراک‌تان خبر بدهید تا تمدیدش کند؛ کد گروه همان می‌ماند. تا آن موقع می‌توانید چند روزی مهمان ما باشید. 🌷"
            !s.active -> "دورهٔ اشتراک‌تان تمام شد" to
                "کتابخانه‌تان صحیح و سالم سر جایش است. هر وقت فرصت کردید تمدیدش کنید؛ اگر هم فعلاً شرایطش نیست، اشکالی ندارد، چند روز دیگر مهمان ما باشید. 🌷"
            s.kind == Kind.TRIAL -> "قدم‌تان روی چشم ☕" to
                "تا ${fa(s.daysLeft)} روز دیگر مهمان ما هستید. با خیال راحت کارتان را بکنید و هر وقت مایل بودید، اشتراک‌تان را ثبت کنید."
            m.mood(s) == Mood.SOON && g != null -> "اشتراک گروهی «$g» رو به پایان است" to
                "${fa(s.daysLeft)} روز دیگر از اشتراک گروه‌تان مانده. اگر زحمتی نیست، به مسئول اشتراک یادآوری کنید که تمدیدش کند. ممنون از همراهی‌تان! 🌷"
            m.mood(s) == Mood.SOON -> "دوست عزیز، موعد تمدید نزدیک است" to
                "فقط ${fa(s.daysLeft)} روز از دورهٔ ${s.planTitle} مانده. تمدیدش با همان کد قبلی یک دقیقه هم طول نمی‌کشد. عجله‌ای نیست؛ فقط خواستیم یادتان بماند. 🌷"
            g != null -> "اشتراک گروهی «$g» برقرار است" to
                "${fa(s.daysLeft)} روز دیگر از اشتراک گروه‌تان باقی است. پژوهش‌تان پربار! 🌿"
            else -> "همه‌چیز رو به راه است 🌿" to
                "ممنون که همراه ما هستید! ${fa(s.daysLeft)} روز از دورهٔ ${s.planTitle} باقی است؛ با خیال راحت پژوهش کنید."
        }
    }
}

/** پنجرهٔ «اشتراک و تمدید» با روزشمار، فعال‌سازی کد، خوداظهاری و سه روز مهمانی */
class SubscriptionDialog(owner: Window?, private val m: SubscriptionManager, private val onChanged: () -> Unit) {
    private val stage = Stage(StageStyle.UNDECORATED)
    private val content = VBox(14.0)

    init {
        stage.initModality(Modality.APPLICATION_MODAL)
        owner?.let { stage.initOwner(it) }
        val root = BorderPane().apply {
            nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
            top = CustomTitleBar(stage, "اشتراک متن‌یاب", showMaximize = false)
            center = ScrollPane(content).apply { isFitToWidth = true; hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER }
            style = "-fx-border-color: ${Constants.BORDER_COLOR}; -fx-background-color: ${Constants.OPTION_BACKGROUND_COLOR};"
            prefWidth = 560.0; prefHeight = 640.0
        }
        content.padding = Insets(18.0)
        content.style = "-fx-background-color: ${Constants.OPTION_BACKGROUND_COLOR};"
        stage.scene = Scene(root)
        Theme.install(stage.scene, stage)
        render()
    }

    private fun section(title: String, vararg nodes: javafx.scene.Node) = VBox(8.0).apply {
        styleClass.add("card"); padding = Insets(14.0)
        children.add(Label(title).apply { styleClass.add("section-title") })
        children.addAll(nodes)
    }

    private fun wrap(text: String, muted: Boolean = false) = Label(text).apply {
        isWrapText = true; maxWidth = 500.0; if (muted) styleClass.add("muted")
    }

    private fun open(url: String) { try { Desktop.getDesktop().browse(URI(url)) } catch (e: Exception) {} }

    private fun render() {
        val s = m.state
        val mood = m.mood(s)
        val (title, body) = SubTexts.headline(m)
        val (bg, fg) = when (mood) {
            Mood.GOOD -> "rgba(16,124,65,0.12)" to "-mx-success"
            Mood.SOON, Mood.ENDED -> "rgba(196,43,28,0.10)" to "-mx-danger"
            Mood.NONE -> "-mx-accent-soft" to "-mx-accent-text"
        }

        // ---- کارت وضعیت و روزشمار
        val big = Label(if (s.kind == Kind.NONE) "—" else SubTexts.fa(s.daysLeft)).apply {
            style = "-fx-font-size: 40px; -fx-font-weight: bold; -fx-text-fill: $fg;"
        }
        val unit = Label(if (s.kind == Kind.NONE) "" else "روز مانده").apply { style = "-fx-text-fill: $fg; -fx-font-weight: bold;" }
        val counter = VBox(0.0, big, unit).apply { alignment = Pos.CENTER; minWidth = 110.0 }
        val details = VBox(4.0,
            Label(title).apply { style = "-fx-font-size: ${Constants.FONT_SIZE_LARGE}px; -fx-font-weight: bold;" },
            wrap(body),
        )
        if (s.start != null && s.end != null) {
            details.children.add(Label("${s.planTitle} · از ${SubTexts.fa(PersianUtil.toJalali(s.start.atStartOfDay()).replace('-', '/'))} تا ${SubTexts.fa(PersianUtil.toJalali(s.end.atStartOfDay()).replace('-', '/'))}").apply { styleClass.add("muted") })
            details.children.add(ProgressBar(s.dayNumber.toDouble() / s.daysTotal).apply { maxWidth = Double.MAX_VALUE; prefHeight = 8.0 })
            if (s.code.isNotBlank() && s.kind == Kind.CODE) details.children.add(Label("کد اشتراک: ${s.code}").apply { styleClass.add("muted") })
        }
        HBox.setHgrow(details, Priority.ALWAYS)
        val status = HBox(14.0, counter, details).apply {
            padding = Insets(16.0); alignment = Pos.CENTER_LEFT
            style = "-fx-background-color: $bg; -fx-background-radius: 12;"
        }

        // ---- کد اشتراک
        val txtCode = TextField(s.code).apply { promptText = "MT-XXXX-XXXX"; nodeOrientation = NodeOrientation.LEFT_TO_RIGHT }
        val lblResult = wrap("", true)
        val btnActivate = Button("فعال‌سازی").apply {
            style = Theme.primaryButton(); prefHeight = Constants.OPTION_BUTTONS_HEIGHT
            isDefaultButton = true
            setOnAction {
                isDisable = true; lblResult.text = "در حال هماهنگی با سایت…"
                Thread {
                    val r = m.activate(txtCode.text)
                    Platform.runLater {
                        isDisable = false
                        r.onSuccess { onChanged(); render() }
                        r.onFailure { lblResult.text = it.message; lblResult.style = "-fx-text-fill: -mx-danger;" }
                    }
                }.apply { isDaemon = true }.start()
            }
        }
        HBox.setHgrow(txtCode, Priority.ALWAYS)
        val codeBox = section("کد اشتراک دارم",
            HBox(8.0, txtCode, btnActivate),
            wrap("کد را بعد از ثبت اشتراک در سایت می‌گیرید (برای سازمان‌ها، کد گروهی). با آن روزشمار برنامه با سایت یکی می‌شود و پیام‌های ما هم به دست‌تان می‌رسد.", true),
            lblResult)

        // ---- ثبت/تمدید در سایت و پلن‌ها
        val plansText = m.config.plans.joinToString("   ·   ") { "${it.title}: ${SubscriptionManager.money(it.price)} تومان" }
        val card = m.config.cards.firstOrNull()
        val btnSite = Button(if (s.kind == Kind.CODE) "تمدید در سایت با همین کد" else "ثبت اشتراک در سایت").apply {
            style = Theme.primaryButton(); prefHeight = Constants.OPTION_BUTTONS_HEIGHT; maxWidth = Double.MAX_VALUE
            setOnAction { open(m.subscribeUrl()) }
        }
        val cardRow = card?.let { c ->
            HBox(8.0,
                Label(c.number.chunked(4).joinToString(" ")).apply {
                    nodeOrientation = NodeOrientation.LEFT_TO_RIGHT
                    style = "-fx-font-family: 'Consolas','Segoe UI'; -fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: -mx-accent-text;"
                },
                Label("به نام ${c.holder}").apply { styleClass.add("muted") },
                Region().also { HBox.setHgrow(it, Priority.ALWAYS) },
                Button("کپی").apply {
                    style = Theme.addButton()
                    setOnAction { Clipboard.getSystemClipboard().setContent(ClipboardContent().apply { putString(c.number) }); text = "کپی شد ✓" }
                },
            ).apply { alignment = Pos.CENTER_LEFT }
        }
        val cmbPlan = ComboBox(FXCollections.observableArrayList(m.config.plans.map { it.title })).apply { selectionModel.select(0) }
        val btnLocal = Button("ثبت خوداظهاری از امروز").apply {
            style = Theme.addButton()
            setOnAction {
                val p = m.config.plans.getOrNull(cmbPlan.selectionModel.selectedIndex) ?: return@setOnAction
                m.declareLocal(p); onChanged(); render()
            }
        }
        val siteBox = section(if (s.kind == Kind.CODE) "تمدید اشتراک" else "ثبت اشتراک",
            wrap(plansText),
            listOfNotNull(cardRow).firstOrNull() ?: Region(),
            btnSite,
            Separator(),
            wrap("بدون اینترنت یا کد؟ پلن خود را همین‌جا اعلام کنید (فقط در همین رایانه):", true),
            HBox(8.0, cmbPlan, btnLocal).apply { alignment = Pos.CENTER_RIGHT },
        )

        // ---- سه روز مهمانی
        val trialBox = if (m.config.trialEnabled) section("چند روز مهمان ما باشید",
            wrap("هر وقت لازم شد، با کمال میل ${SubTexts.fa(m.config.trialDays)} روز دیگر مهمان ما باشید؛ چند بار هم که بخواهید. هر وقت شرایطش جور شد، اشتراک‌تان را ثبت کنید. 🌷", true),
            Button("لطفاً حلال کنید که ${SubTexts.fa(m.config.trialDays)} روز دیگه هم رایگان استفاده کنم!").apply {
                style = Theme.successButton(); prefHeight = Constants.OPTION_BUTTONS_HEIGHT; maxWidth = Double.MAX_VALUE
                setOnAction {
                    m.startTrial(); onChanged()
                    CustomMessageBox.show(stage, "حلالِ حلال! 🌿", "قدم‌تان روی چشم؛ تا ${SubTexts.fa(m.config.trialDays)} روز دیگر با کمال میل میزبان شما هستیم. پژوهش‌تان پربار!")
                    render()
                }
            },
        ) else null

        val trust = wrap("🤝 ما به شما اعتماد داریم؛ برای همین هیچ بررسی و پیگیری‌ای در کار نیست. همراهی شما با ثبت اشتراک، چراغ متن‌یاب را روشن نگه می‌دارد و کمک می‌کند هر روز بهترش کنیم. ممنون که هستید! 🌷", true)
        val btnClose = Button("بستن").apply { style = Theme.addButton(); isCancelButton = true; setOnAction { stage.close() }; maxWidth = Double.MAX_VALUE }

        content.children.setAll(listOfNotNull(status, codeBox, siteBox, trialBox, trust, btnClose))
    }

    fun show() = stage.showAndWait()
    internal fun showNonBlocking(): Stage { stage.show(); return stage }
}

/** فهرست پیام‌های رسیده از متن‌یاب */
object MessagesDialog {
    fun show(owner: Window?, m: SubscriptionManager) {
        val list = m.allMessages()
        val text = if (list.isEmpty()) "فعلاً پیام تازه‌ای نداریم. 🌿"
            else list.joinToString("\n\n") { "● ${it.title}\n${it.body}" }
        m.markAllSeen()
        CustomMessageBox.show(owner, "پیام‌های متن‌یاب", text)
    }
}
