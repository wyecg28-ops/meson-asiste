/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.datos

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Funciones de administración que el administrador puede activar en cada rol. */
enum class Permiso(val etiqueta: String) {
    HOY("Ver Hoy"),
    SOLICITUDES("Revisar solicitudes de permiso"),
    MIEMBROS("Gestionar miembros"),
    JORNADAS("Gestionar jornadas"),
    CALENDARIO("Gestionar calendario"),
    REVISION("Revisar marcas por revisar"),
    REPORTES("Ver reportes"),
    AJUSTES("Ajustes del colegio"),
    ROLES("Gestionar roles");

    companion object {
        fun de(codigo: String?) = entries.firstOrNull { it.name == codigo }
    }
}

/**
 * Rol de una persona (hoja "Roles", lo administra el administrador): si marca asistencia y qué funciones de
 * administración tiene. "ADMINISTRADOR" tiene todas.
 */
data class Rol(
    val codigo: String,
    val nombre: String,
    val marca: Boolean,
    val permisos: Set<Permiso>,
) {
    val etiqueta get() = nombre
    val esAdministrador get() = codigo == "ADMINISTRADOR"

    /** Tiene alguna función de administración: ve el panel Admin. */
    val gestiona get() = esAdministrador || permisos.isNotEmpty()

    fun puede(permiso: Permiso) = esAdministrador || permiso in permisos

    fun aJson(): JSONObject = JSONObject()
        .put("codigo", codigo).put("nombre", nombre).put("marca", marca)
        .put("permisos", JSONArray(permisos.map { it.name }))

    companion object {
        val DOCENTE = Rol("DOCENTE", "Docente", marca = true, permisos = emptySet())

        fun deJson(j: JSONObject) = Rol(
            codigo = j.optString("codigo", "DOCENTE"),
            nombre = j.optString("nombre", j.optString("codigo", "Docente")),
            marca = j.optBoolean("marca", true),
            permisos = j.optJSONArray("permisos")?.let { a -> (0 until a.length()).mapNotNull { Permiso.de(a.getString(it)) } }
                ?.toSet() ?: emptySet(),
        )
    }
}

enum class TipoEvento(val etiqueta: String, val diaLibre: Boolean) {
    VACACIONES("Vacaciones", true),
    FERIADO("Feriado o asueto", true),
    PERMISO("Permiso", true),
    ENTRADA_ESPECIAL("Entrada y salida especial", false);

    companion object {
        fun de(codigo: String?) = entries.firstOrNull { it.name == codigo } ?: PERMISO
    }
}

/** Minutos desde medianoche de un texto "HH:mm" (o "HH:mm:ss"). */
fun aMinutos(hora: String): Int {
    val partes = hora.split(":")
    return (partes.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (partes.getOrNull(1)?.toIntOrNull() ?: 0)
}

/** Minutos entre entrada y salida. Si la salida es igual o menor, termina al día siguiente (18:00 → 02:00 = 8 h). */
fun duracionHorario(entrada: String, salida: String): Int {
    val minutos = aMinutos(salida) - aMinutos(entrada)
    return if (minutos <= 0) minutos + 1440 else minutos
}

/** ¿El horario termina al día siguiente? (turno nocturno) */
fun terminaDiaSiguiente(entrada: String, salida: String) = entrada.isNotBlank() && salida.isNotBlank() &&
    aMinutos(salida) <= aMinutos(entrada)

/** "2026-11-04" del día de un instante, en la zona del teléfono. */
fun claveDia(millis: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

/** Letra de cada día de la semana: 1 = lunes … 7 = domingo. */
val LETRAS_DIAS = mapOf(1 to "L", 2 to "M", 3 to "X", 4 to "J", 5 to "V", 6 to "S", 7 to "D")
val NOMBRES_DIAS = mapOf(1 to "Lunes", 2 to "Martes", 3 to "Miércoles", 4 to "Jueves", 5 to "Viernes", 6 to "Sábado", 7 to "Domingo")

/** Día de la semana de un instante: 1 = lunes … 7 = domingo. */
fun diaIso(millis: Long): Int {
    val d = java.util.Calendar.getInstance().apply { timeInMillis = millis }.get(java.util.Calendar.DAY_OF_WEEK)
    return if (d == java.util.Calendar.SUNDAY) 7 else d - 1
}

/** Entrada y salida de un día. */
data class HoraDia(val entrada: String, val salida: String)

/**
 * Una jornada de trabajo asignada por el administrador, p. ej. "Jornada A" de 07:00 a 16:00, de lunes a viernes.
 * [porDia] = días con horario distinto al general (p. ej. sábado 08:00 a 12:00). La salida puede ser del día
 * siguiente (turno nocturno: 18:00 a 02:00).
 */
data class Horario(
    val codigo: String,
    val nombre: String,
    val entrada: String,
    val salida: String,
    val dias: List<Int> = listOf(1, 2, 3, 4, 5),
    val porDia: Map<Int, HoraDia> = emptyMap(),
) {
    val minutoEntrada get() = aMinutos(entrada)
    val minutoSalida get() = aMinutos(salida)
    val texto get() = "$entrada – $salida"

    /** "L a V", "L a S" o "L M X V". */
    val textoDias: String
        get() {
            val orden = dias.sorted()
            val consecutivos = orden.size >= 3 && orden.zipWithNext().all { (a, b) -> b == a + 1 }
            return if (consecutivos) "${LETRAS_DIAS[orden.first()]} a ${LETRAS_DIAS[orden.last()]}"
            else orden.joinToString(" ") { LETRAS_DIAS[it].orEmpty() }
        }

    fun trabaja(millis: Long) = diaIso(millis) in dias

    /** Horario de un día de la semana (1 = lunes … 7 = domingo). */
    fun delDia(iso: Int): HoraDia = porDia[iso] ?: HoraDia(entrada, salida)

    fun aJson(): JSONObject = JSONObject()
        .put("codigo", codigo).put("nombre", nombre).put("entrada", entrada).put("salida", salida)
        .put("dias", JSONArray(dias))
        .put("porDia", JSONObject().apply {
            porDia.forEach { (d, h) -> put(d.toString(), JSONObject().put("entrada", h.entrada).put("salida", h.salida)) }
        })

    companion object {
        fun deJson(j: JSONObject): Horario {
            val porDia = mutableMapOf<Int, HoraDia>()
            j.optJSONObject("porDia")?.let { o ->
                o.keys().forEach { k ->
                    val h = o.optJSONObject(k)
                    val d = k.toIntOrNull()
                    if (h != null && d != null) porDia[d] = HoraDia(h.optString("entrada"), h.optString("salida"))
                }
            }
            return Horario(
                codigo = j.getString("codigo"),
                nombre = j.optString("nombre", j.getString("codigo")),
                entrada = j.getString("entrada"),
                salida = j.getString("salida"),
                dias = j.optJSONArray("dias")?.let { a -> (0 until a.length()).map { a.getInt(it) } }
                    ?.takeIf { it.isNotEmpty() } ?: listOf(1, 2, 3, 4, 5),
                porDia = porDia,
            )
        }
    }
}

/** Ubicación del colegio (geocerca) y cómo se comprueba la presencia: UBICACION, WIFI o QR. */
data class Sede(
    val id: String,
    val nombre: String,
    val latitud: Double,
    val longitud: Double,
    val radio: Int,
    val metodo: String,
    val wifi: List<String> = emptyList(),
    val wifiRegistrados: Int = 0,
) {
    fun aJson(): JSONObject = JSONObject()
        .put("id", id).put("nombre", nombre).put("latitud", latitud).put("longitud", longitud)
        .put("radio", radio).put("metodo", metodo).put("wifiRegistrados", wifiRegistrados)

    companion object {
        fun deJson(j: JSONObject) = Sede(
            id = j.getString("id"),
            nombre = j.optString("nombre", "Sede"),
            latitud = j.optDouble("latitud"),
            longitud = j.optDouble("longitud"),
            radio = j.optInt("radio", 80),
            metodo = j.optString("metodo", "UBICACION"),
            wifi = j.optJSONArray("wifi")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            wifiRegistrados = j.optInt("wifiRegistrados", 0),
        )
    }
}

/** Vacaciones, feriado, permiso o entrada especial del calendario del administrador. */
data class Evento(
    val id: String,
    val desde: String,
    val hasta: String,
    val tipo: TipoEvento,
    val aplicaA: String,
    val horaEntrada: String,
    val detalle: String,
    /** Solo en permisos por horas, p. ej. "10:00" a "12:00". Vacío = permiso de día completo. */
    val horaInicio: String = "",
    val horaFin: String = "",
    /** Solo en "Entrada y salida especial": hora de salida de ese día (opcional; puede ser del día siguiente). */
    val horaSalida: String = "",
) {
    fun incluye(clave: String) = clave >= desde && clave <= hasta
    val paraTodos get() = aplicaA.equals("todos", ignoreCase = true)
    val porHoras get() = tipo == TipoEvento.PERMISO && horaInicio.isNotBlank() && horaFin.isNotBlank()

    /** Vacaciones, feriado o permiso de todo el día: ese día no se espera que marque. */
    val diaCompletoLibre get() = tipo.diaLibre && !porHoras
    val minutosPermiso get() = if (porHoras) maxOf(0, aMinutos(horaFin) - aMinutos(horaInicio)) else 0

    fun aJson(): JSONObject = JSONObject()
        .put("id", id).put("desde", desde).put("hasta", hasta).put("tipo", tipo.name)
        .put("aplicaA", aplicaA).put("horaEntrada", horaEntrada).put("detalle", detalle)
        .put("horaInicio", horaInicio).put("horaFin", horaFin).put("horaSalida", horaSalida)

    companion object {
        fun deJson(j: JSONObject) = Evento(
            id = j.optString("id"),
            desde = j.getString("desde"),
            hasta = j.optString("hasta", j.getString("desde")),
            tipo = TipoEvento.de(j.optString("tipo")),
            aplicaA = j.optString("aplicaA", "Todos"),
            horaEntrada = j.optString("horaEntrada"),
            detalle = j.optString("detalle"),
            horaInicio = j.optString("horaInicio"),
            horaFin = j.optString("horaFin"),
            horaSalida = j.optString("horaSalida"),
        )
    }
}

/** Mejor racha de puntualidad y quien sumó más horas en la semana (entre todo el personal). */
data class Destacados(
    val semana: String,
    val rachaNombre: String,
    val racha: Int,
    val horasNombre: String,
    val horasMinutos: Long,
) {
    val hayDatos get() = racha > 0 || horasMinutos > 0

    fun aJson(): JSONObject = JSONObject()
        .put("semana", semana)
        .put("mejorRacha", JSONObject().put("nombre", rachaNombre).put("valor", racha))
        .put("masHoras", JSONObject().put("nombre", horasNombre).put("minutos", horasMinutos))

    companion object {
        fun deJson(j: JSONObject?): Destacados? {
            j ?: return null
            val r = j.optJSONObject("mejorRacha")
            val h = j.optJSONObject("masHoras")
            return Destacados(
                semana = j.optString("semana"),
                rachaNombre = r?.optString("nombre").orEmpty(),
                racha = r?.optInt("valor") ?: 0,
                horasNombre = h?.optString("nombre").orEmpty(),
                horasMinutos = h?.optLong("minutos") ?: 0,
            )
        }
    }
}

/** Lo que el servidor sabe de esta persona: su rol, su jornada, la meta de horas y su calendario. */
data class Perfil(
    val rol: Rol,
    val horario: Horario,
    val horarios: List<Horario>,
    val tolerancia: Int,
    val metaMinutos: Int,
    val eventos: List<Evento>,
    val sedes: List<Sede> = emptyList(),
    val destacados: Destacados? = null,
) {
    val esAdministrador get() = rol.esAdministrador

    /** Tiene funciones de administración: ve el panel Admin. */
    val esGestor get() = rol.gestiona

    /** Su rol marca asistencia (entrada, salida, permisos y recordatorios). */
    val marca get() = rol.marca

    fun puede(permiso: Permiso) = rol.puede(permiso)

    /** ¿Ese día es laboral en la jornada de la persona? */
    fun esDiaLaboral(dia: Long) = horario.trabaja(dia)
    val horasMeta get() = metaMinutos / 60

    fun eventosDel(dia: Long): List<Evento> = claveDia(dia).let { c -> eventos.filter { it.incluye(c) } }

    /** Vacaciones, feriado o permiso de todo el día (no se espera que marque). */
    fun diaLibre(dia: Long): Evento? = eventosDel(dia).firstOrNull { it.diaCompletoLibre }

    fun entradaEspecial(dia: Long): Evento? =
        eventosDel(dia).firstOrNull { it.tipo == TipoEvento.ENTRADA_ESPECIAL && it.horaEntrada.isNotBlank() }

    /** Permisos por horas de ese día. */
    fun permisosPorHoras(dia: Long): List<Evento> = eventosDel(dia).filter { it.porHoras }

    /** Minutos de permiso de ese día: cuentan para la meta de horas. */
    fun minutosPermiso(dia: Long): Int = permisosPorHoras(dia).sumOf { it.minutosPermiso }

    /** Horario de la jornada ese día (puede ser distinto según el día de la semana). */
    fun horarioDelDia(dia: Long): HoraDia = horario.delDia(diaIso(dia))

    /**
     * Hora de entrada de ese día: la de la jornada para ese día o la de una entrada especial; si un permiso por
     * horas cubre esa hora, la entrada se corre al final del permiso.
     */
    fun horaEntrada(dia: Long): String {
        var hora = entradaEspecial(dia)?.horaEntrada ?: horarioDelDia(dia).entrada
        permisosPorHoras(dia).forEach { p ->
            if (aMinutos(p.horaInicio) <= aMinutos(hora) && aMinutos(p.horaFin) > aMinutos(hora)) hora = p.horaFin
        }
        return hora
    }

    /** Hora de salida de ese día: la de la jornada para ese día o la de un horario especial. */
    fun horaSalida(dia: Long): String =
        entradaEspecial(dia)?.horaSalida?.takeIf { it.isNotBlank() } ?: horarioDelDia(dia).salida

    /** Meta de ese día: la de Configuración (8 h), o lo que dure el horario de ese día (o el especial) si es menos. */
    fun metaDelDia(dia: Long): Int {
        val especial = entradaEspecial(dia)
        val duracion = if (especial != null && especial.horaSalida.isNotBlank()) {
            duracionHorario(especial.horaEntrada, especial.horaSalida)
        } else horarioDelDia(dia).let { duracionHorario(it.entrada, it.salida) }
        return minOf(metaMinutos, duracion)
    }

    /** Entrar en este minuto o antes cuenta como puntual. */
    fun limiteEntrada(dia: Long): Int = aMinutos(horaEntrada(dia)) + tolerancia

    fun aJson(): JSONObject = JSONObject()
        .put("rol", rol.codigo)
        .put("rolInfo", rol.aJson())
        .put("jornada", horario.aJson())
        .put("jornadas", JSONArray().apply { horarios.forEach { put(it.aJson()) } })
        .put("tolerancia", tolerancia)
        .put("horasMeta", metaMinutos / 60.0)
        .put("eventos", JSONArray().apply { eventos.forEach { put(it.aJson()) } })
        .put("sedes", JSONArray().apply { sedes.forEach { put(it.aJson()) } })
        .apply { destacados?.let { put("destacados", it.aJson()) } }

    companion object {
        private val JORNADA_A = Horario("A", "Jornada A", "07:00", "16:00")
        private val JORNADA_B = Horario("B", "Jornada B", "09:30", "18:30")

        /** Mientras no llega el perfil del servidor (por ejemplo, sin internet la primera vez). */
        val POR_DEFECTO = Perfil(Rol.DOCENTE, JORNADA_A, listOf(JORNADA_A, JORNADA_B), 0, 8 * 60, emptyList())

        /** Rol de una versión anterior del servidor (solo el código). */
        private fun rolAnterior(codigo: String) = when (codigo) {
            "ADMINISTRADOR" -> Rol("ADMINISTRADOR", "Administrador", false, Permiso.entries.toSet())
            "SUPERVISOR" -> Rol("SUPERVISOR", "Supervisor", true,
                setOf(Permiso.HOY, Permiso.SOLICITUDES, Permiso.CALENDARIO, Permiso.REVISION, Permiso.REPORTES))
            else -> Rol.DOCENTE.copy(codigo = codigo.ifBlank { "DOCENTE" })
        }

        /** Lee tanto la respuesta de la acción "perfil" del servidor como lo guardado en el teléfono. */
        fun deJson(j: JSONObject): Perfil {
            val horarios = j.optJSONArray("jornadas")?.let { a -> (0 until a.length()).map { Horario.deJson(a.getJSONObject(it)) } }
            return Perfil(
                rol = j.optJSONObject("rolInfo")?.let(Rol::deJson) ?: rolAnterior(j.optString("rol")),
                horario = j.optJSONObject("jornada")?.let(Horario::deJson) ?: POR_DEFECTO.horario,
                horarios = horarios?.takeIf { it.isNotEmpty() } ?: POR_DEFECTO.horarios,
                tolerancia = j.optInt("tolerancia", 0),
                metaMinutos = (j.optDouble("horasMeta", 8.0) * 60).toInt(),
                eventos = j.optJSONArray("eventos")?.let { a -> (0 until a.length()).map { Evento.deJson(a.getJSONObject(it)) } }
                    ?: emptyList(),
                sedes = j.optJSONArray("sedes")?.let { a -> (0 until a.length()).map { Sede.deJson(a.getJSONObject(it)) } }
                    ?: emptyList(),
                destacados = Destacados.deJson(j.optJSONObject("destacados")),
            )
        }
    }
}
