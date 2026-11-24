/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.datos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Un aviso de la tarjeta "Novedades" de Inicio (se queda hasta tocar "Entendido"). */
data class Novedad(val id: String, val icono: String, val titulo: String, val texto: String)

/**
 * Buzón de novedades de una cuenta en este teléfono: vacaciones o asuetos nuevos, logros de racha y resumen de la
 * semana. Son los mismos avisos que llegan como notificación, pero dentro de la app, por si la persona no la vio
 * (o tiene las notificaciones apagadas). La versión web hace lo mismo.
 */
class Novedades(context: Context, correo: String?) {
    private val prefs = context.getSharedPreferences("novedades", Context.MODE_PRIVATE)
    private val clave = "buzon:" + correo.orEmpty()

    private fun leer(): JSONObject = runCatching { JSONObject(prefs.getString(clave, null) ?: "{}") }.getOrDefault(JSONObject())
    private fun guardar(j: JSONObject) = prefs.edit().putString(clave, j.toString()).apply()

    fun items(): List<Novedad> {
        val a = leer().optJSONArray("items") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            a.optJSONObject(i)?.let { Novedad(it.optString("id"), it.optString("icono"), it.optString("titulo"), it.optString("texto")) }
        }
    }

    private fun escribir(lista: List<Novedad>) {
        val j = leer()
        j.put("items", JSONArray().apply {
            lista.take(20).forEach { put(JSONObject().put("id", it.id).put("icono", it.icono).put("titulo", it.titulo).put("texto", it.texto)) }
        })
        guardar(j)
    }

    /** Agrega un aviso arriba (si ya estaba, no se repite). */
    fun agregar(n: Novedad) {
        val actuales = items()
        if (actuales.none { it.id == n.id }) escribir(listOf(n) + actuales)
    }

    fun quitar(id: String) = escribir(items().filter { it.id != id })
    fun quitarTodas() = escribir(emptyList())

    /** Lunes ("2026-11-23") de la última semana que ya tiene su resumen. */
    var semanaResumida: String
        get() = leer().optString("semana")
        set(valor) = guardar(leer().put("semana", valor))
}
