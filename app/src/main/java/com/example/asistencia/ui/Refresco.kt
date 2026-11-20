/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * Ejecuta [accion] cada vez que la app vuelve a primer plano y luego cada [cadaMilis] mientras siga abierta.
 * Así la app muestra lo que se cambie en la hoja de Google sin tener que tocar "Actualizar".
 * Se reinicia cuando cambia [clave] (por ejemplo, la sección elegida).
 */
@Composable
fun AlVolverYCada(cadaMilis: Long, clave: Any?, accion: () -> Unit) {
    val ciclo = LocalLifecycleOwner.current
    val accionActual by rememberUpdatedState(accion)
    LaunchedEffect(clave, ciclo) {
        ciclo.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                accionActual()
                delay(cadaMilis)
            }
        }
    }
}
