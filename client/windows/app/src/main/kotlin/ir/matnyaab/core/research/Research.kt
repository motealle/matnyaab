package ir.matnyaab.core.research

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import ir.matnyaab.core.Constants
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * فیش پژوهشی: بخشی از یک سند با منبع دقیق (مسیر، جایگاه)، عنوان، یادداشت، برچسب و کد.
 * «موضوع» همان سرفصلی است که فیش زیر آن قرار می‌گیرد (برای ساخت فصل و کتاب).
 */
data class Fish(
    val id: String = UUID.randomUUID().toString().substring(0, 12),
    val project: String = "",
    val topic: String = "",
    val title: String = "",
    val text: String = "",
    val note: String = "",
    val tags: List<String> = emptyList(),
    val codes: List<String> = emptyList(),
    val sourcePath: String = "",
    val sourceName: String = "",
    val collection: String = "",
    val start: Int = -1,
    val end: Int = -1,
    val created: Long = System.currentTimeMillis(),
    val origin: String = "manual",     // manual | mission | word
)

/** پروژهٔ پژوهشی یا کتاب: هدف و فهرست مرتب سرفصل‌ها (فصل‌ها) */
data class Project(
    val id: String = UUID.randomUUID().toString().substring(0, 10),
    val name: String = "",
    val goal: String = "",
    val topics: List<String> = emptyList(),
    val created: Long = System.currentTimeMillis(),
)

/**
 * ذخیره‌گاه فیش‌ها و پروژه‌ها (research.json در پوشهٔ تنظیمات؛ مشترک با افزونهٔ ورد از راه پل).
 * ذخیرهٔ اتمی و یک نسخهٔ پشتیبان پیشین.
 */
object ResearchStore {
    private data class Data(val projects: MutableList<Project> = mutableListOf(), val fishes: MutableList<Fish> = mutableListOf())

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    val file: File get() = Constants.APP_SETTINGS_DIR.resolve("research.json").toFile()
    @Volatile private var data: Data? = null
    var listener: (() -> Unit)? = null

    const val DEFAULT_PROJECT = "پژوهش‌های من"

    @Synchronized
    private fun d(): Data {
        data?.let { return it }
        val loaded = runCatching {
            if (file.exists()) gson.fromJson<Data>(file.readText(Charsets.UTF_8), object : TypeToken<Data>() {}.type) else null
        }.getOrNull() ?: Data()
        // Gson ممکن است فهرست‌های خالی را null بگذارد
        val fixed = Data(loaded.projects?.toMutableList() ?: mutableListOf(), loaded.fishes?.map { normalize(it) }?.toMutableList() ?: mutableListOf())
        if (fixed.projects.none { it.name == DEFAULT_PROJECT }) fixed.projects.add(0, Project(name = DEFAULT_PROJECT))
        data = fixed
        return fixed
    }

    private fun normalize(f: Fish) = f.copy(
        tags = f.tags ?: emptyList(), codes = f.codes ?: emptyList(),
        project = f.project ?: DEFAULT_PROJECT, topic = f.topic ?: "", note = f.note ?: "", title = f.title ?: "",
    )

    @Synchronized
    private fun save() {
        val dd = d()
        file.parentFile.mkdirs()
        val tmp = File(file.parentFile, "research.json.tmp")
        tmp.writeText(gson.toJson(dd), Charsets.UTF_8)
        if (file.exists()) runCatching { Files.copy(file.toPath(), File(file.parentFile, "research.bak.json").toPath(), StandardCopyOption.REPLACE_EXISTING) }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        listener?.invoke()
    }

    @Synchronized fun reload() { data = null }

    @Synchronized fun projects(): List<Project> = d().projects.toList()
    @Synchronized fun project(name: String): Project? = d().projects.firstOrNull { it.name == name }

    @Synchronized
    fun ensureProject(name: String, goal: String = ""): Project {
        val n = name.trim().ifBlank { DEFAULT_PROJECT }
        d().projects.firstOrNull { it.name == n }?.let { return it }
        val p = Project(name = n, goal = goal)
        d().projects.add(p); save()
        return p
    }

    @Synchronized
    fun updateProject(p: Project) {
        val i = d().projects.indexOfFirst { it.id == p.id }
        if (i >= 0) {
            val old = d().projects[i]
            d().projects[i] = p
            if (old.name != p.name) d().fishes.replaceAll { if (it.project == old.name) it.copy(project = p.name) else it }
            save()
        }
    }

    @Synchronized
    fun deleteProject(p: Project, withFishes: Boolean) {
        d().projects.removeIf { it.id == p.id }
        if (withFishes) d().fishes.removeIf { it.project == p.name }
        else d().fishes.replaceAll { if (it.project == p.name) it.copy(project = DEFAULT_PROJECT) else it }
        if (d().projects.isEmpty()) d().projects.add(Project(name = DEFAULT_PROJECT))
        save()
    }

    @Synchronized fun fishes(project: String? = null): List<Fish> = d().fishes.filter { project == null || it.project == project }

    @Synchronized
    fun add(f: Fish): Fish {
        val p = ensureProject(f.project)
        val fish = f.copy(project = p.name)
        d().fishes.add(fish)
        if (fish.topic.isNotBlank() && fish.topic !in p.topics) updateProject(p.copy(topics = p.topics + fish.topic)) else save()
        return fish
    }

    @Synchronized
    fun addAll(list: List<Fish>) {
        list.forEach { f ->
            val p = ensureProject(f.project)
            d().fishes.add(f.copy(project = p.name))
            if (f.topic.isNotBlank()) {
                val cur = project(p.name)!!
                if (f.topic !in cur.topics) d().projects[d().projects.indexOfFirst { it.id == cur.id }] = cur.copy(topics = cur.topics + f.topic)
            }
        }
        save()
    }

    @Synchronized
    fun update(f: Fish) {
        val i = d().fishes.indexOfFirst { it.id == f.id }
        if (i >= 0) { d().fishes[i] = f; save() }
    }

    @Synchronized fun delete(ids: Collection<String>) { d().fishes.removeIf { it.id in ids }; save() }

    /** جست‌وجوی ساده در فیش‌ها (عنوان، متن، یادداشت، برچسب، کد) */
    fun filter(list: List<Fish>, q: String): List<Fish> {
        val t = q.trim()
        if (t.isEmpty()) return list
        val n = ir.matnyaab.core.search.Morph.norm(t)
        return list.filter { f ->
            listOf(f.title, f.text, f.note, f.topic, f.sourceName, f.tags.joinToString(" "), f.codes.joinToString(" "))
                .any { ir.matnyaab.core.search.Morph.norm(it).contains(n) }
        }
    }
}
