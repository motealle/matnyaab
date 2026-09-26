package ir.matnyaab.ui

import ir.matnyaab.core.Constants
import ir.matnyaab.core.AppSettingsManager
import ir.matnyaab.util.PersianUtil
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.concurrent.Worker
import javafx.scene.web.WebView

/**
 * معادل بخش پیش‌نمایش matnyaab.py:
 * WebView با متن هایلایت‌شده، ناوبری بین موارد (بالا/پایین + شمارنده فارسی)،
 * دکمه‌های تراز راست/وسط/چپ، فونت، زوم و جستجوی محلی، و پیام خطا (فایل حذف/تغییر کرده)
 *
 * منطق ناوبری عینا مطابق نسخه اصلی: اسپن انتخاب‌شده با رنگ فیروزه‌ای جایگزین می‌شود
 * و اسکرول به وسط صفحه می‌رود.
 */
class PreviewPane(private val settings: AppSettingsManager) : VBox() {

    val webView = WebView()
    private val lblError = Label().apply {
        style = "${Theme.FONT} -fx-background-color: ${Constants.PREVIEW_ERROR_COLOR}; -fx-padding: 6;"
        maxWidth = Double.MAX_VALUE
        isVisible = false; isManaged = false
    }
    private val counter = Label("۰/۰").apply { style = Theme.FONT }

    private var previewHtml = ""           // متن هایلایت‌شده سراسری فعلی
    private var globalCount = 0
    private var currentIndex = 0            // مورد فعلی (1..count)، 0 = هیچ
    private var localMode = false
    private var localCount = 0
    private var localIndex = 0
    private var localWord = ""
    private var helpMode = false
    private var fontFamily = settings.get("PREVIEW_FONT_FAMILY")
    private var fontSize = settings.get("PREVIEW_FONT_SIZE").toIntOrNull() ?: Constants.PREVIEW_DEFAULT_FONT_SIZE
    private var align = Constants.INITIAL_ALIGN

    private val btnAlignRight = glyphBtn(Theme.GLYPH_ALIGN_RIGHT, "تراز راست") { setAlign("right") }
    private val btnAlignCenter = glyphBtn(Theme.GLYPH_ALIGN_CENTER, "تراز وسط") { setAlign("center") }
    private val btnAlignLeft = glyphBtn(Theme.GLYPH_ALIGN_LEFT, "تراز چپ") { setAlign("left") }
    private var searchBar: LocalSearchBar? = null

    init {
        nodeOrientation = NodeOrientation.RIGHT_TO_LEFT

        val btnUp = glyphBtn(Theme.GLYPH_UP, "مورد قبلی") { previousItem() }
        val btnDown = glyphBtn(Theme.GLYPH_DOWN, "مورد بعدی") { nextItem() }
        val btnFont = glyphBtn(Theme.GLYPH_FONT, "فونت") { showFontDialog() }
        val btnZoomIn = glyphBtn(Theme.GLYPH_ZOOM_IN, "بزرگ‌نمایی") { zoom(1) }
        val btnZoomOut = glyphBtn(Theme.GLYPH_ZOOM_OUT, "کوچک‌نمایی") { zoom(-1) }
        val btnFind = glyphBtn(Theme.GLYPH_SEARCH, "جستجو در متن") { showLocalSearch() }

        val spacer = Region().also { HBox.setHgrow(it, Priority.ALWAYS) }
        val toolbar = HBox(4.0, btnDown, btnUp, counter, spacer,
            btnFind, btnZoomOut, btnZoomIn, btnFont, btnAlignLeft, btnAlignCenter, btnAlignRight).apply {
            alignment = Pos.CENTER_RIGHT
            style = "-fx-background-color: ${Constants.SURFACE_COLOR}; -fx-background-radius: 8 8 0 0; -fx-padding: 4; -fx-border-color: ${Constants.BORDER_COLOR}; -fx-border-width: 0 0 1 0;"
        }

        VBox.setVgrow(webView, Priority.ALWAYS)
        children.addAll(toolbar, lblError, webView)
        setAlignButtons()

        // بارگذاری WebView غیرهمزمان است؛ پس از پایان لود، خودکار به مورد فعلی/اول اسکرول می‌شود
        webView.engine.loadWorker.stateProperty().addListener { _, _, st ->
            if (st == Worker.State.SUCCEEDED && !helpMode) {
                val idx = if (localMode) localIndex else currentIndex
                val total = if (localMode) localCount else globalCount
                if (total > 0) selectItem(if (idx == 0) 1 else idx)
            }
        }
        render()
    }

    private fun glyphBtn(g: String, tip: String, action: () -> Unit) = Button(g).apply {
        style = Theme.awesome(12) + "-fx-background-color: transparent; -fx-cursor: hand;"
        tooltip = Tooltip(tip)
        setOnAction { action() }
    }

    // ------------------------------------------------------------------ نمایش محتوا

    /** معادل showPreview: متن هایلایت‌شده + تعداد موارد + پیام خطای اختیاری */
    fun showContent(html: String, count: Int, error: String? = null) {
        helpMode = false
        // حذف شَدّه مطابق نسخه اصلی و تبدیل خط جدید به <br>
        previewHtml = html.replace("\u0651", "").replace("\n", "<br>")
        globalCount = count
        currentIndex = 0
        localMode = false
        lblError.text = error ?: ""
        lblError.isVisible = error != null
        lblError.isManaged = error != null
        updateCounter()
        render()
    }

    fun clear() = showContent("", 0)

    private fun updateCounter() {
        val idx = if (localMode) localIndex else currentIndex
        val total = if (localMode) localCount else globalCount
        counter.text = PersianUtil.toPersianNumbers("$idx/$total")
    }

    private fun render() {
        val dir = if (align == "left") "ltr" else "rtl"
        val body = """
            <html><head><meta charset='utf-8'>
            <style>
              body { font-family: '$fontFamily', '${Constants.PREVIEW_DEFAULT_FONT}', '${Constants.GENERAL_FONT_NAME_PERSIAN}', 'Tahoma', sans-serif; font-size: ${fontSize}px; line-height: 2.1; direction: $dir; text-align: $align; padding: 8px; }
            </style>
            <script>
              function scrollToItem(i) {
                var els = document.getElementsByClassName('hl');
                if (i >= 1 && i <= els.length) {
                  els[i-1].scrollIntoView({block: 'center'});
                }
              }
            </script>
            </head><body>${tagHighlights(previewHtml)}</body></html>
        """.trimIndent()
        webView.engine.loadContent(body)
    }

    /** افزودن کلاس hl به اسپن‌های هایلایت برای ناوبری جاوااسکریپتی */
    private fun tagHighlights(html: String): String =
        html.replace("<span style=\" background-color:", "<span class=\"hl\" style=\" background-color:")

    // ------------------------------------------------------------------ ناوبری موارد (معادل F_GlobalSearchNext/PreviousItem)

    private fun selectItem(newIndex: Int) {
        val total = if (localMode) localCount else globalCount
        if (total == 0) return
        val color = if (localMode) Constants.LOCAL_SEARCH_HIGHLIGHT_COLOR else Constants.GLOBAL_SEARCH_HIGHLIGHT_COLOR
        val idx = ((newIndex - 1).mod(total)) + 1
        if (localMode) localIndex = idx else currentIndex = idx
        // رنگ مورد انتخاب‌شده را در DOM عوض می‌کنیم و اسکرول به وسط (معادل منطق کرسر نسخه اصلی)
        webView.engine.executeScript("""
            (function(){
              var els = document.getElementsByClassName('hl');
              for (var i = 0; i < els.length; i++) els[i].style.backgroundColor = '$color';
              if (els.length >= $idx) {
                els[$idx-1].style.backgroundColor = '${Constants.SELECTED_SEARCH_HIGHLIGHT_COLOR}';
                els[$idx-1].scrollIntoView({block: 'center'});
              }
            })();
        """.trimIndent())
        updateCounter()
    }

    fun nextItem() { selectItem((if (localMode) localIndex else currentIndex) + 1) }
    fun previousItem() { selectItem((if (localMode) localIndex else currentIndex) - 1) }

    // ------------------------------------------------------------------ جستجوی محلی (معادل F_Find)

    private fun showLocalSearch() {
        val p = webView.localToScreen(0.0, 0.0) ?: return
        searchBar = LocalSearchBar(scene?.window,
            onFind = { word -> localFind(word) },
            onNext = { nextItem(); counter.text },
            onPrev = { previousItem(); counter.text },
            onClose = { clearLocalSearch() },
        ).also { it.showAt(p.x + 20, p.y + 8) }
    }

    /** هایلایت سبز واژه محلی روی متن فعلی؛ خروجی تعداد موارد */
    private fun localFind(word: String): Int {
        localWord = word
        if (word.isBlank()) { clearLocalSearch(); return 0 }
        localMode = true
        localIndex = 0
        val result = webView.engine.executeScript("""
            (function(){
              // پاک‌سازی هایلایت محلی قبلی
              var olds = Array.prototype.slice.call(document.querySelectorAll('span.lhl'));
              olds.forEach(function(s){ s.replaceWith(document.createTextNode(s.textContent)); });
              document.body.normalize();
              var target = ${LuceneJsEscape.toJsString(word)};
              var count = 0;
              var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
              var nodes = [];
              while (walker.nextNode()) nodes.push(walker.currentNode);
              nodes.forEach(function(node){
                var text = node.nodeValue;
                var idx = text.indexOf(target);
                if (idx < 0) return;
                var frag = document.createDocumentFragment();
                var rest = text;
                while (idx >= 0) {
                  frag.appendChild(document.createTextNode(rest.substring(0, idx)));
                  var span = document.createElement('span');
                  span.className = 'hl lhl';
                  span.style.backgroundColor = '${Constants.LOCAL_SEARCH_HIGHLIGHT_COLOR}';
                  span.textContent = target;
                  frag.appendChild(span);
                  rest = rest.substring(idx + target.length);
                  idx = rest.indexOf(target);
                  count++;
                }
                frag.appendChild(document.createTextNode(rest));
                node.parentNode.replaceChild(frag, node);
              });
              return count;
            })();
        """.trimIndent())
        localCount = (result as? Number)?.toInt() ?: 0
        updateCounter()
        return localCount
    }

    /** معادل clearLoclSearch: بازگشت به هایلایت سراسری */
    private fun clearLocalSearch() {
        localMode = false; localWord = ""; localCount = 0; localIndex = 0
        render()
        updateCounter()
    }

    // ------------------------------------------------------------------ تراز، فونت، زوم

    private fun setAlign(a: String) { align = a; setAlignButtons(); render() }

    private fun setAlignButtons() {
        listOf(btnAlignRight to "right", btnAlignCenter to "center", btnAlignLeft to "left").forEach { (b, a) ->
            b.style = Theme.awesome(12) + "-fx-background-color: transparent; -fx-cursor: hand;" +
                if (align == a) "-fx-text-fill: red;" else ""
        }
    }

    private fun showFontDialog() {
        val p = webView.localToScreen(0.0, 0.0) ?: return
        ChooseFontDialog(scene?.window, settings) { family ->
            fontFamily = family; render()
        }.showAt(p.x + 40, p.y + 8)
    }

    private fun zoom(delta: Int) {
        fontSize = (fontSize + delta).coerceIn(6, 72)
        settings.setValue("PREVIEW_FONT_SIZE", fontSize)
        render()
    }

    /** نمایش راهنمما — معادل showHelp */
    fun showHelp() {
        val url = javaClass.getResource("/help/matnyaab_help.htm")
        if (url != null) { helpMode = true; align = "left"; setAlignButtons(); webView.engine.load(url.toExternalForm()) }
    }
}

/** کمکی: تبدیل امن رشته کاتلین به لیترال جاوااسکریپت */
object LuceneJsEscape {
    fun toJsString(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\""
}
