/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.asistencia.datos.Sede
import com.example.asistencia.datos.mensajeDeError
import com.example.asistencia.ui.theme.Granate
import com.example.asistencia.ui.theme.RojoClaro
import com.example.asistencia.verificacion.CodigoQr
import com.example.asistencia.verificacion.MetodoVerificacion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Pantalla completa con el QR de asistencia del colegio. Cambia cada 30 segundos y funciona sin internet
 * una vez abierta (el código se calcula en el teléfono con la clave de la sede).
 * Se deja en la entrada: en un teléfono viejo, una tablet o el celular del supervisor.
 */
@Composable
fun PantallaQrSede(sede: Sede, vm: AdminViewModel, onCerrar: () -> Unit) {
    var clave by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var imagen by remember { mutableStateOf<ImageBitmap?>(null) }
    var restante by remember { mutableLongStateOf(CodigoQr.SEGUNDOS) }

    // Mantener la pantalla encendida mientras se muestra el QR.
    val vista = LocalView.current
    DisposableEffect(Unit) {
        vista.keepScreenOn = true
        onDispose { vista.keepScreenOn = false }
    }

    LaunchedEffect(sede.id) {
        try {
            clave = vm.secretoQr(sede)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = mensajeDeError(e)
        }
    }

    LaunchedEffect(clave) {
        val (secreto, diferencia) = clave ?: return@LaunchedEffect
        var ventanaMostrada = Long.MIN_VALUE
        while (true) {
            val ahora = System.currentTimeMillis() / 1000 + diferencia // hora del servidor
            val ventana = CodigoQr.ventana(ahora)
            if (ventana != ventanaMostrada) {
                ventanaMostrada = ventana
                imagen = CodigoQr.imagen(CodigoQr.contenido(sede.id, CodigoQr.codigo(secreto, ventana)))
            }
            restante = CodigoQr.SEGUNDOS - (ahora - ventana * CodigoQr.SEGUNDOS)
            delay(250)
        }
    }

    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF151313)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Escanea para marcar", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text(sede.nombre, style = MaterialTheme.typography.titleMedium, color = RojoClaro)
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(Color.White).padding(18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        error != null -> Text(error!!, color = Granate, textAlign = TextAlign.Center)
                        imagen != null -> Image(imagen!!, contentDescription = "Código QR de asistencia", modifier = Modifier.fillMaxSize())
                        else -> CircularProgressIndicator()
                    }
                }
                LinearProgressIndicator(
                    progress = { restante / CodigoQr.SEGUNDOS.toFloat() },
                    color = RojoClaro, trackColor = Color(0xFF3A2523), strokeCap = StrokeCap.Round,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
                Text(
                    "El código cambia en $restante s. Una foto del código no sirve: caduca enseguida.",
                    color = Color(0xFFE0DADA), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Deja esta pantalla abierta en la entrada. Funciona aunque se vaya el internet.",
                    color = Color(0xFFA09898), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onCerrar) { Text("Cerrar", color = Color.White) }
            }
        }
    }
}

/** Elegir cómo se comprueba que la persona está dentro del colegio. */
@Composable
fun DialogoVerificacion(
    sede: Sede,
    onCerrar: () -> Unit,
    onGuardar: (MetodoVerificacion, Boolean) -> Unit,
) {
    var elegido by remember { mutableStateOf(MetodoVerificacion.de(sede.metodo)) }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Verificación en ${sede.nombre}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("¿Cómo se comprueba que la persona está dentro?", style = MaterialTheme.typography.bodyMedium)
                MetodoVerificacion.entries.forEach { m ->
                    FilterChip(
                        selected = elegido == m,
                        onClick = { elegido = m },
                        label = {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                Text(m.etiqueta, fontWeight = FontWeight.SemiBold)
                                Text(m.descripcion, style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (elegido == MetodoVerificacion.WIFI) {
                    Text(
                        if (sede.wifi.isEmpty()) "Al guardar se registrarán los Wi-Fi que este teléfono detecta ahora. " +
                            "Hazlo dentro del colegio, con el Wi-Fi encendido."
                        else "Hay ${sede.wifi.size} redes registradas.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (sede.wifi.isNotEmpty()) {
                        TextButton(onClick = { onGuardar(MetodoVerificacion.WIFI, true) }) { Text("Volver a registrar el Wi-Fi aquí") }
                    }
                }
                if (elegido == MetodoVerificacion.QR) {
                    Text(
                        "Después toca \"Mostrar QR\" en un teléfono o tablet que se quede en la entrada.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { onGuardar(elegido, false) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}
