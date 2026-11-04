package ir.matnyaab.ui

import ir.matnyaab.core.Constants
import ir.matnyaab.core.IndexingService
import ir.matnyaab.core.LuceneIndexer
import javafx.animation.PauseTransition
import javafx.application.Platform
import javafx.embed.swing.SwingFXUtils
import javafx.scene.Node
import javafx.scene.SnapshotParameters
import javafx.scene.transform.Scale
import javafx.stage.Stage
import javafx.util.Duration
import java.io.File
import javax.imageio.ImageIO

/**
 * حالت «--screenshots <dir>»: ساخت یک کتابخانهٔ نمونه، اجرای جست‌وجو و ذخیرهٔ تصویر
 * از نماهای اصلی برنامه در پوسته‌های مختلف. فقط در CI برای تصاویر معرفی سایت استفاده می‌شود
 * و روی داده‌های کاربر اثری ندارد (پوشهٔ تنظیمات موقت است).
 */
object DemoShots {

    private val SAMPLES = mapOf(
        "ادبیات/گلستان و آیین زندگی.txt" to """
            سعدی در گلستان بارها از عدالت و مهربانی با زیردستان سخن می‌گوید. به باور او پادشاهی که عدالت را رها کند، بنیان فرمانروایی خود را سست کرده است.
            حکایت‌های گلستان کوتاه‌اند اما هر یک درسی دربارهٔ رفتار نیک، خویشتن‌داری و انصاف در خود دارند. زبان ساده و آهنگین سعدی سبب شده این آموزه‌ها قرن‌ها در یادها بماند.
            در باب نخست، سخن از سیرت پادشاهان است و نویسنده نشان می‌دهد که داد و عدالت، از گنج و سپاه پایدارتر است.
        """,
        "ادبیات/شاهنامه؛ داد و بیداد.txt" to """
            در شاهنامهٔ فردوسی، «داد» واژه‌ای کلیدی است؛ شاهان دادگر ستوده می‌شوند و بیدادگری سرانجامی جز تباهی ندارد.
            پژوهشگران ادبیات حماسی بر این باورند که مفهوم عدالت در شاهنامه، پیوندی ناگسستنی با فرّه ایزدی و مشروعیت فرمانروا دارد.
            داستان ضحاک نمونهٔ روشنی از بیداد است و قیام کاوهٔ آهنگر، بازگشت عدالت به جامعه را نشان می‌دهد.
        """,
        "فلسفه/عدالت در اندیشهٔ فارابی.txt" to """
            فارابی در «آراء اهل المدینة الفاضلة» شهر آرمانی را جامعه‌ای می‌داند که در آن هر کس در جایگاه شایستهٔ خود قرار گیرد؛ این همان تعریف کلاسیک عدالت است.
            از نگاه او سعادت فردی جدا از سعادت جمعی نیست و رئیس مدینه باید حکمت و فضیلت را با هم داشته باشد.
            مقایسهٔ آرای فارابی با جمهوری افلاطون، یکی از موضوعات پرتکرار در پژوهش‌های فلسفهٔ سیاسی است.
        """,
        "فلسفه/اخلاق ناصری؛ یادداشت‌ها.txt" to """
            خواجه نصیرالدین طوسی در اخلاق ناصری حکمت عملی را به سه بخش تهذیب اخلاق، تدبیر منزل و سیاست مدن تقسیم می‌کند.
            فضیلت‌های چهارگانه در این کتاب عبارت‌اند از حکمت، شجاعت، عفت و عدالت؛ و عدالت کمال سه فضیلت دیگر شمرده می‌شود.
        """,
        "تاریخ/سیاست‌نامه.txt" to """
            نظام‌الملک در سیاست‌نامه توصیه‌هایی عملی برای اداره کشور ارائه می‌دهد؛ رسیدگی به شکایت مردم و برپایی دیوان مظالم از نشانه‌های عدالت حاکم است.
            این کتاب منبعی ارزشمند برای شناخت ساختار دیوانی دورهٔ سلجوقی و آیین کشورداری به شمار می‌رود.
        """,
        "تاریخ/تاریخ بیهقی؛ گزیده.txt" to """
            ابوالفضل بیهقی در روایت ماجرای حسنک وزیر، با نثری استوار و دقیق، بی‌عدالتی درباریان را بی‌پرده گزارش می‌کند.
            تاریخ بیهقی را باید هم سندی تاریخی و هم یکی از شاهکارهای نثر فارسی دانست.
        """,
        "یادداشت‌های پژوهشی/طرح پژوهش.txt" to """
            موضوع پیشنهادی: بررسی تطبیقی مفهوم عدالت در متون اخلاقی و ادبی فارسی از قرن چهارم تا هشتم هجری.
            منابع اصلی: گلستان سعدی، شاهنامه، اخلاق ناصری، سیاست‌نامه و آثار فارابی.
            روش: استخراج شواهد متنی، دسته‌بندی کاربردهای واژهٔ عدالت و واژه‌های هم‌خانوادهٔ آن و تحلیل تحول معنایی.
        """,
    )

    /** مسیر خوانا برای تصاویر (روی CI درایو D وجود دارد) */
    fun demoRoot(): File {
        val d = File("D:\\")
        return if (d.isDirectory && d.canWrite()) File(d, "پژوهش\\کتابخانهٔ پژوهشی")
        else Constants.APP_SETTINGS_DIR.resolve("کتابخانهٔ پژوهشی").toFile()
    }

    /** ساخت فایل‌های نمونه و فهرست‌گیری آن‌ها (روی ترد پس‌زمینه صدا زده می‌شود) */
    fun prepareCorpus(indexer: LuceneIndexer) {
        val root = demoRoot()
        SAMPLES.forEach { (rel, text) ->
            val f = File(root, rel)
            f.parentFile.mkdirs()
            f.writeText((text.trimIndent() + "\n\n").repeat(3), Charsets.UTF_8)
        }
        IndexingService(indexer).indexDirectory(
            "کتابخانهٔ پژوهشی", root.absolutePath, setOf(".txt"), 1, 100L * 1024 * 1024, true,
            object : IndexingService.Callbacks {},
        )
    }

    private fun save(node: Node, out: File) {
        val params = SnapshotParameters().apply { transform = Scale(1.5, 1.5) }
        val img = node.snapshot(params, null)
        out.parentFile.mkdirs()
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out)
        println("MATNYAAB_SHOT " + out.absolutePath)
    }

    fun run(main: MainWindow, stage: Stage, indexer: LuceneIndexer, dir: File) {
        val steps = mutableListOf<Pair<Double, () -> Unit>>()
        fun step(seconds: Double, action: () -> Unit) { steps.add(seconds to action) }

        step(3.0) { main.demoSearch("عدالت") }
        step(3.5) { main.selectFirstResult() }
        step(2.5) { save(stage.scene.root, File(dir, "main-blue.png")) }
        step(0.3) { main.openPanel(1); main.expandFirstScope(); Theme.setPalette("night") }
        step(2.5) { save(stage.scene.root, File(dir, "main-night.png")) }
        step(0.3) { main.openPanel(0); Theme.setPalette("paper") }
        step(2.5) { save(stage.scene.root, File(dir, "main-paper.png")) }
        step(0.3) { Theme.setPalette("emerald") }
        step(2.0) { save(stage.scene.root, File(dir, "main-emerald.png")) }
        step(0.3) {
            Theme.setPalette("blue")
            val d = ScanDirectoryDialog(stage, demoRoot().absolutePath, indexer, null) {}.showNonBlocking()
            PauseTransition(Duration.seconds(1.5)).apply {
                setOnFinished { save(d.scene.root, File(dir, "add-folder.png")); d.close() }
                play()
            }
        }
        step(2.5) {
            val d = SubscriptionDialog(stage, main.subs) {}.showNonBlocking()
            PauseTransition(Duration.seconds(1.2)).apply {
                setOnFinished { save(d.scene.root, File(dir, "support.png")); d.close() }
                play()
            }
        }
        step(2.5) { println("MATNYAAB_SHOTS_DONE"); Platform.exit() }

        var delay = 0.0
        steps.forEach { (sec, action) ->
            delay += sec
            PauseTransition(Duration.seconds(delay)).apply {
                setOnFinished {
                    try { action() } catch (e: Exception) { System.err.println("shot step failed: ${e.message}") }
                }
                play()
            }
        }
    }
}
