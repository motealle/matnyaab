package ir.matnyaab.ui

import ir.matnyaab.core.ai.AiClient
import ir.matnyaab.core.ai.AiConfig
import ir.matnyaab.core.ai.AiProvider
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.stage.Modality
import javafx.stage.Stage
import java.awt.Desktop

/** تنظیم سرویس‌های هوش مصنوعی با کلید خود کاربر (مشترک با افزونهٔ ورد در ai.json، کلید با DPAPI رمز می‌شود). */
class AiSettingsDialog(private val owner: Stage) {

    fun show() {
        val st = Stage().apply { initOwner(owner); initModality(Modality.WINDOW_MODAL); title = "سرویس‌های هوش مصنوعی" }
        val list = ListView<String>()
        fun refresh() {
            val d = AiConfig.load()
            list.items.setAll(d.providers.map { (if (it.Id == d.activeId) "● " else "○ ") + it.Title + "  ·  " + it.Model })
        }
        refresh()

        val preset = ComboBox<String>().apply { items.setAll(AiConfig.PRESETS.map { it.title }); selectionModel.select(0); maxWidth = Double.MAX_VALUE }
        val hint = Label().apply { isWrapText = true; styleClass.add("muted"); style = Theme.FONT }
        val site = Hyperlink("دریافت کلید")
        val base = TextField(); val model = ComboBox<String>().apply { isEditable = true; maxWidth = Double.MAX_VALUE }
        val key = PasswordField().apply { promptText = "کلید API (فقط روی همین رایانه و رمزشده نگه داشته می‌شود)" }
        val status = Label().apply { isWrapText = true; style = Theme.FONT }
        fun sync() {
            val p = AiConfig.PRESETS[maxOf(0, preset.selectionModel.selectedIndex)]
            base.text = p.baseUrl; model.items.setAll(p.models); model.value = p.models.firstOrNull() ?: ""
            hint.text = p.hint; site.isVisible = p.site.isNotBlank(); site.setOnAction { runCatching { Desktop.getDesktop().browse(java.net.URI(p.site)) } }
            key.isDisable = p.kind == "openai" && p.baseUrl.contains("localhost")
        }
        preset.setOnAction { sync() }; sync()

        fun draft() = AiConfig.PRESETS[maxOf(0, preset.selectionModel.selectedIndex)].let { p ->
            AiProvider(Title = p.title, Kind = p.kind, BaseUrl = base.text.trim(), Model = (model.editor.text ?: model.value ?: "").trim())
        }
        val test = Button("آزمون اتصال").apply { style = Theme.addButton() }
        val add = Button("افزودن و فعال‌سازی").apply { style = Theme.primaryButton() }
        test.setOnAction {
            val p = draft(); val k = key.text
            status.text = "در حال آزمون…"; test.isDisable = true
            Thread {
                val r = runCatching { AiClient.complete(p, "Reply with one short Persian word.", "سلام", 20, 0.0, keyOverride = k) }
                Platform.runLater { test.isDisable = false; status.text = r.fold({ "✓ اتصال برقرار است: ${it.take(40)}" }, { "✗ ${it.message}" }) }
            }.apply { isDaemon = true }.start()
        }
        add.setOnAction {
            val p = draft()
            if (p.Model.isBlank() || p.BaseUrl.isBlank()) { status.text = "نشانی و مدل را وارد کنید."; return@setOnAction }
            AiConfig.add(p, key.text); key.clear(); refresh(); status.text = "✓ افزوده شد و فعال است."
        }
        val use = Button("فعال کن").apply { style = Theme.addButton(); setOnAction { AiConfig.load().providers.getOrNull(list.selectionModel.selectedIndex)?.let { AiConfig.setActive(it.Id); refresh() } } }
        val del = Button("حذف").apply { style = Theme.addButton(); setOnAction { AiConfig.load().providers.getOrNull(list.selectionModel.selectedIndex)?.let { AiConfig.remove(it.Id); refresh() } } }

        val grid = GridPane().apply {
            hgap = 10.0; vgap = 8.0
            columnConstraints.addAll(ColumnConstraints(90.0), ColumnConstraints().apply { hgrow = Priority.ALWAYS })
            var r = 0
            fun row(l: String, n: javafx.scene.Node) { add(Label(l).apply { style = Theme.FONT }, 0, r); add(n, 1, r); r++ }
            row("سرویس:", preset); row("", HBox(8.0, hint, site).apply { alignment = Pos.CENTER_RIGHT })
            row("نشانی:", base); row("مدل:", model); row("کلید:", key)
        }
        val root = VBox(10.0,
            Label("سرویس‌های شما").apply { styleClass.add("card-title"); style = Theme.FONT }, list.apply { prefHeight = 120.0 },
            HBox(8.0, use, del).apply { alignment = Pos.CENTER_RIGHT },
            Separator(),
            Label("افزودن سرویس").apply { styleClass.add("card-title"); style = Theme.FONT }, grid,
            HBox(8.0, add, test).apply { alignment = Pos.CENTER_RIGHT }, status,
            Label("هزینه و شرایط استفاده از هر سرویس بر عهدهٔ کاربر و طبق قوانین همان سرویس است. متن‌یاب کلید شما را جایی نمی‌فرستد و فقط بخش‌های لازم از متن را به سرویس انتخابی شما می‌دهد. " +
                "این تنظیمات با افزونهٔ ورد متن‌یاب مشترک است.").apply { isWrapText = true; styleClass.add("muted"); style = Theme.FONT + "-fx-font-size: 11px;" },
        ).apply { padding = Insets(16.0); nodeOrientation = NodeOrientation.RIGHT_TO_LEFT; style = Theme.FONT; prefWidth = 560.0 }
        st.scene = Scene(root)
        Theme.install(st.scene, st)
        st.showAndWait()
    }
}
