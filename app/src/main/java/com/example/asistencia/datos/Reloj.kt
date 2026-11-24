/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.datos

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** De dónde salió la hora de una marca. */
enum class OrigenHora {
    /** Reloj propio, puesto en hora con el servidor. */
    SERVIDOR,
    /** Hora de los satélites GPS (después de apagar el teléfono sin internet). */
    GPS,
    /** Reloj del teléfono (la persona puede cambiarlo): la marca queda por revisar si llega sin internet. */
    TELEFONO,
}

/**
 * Reloj propio de la app: no depende de la hora que la persona ponga en el teléfono.
 *
 * Cada respuesta del servidor trae su hora; se guarda junto con un contador interno del teléfono que avanza aunque la
 * pantalla esté apagada o la app cerrada, y que no cambia si se mueve la hora del teléfono. Con ese contador se sabe
 * la hora real en cualquier momento, también sin internet. Si el teléfono se apagó por completo (el contador vuelve a
 * cero), se usa la hora de los satélites GPS, que llega sin internet y el teléfono no puede alterar.
 */
object Reloj {
    private const val PREFS = "reloj"

    /** Hora del servidor recibida: [servidor] en el instante [contador] (milisegundos desde que se prendió el teléfono). */
    fun sincronizar(context: Context, servidor: Long, contador: Long) {
        if (servidor <= 0) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong("servidor", servidor)
            putLong("contador", contador)
            putInt("arranque", numeroDeArranque(context))
        }
    }

    /** Hora real según el reloj propio, o null si el teléfono se apagó desde la última vez que se puso en hora. */
    fun ahora(context: Context): Long? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val servidor = p.getLong("servidor", 0L)
        val contador = p.getLong("contador", 0L)
        val ahora = SystemClock.elapsedRealtime()
        if (servidor <= 0L || ahora < contador) return null // nunca se puso en hora, o el teléfono se reinició
        val arranque = numeroDeArranque(context)
        if (arranque >= 0 && p.getInt("arranque", -1) != arranque) return null // el teléfono se apagó y se volvió a prender
        return servidor + (ahora - contador)
    }

    /** Cuántas veces se ha prendido el teléfono (-1 si Android no lo dice). */
    private fun numeroDeArranque(context: Context): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    } else -1

    /**
     * Hora para una marca: la del reloj propio; si el teléfono se apagó desde la última conexión, la de los satélites
     * GPS (y con ella se vuelve a poner en hora el reloj); si tampoco se puede, la del teléfono.
     * Devuelve la hora y su origen.
     */
    suspend fun horaDeMarca(context: Context): Pair<Long, OrigenHora> {
        ahora(context)?.let { return it to OrigenHora.SERVIDOR }
        horaGps(context)?.let { (hora, contador) ->
            sincronizar(context, hora, contador)
            return hora + (SystemClock.elapsedRealtime() - contador) to OrigenHora.GPS
        }
        return System.currentTimeMillis() to OrigenHora.TELEFONO
    }

    /**
     * Hora de los satélites (lectura del GPS, no de la red): Android garantiza que la hora de una lectura del GPS sale
     * del reloj de los satélites. Devuelve la hora y el contador interno de ese instante, o null (sin GPS en 40 s).
     */
    @SuppressLint("MissingPermission")
    private suspend fun horaGps(context: Context): Pair<Long, Long>? {
        val gestor = ContextCompat.getSystemService(context, LocationManager::class.java) ?: return null
        if (!LocationManagerCompat.isLocationEnabled(gestor) || !gestor.isProviderEnabled(LocationManager.GPS_PROVIDER)) return null
        val lectura: Location = withTimeoutOrNull(40_000) {
            suspendCancellableCoroutine { cont ->
                val cancelar = CancellationSignal()
                cont.invokeOnCancellation { cancelar.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(
                        gestor, LocationManager.GPS_PROVIDER, cancelar, ContextCompat.getMainExecutor(context),
                    ) { loc -> if (cont.isActive) cont.resume(loc) }
                } catch (e: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        } ?: return null
        if (lectura.time <= 0L) return null
        return lectura.time to lectura.elapsedRealtimeNanos / 1_000_000
    }
}
