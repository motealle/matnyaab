package ir.matnyaab.ui

import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Hyperlink
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.*
import javafx.stage.Stage

/** «چه کارهایی می‌توانم بکنم؟» — کارهای واقعی پژوهشگر، هرکدام با مسیر کوتاه انجامش. */
object ScenariosDialog {
    data class Job(val icon: String, val title: String, val how: String, val tab: String?, val ai: Boolean = false)

    val JOBS = listOf(
        Job("⌕", "یک عبارت را در همهٔ کتاب‌ها و مقاله‌هایم پیدا کنم", "عبارت را در جست‌وجو بنویسید؛ با «خانوادهٔ واژه» و «هم‌معنا» صورت‌های دیگرش هم پیدا می‌شود.", null),
        Job("🗂", "هنگام مطالعه فیش بردارم و منبعش گم نشود", "در پیش‌نمایش متن را انتخاب و Ctrl+D بزنید؛ سند و جای دقیق خودکار ثبت می‌شود.", "fish"),
        Job("📘", "از فیش‌هایم کتاب یا فصل پایان‌نامه بسازم", "در «ساخت کتاب» فصل‌ها را بچینید و قطع و قلم را انتخاب کنید؛ خروجی Word با فهرست، پانویس و منابع است.", "book"),
        Job("⧉", "بدانم این نقل‌قول از کدام کتاب است", "متن را در «منبع‌یاب» بچسبانید؛ حتی با تغییر جزئی، سند و جای دقیق را نشان می‌دهد.", "source"),
        Job("✓", "پیش از تحویل، همانندی متنم را با منابعم بسنجم", "فایل Word خود را در «منبع‌یاب» انتخاب و «همانندجویی» را بزنید؛ گزارش Word هم می‌گیرید.", "source"),
        Job("✦", "فیش‌برداری موضوعی خودکار از همهٔ منابعم", "در «دستیار هوشمند» بنویسید: «همهٔ فیش‌های موضوع … را با عنوان استخراج و در … ذخیره کن».", "ai", true),
        Job("✦", "کدگذاری به روش گراندد تئوری", "سناریوی «گراندد تئوری»: کدهای باز، مقوله‌ها و مقولهٔ هسته با نقل‌قول‌های راستی‌آزمایی‌شده.", "ai", true),
        Job("✦", "پیشینهٔ پژوهش و مرور منابع بنویسم", "سناریوی «پیشینهٔ پژوهش»: متن پیوسته با پانویس دقیق از منابع خود شما.", "ai", true),
        Job("✦", "دیدگاه‌های مختلف دربارهٔ یک مسئله را مقایسه کنم", "سناریوی «دیدگاه‌ها»: دسته‌بندی آرا با استناد به گوینده و منبع.", "ai", true),
        Job("📖", "از عنوان و کلیدواژه، پیش‌نویس کامل کتاب بگیرم", "«کتاب ← کتاب با هوش مصنوعی»: سبک نگارش، قطع و قلم را انتخاب کنید؛ Word صفحه‌آرایی‌شده با پانویس منابع تحویل بگیرید.", "bookai", true),
        Job("۞", "بدانم یک آیه در کدام اسنادم آمده است", "«کشف‌الآیات»: نقل کامل و بخشی، ارجاع شماره‌ای و ترجمه، با اطمینان و متن شاهد؛ خروجی Word و Excel.", "quran"),
        Job("⭳", "نتایج جست‌وجو را با متن پیرامون در Word یا Excel داشته باشم", "دکمهٔ «خروجی Word/Excel» بالای نتایج: بند کامل، چند بند یا شمار واژهٔ پیش و پس، با برجسته‌سازی و شمارهٔ صفحه.", null),
        Job("🎙", "سخنرانی و درس صوتی را جست‌وجوپذیر کنم", "«گفتار به متن»: بستهٔ اختیاری آفلاین Whisper؛ متن با زمان هر جمله.", "whisper"),
        Job("W", "در ورد بنویسم و همان‌جا جست‌وجو و استناد کنم", "افزونهٔ ورد متن‌یاب: جست‌وجو، درج نقل‌قول با ارجاع، قالب پایان‌نامه، نامهٔ اداری و قالب‌های رسمی.", null),
    )

    fun show(owner: Stage, open: (String) -> Unit) {
        val st = Stage().apply { initOwner(owner); title = "چه کارهایی می‌توانم بکنم؟" }
        val grid = TilePane(10.0, 10.0).apply { prefColumns = 2; padding = Insets(14.0); prefTileWidth = 400.0 }
        JOBS.forEach { j ->
            val card = VBox(6.0,
                HBox(8.0, Label(j.icon).apply { styleClass.add("tool-glyph") }, Label(j.title).apply { styleClass.add("card-title"); isWrapText = true; style = Theme.FONT },
                    *(if (j.ai) arrayOf(Label("هوشمند").apply { styleClass.add("badge"); style = Theme.FONT }) else emptyArray())).apply { alignment = Pos.CENTER_RIGHT },
                Label(j.how).apply { isWrapText = true; styleClass.add("muted"); style = Theme.FONT },
            ).apply { styleClass.add("card"); prefHeight = 120.0 }
            j.tab?.let { t -> card.children.add(Button("برو").apply { style = Theme.addButton(); setOnAction { st.close(); open(t) } }) }
            grid.children.add(card)
        }
        val guide = Hyperlink("راهنمای کامل متن‌یاب (PDF)").apply { setOnAction { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(ir.matnyaab.core.Constants.BASE_URL + "/docs/matnyaab-guide-3.1.pdf")) } } }
        val root = VBox(HBox(10.0, Label("کار شما چیست؟ همان را انتخاب کنید.").apply { styleClass.add("section-title"); style = Theme.FONT }, Region().also { HBox.setHgrow(it, Priority.ALWAYS) }, guide)
            .apply { padding = Insets(14.0, 14.0, 0.0, 14.0); alignment = Pos.CENTER_RIGHT },
            ScrollPane(grid).apply { isFitToWidth = true; VBox.setVgrow(this, Priority.ALWAYS) })
            .apply { nodeOrientation = NodeOrientation.RIGHT_TO_LEFT; style = Theme.FONT }
        st.scene = Scene(root, 880.0, 640.0)
        Theme.install(st.scene, st)
        st.show()
    }
}
