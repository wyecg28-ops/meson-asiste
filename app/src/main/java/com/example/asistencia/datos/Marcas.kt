/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.datos

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

enum class TipoRegistro(val etiqueta: String) {
    ENTRADA("Entrada"),
    SALIDA("Salida"),
}

enum class EstadoMarca(val etiqueta: String) {
    PENDIENTE("Pendiente de enviar"),
    ENVIADA("Enviada a la hoja"),
    RECHAZADA("Rechazada"),
}

/** Una entrada o salida tomada en el teléfono. Se guarda aquí antes de enviarse a Google Sheets. */
data class Marca(
    val id: String,
    val tipo: String,
    val fechaHora: Long,
    val metodo: String,
    val latitud: Double,
    val longitud: Double,
    val precision: Double,
    val observacion: String,
    val estado: EstadoMarca = EstadoMarca.PENDIENTE,
    val detalle: String = "",
    /** Marcada fuera del colegio por una actividad especial (la dirección la revisa). */
    val actividad: Boolean = false,
    /** Código del QR de la entrada que se escaneó al marcar (si la sede usa QR). */
    val codigoQr: String = "",
    /** Redes Wi-Fi que el teléfono veía al marcar (si la sede usa Wi-Fi). */
    val wifi: List<String> = emptyList(),
    /** Si el servidor la dejó "Por revisar" (QR o Wi-Fi no comprobados), el motivo. */
    val revision: String = "",
) {
    fun aJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("tipo", tipo)
        .put("fechaHora", fechaHora)
        .put("metodo", metodo)
        .put("latitud", latitud)
        .put("longitud", longitud)
        .put("precision", precision)
        .put("observacion", observacion)
        .put("estado", estado.name)
        .put("detalle", detalle)
        .put("actividad", actividad)
        .put("codigoQr", codigoQr)
        .put("wifi", JSONArray(wifi))
        .put("revision", revision)

    companion object {
        fun deJson(j: JSONObject) = Marca(
            id = j.getString("id"),
            tipo = j.getString("tipo"),
            fechaHora = j.getLong("fechaHora"),
            metodo = j.getString("metodo"),
            latitud = j.getDouble("latitud"),
            longitud = j.getDouble("longitud"),
            precision = j.getDouble("precision"),
            observacion = j.optString("observacion"),
            estado = EstadoMarca.valueOf(j.getString("estado")),
            detalle = j.optString("detalle"),
            actividad = j.optBoolean("actividad", false),
            codigoQr = j.optString("codigoQr"),
            wifi = j.optJSONArray("wifi")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            revision = j.optString("revision"),
        )
    }
}

/** Historial local de marcas de una cuenta, la más reciente primero. Sin cuenta abierta, está vacío. */
class Marcas(context: Context, private val cuenta: String?) {
    private val prefs = context.getSharedPreferences("marcas", Context.MODE_PRIVATE)
    private val clave = "lista@$cuenta"

    fun todas(): List<Marca> {
        if (cuenta == null) return emptyList()
        val arreglo = JSONArray(prefs.getString(clave, "[]"))
        return (0 until arreglo.length()).map { Marca.deJson(arreglo.getJSONObject(it)) }
    }

    fun agregar(marca: Marca) = guardar(listOf(marca) + todas())

    fun actualizar(marca: Marca) = guardar(todas().map { if (it.id == marca.id) marca else it })

    fun borrarTodo() = prefs.edit { remove(clave) }

    // Se conservan las pendientes siempre y, de las demás, solo las más recientes.
    private fun guardar(lista: List<Marca>) {
        if (cuenta == null) return
        var otras = 0
        val recortada = lista.filter { it.estado == EstadoMarca.PENDIENTE || otras++ < MAXIMO }
        val arreglo = JSONArray().apply { recortada.forEach { put(it.aJson()) } }
        prefs.edit { putString(clave, arreglo.toString()) }
    }

    companion object {
        // Suficiente para un ciclo escolar de estadísticas (≈ 2 marcas por día).
        private const val MAXIMO = 500

        /** La versión anterior guardaba un solo historial: pasa a ser el de esa cuenta. */
        fun migrarHistorialAnterior(context: Context, cuenta: String) {
            val prefs = context.getSharedPreferences("marcas", Context.MODE_PRIVATE)
            val anterior = prefs.getString("lista", null) ?: return
            prefs.edit {
                putString("lista@$cuenta", anterior)
                remove("lista")
            }
        }
    }
}
