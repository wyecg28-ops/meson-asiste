/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.datos

import org.json.JSONObject
import java.io.IOException

/** Permiso o vacaciones que el personal pide a la administración (hoja "Solicitudes"). */
data class Solicitud(
    val id: String,
    val nombre: String,
    val tipo: String,
    val desde: String,
    val hasta: String,
    val horaInicio: String,
    val horaFin: String,
    val motivo: String,
    val estado: String,
    val respuesta: String,
    val correo: String = "",
    /** Rol de quien la pidió (para el PDF). */
    val rol: String = "",
    /** Fecha en que se pidió ("2026-11-19"). */
    val creada: String = "",
    val revisadaPor: String = "",
    val revisadaEl: String = "",
    /** Solo en vacaciones pendientes (panel): cuántos días laborales son y si ya tiene vacaciones en esas fechas. */
    val vista: VistaVacaciones? = null,
) {
    val pendiente get() = estado == "PENDIENTE"
    val porHoras get() = horaInicio.isNotBlank() && horaFin.isNotBlank()

    val tipoTexto: String
        get() = when {
            tipo == "VACACIONES" -> "Vacaciones"
            porHoras -> "Permiso por horas"
            else -> "Permiso"
        }

    val estadoTexto: String
        get() = when (estado) {
            "APROBADA" -> "Aprobada"
            "RECHAZADA" -> "Rechazada"
            "CANCELADA" -> "Cancelada"
            else -> "Pendiente"
        }

    companion object {
        fun deJson(j: JSONObject) = Solicitud(
            id = j.getString("id"),
            nombre = j.optString("nombre"),
            tipo = j.optString("tipo", "PERMISO"),
            desde = j.optString("desde"),
            hasta = j.optString("hasta"),
            horaInicio = j.optString("horaInicio"),
            horaFin = j.optString("horaFin"),
            motivo = j.optString("motivo"),
            estado = j.optString("estado", "PENDIENTE"),
            respuesta = j.optString("respuesta"),
            correo = j.optString("correo"),
            rol = j.optString("rol"),
            creada = j.optString("creada"),
            revisadaPor = j.optString("revisadaPor"),
            revisadaEl = j.optString("revisadaEl"),
            vista = VistaVacaciones.deJson(j.optJSONObject("vista")),
        )
    }
}

/** Mensaje claro para mostrar cuando una llamada al servidor falla. */
fun mensajeDeError(e: Exception): String = when (e) {
    is ServidorException -> e.message ?: "El servidor rechazó la solicitud."
    is IOException -> "Sin conexión con el servidor. Revisa el internet e inténtalo de nuevo."
    else -> e.message ?: "Ocurrió un error inesperado."
}
