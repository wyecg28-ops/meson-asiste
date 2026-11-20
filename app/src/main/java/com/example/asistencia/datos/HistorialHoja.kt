/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.datos

import org.json.JSONArray
import java.util.Calendar

/**
 * Historial tal como está en la hoja de Google (con las correcciones que la administración haga a mano).
 * La hoja manda: para los días que el servidor devuelve, se muestran sus horas en lugar de las del teléfono.
 * Las marcas pendientes o rechazadas del teléfono, y los días que el servidor no devolvió, se conservan.
 */
object HistorialHoja {

    /** Convierte los días de la hoja ("dias" de misMarcas) en marcas de entrada y salida ya enviadas. */
    fun marcasDe(json: String?): List<Marca> {
        if (json.isNullOrBlank()) return emptyList()
        val dias = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val lista = mutableListOf<Marca>()
        for (i in 0 until dias.length()) {
            val d = dias.optJSONObject(i) ?: continue
            val fecha = d.optString("fecha")
            val entrada = instante(fecha, d.optString("entrada")) ?: continue
            val actividad = d.optBoolean("actividad", false)
            val observacion = d.optString("observacion")
            val revision = d.optString("revision")
            lista += sintetica("hoja-$fecha-E", TipoRegistro.ENTRADA, entrada, actividad, observacion, revision)
            instante(fecha, d.optString("salida"))?.let { s ->
                // Turno que pasa la medianoche: la salida es del día siguiente.
                val salida = if (s < entrada) s + DIA else s
                lista += sintetica("hoja-$fecha-S", TipoRegistro.SALIDA, salida, actividad, "", revision)
            }
        }
        return lista
    }

    /**
     * Une la hoja con el historial del teléfono. [descargadaEn] es cuándo se descargó la hoja: las marcas enviadas
     * después todavía no aparecen en ella y se conservan.
     */
    fun combinar(locales: List<Marca>, json: String?, descargadaEn: Long, perfil: Perfil): List<Marca> {
        // Todavía no se ha descargado la hoja (sin conexión desde que se instaló): solo lo del teléfono.
        if (json == null || descargadaEn == 0L) return locales
        val hoja = marcasDe(json)
        // La hoja devuelve los últimos 60 días. En ese periodo manda la hoja: si la administración borró o corrigió
        // un registro, el teléfono deja de mostrar el suyo. Lo anterior a esos días se queda del teléfono.
        val desde = inicioDelDia(descargadaEn) - DIAS_DE_LA_HOJA * DIA
        val reemplazadas = agruparPorDia(locales.filter { it.estado == EstadoMarca.ENVIADA }, perfil)
            .filter { it.dia >= desde }
            .flatMap { j -> j.marcas.filter { it.fechaHora < descargadaEn } }
            .map { it.id }
            .toSet()
        return (hoja + locales.filter { it.id !in reemplazadas }).sortedByDescending { it.fechaHora }
    }

    private const val DIAS_DE_LA_HOJA = 60L

    private fun sintetica(
        id: String, tipo: TipoRegistro, cuando: Long, actividad: Boolean, observacion: String, revision: String,
    ) = Marca(
        id = id,
        tipo = tipo.name,
        fechaHora = cuando,
        metodo = "",
        latitud = 0.0,
        longitud = 0.0,
        precision = 0.0,
        observacion = observacion,
        estado = EstadoMarca.ENVIADA,
        actividad = actividad,
        revision = revision,
    )

    /** "2026-11-19" + "07:05" → instante en la hora del teléfono, o null si falta algo. */
    private fun instante(fecha: String, hora: String): Long? {
        val f = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(fecha)?.groupValues ?: return null
        val h = Regex("""^(\d{1,2}):(\d{2})""").find(hora.trim())?.groupValues ?: return null
        return Calendar.getInstance().apply {
            clear()
            set(f[1].toInt(), f[2].toInt() - 1, f[3].toInt(), h[1].toInt(), h[2].toInt(), 0)
        }.timeInMillis
    }

    private const val DIA = 24 * 60 * 60_000L
}
