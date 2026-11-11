package ir.matnyaab.ui

import ir.matnyaab.core.Constants
import ir.matnyaab.core.search.Combine
import ir.matnyaab.core.search.MatchEngine
import ir.matnyaab.core.search.MatchType
import ir.matnyaab.core.search.SearchSpec
import ir.matnyaab.core.search.Spacing
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox

/** داده‌های جستجوی پیشرفته — معادل خروجی advanceSearching */
data class AdvancedSearchParams(
    val allWords: String, val allAccuracy: Boolean,
    val anyWords: String, val anyAccuracy: Boolean,
    val noneWords: String, val noneAccuracy: Boolean,
    val spaceBetween: Int,
)

/** حالت‌های جست‌وجو */
enum class SearchMode(val title: String, val tip: String) {
    PHRASE("واژه / عبارت", "یک واژه یا یک عبارت چندواژه‌ای؛ تعداد موارد هر فایل شمرده می‌شود"),
    WORDS("چند واژه", "چند واژه با هم: همه باشند یا دست‌کم یکی"),
    PHRASES("چند عبارت", "چند عبارت مستقل در یک اجرا؛ هر عبارت در یک سطر"),
    ADVANCED("منطقی", "ترکیب شرط‌ها با AND / OR / NOT و جست‌وجوی فاصله‌دار NEAR/5"),
}

/**
 * پنل جست‌وجوی متن‌یاب ۲٫۳:
 * چهار حالت در یک نوار تکه‌ای، کادر ورودی متناسب با هر حالت، و کارت «شیوهٔ تطبیق» مشترک
 * (واژهٔ کامل/بخشی، فاصله‌گذاری، علائم نگارشی، یکسان‌سازی حروف، جست‌وجوی دقیق).
 * با پرچم FF_SEARCH_V2=0 همان پنل قدیمی نمایش داده می‌شود (بدون پسرفت).
 */
class SearchPanel(
    private val onQuickSearch: (String) -> Unit,
    private val onAdvancedSearch: (AdvancedSearchParams) -> Unit,
    private val settings: ir.matnyaab.core.AppSettingsManager? = null,
    private val onSpecSearch: ((SearchSpec, SearchMode) -> Unit)? = null,
) : VBox(10.0) {

    private val v2 = settings?.get("FF_SEARCH_V2") != "0" && onSpecSearch != null

    // ----- ورودی‌ها
    private val quickInput = TextField().apply { promptText = "واژه یا عبارت؛ مثلاً: تهذیب نفس" }
    private val wordsInput = TextField().apply { promptText = "واژه‌ها با فاصله یا ویرگول: جهاد، نفس، تقوا" }
    private val excludeInput = TextField().apply { promptText = "این واژه‌ها نباشند (اختیاری)" }
    private val phrasesInput = TextArea().apply {
        promptText = "هر عبارت در یک سطر:\nتهذیب نفس\nجهاد اکبر\nمبارزه با نفس"
        prefRowCount = 5; isWrapText = true; styleClass.add("phrase-area")
    }
    private val wordsAll = RadioButton("همه باشند")
    private val wordsAny = RadioButton("دست‌کم یکی")
    private val phrasesAny = RadioButton("هر کدام")
    private val phrasesAll = RadioButton("همه با هم")

    // ----- شیوهٔ تطبیق
    private val strict = CheckBox("جست‌وجوی دقیق (همان شکل، ترتیب و فاصله)")
    private val whole = RadioButton("واژهٔ کامل")
    private val partial = RadioButton("بخشی از واژه")
    private val family = RadioButton("هم‌خانواده").apply { tooltip = Tooltip("واژه‌های هم‌ریشه: علم ← عالم، علوم، تعلیم، معلم…") }
    private val semantic = CheckBox("جست‌وجوی مفهومی (معنایی)").apply {
        tooltip = Tooltip("متن‌های هم‌معنا را هم پیدا می‌کند، حتی اگر واژه‌ها فرق کنند؛ نتیجه‌های مفهومی با نشان «مفهومی» می‌آیند")
    }
    private val semanticHint = Label().apply { isWrapText = true; styleClass.add("hint") }
    private val semanticRow = VBox(2.0, semantic, semanticHint)
    private val synonyms = CheckBox("مترادف‌ها هم جست‌وجو شوند (خودسازی ← تزکیه نفس…)")
    private val spacing = ComboBox<String>().apply {
        items.addAll("یکسان‌سازی فاصله و نیم‌فاصله", "نادیده‌گرفتن فاصله‌های اضافه", "فاصله‌گذاری دقیق")
        maxWidth = Double.MAX_VALUE
    }
    private val punct = CheckBox("بی‌اعتنا به علائم نگارشی میان واژه‌ها")
    private val letters = CheckBox("یکسان‌سازی املا: ی/ک عربی، همزه، آ، ۀ، اِعراب و اعداد")
    private val optSummary = Label().apply { styleClass.addAll("opt-summary"); isWrapText = true }

    // ----- حالت پیشرفته (همان پنل پیشین)
    private val allInput = AdvanceInput("همه کلمات")
    private val anyInput = AdvanceInput("یکی از کلمات")
    private val noneInput = AdvanceInput("هیچکدام از کلمات")
    private val spaceInput = TextField("0").apply {
        prefWidth = 70.0
        textProperty().addListener { _, old, new ->
            if (!new.matches(Regex("\\d{0,4}"))) text = old
            val v = text.toIntOrNull() ?: 0
            if (v > 0) { allInput.accuracy.isSelected = true; allInput.accuracy.isDisable = true }
            else allInput.accuracy.isDisable = false
        }
    }

    private val boolInput = TextField().apply { promptText = "(جهاد OR مبارزه) AND نفس NOT نظامی" }
    private val boolError = Label().apply { isWrapText = true; isVisible = false; isManaged = false; style = "-fx-text-fill: -mx-danger;" }
    private val legacyOpen = CheckBox("فرم کلاسیک (همه / یکی / هیچ‌کدام)")

    private var mode = SearchMode.PHRASE
    private val body = StackPane()
    private val optCard = VBox(6.0)

    init {
        nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
        padding = Insets(10.0)
        style = "-fx-background-color: ${Constants.OPTION_BACKGROUND_COLOR}; -fx-border-color: ${Constants.BORDER_COLOR}; -fx-border-radius: 6px;"
        listOf(quickInput, wordsInput, excludeInput, phrasesInput).forEach { it.style = Theme.FONT }
        if (v2) buildV2() else buildLegacy()
    }

    // ------------------------------------------------------------------ v2

    private fun buildV2() {
        ToggleGroup().also { wordsAll.toggleGroup = it; wordsAny.toggleGroup = it }
        ToggleGroup().also { phrasesAny.toggleGroup = it; phrasesAll.toggleGroup = it }
        ToggleGroup().also { whole.toggleGroup = it; partial.toggleGroup = it; family.toggleGroup = it }
        restore()

        // نوار تکه‌ای حالت‌ها
        val group = ToggleGroup()
        val seg = HBox(2.0).apply { styleClass.add("seg"); alignment = Pos.CENTER }
        SearchMode.values().forEach { m ->
            val b = ToggleButton(m.title).apply {
                styleClass.setAll("seg-btn"); toggleGroup = group; maxWidth = Double.MAX_VALUE
                style = "${Theme.FONT} -fx-font-size: ${Constants.FONT_SIZE_SMALL}px;"
                tooltip = Tooltip(m.tip); userData = m; isSelected = m == mode
            }
            HBox.setHgrow(b, Priority.ALWAYS)
            seg.children.add(b)
        }
        group.selectedToggleProperty().addListener { _, old, new ->
            if (new == null) { old?.isSelected = true; return@addListener }
            mode = new.userData as SearchMode
            settings?.setValue("SEARCH_MODE", mode.name)
            showMode()
        }

        // کارت شیوهٔ تطبیق (تاشو)
        val optBody = VBox(7.0,
            strict,
            HBox(12.0, whole, partial, family).apply { alignment = Pos.CENTER_RIGHT },
            label("فاصله‌ها:"), spacing, punct, letters,
            semanticRow,
            HBox(6.0, synonyms, Hyperlink("فهرست…").apply {
                tooltip = Tooltip("ویرایش فهرست مترادف‌های شخصی (هر سطر یک گروه)")
                setOnAction {
                    runCatching { java.awt.Desktop.getDesktop().open(ir.matnyaab.core.search.Thesaurus.ensureUserFile()) }
                    ir.matnyaab.core.search.Thesaurus.reload()
                }
            }).apply { alignment = Pos.CENTER_RIGHT },
        ).apply { padding = Insets(4.0, 0.0, 0.0, 0.0) }
        val open = settings?.get("SEARCH_OPTS_OPEN") == "1"
        optBody.isVisible = open; optBody.isManaged = open
        val head = Button((if (open) "▾ " else "◂ ") + "شیوهٔ تطبیق").apply {
            styleClass.setAll("opt-head"); maxWidth = Double.MAX_VALUE; alignment = Pos.CENTER_RIGHT
            style = "${Theme.FONT} -fx-font-weight: bold; -fx-font-size: ${Constants.FONT_SIZE_SMALL}px;"
            setOnAction {
                val v = !optBody.isVisible
                optBody.isVisible = v; optBody.isManaged = v
                text = (if (v) "▾ " else "◂ ") + "شیوهٔ تطبیق"
                settings?.setValue("SEARCH_OPTS_OPEN", if (v) "1" else "0")
            }
        }
        optSummary.style = "${Theme.FONT} -fx-font-size: ${Constants.FONT_SIZE_SMALL}px;"
        listOf(strict, whole, partial, family, synonyms, semantic, semanticHint, punct, letters, wordsAll, wordsAny, phrasesAll, phrasesAny).forEach {
            it.style = "${Theme.FONT} -fx-font-size: ${Constants.FONT_SIZE_SMALL}px;"
        }
        spacing.style = Theme.FONT
        optCard.styleClass.add("opt-card")
        optCard.children.setAll(head, optSummary, optBody)
        listOf(strict.selectedProperty(), whole.selectedProperty(), family.selectedProperty(), synonyms.selectedProperty(), punct.selectedProperty(), letters.selectedProperty())
            .forEach { it.addListener { _ -> syncOptions() } }
        spacing.selectionModel.selectedIndexProperty().addListener { _ -> syncOptions() }
        syncOptions()

        quickInput.setOnAction { fire() }
        wordsInput.setOnAction { fire() }
        excludeInput.setOnAction { fire() }
        phrasesInput.setOnKeyPressed { e -> if (e.code == KeyCode.ENTER && e.isShortcutDown) { fire(); e.consume() } }

        children.addAll(seg, body, optCard)
        showMode()
    }

    private fun label(t: String) = Label(t).apply { style = "${Theme.FONT} -fx-font-size: ${Constants.FONT_SIZE_SMALL}px;"; styleClass.add("hint") }

    private fun searchButton(text: String) = Button(text).apply {
        style = Theme.primaryButton(); prefHeight = Constants.OPTION_BUTTONS_HEIGHT; maxWidth = Double.MAX_VALUE
        setOnMouseEntered { style = Theme.primaryButtonHover() }
        setOnMouseExited { style = Theme.primaryButton() }
        setOnAction { fire() }
    }

    private fun quickRow(): Node {
        val btnQuick = Button(Theme.GLYPH_SEARCH).apply {
            style = Theme.awesome(13) + Theme.primaryButton()
            prefHeight = Constants.OPTION_BUTTONS_HEIGHT
            tooltip = Tooltip("جست‌وجو (Enter)")
            setOnAction { if (v2) fire() else fireQuick() }
        }
        quickInput.prefHeight = Constants.OPTION_BUTTONS_HEIGHT
        val btnRecent = Button("🕘").apply {
            style = Theme.addButton(); prefHeight = Constants.OPTION_BUTTONS_HEIGHT
            tooltip = Tooltip("جست‌وجوهای اخیر")
            setOnAction {
                val items = recent().map { q ->
                    MenuItem(q.replace('\n', '؛')).apply { setOnAction { recall(q) } }
                }.ifEmpty { listOf(MenuItem("هنوز جست‌وجویی نکرده‌اید").apply { isDisable = true }) }
                ContextMenu(*items.toTypedArray()).show(this, javafx.geometry.Side.BOTTOM, 0.0, 0.0)
            }
        }
        return HBox(6.0, quickInput, btnRecent, btnQuick).apply {
            alignment = Pos.CENTER
            HBox.setHgrow(quickInput, Priority.ALWAYS)
        }
    }

    private fun recall(q: String) {
        if (!v2) { quickInput.text = q; fireQuick(); return }
        when {
            q.contains('\n') -> { selectMode(SearchMode.PHRASES); phrasesInput.text = q }
            ir.matnyaab.core.search.BoolQuery.looksBoolean(q) -> { selectMode(SearchMode.ADVANCED); boolInput.text = q }
            else -> { selectMode(SearchMode.PHRASE); quickInput.text = q }
        }
        fire()
    }

    private fun selectMode(m: SearchMode) {
        (children.firstOrNull() as? HBox)?.children?.filterIsInstance<ToggleButton>()?.firstOrNull { it.userData == m }?.isSelected = true
        mode = m; showMode()
    }

    private fun showMode() {
        val content: Node = when (mode) {
            SearchMode.PHRASE -> VBox(6.0, quickRow(),
                label("نمونه‌ها: «نفس» · «تهذیب نفس» · برای بخشی از واژه، «بخشی از واژه» را بزنید"))
            SearchMode.WORDS -> VBox(7.0, wordsInput,
                HBox(14.0, wordsAll, wordsAny).apply { alignment = Pos.CENTER_RIGHT },
                excludeInput, searchButton("جست‌وجوی واژه‌ها"))
            SearchMode.PHRASES -> VBox(7.0, phrasesInput,
                HBox(14.0, phrasesAny, phrasesAll).apply { alignment = Pos.CENTER_RIGHT },
                label("Ctrl+Enter = جست‌وجو · نتیجه‌ها با «چند عبارت از چند» مرتب می‌شوند"),
                searchButton("جست‌وجوی همهٔ عبارت‌ها"))
            SearchMode.ADVANCED -> boolBody()
        }
        body.children.setAll(content)
    }

    private fun syncOptions() {
        val s = strict.isSelected
        listOf(whole, partial, family, synonyms, spacing, punct, letters).forEach { it.isDisable = s }
        val parts = if (s) listOf("دقیق: همان شکل، ترتیب و فاصله") else listOfNotNull(
            if (partial.isSelected) "بخشی از واژه" else if (family.isSelected) "هم‌خانواده" else "واژهٔ کامل",
            if (synonyms.isSelected) "با مترادف‌ها" else null,
            if (semantic.isSelected && semanticRow.isVisible) "+ مفهومی" else null,
            listOf("یکسان‌سازی فاصله و نیم‌فاصله", "بی‌اعتنا به فاصلهٔ اضافه", "فاصلهٔ دقیق")[spacing.selectionModel.selectedIndex.coerceAtLeast(0)],
            if (punct.isSelected) "بی‌اعتنا به علائم" else null,
        )
        optSummary.text = parts.joinToString(" · ")
        save()
    }

    private fun spec(phrases: List<String>, combine: Combine, exclude: List<String> = emptyList()) = SearchSpec(
        phrases = phrases, combine = combine, exclude = exclude,
        match = if (partial.isSelected) MatchType.PARTIAL else if (family.isSelected) MatchType.FAMILY else MatchType.WHOLE,
        synonyms = synonyms.isSelected,
        semantic = semantic.isSelected && semanticRow.isVisible,
        spacing = when (spacing.selectionModel.selectedIndex) { 1 -> Spacing.COLLAPSE; 2 -> Spacing.EXACT; else -> Spacing.UNIFY },
        ignorePunct = punct.isSelected, normalizeLetters = letters.isSelected, strict = strict.isSelected,
    )

    private fun fire() {
        val cb = onSpecSearch ?: return
        when (mode) {
            SearchMode.PHRASE -> quickInput.text.trim().takeIf { it.isNotEmpty() }?.let { q ->
                if (ir.matnyaab.core.search.BoolQuery.looksBoolean(q) && runCatching { ir.matnyaab.core.search.BoolQuery.parse(q) }.isSuccess) {
                    remember(q); cb(spec(emptyList(), Combine.ALL).copy(expression = q), SearchMode.ADVANCED)
                } else { remember(q); cb(spec(listOf(q), Combine.ALL), mode) }
            }
            SearchMode.WORDS -> MatchEngine.splitWords(wordsInput.text).takeIf { it.isNotEmpty() }?.let { w ->
                cb(spec(w, if (wordsAny.isSelected) Combine.ANY else Combine.ALL, MatchEngine.splitWords(excludeInput.text)), mode)
            }
            SearchMode.PHRASES -> MatchEngine.splitLines(phrasesInput.text).takeIf { it.isNotEmpty() }?.let { p ->
                remember(p.joinToString("\n")); cb(spec(p, if (phrasesAll.isSelected) Combine.ALL else Combine.ANY), mode)
            }
            SearchMode.ADVANCED -> boolInput.text.trim().takeIf { it.isNotEmpty() }?.let { q ->
                val err = runCatching { ir.matnyaab.core.search.BoolQuery.parse(q) }.exceptionOrNull()
                boolError.text = err?.message ?: ""; boolError.isVisible = err != null; boolError.isManaged = err != null
                if (err == null) { remember(q); cb(spec(emptyList(), Combine.ALL).copy(expression = q), mode) }
            }
        }
    }

    /** حالت منطقی: یک کادر با راهنمای فشرده + فرم کلاسیک تاشو (همان جست‌وجوی پیشرفتهٔ پیشین) */
    private fun boolBody(): VBox {
        boolInput.style = Theme.FONT; boolInput.prefHeight = Constants.OPTION_BUTTONS_HEIGHT
        boolInput.setOnAction { fire() }
        boolError.style = "${Theme.FONT} -fx-text-fill: -mx-danger; -fx-font-size: ${Constants.FONT_SIZE_SMALL}px;"
        fun chip(text: String, insert: String) = Button(text).apply {
            styleClass.setAll("chip"); style = "${Theme.FONT} -fx-font-size: ${Constants.FONT_SIZE_SMALL}px;"
            tooltip = Tooltip("افزودن $insert")
            setOnAction {
                val t = boolInput.text.trimEnd()
                boolInput.text = (if (t.isEmpty()) "" else "$t ") + insert + " "
                boolInput.requestFocus(); boolInput.positionCaret(boolInput.text.length)
            }
        }
        val ops = javafx.scene.layout.FlowPane(6.0, 6.0,
            chip("و (AND)", "AND"), chip("یا (OR)", "OR"), chip("بدون (NOT)", "NOT"),
            chip("نزدیک ۵ واژه", "NEAR/5"), chip("( )", "(")).apply { alignment = Pos.CENTER_RIGHT }
        val legacy = advancedBody().apply { isVisible = legacyOpen.isSelected; isManaged = legacyOpen.isSelected }
        legacyOpen.style = "${Theme.FONT} -fx-font-size: ${Constants.FONT_SIZE_SMALL}px;"
        legacyOpen.setOnAction { legacy.isVisible = legacyOpen.isSelected; legacy.isManaged = legacyOpen.isSelected }
        return VBox(7.0, boolInput, ops, boolError,
            label("واژه‌های پشت‌سرهم یک عبارت‌اند؛ «جهاد NEAR/5 نفس» یعنی حداکثر ۵ واژه فاصله، به هر ترتیب."),
            searchButton("جست‌وجوی منطقی"), Separator(), legacyOpen, legacy)
    }

    /** نمایش گزینهٔ معنایی فقط وقتی بسته نصب و روشن است، همراه با درصد آماده‌سازی */
    fun refreshSemantic() {
        val s = settings ?: return
        val on = ir.matnyaab.core.semantic.SemanticService.active(s)
        semanticRow.isVisible = on; semanticRow.isManaged = on
        val pct = ir.matnyaab.core.semantic.SemanticService.percent()
        val st = ir.matnyaab.core.semantic.SemanticService.status
        semanticHint.text = when {
            !on -> ""
            st.startsWith("خطا") -> st
            ir.matnyaab.core.semantic.SemanticService.total > 0 && pct < 100 -> "آماده‌سازی ${ir.matnyaab.util.PersianUtil.toPersianNumbers(pct)}٪ — روی اسناد آماده کار می‌کند"
            else -> ""
        }
        semanticHint.isVisible = semanticHint.text.isNotEmpty(); semanticHint.isManaged = semanticHint.isVisible
        syncOptions()
    }

    /** جست‌وجوی سریع از بیرون (مثلاً تصاویر معرفی یا افزونهٔ ورد) */
    fun runPhrase(text: String) {
        if (!v2) { quickInput.text = text; fireQuick(); return }
        selectMode(SearchMode.PHRASE); quickInput.text = text; fire()
    }

    fun runPhrases(lines: List<String>) {
        if (!v2) return
        selectMode(SearchMode.PHRASES); phrasesInput.text = lines.joinToString("\n"); fire()
    }

    private fun save() {
        val s = settings ?: return
        s.setValue("SEARCH_OPTS", listOf(
            if (strict.isSelected) 1 else 0, if (partial.isSelected) 1 else if (family.isSelected) 2 else 0, spacing.selectionModel.selectedIndex.coerceAtLeast(0),
            if (punct.isSelected) 1 else 0, if (letters.isSelected) 1 else 0,
            if (wordsAny.isSelected) 1 else 0, if (phrasesAll.isSelected) 1 else 0, if (synonyms.isSelected) 1 else 0,
            if (semantic.isSelected) 1 else 0,
        ).joinToString(","))
    }

    private fun restore() {
        val o = (settings?.get("SEARCH_OPTS") ?: "").split(',').mapNotNull { it.toIntOrNull() }
        fun f(i: Int, d: Int) = o.getOrElse(i) { d }
        strict.isSelected = f(0, 0) == 1
        (when (f(1, 0)) { 1 -> partial; 2 -> family; else -> whole }).isSelected = true
        synonyms.isSelected = f(7, 0) == 1
        semantic.isSelected = f(8, 1) == 1
        spacing.selectionModel.select(f(2, 0).coerceIn(0, 2))
        punct.isSelected = f(3, 1) == 1
        letters.isSelected = f(4, 1) == 1
        (if (f(5, 0) == 1) wordsAny else wordsAll).isSelected = true
        (if (f(6, 0) == 1) phrasesAll else phrasesAny).isSelected = true
        mode = runCatching { SearchMode.valueOf(settings?.get("SEARCH_MODE") ?: "") }.getOrDefault(SearchMode.PHRASE)
    }

    // ------------------------------------------------------------------ قدیمی / پیشرفته

    private fun advancedBody(): VBox {
        val lblSpace = Label("حداکثر فاصله بین کلمات").apply {
            style = "${Theme.FONT} -fx-font-size: ${Constants.FONT_SIZE_SMALL}px;"
            tooltip = Tooltip("حداکثر تعداد کلمات مجاز بین کلمات جستجو (مخصوص همه کلمات)")
        }
        val spaceRow = HBox(8.0, lblSpace, spaceInput).apply { alignment = Pos.CENTER_RIGHT }
        val btnSearch = Button("جستجو کن").apply {
            style = Theme.primaryButton()
            prefHeight = Constants.OPTION_BUTTONS_HEIGHT
            maxWidth = Double.MAX_VALUE
            setOnMouseEntered { style = Theme.primaryButtonHover() }
            setOnMouseExited { style = Theme.primaryButton() }
            setOnAction { fireAdvanced() }
        }
        return VBox(8.0, allInput, anyInput, noneInput, spaceRow, btnSearch)
    }

    private fun buildLegacy() {
        quickInput.setOnAction { fireQuick() }
        children.addAll(quickRow(), Separator(), advancedBody())
    }

    fun focusQuick() {
        if (v2 && mode != SearchMode.PHRASE) selectMode(SearchMode.PHRASE)
        quickInput.requestFocus(); quickInput.selectAll()
    }

    private fun recent(): List<String> =
        settings?.get("RECENT_SEARCHES")?.split('\u001F')?.filter { it.isNotBlank() } ?: emptyList()

    private fun remember(q: String) {
        val s = settings ?: return
        s.setValue("RECENT_SEARCHES", (listOf(q) + recent().filter { it != q }).take(15).joinToString("\u001F"))
    }

    private fun fireQuick() {
        val q = quickInput.text.trim()
        if (q.isNotBlank()) { remember(q); onQuickSearch(q) }
    }

    private fun fireAdvanced() {
        onAdvancedSearch(
            AdvancedSearchParams(
                allInput.input.text.trim(), allInput.accuracy.isSelected,
                anyInput.input.text.trim(), anyInput.accuracy.isSelected,
                noneInput.input.text.trim(), noneInput.accuracy.isSelected,
                spaceInput.text.toIntOrNull() ?: 0,
            )
        )
    }

    /** معادل advanceInputWidget: فیلد متن با برچسب شناور و چک‌باکس «دقیق» (پیش‌فرض فعال) */
    class AdvanceInput(labelText: String) : VBox(2.0) {
        val input = TextField().apply { promptText = labelText; style = Theme.FONT }
        val accuracy = CheckBox("دقیق").apply {
            isSelected = true
            style = "${Theme.FONT} -fx-font-size: ${Constants.FONT_SIZE_SMALL}px;"
        }
        init {
            val row = HBox(8.0, input, accuracy).apply {
                alignment = Pos.CENTER_RIGHT
                HBox.setHgrow(input, Priority.ALWAYS)
            }
            children.add(row)
        }
    }
}
