/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.ui.semantics.Role
import com.example.asistencia.ui.theme.colorAviso
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.asistencia.datos.Sede
import com.example.asistencia.verificacion.MetodoVerificacion

/** Reglas de asistencia y ubicación del colegio (solo el administrador). */
@Composable
fun SeccionAjustes(estado: EstadoAdmin, vm: AdminViewModel) {
    val config = estado.config ?: return
    val enlaces = LocalUriHandler.current

    // ----- Reglas -----
    var meta by remember(config.horasMeta) { mutableIntStateOf(config.horasMeta) }
    var tolerancia by remember(config.tolerancia) { mutableIntStateOf(config.tolerancia) }
    Tarjeta {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Reglas de asistencia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Meta de horas por día", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(4, 5, 6, 7, 8, 9, 10, 12).forEach { h ->
                    FilterChip(selected = meta == h, onClick = { meta = h }, label = { Text("$h h") })
                }
            }
            Text("Minutos de gracia para llegar", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 5, 10, 15, 20, 30).forEach { m ->
                    FilterChip(selected = tolerancia == m, onClick = { tolerancia = m }, label = { Text(if (m == 0) "Sin" else "$m min") })
                }
            }
            Text(
                "Con $tolerancia min de gracia, quien entra a las 07:00 puede marcar hasta las " +
                    "%02d:%02d sin contar como tarde.".format(7 + tolerancia / 60, tolerancia % 60),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { vm.guardarReglas(meta, tolerancia) }, enabled = !estado.cargando, modifier = Modifier.fillMaxWidth()) {
                Text("Guardar reglas")
            }
        }
    }

    // ----- Ubicación del colegio (geocerca) -----
    var agregando by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<Sede?>(null) }
    var eliminando by remember { mutableStateOf<Sede?>(null) }
    var configurando by remember { mutableStateOf<Sede?>(null) }
    var mostrandoQr by remember { mutableStateOf<Sede?>(null) }
    configurando?.let { s ->
        DialogoVerificacion(
            sede = s,
            onCerrar = { configurando = null },
            onGuardar = { metodo, registrarWifi -> configurando = null; vm.guardarVerificacion(s, metodo, registrarWifi) },
        )
    }
    mostrandoQr?.let { s -> PantallaQrSede(s, vm) { mostrandoQr = null } }
    Tarjeta {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ubicación del colegio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (config.sedes.isEmpty())
                    "Sin ubicación configurada: el personal puede marcar desde cualquier lugar. " +
                        "Agrégala para exigir que marquen dentro del colegio."
                else "El personal solo puede marcar dentro del radio de esta ubicación " +
                    "(salvo con \"Actividad especial\", que la administración revisa).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            config.sedes.forEach { s ->
                val metodo = MetodoVerificacion.de(s.metodo)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                "Radio ${s.radio} m · ${metodo.etiqueta}" +
                                    if (metodo == MetodoVerificacion.WIFI) " (${s.wifi.size} redes)" else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { runCatching { enlaces.openUri("https://maps.google.com/?q=${s.latitud},${s.longitud}") } }) { Text("Mapa") }
                        TextButton(onClick = { editando = s }) { Text("Editar") }
                        TextButton(onClick = { eliminando = s }) { Text("Quitar", color = MaterialTheme.colorScheme.error) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { configurando = s }, enabled = !estado.cargando, modifier = Modifier.weight(1f)) {
                            Text("Verificación")
                        }
                        if (metodo == MetodoVerificacion.QR) {
                            Button(onClick = { mostrandoQr = s }, modifier = Modifier.weight(1f)) { Text("Mostrar QR") }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { agregando = true }, enabled = !estado.cargando, modifier = Modifier.fillMaxWidth()) {
                Text("+ Agregar ubicación del colegio")
            }
            Text(
                "Puedes usar tu ubicación actual (estando en el colegio) o pegar las coordenadas del punto desde Google Maps.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (agregando || editando != null) {
        val original = editando
        DialogoSede(original, onCerrar = { agregando = false; editando = null }) { nombre, radio, punto, moverAqui ->
            agregando = false
            editando = null
            vm.guardarSede(original, nombre, radio, punto, moverAqui)
        }
    }
    eliminando?.let { s ->
        AlertDialog(
            onDismissRequest = { eliminando = null },
            title = { Text("¿Quitar ${s.nombre}?") },
            text = { Text("Ya no se exigirá marcar dentro de esta ubicación.") },
            confirmButton = { TextButton(onClick = { eliminando = null; vm.eliminarSede(s) }) { Text("Quitar") } },
            dismissButton = { TextButton(onClick = { eliminando = null }) { Text("Cancelar") } },
        )
    }
}

/** De dónde sale el punto del colegio. */
private enum class OrigenPunto { ACTUAL, AQUI, COORDENADAS }

/**
 * Crear o editar la ubicación: nombre, punto (mi ubicación, o coordenadas / enlace de Google Maps)
 * y radio con una barra de 0 a 100 m. Al editar se puede conservar el punto y cambiar solo el radio.
 */
@Composable
private fun DialogoSede(
    original: Sede?,
    onCerrar: () -> Unit,
    onGuardar: (nombre: String, radio: Int, punto: Pair<Double, Double>?, moverAqui: Boolean) -> Unit,
) {
    var nombre by remember { mutableStateOf(original?.nombre ?: "Colegio Mesón") }
    var radio by remember { mutableFloatStateOf((original?.radio ?: 80).coerceIn(0, 100).toFloat()) }
    var origen by remember { mutableStateOf(if (original != null) OrigenPunto.ACTUAL else OrigenPunto.AQUI) }
    var texto by remember { mutableStateOf("") }
    val punto = leerCoordenadas(texto)
    val metros = radio.roundToInt()
    val valido = nombre.isNotBlank() && (origen != OrigenPunto.COORDENADAS || punto != null)
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(if (original == null) "Ubicación del colegio" else "Editar ubicación") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nombre, onValueChange = { nombre = it.take(60) },
                    label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )

                Text("Punto del colegio", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (original != null) OpcionPunto("Dejar el punto como está", origen == OrigenPunto.ACTUAL) { origen = OrigenPunto.ACTUAL }
                OpcionPunto("Donde estoy ahora (GPS)", origen == OrigenPunto.AQUI) { origen = OrigenPunto.AQUI }
                OpcionPunto("Pegar coordenadas o enlace de Google Maps", origen == OrigenPunto.COORDENADAS) { origen = OrigenPunto.COORDENADAS }
                when (origen) {
                    OrigenPunto.AQUI -> Text(
                        "Párate en el centro del colegio, con el GPS activado.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OrigenPunto.COORDENADAS -> {
                        OutlinedTextField(
                            value = texto, onValueChange = { texto = it.take(500) },
                            label = { Text("Coordenadas o enlace") },
                            placeholder = { Text("14.6349, -90.5069") },
                            isError = texto.isNotBlank() && punto == null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            if (punto != null) "Punto: %.6f, %.6f".format(Locale.US, punto.first, punto.second)
                            else "En Google Maps mantén presionado el centro del colegio, toca las coordenadas que aparecen " +
                                "arriba para copiarlas y pégalas aquí.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (texto.isNotBlank() && punto == null) colorAviso else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OrigenPunto.ACTUAL -> Unit
                }

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Radio permitido", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Text("$metros m", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = radio,
                    onValueChange = { radio = it },
                    valueRange = 0f..100f,
                    steps = 99,
                )
                Row {
                    Text("0 m", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Text("100 m", style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    if (metros < 30) "Ojo: el GPS de los teléfonos suele variar entre 5 y 30 m. Con un radio tan pequeño " +
                        "algunas marcas podrían rechazarse aunque la persona esté dentro."
                    else "Mientras más pequeño, más difícil marcar desde afuera. Recomendado: 50–80 m.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (metros < 30) colorAviso else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valido, onClick = {
                onGuardar(nombre, metros, if (origen == OrigenPunto.COORDENADAS) punto else null, origen == OrigenPunto.AQUI)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

@Composable
private fun OpcionPunto(texto: String, seleccionado: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).selectable(selected = seleccionado, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = seleccionado, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Lee un punto de "14.6349, -90.5069" o de un enlace de Google Maps (…@14.63,-90.50,17z, ?q=14.63,-90.50
 * o !3d14.63!4d-90.50). Devuelve null si no encuentra coordenadas válidas.
 */
fun leerCoordenadas(texto: String): Pair<Double, Double>? {
    val t = texto.trim()
    if (t.isEmpty()) return null
    val numero = "(-?\\d{1,3}(?:\\.\\d+)?)"
    val patrones = listOf(
        Regex("!3d$numero!4d$numero"),
        Regex("@$numero,\\s*$numero"),
        Regex("[?&](?:q|query|ll|center)=$numero,\\s*$numero"),
        Regex("^$numero\\s*[,;\\s]\\s*$numero$"),
    )
    for (p in patrones) {
        val m = p.find(t) ?: continue
        val lat = m.groupValues[1].toDoubleOrNull() ?: continue
        val lon = m.groupValues[2].toDoubleOrNull() ?: continue
        if (lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)) return lat to lon
    }
    return null
}
