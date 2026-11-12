package ir.matnyaab.core.ai

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import ir.matnyaab.core.Constants
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

/** یک سرویس هوش مصنوعی که کاربر با کلید خودش اضافه می‌کند (مشترک میان متن‌یاب و افزونهٔ ورد) */
data class AiProvider(
    val Id: String = UUID.randomUUID().toString().substring(0, 8),
    val Title: String = "",
    val Kind: String = "openai",      // openai (هر سرویس سازگار) | anthropic | gemini
    val BaseUrl: String = "",
    val Model: String = "",
    val KeyProtected: String = "",
)

/** پیشنهادهای آماده (فهرست کامل‌تر از پنل مدیریت سایت می‌آید) */
data class AiPreset(val id: String, val title: String, val kind: String, val baseUrl: String, val models: List<String>, val hint: String = "", val site: String = "")

/**
 * پیکربندی مشترک هوش مصنوعی در ai.json (پوشهٔ تنظیمات متن‌یاب).
 * کلیدها با DPAPI ویندوز (فقط برای همین کاربر روی همین رایانه) رمز می‌شوند؛ همان قالبی که افزونهٔ ورد به کار می‌برد.
 */
object AiConfig {
    data class Data(var providers: MutableList<AiProvider> = mutableListOf(), var activeId: String = "")

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    val file: File get() = Constants.APP_SETTINGS_DIR.resolve("ai.json").toFile()
    private val keyCache = HashMap<String, String>()

    val PRESETS = listOf(
        AiPreset("avalai", "AvalAI (درگاه ایرانی، پرداخت ریالی)", "openai", "https://api.avalai.ir/v1", listOf("gpt-4o-mini", "gpt-4o", "claude-3-5-sonnet-latest", "gemini-2.0-flash"), "نشانی پایه را از مستندات AvalAI هم بررسی کنید.", "https://avalai.ir"),
        AiPreset("gapgpt", "GapGPT (درگاه ایرانی)", "openai", "https://api.gapgpt.app/v1", listOf("gpt-4o-mini", "gpt-4o"), "نشانی پایه را از مستندات GapGPT هم بررسی کنید.", "https://gapgpt.app"),
        AiPreset("openrouter", "OpenRouter (مدل‌های رایگان و پولی)", "openai", "https://openrouter.ai/api/v1", listOf("deepseek/deepseek-chat-v3-0324:free", "meta-llama/llama-3.3-70b-instruct:free", "openai/gpt-4o-mini"), "مدل‌های دارای «:free» رایگان‌اند (با محدودیت تعداد درخواست).", "https://openrouter.ai"),
        AiPreset("deepseek", "DeepSeek (کم‌هزینه)", "openai", "https://api.deepseek.com/v1", listOf("deepseek-chat"), "", "https://platform.deepseek.com"),
        AiPreset("openai", "OpenAI", "openai", "https://api.openai.com/v1", listOf("gpt-4o-mini", "gpt-4o"), "", "https://platform.openai.com"),
        AiPreset("anthropic", "Anthropic Claude", "anthropic", "https://api.anthropic.com/v1", listOf("claude-sonnet-4-5", "claude-haiku-4-5"), "", "https://console.anthropic.com"),
        AiPreset("gemini", "Google Gemini", "gemini", "https://generativelanguage.googleapis.com/v1beta", listOf("gemini-2.0-flash", "gemini-1.5-pro"), "", "https://aistudio.google.com"),
        AiPreset("groq", "Groq (سریع، سطح رایگان)", "openai", "https://api.groq.com/openai/v1", listOf("llama-3.3-70b-versatile"), "", "https://console.groq.com"),
        AiPreset("ollama", "Ollama — مدل محلی و آفلاین (رایگان)", "openai", "http://localhost:11434/v1", listOf("qwen2.5:7b", "llama3.1:8b"), "Ollama را نصب و مدل را دانلود کنید؛ کلید لازم نیست و متن از رایانه بیرون نمی‌رود.", "https://ollama.com"),
        AiPreset("custom", "سرویس سازگار با OpenAI (دلخواه)", "openai", "", emptyList(), "نشانی پایهٔ API و نام مدل را از مستندات سرویس بردارید."),
    )

    @Synchronized
    fun load(): Data = runCatching {
        if (file.exists()) gson.fromJson(file.readText(Charsets.UTF_8), Data::class.java)?.also { if (it.providers == null) it.providers = mutableListOf() } else fromWordAddin()
    }.getOrNull() ?: Data()

    /** اگر کاربر پیش‌تر در افزونهٔ ورد سرویس افزوده، همان را بردار (کلیدها با همان DPAPI رمزند). */
    private fun fromWordAddin(): Data? = runCatching {
        val w = File(file.parentFile, "word-addin.json").takeIf { it.exists() } ?: return null
        val o = com.google.gson.JsonParser.parseString(w.readText(Charsets.UTF_8)).asJsonObject
        val list = o.getAsJsonArray("AiProviders")?.map { gson.fromJson(it, AiProvider::class.java) }?.toMutableList() ?: return null
        if (list.isEmpty()) null else Data(list, o.get("AiProviderId")?.asString ?: list.first().Id)
    }.getOrNull()

    @Synchronized
    fun save(d: Data) {
        file.parentFile.mkdirs()
        val tmp = File(file.parentFile, "ai.json.tmp")
        tmp.writeText(gson.toJson(d), Charsets.UTF_8)
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    fun active(): AiProvider? = load().let { d -> d.providers.firstOrNull { it.Id == d.activeId } ?: d.providers.firstOrNull() }

    fun configured() = load().providers.isNotEmpty()

    fun add(p: AiProvider, key: String): AiProvider {
        val withKey = p.copy(KeyProtected = if (key.isBlank()) "" else Dpapi.protect(key))
        keyCache[withKey.Id] = key
        val d = load(); d.providers.add(withKey); d.activeId = withKey.Id; save(d)
        return withKey
    }

    fun remove(id: String) { val d = load(); d.providers.removeIf { it.Id == id }; if (d.activeId == id) d.activeId = d.providers.firstOrNull()?.Id ?: ""; save(d) }
    fun setActive(id: String) { val d = load(); d.activeId = id; save(d) }

    fun key(p: AiProvider): String = keyCache.getOrPut(p.Id) { if (p.KeyProtected.isBlank()) "" else Dpapi.unprotect(p.KeyProtected) }
}

/** رمزنگاری DPAPI ویندوز از راه PowerShell (هم‌قالب با افزونهٔ ورد: entropy = matnyaab-word-ai) */
object Dpapi {
    private const val ENTROPY = "matnyaab-word-ai"
    private val win = System.getProperty("os.name").lowercase().contains("win")

    private fun ps(script: String): String {
        val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        if (!p.waitFor(20, TimeUnit.SECONDS) || p.exitValue() != 0) throw IllegalStateException("DPAPI: $out")
        return out
    }

    fun protect(plain: String): String {
        if (!win) return "plain:" + Base64.getEncoder().encodeToString(plain.toByteArray())
        val b64 = Base64.getEncoder().encodeToString(plain.toByteArray(Charsets.UTF_8))
        return ps("Add-Type -AssemblyName System.Security; [Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Protect([Convert]::FromBase64String('$b64'), [Text.Encoding]::UTF8.GetBytes('$ENTROPY'), 'CurrentUser'))")
    }

    fun unprotect(enc: String): String {
        if (enc.startsWith("plain:")) return String(Base64.getDecoder().decode(enc.removePrefix("plain:")))
        require(enc.matches(Regex("[A-Za-z0-9+/=]+"))) { "invalid key data" }
        val b64 = ps("Add-Type -AssemblyName System.Security; [Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Unprotect([Convert]::FromBase64String('$enc'), [Text.Encoding]::UTF8.GetBytes('$ENTROPY'), 'CurrentUser'))")
        return String(Base64.getDecoder().decode(b64), Charsets.UTF_8)
    }
}

class AiException(msg: String) : Exception(msg)

/** فراخوانی سرویس هوش مصنوعی (OpenAI-سازگار، Anthropic، Gemini) */
object AiClient {
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()
    private val JSON = "application/json; charset=utf-8".toMediaType()
    var calls = 0; private set

    fun complete(p: AiProvider, system: String, user: String, maxTokens: Int = 3000, temperature: Double = 0.2, keyOverride: String? = null): String {
        val key = keyOverride ?: AiConfig.key(p)
        val base = p.BaseUrl.trim().trimEnd('/')
        val req = Request.Builder()
        val body = JsonObject()
        when (p.Kind) {
            "anthropic" -> {
                req.url((base.ifBlank { "https://api.anthropic.com/v1" }) + "/messages").header("x-api-key", key).header("anthropic-version", "2023-06-01")
                body.addProperty("model", p.Model); body.addProperty("max_tokens", maxTokens); body.addProperty("system", system)
                body.addProperty("temperature", temperature)
                body.add("messages", JsonArray().apply { add(JsonObject().apply { addProperty("role", "user"); addProperty("content", user) }) })
            }
            "gemini" -> {
                req.url((base.ifBlank { "https://generativelanguage.googleapis.com/v1beta" }) + "/models/" + p.Model + ":generateContent").header("x-goog-api-key", key)
                body.add("systemInstruction", JsonObject().apply { add("parts", JsonArray().apply { add(JsonObject().apply { addProperty("text", system) }) }) })
                body.add("contents", JsonArray().apply { add(JsonObject().apply { addProperty("role", "user"); add("parts", JsonArray().apply { add(JsonObject().apply { addProperty("text", user) }) }) }) })
                body.add("generationConfig", JsonObject().apply { addProperty("temperature", temperature); addProperty("maxOutputTokens", maxTokens) })
            }
            else -> {
                req.url((base.ifBlank { "https://api.openai.com/v1" }) + "/chat/completions")
                if (key.isNotBlank()) req.header("Authorization", "Bearer $key")
                body.addProperty("model", p.Model); body.addProperty("temperature", temperature); body.addProperty("max_tokens", maxTokens)
                body.add("messages", JsonArray().apply {
                    add(JsonObject().apply { addProperty("role", "system"); addProperty("content", system) })
                    add(JsonObject().apply { addProperty("role", "user"); addProperty("content", user) })
                })
            }
        }
        calls++
        val text = try {
            http.newCall(req.post(body.toString().toRequestBody(JSON)).build()).execute().use { r ->
                val t = r.body?.string() ?: ""
                if (!r.isSuccessful) {
                    val msg = runCatching { JsonParser.parseString(t).asJsonObject.get("error")?.let { if (it.isJsonObject) it.asJsonObject.get("message")?.asString else it.asString } }.getOrNull()
                    throw AiException(when (r.code) {
                        401, 403 -> "کلید API پذیرفته نشد؛ آن را در «تنظیمات هوش مصنوعی» بررسی کنید."
                        402 -> "اعتبار حساب سرویس کافی نیست."
                        429 -> "سرویس موقتاً درخواست بیشتری نمی‌پذیرد (محدودیت سرعت). کمی بعد دوباره امتحان کنید."
                        else -> "خطای سرویس (${r.code}): ${msg ?: t.take(200)}"
                    })
                }
                t
            }
        } catch (e: java.io.IOException) { throw AiException("اتصال به سرویس برقرار نشد: ${e.message}") }
        val o = JsonParser.parseString(text).asJsonObject
        val out = when (p.Kind) {
            "anthropic" -> o.getAsJsonArray("content")?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" }
            "gemini" -> o.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject?.getAsJsonObject("content")?.getAsJsonArray("parts")?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" }
            else -> o.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject?.getAsJsonObject("message")?.get("content")?.asString
        }
        return out?.trim() ?: throw AiException("پاسخ خالی از سرویس")
    }

    /** بیرون‌کشیدن JSON از پاسخ مدل (حتی اگر داخل ```json باشد یا متن اضافه داشته باشد) */
    fun json(text: String): com.google.gson.JsonElement? {
        val t = text.replace(Regex("```(json)?"), "").trim()
        for ((open, close) in listOf('[' to ']', '{' to '}')) {
            val a = t.indexOf(open); val b = t.lastIndexOf(close)
            if (a >= 0 && b > a) runCatching { return JsonParser.parseString(t.substring(a, b + 1)) }
        }
        return null
    }
}
