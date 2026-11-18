/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.notificaciones

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.asistencia.MainActivity
import com.example.asistencia.R
import com.example.asistencia.datos.Destacados
import com.example.asistencia.datos.Marcas
import com.example.asistencia.datos.Perfil
import com.example.asistencia.datos.Periodo
import com.example.asistencia.datos.Sesion
import com.example.asistencia.datos.aMinutos
import com.example.asistencia.datos.agruparPorDia
import com.example.asistencia.datos.calcularEstadisticas
import com.example.asistencia.datos.diaIso
import com.example.asistencia.datos.formatoDuracion
import com.example.asistencia.datos.inicioDelDia
import com.example.asistencia.datos.terminaDiaSiguiente
import com.example.asistencia.ui.horaAmigable
import java.util.Calendar
import kotlin.math.abs

object Notificaciones {
    private const val CANAL = "asistencia"

    fun permitidas(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED)

    @SuppressLint("MissingPermission")
    fun mostrar(context: Context, id: Int, titulo: String, texto: String) {
        if (!permitidas(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(CANAL, "Recordatorios y logros", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Recordatorios de entrada y salida, alertas de tardanza y resumen semanal." }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }
        val abrir = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notificacion = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(0xFFA62022.toInt())
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notificacion)
        } catch (_: SecurityException) {
            // La persona quitó el permiso justo ahora: no hay nada que hacer.
        }
    }
}

/** Avisos programados según el horario de cada día de la persona. */
enum class Recordatorio(val codigo: Int) {
    /** 15 minutos antes de la entrada. */
    ENTRADA(1),
    ENTRADA_TARDE(2),
    /** 10 minutos antes de la salida. */
    SALIDA(3),
    RESUMEN_SEMANAL(4);

    /**
     * Momento del aviso para el día [dia] (medianoche), o null si ese día no aplica. Usa el horario de ese día
     * (puede ser distinto cada día o especial) y, si la salida es de madrugada, la cuenta al día siguiente.
     */
    fun momento(dia: Long, perfil: Perfil): Long? {
        val trabaja = perfil.horario.trabaja(dia) || perfil.entradaEspecial(dia) != null
        if (!trabaja || perfil.diaLibre(dia) != null) return null
        val entrada = aMinutos(perfil.horaEntrada(dia))
        var salida = aMinutos(perfil.horaSalida(dia))
        if (salida <= entrada) salida += 24 * 60
        val minuto = when (this) {
            ENTRADA -> entrada - 15
            ENTRADA_TARDE -> entrada + perfil.tolerancia + 15
            SALIDA -> salida - 10
            RESUMEN_SEMANAL -> {
                if (diaIso(dia) != (perfil.horario.dias.maxOrNull() ?: 5)) return null
                salida + 30
            }
        }
        return enMinuto(dia, minuto)
    }
}

/** [dia] (medianoche) + [minuto] minutos, respetando cambios de hora. */
private fun enMinuto(dia: Long, minuto: Int): Long = Calendar.getInstance().apply {
    timeInMillis = dia
    add(Calendar.MINUTE, minuto)
}.timeInMillis

object Recordatorios {
    private const val ACCION = "com.example.asistencia.RECORDATORIO"
    private const val EXTRA = "recordatorio"
    private const val EXTRA_DIA = "dia"
    private const val EXTRA_CUANDO = "cuando"

    /** Programa (o cancela) todos los avisos según la sesión. Se llama al abrir la app, al cambiar el perfil y al reiniciar. */
    fun programarTodos(context: Context) {
        val sesion = Sesion(context)
        // Solo los roles que marcan asistencia reciben recordatorios; tampoco quien tiene el acceso cerrado.
        if (sesion.correo == null || !sesion.recordatorios || !sesion.perfil.marca || sesion.accesoCerrado != null) {
            return cancelarTodos(context)
        }
        Recordatorio.entries.forEach { programar(context, it) }
    }

    fun cancelarTodos(context: Context) {
        val alarmas = context.getSystemService(AlarmManager::class.java)
        Recordatorio.entries.forEach { alarmas.cancel(pendiente(context, it, 0, 0)) }
    }

    // Alarma inexacta: Android puede retrasarla unos minutos para ahorrar batería; no requiere permisos especiales.
    fun programar(context: Context, r: Recordatorio) {
        val perfil = Sesion(context).perfil
        val ahora = System.currentTimeMillis()
        // Desde ayer (una salida de madrugada pertenece al turno de ayer) hasta dentro de dos semanas.
        val hoy = inicioDelDia(ahora)
        val siguiente = (-1..14).asSequence()
            .map { inicioDelDia(enMinuto(hoy, it * 24 * 60 + 12 * 60)) }
            .mapNotNull { dia -> r.momento(dia, perfil)?.let { dia to it } }
            .firstOrNull { (_, cuando) -> cuando > ahora } ?: return
        AlarmManagerCompat.setAndAllowWhileIdle(
            context.getSystemService(AlarmManager::class.java),
            AlarmManager.RTC_WAKEUP,
            siguiente.second,
            pendiente(context, r, siguiente.first, siguiente.second),
        )
    }

    private fun pendiente(context: Context, r: Recordatorio, dia: Long, cuando: Long) = PendingIntent.getBroadcast(
        context,
        r.codigo,
        Intent(context, ReceptorRecordatorios::class.java).setAction(ACCION).putExtra(EXTRA, r.name)
            .putExtra(EXTRA_DIA, dia).putExtra(EXTRA_CUANDO, cuando),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun leer(intent: Intent): Recordatorio? =
        if (intent.action != ACCION) null
        else intent.getStringExtra(EXTRA)?.let { nombre -> Recordatorio.entries.firstOrNull { it.name == nombre } }

    /** Día del turno al que corresponde el aviso y hora para la que se programó. */
    internal fun diaYHora(intent: Intent): Pair<Long, Long> =
        intent.getLongExtra(EXTRA_DIA, 0) to intent.getLongExtra(EXTRA_CUANDO, 0)
}

/** Recibe las alarmas y decide si el aviso sigue siendo útil (por ejemplo, si ya marcó, no se avisa). */
class ReceptorRecordatorios : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val r = Recordatorios.leer(intent)
        if (r == null) {
            // Reinicio del teléfono, actualización de la app o cambio de hora.
            Recordatorios.programarTodos(context)
            return
        }
        try {
            val (dia, cuando) = Recordatorios.diaYHora(intent)
            avisar(context, r, dia, cuando)
        } finally {
            Recordatorios.programar(context, r)
        }
    }

    private fun avisar(context: Context, r: Recordatorio, diaProgramado: Long, cuando: Long) {
        val sesion = Sesion(context)
        if (sesion.correo == null || !sesion.recordatorios || !sesion.perfil.marca) return
        val perfil = sesion.perfil
        val ahora = System.currentTimeMillis()
        // Si Android entregó la alarma con mucho retraso, el aviso ya no tiene sentido.
        if (cuando > 0 && abs(ahora - cuando) > 45 * 60_000L) return
        val dia = if (diaProgramado > 0) diaProgramado else inicioDelDia(ahora)

        val marcas = Marcas(context, sesion.correo).todas()
        val turno = agruparPorDia(marcas, perfil).firstOrNull { it.dia == dia }
        val libre = perfil.diaLibre(dia)
        val especial = perfil.entradaEspecial(dia)
        val entrada = perfil.horaEntrada(dia)
        val salida = perfil.horaSalida(dia)
        val nocturno = terminaDiaSiguiente(entrada, salida)
        when (r) {
            Recordatorio.ENTRADA -> if (libre == null && turno?.entrada == null) Notificaciones.mostrar(
                context, r.codigo,
                if (especial != null) "En 15 minutos: entrada especial" else "Tu entrada es en 15 minutos",
                if (especial != null) ("Hoy la entrada es a las ${horaAmigable(entrada)}" +
                    (if (especial.horaSalida.isNotBlank()) " y la salida a las ${horaAmigable(salida)}" else "") +
                    ". ${especial.detalle}").trim()
                else "Entras a las ${horaAmigable(entrada)} y sales a las ${horaAmigable(salida)}" +
                    (if (nocturno) " (del día siguiente)" else "") + ". ¡Llega puntual y mantén tu racha!",
            )
            Recordatorio.ENTRADA_TARDE ->
                if (libre == null && turno?.entrada == null) {
                    Notificaciones.mostrar(
                        context, r.codigo,
                        "Aún no registras tu entrada",
                        "Ya pasó tu hora de entrada (${horaAmigable(entrada)}). Marca en cuanto llegues; si estás " +
                            "en una actividad fuera del colegio, usa \"Actividad especial\".",
                    )
                }
            Recordatorio.SALIDA -> if (turno?.entrada != null && turno.salida == null) {
                // 10 minutos antes de la salida: cuánto lleva al momento de salir.
                val alSalir = (turno.minutosTrabajados(ahora) ?: 0) + 10
                val faltan = turno.meta - turno.minutosPermiso - alSalir
                Notificaciones.mostrar(
                    context, r.codigo,
                    "Tu salida es en 10 minutos",
                    "A las ${horaAmigable(salida)} llevarás ${formatoDuracion(alSalir)}. " +
                        if (faltan <= 0) "Cumplirás tu meta del día. No olvides marcar tu salida."
                        else "Te faltarán ${formatoDuracion(faltan)} para tu meta del día.",
                )
            }
            Recordatorio.RESUMEN_SEMANAL -> {
                val e = calcularEstadisticas(marcas, perfil, Periodo.SEMANA, ahora)
                if (e.diasAsistidos == 0) return
                val titulo = when {
                    e.tardes == 0 && e.metaIncompleta == 0 -> "¡Felicidades! Semana perfecta"
                    (e.puntualidad ?: 100) >= 80 -> "¡Buena semana!"
                    else -> "Tu semana en resumen"
                }
                val d = perfil.destacados?.takeIf { it.hayDatos }
                Notificaciones.mostrar(
                    context, r.codigo, titulo,
                    "${e.diasAsistidos} días · ${e.puntuales} puntuales · ${e.tardes} tarde · " +
                        "${e.metaCumplida} con la meta cumplida · ${formatoDuracion(e.minutosTrabajados)} en total" +
                        (if (e.minutosExtra > 0) " · ${formatoDuracion(e.minutosExtra)} extra" else "") + "." +
                        (if (d != null) "\n" + textoDestacados(d) else ""),
                )
            }
        }
    }
}

/** "Mejor racha: Ana (7 días) · Más horas: Luis (45 h)". */
fun textoDestacados(d: Destacados): String = listOfNotNull(
    if (d.racha > 0) "Mejor racha de la semana: ${d.rachaNombre} (${d.racha} ${if (d.racha == 1) "día" else "días"})" else null,
    if (d.horasMinutos > 0) "Más horas: ${d.horasNombre} (${formatoDuracion(d.horasMinutos)})" else null,
).joinToString(" · ")
