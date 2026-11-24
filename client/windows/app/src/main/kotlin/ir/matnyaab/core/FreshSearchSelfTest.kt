package ir.matnyaab.core

import ir.matnyaab.core.search.SearchSpec
import ir.matnyaab.ui.ScopeTree
import ir.matnyaab.ui.SearchPanel
import javafx.application.Platform
import java.io.File
import java.nio.file.Files
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/** Runs the real file indexing, restart/load, scope tree, and UI search callback on a clean profile. */
object FreshSearchSelfTest {
    fun run() {
        val root = Files.createTempDirectory("matnyaab-fresh-search-").toFile()
        System.setProperty("matnyaab.home", File(root, "profile").absolutePath)
        try {
            val source = File(root, "اسناد نمونه").apply { mkdirs() }
            File(source, "متن کوتاه.txt").writeText("عدالت،پژوهش و کتاب", Charsets.UTF_8)
            val sub = File(source, "پوشه").apply { mkdirs() }
            org.apache.poi.xwpf.usermodel.XWPFDocument().use { doc ->
                doc.createParagraph().createRun().setText("پژوهش دربارهٔ عدالت و دانش. windowsprobe")
                File(sub, "سند ورد.docx").outputStream().use { doc.write(it) }
            }
            val settings = AppSettingsManager()
            val indexer = LuceneIndexer(settings)
            fun index(folder: File, name: String): Int {
                var code = -1
                IndexingService(indexer).indexDirectory(name, folder.absolutePath, setOf(".txt", ".docx"), 1L, 200L * 1024 * 1024, true,
                    object : IndexingService.Callbacks {
                        override fun onFinish(err: Int) { code = err }
                    })
                return code
            }
            val empty = File(root, "empty").apply { mkdirs() }
            check(index(empty, "empty") == 4 && indexer.allObjects.isEmpty()) { "Empty folder was accepted" }
            check(index(source, "اسناد نمونه") == 0) { "Fresh index failed" }
            val reloaded = LuceneIndexer(AppSettingsManager())
            ir.matnyaab.core.bridge.LocalBridge.loadCollections(reloaded)
            check(reloaded.allObjects.size == 1) { "Reload did not restore fresh index" }
            check(reloaded.readFileList(java.nio.file.Path.of(reloaded.allObjects.single().savePath)).size == 2)
            check(reloaded.searchSpec(SearchSpec(listOf("عدالت")), reloaded.allObjects, emptySet()).size == 2) { "Fresh Persian search failed" }
            check(reloaded.searchSpec(SearchSpec(listOf("پژوهش")), reloaded.allObjects, emptySet()).size == 2) { "Punctuation-glued Persian search failed" }
            check(reloaded.searchSpec(SearchSpec(listOf("windowsprobe")), reloaded.allObjects, emptySet()).size == 1)
            check(reloaded.searchSpec(SearchSpec(listOf("zzmissingzz")), reloaded.allObjects, emptySet()).isEmpty())
            val broken = File(root, "broken").apply { mkdirs() }
            val bad = listOf(IndexerInfo("broken", source.absolutePath, broken.absolutePath))
            check(runCatching { reloaded.searchSpec(SearchSpec(listOf("عدالت")), bad, emptySet()) }.isFailure) { "Unreadable index reported zero hits" }

            Platform.startup { Platform.setImplicitExit(false) }
            try {
                val fx = FutureTask {
                    ir.matnyaab.ui.Theme.init(settings)
                    val scope = ScopeTree(reloaded, { null }, {}, {}, {})
                    scope.reload()
                    val (selected, masked) = scope.getSelectedDocs()
                    check(selected.size == 1 && masked.isEmpty()) { "Fresh scope excluded files: ${selected.size}, $masked" }
                    var hits = -1
                    val panel = SearchPanel({}, {}, settings) { spec, _ ->
                        val (cols, hidden) = scope.getSelectedDocs()
                        hits = reloaded.searchSpec(spec, cols, hidden).size
                    }
                    panel.runPhrase("عدالت")
                    check(hits == 2) { "Actual search panel callback failed: $hits" }
                    val item = scope.tree.root.children.first() as javafx.scene.control.CheckBoxTreeItem<ScopeTree.ScopeNode>
                    item.isSelected = false
                    check(scope.getSelectedDocs().first.isEmpty()) { "Explicit exclusion ignored" }
                    scope.reload()
                    check(scope.getSelectedDocs().first.size == 1 && scope.getSelectedDocs().second.isEmpty())
                }
                Platform.runLater(fx)
                fx.get(60, TimeUnit.SECONDS)
            } finally {
                Platform.exit()
            }
            println("MATNYAAB_FRESH_SEARCH_OK " + Constants.APP_VERSION)
        } finally {
            root.deleteRecursively()
        }
    }
}
