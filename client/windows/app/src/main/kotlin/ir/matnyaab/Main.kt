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
        ir.matnyaab.ui.AppFonts.load()
        // مهاجرت یک‌باره: پیش‌نمایش با وزیرمتن
        if (settings.get("FONT_MIGRATED") != "1") {
            if (settings.get("PREVIEW_FONT_FAMILY") in listOf("", "Tahoma") && ir.matnyaab.ui.AppFonts.has("Vazirmatn"))
                settings.setValue("PREVIEW_FONT_FAMILY", "Vazirmatn")
            settings.setValue("FONT_MIGRATED", "1")
        }
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
                raw.indexOf("--search").takeIf { it >= 0 && it + 1 < raw.size }?.let { main.externalSearchLater(raw[it + 1]) }
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

    // آزمون خودکار بستهٔ معنایی در CI: --semantic-selftest <pack.zip> <out.json>
    args.indexOf("--semantic-selftest").takeIf { it >= 0 && it + 2 < args.size }?.let { i ->
        val out = File(args[i + 2])
        val code = try {
            val r = ir.matnyaab.core.semantic.SemanticPack.selfTest(File(args[i + 1]))
            out.writeText(com.google.gson.Gson().toJson(r)); if (r["ok"] == true) 0 else 2
        } catch (e: Throwable) { out.writeText("{\"ok\":false,\"error\":\"" + (e.toString().replace("\"", "'")) + "\"}"); 1 }
        ir.matnyaab.core.semantic.SemanticPack.unload()
        kotlin.system.exitProcess(code)
    }

    if (args.contains("--bridge")) {
        // حالت پنهان برای افزونهٔ ورد: بدون پنجره، فقط پل محلی؛ پس از ۳۰ دقیقه بیکاری بسته می‌شود
        if (ir.matnyaab.core.bridge.LocalBridge.existing()?.let { (port, _) ->
                runCatching { java.net.Socket().use { s -> s.connect(java.net.InetSocketAddress("127.0.0.1", port), 400) }; true }.getOrDefault(false)
            } == true) return
        val settings = AppSettingsManager()
        val indexer = LuceneIndexer(settings)
        ir.matnyaab.core.bridge.LocalBridge.loadCollections(indexer)
        ir.matnyaab.core.semantic.SemanticService.start(settings) { indexer.allObjects.toList() }
        val bridge = ir.matnyaab.core.bridge.LocalBridge(settings, indexer, "headless")
        val lock = Object()
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        bridge.onShutdown = { done.set(true); synchronized(lock) { lock.notifyAll() } }
        bridge.start()
        while (!done.get() && bridge.idleMillis() < 30 * 60_000L) {
            synchronized(lock) { lock.wait(60_000) }
        }
        bridge.stop()
        kotlin.system.exitProcess(0)
    }

    if (args.contains("--screenshots")) {
        // پوشهٔ تنظیمات جدا تا داده‌های واقعی کاربر دست نخورد
        System.setProperty("matnyaab.home", java.nio.file.Files.createTempDirectory("matnyaab-shots").toString())
        System.setProperty("matnyaab.shots", "1")
    }
    Application.launch(MatnyaabApp::class.java, *args)
}
