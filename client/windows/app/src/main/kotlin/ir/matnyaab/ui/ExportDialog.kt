package ir.matnyaab.ui

import ir.matnyaab.core.AppSettingsManager
import ir.matnyaab.core.research.ResultExporter
import ir.matnyaab.core.research.ResultExporter.Group
import ir.matnyaab.core.research.ResultExporter.Order
import ir.matnyaab.core.research.ResultExporter.Unit
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Stage

/** گفت‌وگوی «خروجی با متن و بافت»: یک صفحه، پیش‌فرض‌های خوب، و به خاطر سپردن انتخاب‌ها. */
object ExportDialog {
    data class Choice(val format: String, val options: ResultExporter.Options)

    private val PRESETS = mapOf(
        Unit.PARAGRAPH to listOf(0), Unit.PARAGRAPHS to listOf(1, 2, 3),
        Unit.WORDS to listOf(50, 100, 200), Unit.CHARS to listOf(500, 1000, 2000),
    )

    fun ask(owner: Stage, settings: AppSettingsManager, hasPhrases: Boolean): Choice? {
        val saved = settings.get("EXPORT_OPTS").split(',')
        fun s(i: Int, d: String) = saved.getOrNull(i)?.takeIf { it.isNotBlank() } ?: d

        val word = RadioButton("Word (برای خواندن و چاپ)"); val excel = RadioButton("Excel (برای دسته‌بندی و تحلیل)"); val csv = RadioButton("CSV (فقط فهرست سندها)")
        ToggleGroup().also { g -> listOf(word, excel, csv).forEach { it.toggleGroup = g } }
        when (s(0, "docx")) { "xlsx" -> excel; "csv" -> csv; else -> word }.isSelected = true

        val unit = ComboBox<Unit>().apply { items.setAll(*Unit.values()); value = runCatching { Unit.valueOf(s(1, "PARAGRAPH")) }.getOrDefault(Unit.PARAGRAPH)
            setCellFactory { object : ListCell<Unit>() { override fun updateItem(u: Unit?, e: Boolean) { super.updateItem(u, e); text = u?.title } } }; buttonCell = cellFactory.call(null) }
        val before = Spinner<Int>(0, 5000, s(2, "100").toIntOrNull() ?: 100).apply { isEditable = true; prefWidth = 90.0 }
        val after = Spinner<Int>(0, 5000, s(3, "100").toIntOrNull() ?: 100).apply { isEditable = true; prefWidth = 90.0 }
        val quick = HBox(4.0)
        val amountRow = HBox(8.0, Label("پیش:"), before, Label("پس:"), after, quick).apply { alignment = Pos.CENTER_RIGHT }
        fun syncUnit() {
            val u = unit.value
            amountRow.isVisible = u != Unit.PARAGRAPH; amountRow.isManaged = amountRow.isVisible
            quick.children.setAll(PRESETS[u].orEmpty().filter { it > 0 }.map { n ->
                Button(ir.matnyaab.util.PersianUtil.toPersianNumbers(n)).apply { styleClass.add("chip"); setOnAction { before.valueFactory.value = n; after.valueFactory.value = n } }
            })
            if (u == Unit.PARAGRAPHS && before.value > 10) { before.valueFactory.value = 1; after.valueFactory.value = 1 }
            if ((u == Unit.WORDS || u == Unit.CHARS) && before.value < 10) { val d = if (u == Unit.WORDS) 100 else 1000; before.valueFactory.value = d; after.valueFactory.value = d }
        }
        unit.setOnAction { syncUnit() }; syncUnit()

        val highlight = CheckBox("برجسته‌سازی عبارت جست‌وجو").apply { isSelected = s(4, "1") == "1" }
        val meta = CheckBox("مشخصات سند: نام، مسیر، صفحه و شمارهٔ بند").apply { isSelected = s(5, "1") == "1" }
        val info = CheckBox("اطلاعات تطبیق: نوع (لفظی/مفهومی) و امتیاز ارتباط").apply { isSelected = s(6, "1") == "1" }
        val order = ComboBox<Order>().apply { items.setAll(*Order.values()); value = runCatching { Order.valueOf(s(7, "RELEVANCE")) }.getOrDefault(Order.RELEVANCE)
            setCellFactory { object : ListCell<Order>() { override fun updateItem(u: Order?, e: Boolean) { super.updateItem(u, e); text = u?.title } } }; buttonCell = cellFactory.call(null) }
        val group = ComboBox<Group>().apply { items.setAll(*Group.values()); value = runCatching { Group.valueOf(s(8, "DOCUMENT")) }.getOrDefault(Group.DOCUMENT)
            setCellFactory { object : ListCell<Group>() { override fun updateItem(u: Group?, e: Boolean) { super.updateItem(u, e); text = u?.title } } }; buttonCell = cellFactory.call(null)
            isDisable = !hasPhrases }
        val maxPer = Spinner<Int>(1, 500, s(9, "50").toIntOrNull() ?: 50).apply { isEditable = true; prefWidth = 90.0 }

        val textOpts = VBox(8.0)
        val grid = GridPane().apply {
            hgap = 10.0; vgap = 8.0; var r = 0
            fun row(l: String, n: javafx.scene.Node) { add(Label(l), 0, r); add(n, 1, r); r++ }
            row("محدودهٔ متن:", unit); row("", amountRow); row("ترتیب:", order); row("دسته‌بندی:", group); row("بیشینه در هر سند:", maxPer)
        }
        textOpts.children.addAll(grid, highlight, meta, info)
        fun syncFormat() { textOpts.isDisable = csv.isSelected; group.isDisable = !hasPhrases || !word.isSelected }
        listOf(word, excel, csv).forEach { it.setOnAction { syncFormat() } }; syncFormat()

        val content = VBox(10.0,
            Label("قالب خروجی").apply { styleClass.add("card-title") }, VBox(4.0, word, excel, csv),
            Separator(), Label("متن همراه هر نتیجه").apply { styleClass.add("card-title") }, textOpts,
            Label("نکته: شمارهٔ صفحه برای PDFهایی نمایش داده می‌شود که در نسخهٔ ۳٫۱ به بعد فهرست شده‌اند (برای اسناد قدیمی‌تر، «به‌روزرسانی فهرست» را بزنید).")
                .apply { isWrapText = true; styleClass.add("muted"); maxWidth = 460.0 },
        ).apply { padding = Insets(14.0); nodeOrientation = NodeOrientation.RIGHT_TO_LEFT; style = Theme.FONT; prefWidth = 500.0 }

        val ok = ButtonType("ذخیرهٔ خروجی", ButtonBar.ButtonData.OK_DONE)
        val dlg = Dialog<ButtonType>().apply {
            initOwner(owner); title = "خروجی نتایج با متن و بافت"; headerText = null
            dialogPane.content = content; dialogPane.nodeOrientation = NodeOrientation.RIGHT_TO_LEFT; dialogPane.style = Theme.FONT
            dialogPane.buttonTypes.addAll(ok, ButtonType("انصراف", ButtonBar.ButtonData.CANCEL_CLOSE))
        }
        if (dlg.showAndWait().orElse(null) != ok) return null
        val fmt = if (excel.isSelected) "xlsx" else if (csv.isSelected) "csv" else "docx"
        val o = ResultExporter.Options(unit.value, before.value, after.value, highlight.isSelected, meta.isSelected, info.isSelected, order.value, group.value, maxPer.value)
        settings.setValue("EXPORT_OPTS", listOf(fmt, o.unit.name, o.before, o.after, if (o.highlight) 1 else 0, if (o.meta) 1 else 0, if (o.matchInfo) 1 else 0, o.order.name, o.group.name, o.maxPerDoc).joinToString(","))
        return Choice(fmt, o)
    }
}
