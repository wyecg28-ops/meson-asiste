/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.datos

import java.util.Calendar
import kotlin.math.max
import kotlin.math.roundToInt

// Todas las fechas se calculan en la zona horaria del teléfono.

/** Minuto del día (0–1439) de un instante. */
fun minutoDelDia(millis: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

/** Medianoche del día de un instante. */
fun inicioDelDia(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun esDiaLaboral(millis: Long): Boolean {
    val dia = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)
    return dia != Calendar.SATURDAY && dia != Calendar.SUNDAY
}

fun formatoHora(minutoDelDia: Int) = "%02d:%02d".format(minutoDelDia / 60, minutoDelDia % 60)

/** "45 min", "7 h", "7 h 50 min". */
fun formatoDuracion(minutos: Long): String {
    val h = minutos / 60
    val m = minutos % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h $m min"
    }
}

/** Versión compacta para mosaicos: "38h 20m". */
fun formatoDuracionCorta(minutos: Long) = if (minutos < 60) "${minutos}m" else "${minutos / 60}h ${minutos % 60}m"

/** Lo ocurrido en un día: su entrada y su salida válidas, más todas las marcas (también rechazadas). */
class Jornada(val dia: Long, val marcas: List<Marca>, private val perfil: Perfil) {
    private val validas = marcas.filter { it.estado != EstadoMarca.RECHAZADA }
    val entrada = validas.filter { it.tipo == TipoRegistro.ENTRADA.name }.minByOrNull { it.fechaHora }
    val salida = validas.filter { it.tipo == TipoRegistro.SALIDA.name }.maxByOrNull { it.fechaHora }
    val minutoEntrada = entrada?.let { minutoDelDia(it.fechaHora) }
    val minutoSalida = salida?.let { minutoDelDia(it.fechaHora) }

    /** Algún registro del día fue de una actividad especial: no se evalúa la puntualidad y la dirección lo revisa. */
    val actividad = validas.any { it.actividad }

    /** Vacaciones, feriado o permiso de ese día. */
    val libre: Evento? = perfil.diaLibre(dia)
    val horaEntradaEsperada = perfil.horaEntrada(dia)
    /** Meta de ese día (8 h, o la duración de un horario especial). */
    val meta = perfil.metaDelDia(dia)
    private val limite = perfil.limiteEntrada(dia)

    /** Minutos de permiso por horas de ese día: se suman a lo trabajado para la meta. */
    val minutosPermiso = perfil.minutosPermiso(dia)

    /** null si no hay entrada o si fue actividad especial. Se compara por minuto (07:00:59 es puntual). */
    val puntual: Boolean? = if (actividad) null else minutoEntrada?.let { it <= limite }
    val minutosTarde = if (actividad) 0 else minutoEntrada?.let { max(0, it - limite) } ?: 0

    /** La salida fue al día siguiente (turno nocturno). */
    val salidaDiaSiguiente get() = salida != null && inicioDelDia(salida.fechaHora) != dia

    /** Permisos por horas de ese día, como intervalos (inicio y fin en milisegundos). */
    private val intervalosPermiso: List<Pair<Long, Long>> = perfil.permisosPorHoras(dia).map {
        (dia + aMinutos(it.horaInicio) * 60_000L) to (dia + aMinutos(it.horaFin) * 60_000L)
    }

    /**
     * Minutos entre entrada y salida (si el turno sigue abierto, hasta [ahora]), sin el tiempo de permiso que cae
     * en medio (por ejemplo, con permiso de 12:00 a 13:00 de 7:00 a 16:00 son 8 h; ese permiso cuenta para la meta)
     * ni el almuerzo de la jornada, que no cuenta como trabajo.
     */
    fun minutosTrabajados(ahora: Long): Long? {
        val e = entrada ?: return null
        val fin = salida?.fechaHora ?: if (enCurso(ahora)) ahora else return null
        val inicio = e.fechaHora
        val permiso = intervalosPermiso.sumOf { (i0, f0) ->
            // En un turno nocturno, un permiso antes de la hora de entrada es del día siguiente.
            val (i, f) = if (i0 < inicio && fin - dia > 24 * 60 * 60_000L) (i0 + 86_400_000L) to (f0 + 86_400_000L) else i0 to f0
            max(0L, minOf(f, fin) - maxOf(i, inicio))
        }
        return perfil.sinAlmuerzo(max(0L, (fin - inicio - permiso) / 60_000), dia)
    }

    /** null mientras no haya salida. */
    fun cumplioMeta(ahora: Long): Boolean? =
        if (salida == null) null else (minutosTrabajados(ahora) ?: 0) + minutosPermiso >= meta

    /** Minutos que faltaron para la meta (contando el permiso). */
    fun minutosFaltantes(ahora: Long): Long = max(0L, meta - minutosPermiso - (minutosTrabajados(ahora) ?: 0L))

    /** Horas extra: lo trabajado después de cumplir la meta del día (solo con salida marcada). */
    fun minutosExtra(ahora: Long): Long =
        if (salida == null) 0 else max(0L, (minutosTrabajados(ahora) ?: 0L) - meta)

    /** Hay entrada sin salida y no han pasado más de 20 h (un turno puede terminar al día siguiente). */
    fun enCurso(ahora: Long) = entrada != null && salida == null && ahora - entrada.fechaHora in 0 until LIMITE_TURNO
}

/** Tiempo máximo entre la entrada y la salida de un mismo turno (también nocturno). */
const val LIMITE_TURNO = 20 * 60 * 60_000L

/**
 * Agrupa las marcas por día, el más reciente primero. Una salida de madrugada sin entrada ese día se une a la
 * entrada abierta del día anterior (turno nocturno, p. ej. 18:00 a 02:00).
 */
fun agruparPorDia(marcas: List<Marca>, perfil: Perfil): List<Jornada> {
    val grupos = linkedMapOf<Long, MutableList<Marca>>()
    var abierta: Pair<Long, Long>? = null // día y hora de la entrada sin salida
    marcas.sortedBy { it.fechaHora }.forEach { m ->
        var dia = inicioDelDia(m.fechaHora)
        val valida = m.estado != EstadoMarca.RECHAZADA
        if (m.tipo == TipoRegistro.SALIDA.name) {
            val a = abierta
            val sinEntradaHoy = grupos[dia]?.none { it.tipo == TipoRegistro.ENTRADA.name && it.estado != EstadoMarca.RECHAZADA } ?: true
            if (a != null && a.first != dia && sinEntradaHoy && m.fechaHora - a.second <= LIMITE_TURNO) dia = a.first
        }
        grupos.getOrPut(dia) { mutableListOf() } += m
        if (valida) {
            if (m.tipo == TipoRegistro.ENTRADA.name) {
                if (abierta == null) abierta = dia to m.fechaHora
            } else if (m.tipo == TipoRegistro.SALIDA.name) abierta = null
        }
    }
    return grupos.map { (dia, lista) -> Jornada(dia, lista, perfil) }.sortedByDescending { it.dia }
}

enum class Periodo(val etiqueta: String) {
    SEMANA("Esta semana"),
    MES("Este mes"),
}

/** Un día laboral del periodo en la gráfica. */
data class Barra(
    val dia: Long,
    val jornada: Jornada?,
    val minutos: Long?,
    val esHoy: Boolean,
    val futuro: Boolean,
    val enCurso: Boolean,
    val libre: Evento?,
)

data class Estadisticas(
    val diasAsistidos: Int,
    val puntuales: Int,
    val tardes: Int,
    val actividades: Int,
    val minutosTardeTotal: Int,
    val minutosTrabajados: Long,
    val metaCumplida: Int,
    val metaIncompleta: Int,
    val minutosFaltantes: Long,
    /** Suma de horas extra del periodo (lo trabajado después de la meta de cada día). */
    val minutosExtra: Long,
    val promedioEntrada: Int?,
    val promedioJornada: Long?,
    val diasSinMarcar: Int,
    val diasLibres: Int,
    val salidasSinMarcar: Int,
    /** Entradas puntuales seguidas, contando hacia atrás desde la más reciente (las actividades no la cortan). */
    val rachaActual: Int,
    val mejorRacha: Int,
    val barras: List<Barra>,
) {
    /** Sobre los días en que se evalúa la puntualidad (sin actividades especiales). */
    val puntualidad: Int? = (puntuales + tardes).takeIf { it > 0 }?.let { (puntuales * 100f / it).roundToInt() }
}

fun calcularEstadisticas(marcas: List<Marca>, perfil: Perfil, periodo: Periodo, ahora: Long): Estadisticas {
    val todas = agruparPorDia(marcas, perfil)
    val conEntrada = todas.filter { it.entrada != null }
    val hoy = inicioDelDia(ahora)
    val (desde, hasta) = limitesPeriodo(periodo, ahora)
    val delPeriodo = conEntrada.filter { it.dia in desde until hasta }
    val porDia = delPeriodo.associateBy { it.dia }
    val completas = delPeriodo.filter { it.salida != null }

    // Los días sin marcar se cuentan desde que la persona empezó a usar la app.
    val primerDia = todas.minOfOrNull { it.dia } ?: hoy
    // Días laborales según la jornada de la persona (por ejemplo, L a V o L a S).
    val diasLaborales = diasEntre(desde, hasta).filter { perfil.esDiaLaboral(it) }

    var mejor = 0
    var actual = 0
    conEntrada.sortedBy { it.dia }.forEach { j ->
        when (j.puntual) {
            true -> actual++
            false -> actual = 0
            null -> Unit
        }
        mejor = max(mejor, actual)
    }

    return Estadisticas(
        diasAsistidos = delPeriodo.size,
        puntuales = delPeriodo.count { it.puntual == true },
        tardes = delPeriodo.count { it.puntual == false },
        actividades = delPeriodo.count { it.actividad },
        minutosTardeTotal = delPeriodo.sumOf { it.minutosTarde },
        minutosTrabajados = delPeriodo.sumOf { it.minutosTrabajados(ahora) ?: 0L },
        metaCumplida = completas.count { it.cumplioMeta(ahora) == true },
        metaIncompleta = completas.count { it.cumplioMeta(ahora) == false },
        minutosFaltantes = completas.sumOf { it.minutosFaltantes(ahora) },
        minutosExtra = completas.sumOf { it.minutosExtra(ahora) },
        promedioEntrada = delPeriodo.mapNotNull { it.minutoEntrada }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        promedioJornada = completas.mapNotNull { it.minutosTrabajados(ahora) }.takeIf { it.isNotEmpty() }
            ?.average()?.roundToInt()?.toLong(),
        diasSinMarcar = diasLaborales.count {
            it in primerDia until hoy && it !in porDia && perfil.diaLibre(it) == null
        },
        diasLibres = diasLaborales.count { it < hasta && perfil.diaLibre(it) != null },
        salidasSinMarcar = delPeriodo.count { it.salida == null && it.dia < hoy },
        rachaActual = actual,
        mejorRacha = mejor,
        barras = diasLaborales.map { dia ->
            val j = porDia[dia]
            Barra(
                dia = dia,
                jornada = j,
                minutos = j?.minutosTrabajados(ahora),
                esHoy = dia == hoy,
                futuro = dia > hoy,
                enCurso = j?.enCurso(ahora) == true,
                libre = perfil.diaLibre(dia),
            )
        },
    )
}

/** [inicio, fin) del periodo que contiene a [ahora]. La semana empieza el lunes. */
private fun limitesPeriodo(periodo: Periodo, ahora: Long): Pair<Long, Long> {
    val c = Calendar.getInstance().apply { timeInMillis = inicioDelDia(ahora) }
    when (periodo) {
        Periodo.SEMANA -> {
            while (c.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) c.add(Calendar.DAY_OF_YEAR, -1)
            val inicio = c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, 7)
            return inicio to c.timeInMillis
        }
        Periodo.MES -> {
            c.set(Calendar.DAY_OF_MONTH, 1)
            val inicio = c.timeInMillis
            c.add(Calendar.MONTH, 1)
            return inicio to c.timeInMillis
        }
    }
}

private fun diasEntre(desde: Long, hasta: Long): List<Long> {
    val c = Calendar.getInstance().apply { timeInMillis = desde }
    val dias = mutableListOf<Long>()
    while (c.timeInMillis < hasta) {
        dias += c.timeInMillis
        c.add(Calendar.DAY_OF_YEAR, 1)
    }
    return dias
}
