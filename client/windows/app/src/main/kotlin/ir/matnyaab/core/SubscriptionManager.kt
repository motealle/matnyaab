package ir.matnyaab.core

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * اشتراک اعتمادمحور متن‌یاب:
 * - «کد اشتراک» از سایت (هماهنگ با پنل مدیریت: روزشمار یکسان، پیام‌های مدیر)
 * - خوداظهاری محلی بدون کد (فقط روی همین رایانه)
 * - «سه روز مهمانی» که هر بار قابل تکرار است
 * همه‌چیز در settings.ini ذخیره می‌شود و بدون اینترنت هم کار می‌کند.
 */
class SubscriptionManager(private val settings: AppSettingsManager, private val systemId: String) {

    data class Plan(val id: String, val title: String, val days: Int, val price: Int)
    data class Card(val number: String, val holder: String)
    data class Notice(val id: String, val title: String, val body: String, val level: String, val date: String)
    data class Config(
        val plans: List<Plan>, val cards: List<Card>, val trialEnabled: Boolean,
        val trialDays: Int, val reminderDays: Int, val subscribeUrl: String, val contactUrl: String,
    )

    enum class Kind { NONE, TRIAL, LOCAL, CODE }
    enum class Mood { GOOD, SOON, ENDED, NONE }

    data class State(
        val kind: Kind, val planTitle: String, val start: LocalDate?, val end: LocalDate?,
        val code: String, val holder: String,
    ) {
        fun today(): LocalDate = LocalDate.now()
        val daysLeft: Long get() = end?.let { maxOf(0, ChronoUnit.DAYS.between(today(), it)) } ?: 0
        val daysTotal: Long get() = if (start != null && end != null) maxOf(1, ChronoUnit.DAYS.between(start, end)) else 1
        val dayNumber: Long get() = start?.let { minOf(daysTotal, maxOf(1, ChronoUnit.DAYS.between(it, today()) + 1)) } ?: 0
        val active: Boolean get() = kind != Kind.NONE && daysLeft > 0
    }

    companion object {
        val DEFAULT_CONFIG = Config(
            plans = listOf(
                Plan("m1", "ماهانه", 30, 89000),
                Plan("m6", "شش‌ماهه", 180, 299000),
                Plan("m12", "یک‌ساله", 365, 490000),
            ),
            cards = listOf(
                Card("6037997770259241", "محمد کاظم رحیمی"),
                Card("5041721202227469", "محمد رمضانی"),
            ),
            trialEnabled = true, trialDays = 3, reminderDays = 7,
            subscribeUrl = Constants.BASE_URL + "/subscribe",
            contactUrl = Constants.BASE_URL + "/#contact",
        )
        private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
        private val gson = Gson()

        fun normalizeCode(raw: String): String {
            val c = PersianDigits.latin(raw).uppercase().filter { it.isLetterOrDigit() }
            return if (c.startsWith("MT") && c.length == 10) "MT-${c.substring(2, 6)}-${c.substring(6, 10)}" else ""
        }

        fun money(n: Int): String = ir.matnyaab.util.PersianUtil.toPersianNumbers(String.format(java.util.Locale.US, "%,d", n)).replace(',', '٬')
    }

    @Volatile var config: Config = loadCachedConfig(); private set

    val state: State get() {
        val kind = runCatching { Kind.valueOf(settings.get("SUB_KIND")) }.getOrDefault(Kind.NONE)
        fun d(k: String) = settings.get(k).takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return State(kind, settings.get("SUB_PLAN"), d("SUB_START"), d("SUB_END"), settings.get("SUB_CODE"), settings.get("SUB_HOLDER"))
    }

    /** حال‌وهوای وضعیت برای رنگ نشان: سبز، قرمز در روزهای پایانی، یا پایان‌یافته */
    fun mood(s: State = state): Mood = when {
        s.kind == Kind.NONE -> Mood.NONE
        !s.active -> Mood.ENDED
        s.kind == Kind.TRIAL -> if (s.daysLeft <= 1) Mood.SOON else Mood.GOOD
        s.daysLeft <= config.reminderDays -> Mood.SOON
        else -> Mood.GOOD
    }

    /** حالت گزارش‌شده به سرور برای آمار پنل مدیریت */
    fun mode(): String = state.let { s ->
        when { s.kind == Kind.NONE -> "none"; !s.active -> "expired"; s.kind == Kind.TRIAL -> "trial"; else -> s.kind.name.lowercase() }
    }

    private fun save(kind: Kind, plan: String, start: LocalDate, end: LocalDate, code: String = "", holder: String = "") {
        settings.setValue("SUB_KIND", kind.name)
        settings.setValue("SUB_PLAN", plan)
        settings.setValue("SUB_START", start.toString())
        settings.setValue("SUB_END", end.toString())
        settings.setValue("SUB_CODE", code)
        settings.setValue("SUB_HOLDER", holder)
    }

    fun startTrial() {
        val today = LocalDate.now()
        val keepCode = state.code
        save(Kind.TRIAL, "مهمانی", today, today.plusDays(config.trialDays.toLong()), keepCode, state.holder)
        settings.setValue("TRIAL_COUNT", (settings.get("TRIAL_COUNT").toIntOrNull() ?: 0) + 1)
    }

    fun declareLocal(plan: Plan) {
        val today = LocalDate.now()
        save(Kind.LOCAL, plan.title, today, today.plusDays(plan.days.toLong()))
    }

    fun subscribeUrl(): String {
        val code = state.code
        return config.subscribeUrl + if (code.isNotBlank()) "?code=$code" else ""
    }

    // ------------------------------------------------------------ شبکه

    /** فعال‌سازی کد؛ در صورت خطا متن خطا برگردانده می‌شود */
    fun activate(rawCode: String): Result<State> {
        val code = normalizeCode(rawCode)
        if (code.isBlank()) return Result.failure(IllegalArgumentException("کد اشتراک به شکل MT-XXXX-XXXX است؛ لطفاً دوباره بررسی کنید."))
        return try {
            val json = post("activate", code)
            if (json == null) Result.failure(IllegalStateException("اتصال به سایت متن‌یاب برقرار نشد. اینترنت را بررسی کنید و دوباره تلاش کنید."))
            else if (!json.get("ok").asBoolean) Result.failure(IllegalStateException(json.get("error")?.asString ?: "کد پیدا نشد."))
            else { apply(json); Result.success(state) }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("اتصال به سایت متن‌یاب برقرار نشد. اینترنت را بررسی کنید و دوباره تلاش کنید."))
        }
    }

    /** هماهنگی دوره‌ای با سایت؛ پیام‌های تازه برگردانده می‌شوند */
    fun sync(): List<Notice> = try {
        val json = post("ping", state.code.takeIf { state.kind == Kind.CODE || it.isNotBlank() } ?: "")
        if (json != null) apply(json) else emptyList()
    } catch (e: Exception) { emptyList() }

    private fun post(path: String, code: String): JsonObject? {
        val body = FormBody.Builder()
            .add("code", code).add("system_id", systemId)
            .add("app_version", Constants.APP_VERSION).add("mode", mode()).build()
        val req = Request.Builder().url("${Constants.BASE_URL}/api/client/$path").post(body).build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: return null
            return runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
        }
    }

    private fun apply(json: JsonObject): List<Notice> {
        json.getAsJsonObject("config")?.let { c ->
            settings.setValue("CFG_CACHE", c.toString())
            config = parseConfig(c)
        }
        val sub = json.get("subscription")
        val local = state
        if (sub != null && sub.isJsonObject) {
            val s = sub.asJsonObject
            val serverEnd = LocalDate.parse(s.get("end").asString)
            // دورهٔ مهمانی یا خوداظهاری محلیِ بلندتر با دادهٔ قدیمی سرور جایگزین نشود
            if (local.kind == Kind.CODE || local.kind == Kind.NONE || local.end == null || !serverEnd.isBefore(local.end))
            save(Kind.CODE, s.get("plan_title").asString, LocalDate.parse(s.get("start").asString),
                LocalDate.parse(s.get("end").asString), s.get("code").asString, s.get("holder")?.asString ?: "")
        } else if (state.kind == Kind.CODE && json.has("subscription")) {
            // کد در پنل لغو یا حذف شده؛ دورهٔ فعلی محلی حفظ می‌شود ولی دیگر همگام نمی‌شود
            settings.setValue("SUB_KIND", Kind.LOCAL.name)
        }
        val seen = settings.get("MSG_SEEN").split(',').filter { it.isNotBlank() }.toMutableSet()
        val all = json.getAsJsonArray("messages")?.map { e ->
            val o = e.asJsonObject
            Notice(o.get("id").asString, o.get("title").asString, o.get("body").asString,
                o.get("level")?.asString ?: "info", o.get("date")?.asString ?: "")
        } ?: emptyList()
        settings.setValue("MSG_ALL", gson.toJson(all))
        return all.filter { it.id !in seen }
    }

    fun allMessages(): List<Notice> = runCatching {
        gson.fromJson(settings.get("MSG_ALL"), Array<Notice>::class.java)?.toList() ?: emptyList()
    }.getOrDefault(emptyList())

    fun unseenCount(): Int {
        val seen = settings.get("MSG_SEEN").split(',').toSet()
        return allMessages().count { it.id !in seen }
    }

    fun markAllSeen() {
        val ids = (settings.get("MSG_SEEN").split(',') + allMessages().map { it.id }).filter { it.isNotBlank() }.distinct().takeLast(200)
        settings.setValue("MSG_SEEN", ids.joinToString(","))
    }

    private fun loadCachedConfig(): Config =
        settings.get("CFG_CACHE").takeIf { it.isNotBlank() }?.let { runCatching { parseConfig(JsonParser.parseString(it).asJsonObject) }.getOrNull() }
            ?: DEFAULT_CONFIG

    private fun parseConfig(c: JsonObject): Config {
        val plans = c.getAsJsonArray("plans")?.map { e ->
            val o = e.asJsonObject
            Plan(o.get("id").asString, o.get("title").asString, o.get("days").asInt, o.get("price").asInt)
        }?.takeIf { it.isNotEmpty() } ?: DEFAULT_CONFIG.plans
        val cards = c.getAsJsonArray("cards")?.map { e ->
            val o = e.asJsonObject
            Card(o.get("number").asString, o.get("holder").asString)
        }?.takeIf { it.isNotEmpty() } ?: DEFAULT_CONFIG.cards
        return Config(
            plans, cards,
            c.get("trial_enabled")?.asBoolean ?: true,
            c.get("trial_days")?.asInt ?: 3,
            c.get("reminder_days")?.asInt ?: 7,
            c.get("subscribe_url")?.asString ?: DEFAULT_CONFIG.subscribeUrl,
            c.get("contact_url")?.asString ?: DEFAULT_CONFIG.contactUrl,
        )
    }
}

object PersianDigits {
    fun latin(s: String): String = buildString {
        for (ch in s) append(when (ch) { in '۰'..'۹' -> '0' + (ch - '۰'); in '٠'..'٩' -> '0' + (ch - '٠'); else -> ch })
    }
}
