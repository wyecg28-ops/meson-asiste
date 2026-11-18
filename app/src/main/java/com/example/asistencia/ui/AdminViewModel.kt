/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.asistencia.datos.Evento
import com.example.asistencia.datos.Horario
import com.example.asistencia.datos.Rol
import com.example.asistencia.datos.Sede
import com.example.asistencia.datos.Solicitud
import com.example.asistencia.ubicacion.Ubicacion
import com.example.asistencia.verificacion.MetodoVerificacion
import com.example.asistencia.verificacion.WifiCercano
import com.example.asistencia.datos.SheetsApi
import com.example.asistencia.datos.datosDelTelefono
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

enum class EstadoAsistencia(val etiqueta: String) {
    PUNTUAL("Puntual"),
    TARDE("Tarde"),
    ACTIVIDAD("Actividad"),
    LIBRE("Día libre"),
    SIN_MARCAR("Sin marcar"),
}

data class Miembro(
    val correo: String,
    val nombre: String,
    /** Código del rol (p. ej. DOCENTE). */
    val rol: String,
    val rolNombre: String,
    val jornada: String,
    val modelo: String,
    val conTelefono: Boolean,
    val activo: Boolean,
)

data class AsistenciaHoy(
    val nombre: String,
    /** Nombre del rol. */
    val rol: String,
    val jornada: String,
    val entrada: String,
    val salida: String,
    val horas: String,
    val estado: EstadoAsistencia,
    val detalle: String,
)

data class ActividadPorRevisar(
    val id: String,
    val fecha: String,
    val nombre: String,
    val entrada: String,
    val salida: String,
    val descripcion: String,
    val ubicacion: String,
)

data class EstadoAdmin(
    val cargando: Boolean = false,
    val hoy: List<AsistenciaHoy>? = null,
    val miembros: List<Miembro>? = null,
    val horarios: List<Horario> = emptyList(),
    /** Jornadas con cuántos miembros las usan (sección Jornadas). */
    val jornadas: List<Pair<Horario, Int>>? = null,
    val eventos: List<Evento>? = null,
    val actividades: List<ActividadPorRevisar>? = null,
    val solicitudes: List<Solicitud>? = null,
    val reporte: ReporteMes? = null,
    val config: AjustesColegio? = null,
    /** Roles del colegio (hoja "Roles"). */
    val roles: List<Rol> = emptyList(),
    /** Cuántas personas tienen cada rol; null = aún no se cargó la sección Roles. */
    val rolesEnUso: Map<String, Int>? = null,
    val mensaje: Mensaje? = null,
)

/** Reglas y ubicaciones del colegio (Admin → Ajustes). */
data class AjustesColegio(val horasMeta: Int, val tolerancia: Int, val sedes: List<Sede>)

/** Una fila del resumen mensual. */
data class FilaReporte(
    val nombre: String,
    val rol: String,
    val jornada: String,
    val dias: Int,
    val puntuales: Int,
    val tardes: Int,
    val faltas: Int,
    val actividades: Int,
    val minutos: Long,
    val minutosExtra: Long,
    val porRevisar: Int,
)

/** Reporte de un mes: tarjetas en la app y archivo CSV para Excel. */
data class ReporteMes(val titulo: String, val archivo: String, val csv: String, val filas: List<FilaReporte>)

/** Panel del administrador: todo se lee y se guarda directamente en el servidor (necesita internet). */
class AdminViewModel(app: Application) : AndroidViewModel(app) {

    private val _estado = MutableStateFlow(EstadoAdmin())
    val estado = _estado.asStateFlow()

    fun cargarHoy() = ejecutar {
        val r = pedir("adminHoy")
        _estado.update { it.copy(hoy = lista(r.getJSONArray("miembros")) { j ->
            AsistenciaHoy(
                nombre = j.getString("nombre"),
                rol = j.optString("rol"),
                jornada = j.optString("jornada"),
                entrada = j.optString("entrada"),
                salida = j.optString("salida"),
                horas = j.optString("horas"),
                estado = EstadoAsistencia.entries.firstOrNull { it.name == j.optString("estado") }
                    ?: EstadoAsistencia.SIN_MARCAR,
                detalle = j.optString("detalle"),
            )
        }.sortedWith(compareBy({ it.estado.ordinal }, { it.nombre })) ) }
    }

    fun cargarMiembros() = ejecutar { leerMiembros() }

    private suspend fun leerMiembros() {
        val r = pedir("adminMiembros")
        _estado.update {
            it.copy(
                miembros = lista(r.getJSONArray("miembros")) { j ->
                    Miembro(
                        correo = j.getString("correo"),
                        nombre = j.getString("nombre"),
                        rol = j.optString("rol", "DOCENTE"),
                        rolNombre = j.optString("rolNombre", j.optString("rol")),
                        jornada = j.optString("jornada"),
                        modelo = j.optString("modelo"),
                        conTelefono = j.optBoolean("conTelefono"),
                        activo = j.optString("estado", "ACTIVO") == "ACTIVO",
                    )
                }.sortedBy { m -> m.nombre },
                horarios = lista(r.getJSONArray("jornadas"), Horario::deJson),
                roles = r.optJSONArray("roles")?.let(::leerListaRoles) ?: it.roles,
            )
        }
    }

    /** Guarda rol, jornada y estado (activo/inactivo) de un miembro. */
    fun guardarMiembro(miembro: Miembro, rol: String, jornada: String, activo: Boolean) = ejecutar {
        var r = pedir("adminAsignar", JSONObject().put("objetivo", miembro.correo).put("rol", rol).put("jornada", jornada))
        if (activo != miembro.activo) {
            r = pedir("adminEstado", JSONObject().put("objetivo", miembro.correo).put("estado", if (activo) "ACTIVO" else "INACTIVO"))
        }
        aviso(r.optString("mensaje", "Cambios guardados."))
        leerMiembros()
    }

    /** Eliminar: se le cierra el acceso y deja de aparecer (su historial queda en la hoja). */
    fun eliminarMiembro(miembro: Miembro) = ejecutar {
        val r = pedir("adminEliminarMiembro", JSONObject().put("objetivo", miembro.correo))
        aviso(r.optString("mensaje"))
        leerMiembros()
    }

    fun cargarJornadas() = ejecutar { leerJornadas() }

    private suspend fun leerJornadas() {
        val r = pedir("adminJornadas")
        val enUso = r.optJSONObject("enUso") ?: JSONObject()
        val lista = lista(r.getJSONArray("jornadas"), Horario::deJson)
        _estado.update { it.copy(jornadas = lista.map { h -> h to enUso.optInt(h.codigo, 0) }, horarios = lista) }
    }

    /** Crea (código vacío) o edita una jornada. */
    fun guardarJornada(horario: Horario) = ejecutar {
        val r = pedir("adminGuardarJornada", JSONObject().put("jornada", horario.aJson()))
        aviso(r.optString("mensaje"))
        leerJornadas()
    }

    fun eliminarJornada(horario: Horario) = ejecutar {
        val r = pedir("adminEliminarJornada", JSONObject().put("codigo", horario.codigo))
        aviso(r.optString("mensaje"))
        leerJornadas()
    }

    fun liberarTelefono(miembro: Miembro) = ejecutar {
        val r = pedir("adminLiberar", JSONObject().put("objetivo", miembro.correo))
        aviso(r.optString("mensaje"))
        leerMiembros()
    }

    fun cargarCalendario() = ejecutar {
        leerEventos()
        if (_estado.value.miembros == null) leerMiembros()
    }

    private suspend fun leerEventos() {
        val r = pedir("adminEventos")
        _estado.update { it.copy(eventos = lista(r.getJSONArray("eventos"), Evento::deJson).sortedBy { e -> e.desde }) }
    }

    fun agregarEvento(evento: Evento) = ejecutar {
        val r = pedir("adminAgregarEvento", JSONObject().put("evento", evento.aJson()))
        aviso(r.optString("mensaje"))
        leerEventos()
    }

    fun eliminarEvento(evento: Evento) = ejecutar {
        val r = pedir("adminEliminarEvento", JSONObject().put("id", evento.id))
        aviso(r.optString("mensaje"))
        leerEventos()
    }

    fun cargarActividades() = ejecutar { leerActividades() }

    private suspend fun leerActividades() {
        val r = pedir("adminActividades")
        _estado.update { it.copy(actividades = lista(r.getJSONArray("actividades")) { j ->
            ActividadPorRevisar(
                id = j.getString("id"),
                fecha = j.optString("fecha"),
                nombre = j.optString("nombre"),
                entrada = j.optString("entrada"),
                salida = j.optString("salida"),
                descripcion = j.optString("descripcion"),
                ubicacion = j.optString("ubicacion"),
            )
        }) }
    }

    fun revisarActividad(actividad: ActividadPorRevisar, aprobar: Boolean) = ejecutar {
        val r = pedir("adminRevisarActividad", JSONObject().put("id", actividad.id).put("aprobar", aprobar))
        aviso(r.optString("mensaje"))
        leerActividades()
    }

    // ---------- Roles ----------

    fun cargarRoles() = ejecutar { leerRoles() }

    private suspend fun leerRoles() {
        val r = pedir("adminRoles")
        val enUso = r.optJSONObject("enUso") ?: JSONObject()
        val roles = lista(r.getJSONArray("roles"), Rol::deJson)
        _estado.update { it.copy(roles = roles, rolesEnUso = roles.associate { rol -> rol.codigo to enUso.optInt(rol.codigo, 0) }) }
    }

    /** Crea (código vacío) o edita un rol: nombre, si marca asistencia y sus funciones. */
    fun guardarRol(rol: Rol) = ejecutar {
        val r = pedir("adminGuardarRol", JSONObject().put("rol", rol.aJson()))
        aviso(r.optString("mensaje"))
        leerRoles()
    }

    fun eliminarRol(rol: Rol) = ejecutar {
        val r = pedir("adminEliminarRol", JSONObject().put("codigo", rol.codigo))
        aviso(r.optString("mensaje"))
        leerRoles()
    }

    // ---------- Solicitudes de permiso ----------

    fun cargarSolicitudes() = ejecutar { leerSolicitudes() }

    private suspend fun leerSolicitudes() {
        val r = pedir("adminSolicitudes")
        _estado.update { it.copy(solicitudes = lista(r.getJSONArray("solicitudes"), Solicitud::deJson)) }
    }

    /** Aprobar agrega el permiso al calendario de la persona (lo hace el servidor). */
    fun revisarSolicitud(s: Solicitud, aprobar: Boolean, respuesta: String) = ejecutar {
        val r = pedir("adminRevisarSolicitud", JSONObject().put("id", s.id).put("aprobar", aprobar).put("respuesta", respuesta.trim()))
        aviso(r.optString("mensaje"))
        leerSolicitudes()
    }

    // ---------- Reporte mensual ----------

    /** [hace] = 0 este mes, 1 el mes pasado, 2 hace dos meses. */
    fun generarReporte(hace: Int) = ejecutar {
        val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, -hace) }
        val mes = "%04d-%02d".format(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1)
        val titulo = formatear("MMMM yyyy", cal.timeInMillis)
        val r = pedir("adminReporte", JSONObject().put("mes", mes))
        val filas = lista(r.getJSONArray("resumen")) { j ->
            FilaReporte(
                nombre = j.optString("nombre"),
                rol = j.optString("rol"),
                jornada = j.optString("jornada"),
                dias = j.optInt("dias"),
                puntuales = j.optInt("puntuales"),
                tardes = j.optInt("tardes"),
                faltas = j.optInt("faltas"),
                actividades = j.optInt("actividades"),
                minutos = j.optLong("minutos"),
                minutosExtra = j.optLong("extra"),
                porRevisar = j.optInt("porRevisar"),
            )
        }
        val detalle = r.getJSONArray("detalle")
        _estado.update { it.copy(reporte = ReporteMes(titulo, "asistencia_$mes.csv", csvReporte(titulo, filas, detalle), filas)) }
    }

    /** CSV con ";" y BOM UTF-8 para que Excel en español lo abra bien (tildes y columnas). */
    private fun csvReporte(titulo: String, filas: List<FilaReporte>, detalle: JSONArray): String {
        fun celda(v: Any?) = v.toString().replace(";", ",").replace("\n", " ")
        fun horas(m: Long) = "%d:%02d".format(m / 60, m % 60)
        return buildString {
            append('﻿')
            append("Colegio Mesón — Asistencia de ${celda(titulo)}\n\n")
            append("RESUMEN\n")
            append("Nombre;Rol;Jornada;Días asistidos;Puntuales;Tarde;Faltas;Actividades especiales;Horas;Horas extra verificadas;Días por revisar\n")
            filas.forEach { f ->
                append(listOf(f.nombre, f.rol, f.jornada, f.dias, f.puntuales, f.tardes, f.faltas, f.actividades,
                    horas(f.minutos), horas(f.minutosExtra), f.porRevisar).joinToString(";") { celda(it) })
                append('\n')
            }
            append("\nDETALLE POR DÍA\n")
            append("Nombre;Fecha;Entrada;Salida;Horas;Estado;Revisión\n")
            for (i in 0 until detalle.length()) {
                val d = detalle.getJSONObject(i)
                append(listOf("nombre", "fecha", "entrada", "salida", "horas", "estado", "revision")
                    .joinToString(";") { celda(d.optString(it)) })
                append('\n')
            }
        }
    }

    // ---------- Ajustes: reglas y ubicación del colegio ----------

    fun cargarAjustes() = ejecutar { leerAjustes() }

    private suspend fun leerAjustes() {
        val r = pedir("adminConfig")
        _estado.update {
            it.copy(config = AjustesColegio(r.optInt("horasMeta", 8), r.optInt("tolerancia", 0), lista(r.getJSONArray("sedes"), Sede::deJson)))
        }
    }

    fun guardarReglas(horasMeta: Int, tolerancia: Int) = ejecutar {
        val r = pedir("adminGuardarReglas", JSONObject().put("horasMeta", horasMeta).put("tolerancia", tolerancia))
        aviso(r.optString("mensaje"))
        leerAjustes()
    }

    /**
     * Crea ([original] = null) o edita la ubicación del colegio. [punto] = coordenadas elegidas a mano;
     * null = el lugar donde está parado el administrador ahora. Al editar, [moverAqui] = false conserva el punto.
     */
    fun guardarSede(original: Sede?, nombre: String, radio: Int, punto: Pair<Double, Double>?, moverAqui: Boolean) = ejecutar {
        val (lat, lon) = when {
            punto != null -> punto
            original != null && !moverAqui -> original.latitud to original.longitud
            else -> ubicacionActual()
        }
        val sede = JSONObject().put("nombre", nombre.trim()).put("radio", radio).put("latitud", lat).put("longitud", lon)
        val r = if (original == null) pedir("adminAgregarSede", JSONObject().put("sede", sede))
        else pedir("adminEditarSede", JSONObject().put("sede", sede.put("id", original.id)))
        aviso(r.optString("mensaje"))
        leerAjustes()
    }

    private suspend fun ubicacionActual(): Pair<Double, Double> {
        val app = getApplication<Application>()
        if (!ubicacionConcedida(app)) throw IllegalStateException("Activa el permiso de ubicación de la app.")
        val lectura = Ubicacion.obtener(app)
        if (lectura.simulada) throw IllegalStateException("Se detectó una ubicación simulada. Desactiva el GPS falso.")
        if (lectura.precisionMetros > 50) {
            throw IllegalStateException("La señal GPS es imprecisa (±${lectura.precisionMetros.toInt()} m). Sal a un lugar abierto e inténtalo de nuevo.")
        }
        return lectura.latitud to lectura.longitud
    }

    fun eliminarSede(sede: Sede) = ejecutar {
        val r = pedir("adminEliminarSede", JSONObject().put("id", sede.id))
        aviso(r.optString("mensaje"))
        leerAjustes()
    }

    /** Elige cómo se comprueba la presencia. Con Wi-Fi registra las redes que este teléfono ve ahora. */
    fun guardarVerificacion(sede: Sede, metodo: MetodoVerificacion, registrarWifi: Boolean) = ejecutar {
        val app = getApplication<Application>()
        val datos = JSONObject().put("id", sede.id).put("metodo", metodo.name)
        if (metodo == MetodoVerificacion.WIFI && (registrarWifi || sede.wifi.isEmpty())) {
            if (!WifiCercano.wifiEncendido(app)) throw IllegalStateException("Enciende el Wi-Fi del teléfono para registrar la red del colegio.")
            datos.put("wifi", JSONArray(WifiCercano.cercanos(app)))
        }
        val r = pedir("adminGuardarVerificacion", datos)
        aviso(r.optString("mensaje"))
        leerAjustes()
    }

    /** Clave del QR de la sede y diferencia (segundos) entre la hora del servidor y la del teléfono. */
    suspend fun secretoQr(sede: Sede): Pair<String, Long> {
        val r = pedir("adminSecretoQr", JSONObject().put("id", sede.id))
        return r.getString("secreto") to (r.getLong("ahora") - System.currentTimeMillis() / 1000)
    }

    fun mensajeMostrado(mensaje: Mensaje) = _estado.update { if (it.mensaje === mensaje) it.copy(mensaje = null) else it }

    private fun aviso(texto: String) = _estado.update { it.copy(mensaje = Mensaje(texto, error = false)) }

    private suspend fun pedir(accion: String, datos: JSONObject = JSONObject()): JSONObject {
        val cuerpo = datosDelTelefono(getApplication())
        datos.keys().forEach { cuerpo.put(it, datos.get(it)) }
        return SheetsApi.enviar(accion, cuerpo)
    }

    /** Roles como objetos; una versión anterior del servidor los manda solo como códigos ("DOCENTE"). */
    private fun leerListaRoles(a: JSONArray): List<Rol> = (0 until a.length()).map { i ->
        a.optJSONObject(i)?.let(Rol::deJson) ?: a.optString(i).let { codigo ->
            Rol.deJson(JSONObject().put("codigo", codigo).put("nombre", codigo.lowercase().replaceFirstChar { c -> c.uppercase() })
                .put("marca", codigo != "ADMINISTRADOR"))
        }
    }

    private fun <T> lista(arreglo: JSONArray, convertir: (JSONObject) -> T): List<T> =
        (0 until arreglo.length()).map { convertir(arreglo.getJSONObject(it)) }

    private fun ejecutar(bloque: suspend () -> Unit) = viewModelScope.launch {
        if (_estado.value.cargando) return@launch
        _estado.update { it.copy(cargando = true) }
        try {
            bloque()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            _estado.update { it.copy(mensaje = Mensaje("Sin conexión con el servidor. El panel necesita internet.", error = true)) }
        } catch (e: Exception) {
            // "Acción no válida" = el servidor publicado es una versión anterior que no tiene esta función.
            val texto = if (e.message == "Acción no válida.") {
                "El servidor todavía no tiene esta función. Publica la nueva versión del servidor " +
                    "(Implementar → Gestionar implementaciones → Nueva versión)."
            } else e.message ?: "Ocurrió un error inesperado."
            _estado.update { it.copy(mensaje = Mensaje(texto, error = true)) }
        } finally {
            _estado.update { it.copy(cargando = false) }
        }
    }
}
