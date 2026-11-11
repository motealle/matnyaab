package ir.matnyaab.core.semantic

// نسخهٔ کامپایل؛ باید دقیقاً با رابط برنامهٔ پایه (core/semantic/SemanticPack.kt) یکی باشد.
interface SemanticEngine : AutoCloseable {
    val id: String
    val dim: Int
    fun embedQuery(text: String): FloatArray
    fun embedPassages(texts: List<String>): List<FloatArray>
}
