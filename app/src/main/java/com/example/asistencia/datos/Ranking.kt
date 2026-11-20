/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.datos

import org.json.JSONObject

/** Un puesto del ranking mensual de horas. [yo] = es la persona que tiene la app abierta. */
data class PuestoRanking(
    val posicion: Int,
    val nombre: String,
    val minutos: Long,
    val dias: Int,
    val puntuales: Int,
    val tardes: Int,
    val extra: Long,
    val yo: Boolean,
)

/** Ranking de un mes ("2026-11"): todo el personal que marca, ordenado por horas trabajadas. */
data class Ranking(val mes: String, val lista: List<PuestoRanking>, val miPosicion: Int) {
    companion object {
        fun deJson(j: JSONObject): Ranking {
            val a = j.optJSONArray("lista")
            val lista = (0 until (a?.length() ?: 0)).map { i ->
                val p = a!!.getJSONObject(i)
                PuestoRanking(
                    posicion = p.optInt("posicion", i + 1),
                    nombre = p.optString("nombre"),
                    minutos = p.optLong("minutos"),
                    dias = p.optInt("dias"),
                    puntuales = p.optInt("puntuales"),
                    tardes = p.optInt("tardes"),
                    extra = p.optLong("extra"),
                    yo = p.optBoolean("yo"),
                )
            }
            return Ranking(j.optString("mes"), lista, j.optInt("miPosicion"))
        }
    }
}
