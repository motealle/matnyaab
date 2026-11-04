package ir.matnyaab

import ir.matnyaab.core.*
import ir.matnyaab.ui.MainWindow
import ir.matnyaab.ui.SplashWindow
import javafx.application.Application
import javafx.application.Platform
import javafx.stage.Stage
import java.io.File

/**
 * نقطه ورود برنامه — معادل بخش __main__ در matnyaab.py
 */
class MatnyaabApp : Application() {
    override fun start(primaryStage: Stage) {
        val raw = parameters.raw
        val shotsDir = raw.indexOf("--screenshots").takeIf { it >= 0 && it + 1 < raw.size }?.let { raw[it + 1] }
        val settings = AppSettingsManager()
        Constants.FONT_CHANGE_RATE = (settings.get("FONT_RATE").toIntOrNull() ?: 100) / 100.0 * 1.2
        ir.matnyaab.ui.Theme.init(settings)

        val splash = SplashWindow()
        splash.show()

        Thread {
            val license = LicenseChecker(settings, init = true)
            val indexer = LuceneIndexer(settings)
            if (shotsDir != null) ir.matnyaab.ui.DemoShots.prepareCorpus(indexer)
            Platform.runLater {
                val main = MainWindow(primaryStage, settings, indexer, license)
                splash.close()
                main.show()
                if (shotsDir != null) ir.matnyaab.ui.DemoShots.run(main, primaryStage, indexer, File(shotsDir))
                else StatisticsReporter.report("open_app", license.systemId, license.isSubscribed())
            }
        }.apply { isDaemon = true }.start()
    }

}

fun main(args: Array<String>) {
    if (args.contains("--smoke")) {
        val probe = File.createTempFile("matnyaab-client-smoke", ".txt")
        try {
            probe.writeText("MATNYAAB smoke test")
            val parsed = TikaExtractor.parse(probe)
            check(parsed.status == 200 && parsed.content?.contains("MATNYAAB") == true) {
                "Tika smoke failed"
            }
            org.apache.lucene.analysis.standard.StandardAnalyzer().use { analyzer ->
                val tokens = analyzer.tokenStream("content", "متن یاب MATNYAAB")
                tokens.reset()
                check(tokens.incrementToken()) { "Lucene smoke failed" }
                tokens.end()
            }
            println("MATNYAAB_CLIENT_SMOKE_OK " + Constants.APP_VERSION)
        } finally {
            probe.delete()
        }
        return
    }

    if (args.contains("--screenshots")) {
        // پوشهٔ تنظیمات جدا تا داده‌های واقعی کاربر دست نخورد
        System.setProperty("matnyaab.home", java.nio.file.Files.createTempDirectory("matnyaab-shots").toString())
        System.setProperty("matnyaab.shots", "1")
    }
    Application.launch(MatnyaabApp::class.java, *args)
}
