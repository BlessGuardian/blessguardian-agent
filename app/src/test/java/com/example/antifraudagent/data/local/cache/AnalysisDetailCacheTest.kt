package com.example.antifraudagent.data.local.cache

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnalysisDetailCacheTest {
    @Before
    fun setUp() {
        AnalysisDetailCache.resetForTest()
    }

    @Test
    fun `chave ignora espacos extras e caixa`() {
        assertEquals(
            AnalysisDetailCache.key("Preciso de uma  transferência agora"),
            AnalysisDetailCache.key("  preciso de uma transferência\nagora ")
        )
    }

    @Test
    fun `conteudos diferentes geram chaves diferentes`() {
        assertNotEquals(AnalysisDetailCache.key("mensagem a"), AnalysisDetailCache.key("mensagem b"))
    }

    @Test
    fun `sem init put get e clear nao quebram`() {
        AnalysisDetailCache.put("x", "r", listOf("i"))
        assertEquals(null, AnalysisDetailCache.get("x"))
        AnalysisDetailCache.clear()
    }

    @Test
    fun `put com raciocinio e indicadores vazios nao faz nada`() {
        AnalysisDetailCache.put("x", "  ", emptyList())
        assertEquals(null, AnalysisDetailCache.get("x"))
    }

    @Test
    fun `despejo remove os menores contadores independente da ordem das chaves`() {
        val root = JSONObject()
        // Insercao fora de ordem de proposito.
        listOf("c" to 3L, "a" to 1L, "e" to 5L, "b" to 2L, "d" to 4L).forEach { (k, t) ->
            root.put(k, JSONObject().put("t", t))
        }
        AnalysisDetailCache.evict(root, 3)
        assertEquals(3, root.length())
        assertFalse(root.has("a"))
        assertFalse(root.has("b"))
        assertTrue(root.has("c") && root.has("d") && root.has("e"))
    }

    @Test
    fun `despejo nao mexe quando esta dentro do limite`() {
        val root = JSONObject().put("a", JSONObject().put("t", 1L))
        AnalysisDetailCache.evict(root, 300)
        assertEquals(1, root.length())
    }
}
