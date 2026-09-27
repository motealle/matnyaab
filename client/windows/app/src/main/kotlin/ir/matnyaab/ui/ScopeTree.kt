package ir.matnyaab.ui

import ir.matnyaab.core.*
import javafx.geometry.NodeOrientation
import javafx.scene.control.*
import javafx.scene.control.cell.CheckBoxTreeCell
import javafx.scene.layout.VBox
import javafx.stage.Window
import java.io.File
import java.nio.file.Path

/**
 * معادل customTreeWidget — درخت محدوده جستجو با چک‌باکس:
 * هر فهرست (دسته) یک آیتم سطح بالا، زیرمجموعه: پوشه‌ها و فایل‌ها با اندازه.
 * منوی راست‌کلیک: حذف سند از محدوده / به‌روزرسانی فهرست / به‌روزرسانی همه / افزودن سند جدید
 */
class ScopeTree(
    private val indexer: LuceneIndexer,
    private val ownerWindow: () -> Window?,
    private val onAddDocument: () -> Unit,
    private val onUpdateIndex: (IndexerInfo?) -> Unit,
    private val onIndexesChanged: () -> Unit,
) : VBox() {

    val tree = TreeView<ScopeNode>()

    /** گره درخت: یا فهرست (top) یا فایل/پوشه */
    data class ScopeNode(val label: String, val savePath: String?, val filePath: String?) {
        override fun toString() = label
    }

    init {
        nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
        tree.isShowRoot = false
        tree.root = CheckBoxTreeItem(ScopeNode("root", null, null))
        tree.setCellFactory { CheckBoxTreeCell<ScopeNode>() }
        tree.style = Theme.FONT
        tree.prefHeight = 320.0

        val menu = ContextMenu()
        val miDelete = MenuItem("حذف سند از محدوده جستجو").apply { setOnAction { deleteSelected() } }
        val miUpdate = MenuItem("به روز رسانی فهرست").apply { setOnAction { updateSelected() } }
        val miUpdateAll = MenuItem("به روز رسانی همه فهرست‌ها").apply { setOnAction { onUpdateIndex(null) } }
        val miAdd = MenuItem("افزودن سند جدید").apply { setOnAction { onAddDocument() } }
        menu.items.addAll(miAdd, miUpdate, miUpdateAll, SeparatorMenuItem(), miDelete)
        tree.contextMenu = menu

        children.add(tree)
    }

    private fun topLevelOf(item: TreeItem<ScopeNode>?): TreeItem<ScopeNode>? {
        var cur = item ?: return null
        while (cur.parent != null && cur.parent != tree.root) cur = cur.parent
        return if (cur.parent == tree.root) cur else null
    }

    /** معادل deleteTree: حذف فهرست با تایید کاربر */
    private fun deleteSelected() {
        val top = topLevelOf(tree.selectionModel.selectedItem) ?: return
        val node = top.value
        val r = CustomMessageBox.show(
            ownerWindow(), "حذف سند",
            "آیا می خواهید سند ${node.label} از محدوده جستجو حذف شود؟",
            listOf("بله", "خیر"),
        )
        if (r == 0 && node.savePath != null) {
            indexer.deleteIndexer(node.savePath)
            reload()
            onIndexesChanged()
        }
    }

    private fun updateSelected() {
        val top = topLevelOf(tree.selectionModel.selectedItem) ?: return
        onUpdateIndex(top.value.savePath?.let { indexer.findBySavePath(it) })
    }

    /** معادل addTree + unpackFiles: ساخت زیردرخت پوشه‌ها و فایل‌ها از file_list.txt */
    fun reload() {
        val root = tree.root as CheckBoxTreeItem<ScopeNode>
        root.children.clear()
        for (obj in indexer.allObjects) {
            val topItem = CheckBoxTreeItem(ScopeNode(obj.indexName, obj.savePath, null)).apply { isSelected = true }
            val files = indexer.readFileList(Path.of(obj.savePath))
            val rootPathLen = obj.rootPath.trimEnd('/', '\\').length
            val folderItems = mutableMapOf<String, CheckBoxTreeItem<ScopeNode>>()
            for (f in files) {
                val rel = f.filename.drop(rootPathLen).trimStart('/', '\\')
                val parts = rel.split('/', '\\')
                var parent: CheckBoxTreeItem<ScopeNode> = topItem
                var acc = obj.rootPath.trimEnd('/', '\\')
                for (i in 0 until parts.size - 1) {
                    acc = acc + File.separator + parts[i]
                    parent = folderItems.getOrPut(acc) {
                        CheckBoxTreeItem(ScopeNode(parts[i], null, acc)).also { child ->
                            child.isSelected = true; parent.children.add(child)
                        }
                    }
                }
                val label = "${parts.last()} (${ir.matnyaab.util.PersianUtil.properSize(f.size)})"
                parent.children.add(CheckBoxTreeItem(ScopeNode(label, null, f.filename)).apply { isSelected = true })
            }
            root.children.add(topItem)
        }
    }

    /** معادل GetSelectedDocs: فهرست‌های فعال + مسیرهای حذف‌شده (آیتم‌های تیک‌نخورده) */
    fun getSelectedDocs(): Pair<List<IndexerInfo>, Set<String>> {
        val selected = mutableListOf<IndexerInfo>()
        val masked = mutableSetOf<String>()
        val root = tree.root
        for (top in root.children) {
            val cb = top as CheckBoxTreeItem<ScopeNode>
            val info = indexer.findBySavePath(top.value.savePath ?: "") ?: continue
            if (!cb.isSelected && !cb.isIndeterminate) continue
            selected.add(info)
            collectMasked(cb, masked)
        }
        return Pair(selected, masked)
    }

    private fun collectMasked(item: TreeItem<ScopeNode>, masked: MutableSet<String>) {
        for (child in item.children) {
            val cb = child as CheckBoxTreeItem<ScopeNode>
            if (child.children.isEmpty()) {
                if (!cb.isSelected && child.value.filePath != null) masked.add(child.value.filePath!!)
            } else collectMasked(child, masked)
        }
    }
}
