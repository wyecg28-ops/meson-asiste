/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ubicacion

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

data class Lectura(
    val latitud: Double,
    val longitud: Double,
    val precisionMetros: Float,
    val simulada: Boolean,
)

class UbicacionException(mensaje: String) : Exception(mensaje)

object Ubicacion {

    /** Requiere que el permiso de ubicación precisa ya esté concedido. */
    @SuppressLint("MissingPermission")
    suspend fun obtener(context: Context): Lectura {
        val cliente = LocationServices.getFusedLocationProviderClient(context)
        val cancelacion = CancellationTokenSource()
        val loc = cliente.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancelacion.token)
            .await(cancelacion)
            ?: throw UbicacionException("No se pudo obtener la ubicación. Verifica que el GPS esté activado.")

        val simulada = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            loc.isMock
        } else {
            @Suppress("DEPRECATION")
            loc.isFromMockProvider
        }
        return Lectura(loc.latitude, loc.longitude, loc.accuracy, simulada)
    }

    fun distanciaMetros(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val resultado = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, resultado)
        return resultado[0]
    }
}
