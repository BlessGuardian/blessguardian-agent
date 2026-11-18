package com.example.antifraudagent.data.local.cache

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Guarda no aparelho a "analise detalhada" e os "sinais" que o POST /detect devolve.
 *
 * O backend so persiste o veredito curto (fraud_logs.explanation); raciocinio e indicadores
 * existem apenas na resposta do /detect. Sem este cache, o detalhe vindo do Historico (GET /logs)
 * nao tem essas secoes. Nao e historico oficial: so complementa a exibicao no aparelho que
 * capturou a mensagem. Chave = hash do conteudo normalizado; limitado a [MAX_ENTRIES].
 */
object AnalysisDetailCache {
    private const val PREFS = "analysis_detail_cache"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 300

    data class Entry(val reasoning: String, val indicators: List<String>)

    @Volatile private var prefs: SharedPreferences? = null
    private val lock = Any()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun put(content: String, reasoning: String, indicators: List<String>) {
        if (reasoning.isBlank() && indicators.isEmpty()) return
        val p = prefs ?: return
        synchronized(lock) {
            val root = load(p)
            // Contador crescente (nao depende da ordem das chaves do JSONObject).
            val last = root.keys().asSequence()
                .maxOfOrNull { root.optJSONObject(it)?.optLong("t", 0L) ?: 0L } ?: 0L
            val entry = JSONObject()
                .put("r", reasoning)
                .put("i", JSONArray(indicators))
                .put("t", last + 1)
            root.put(key(content), entry)
            evict(root, MAX_ENTRIES)
            p.edit().putString(KEY_ENTRIES, root.toString()).apply()
        }
    }

    /** Apaga tudo (logout): o raciocinio da LLM cita trechos das mensagens. */
    fun clear() {
        val p = prefs ?: return
        synchronized(lock) { p.edit().remove(KEY_ENTRIES).apply() }
    }

    /** Remove as entradas de menor contador ("t") ate sobrarem [max]. */
    internal fun evict(root: JSONObject, max: Int) {
        val names = root.keys().asSequence().toList()
        if (names.size <= max) return
        names.sortedBy { root.optJSONObject(it)?.optLong("t", 0L) ?: 0L }
            .take(names.size - max)
            .forEach { root.remove(it) }
    }

    internal fun resetForTest() {
        prefs = null
    }

    fun get(content: String): Entry? {
        val p = prefs ?: return null
        val item = synchronized(lock) { load(p).optJSONObject(key(content)) } ?: return null
        val array = item.optJSONArray("i")
        val indicators = if (array == null) emptyList() else (0 until array.length()).map { array.optString(it) }
        return Entry(item.optString("r"), indicators)
    }

    private fun load(p: SharedPreferences): JSONObject =
        try { JSONObject(p.getString(KEY_ENTRIES, null) ?: "{}") } catch (_: Exception) { JSONObject() }

    internal fun key(content: String): String {
        val normalized = content.trim().replace(Regex("[ \\t\\r\\n]+"), " ").lowercase()
        return MessageDigest.getInstance("SHA-1").digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
