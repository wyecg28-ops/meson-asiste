/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.asistencia.Config
import com.example.asistencia.datos.EstadoMarca
import com.example.asistencia.datos.Jornada
import com.example.asistencia.datos.Marca
import com.example.asistencia.datos.TipoRegistro
import com.example.asistencia.datos.agruparPorDia
import com.example.asistencia.datos.formatoDuracionCorta
import com.example.asistencia.datos.formatoHora
import com.example.asistencia.notificaciones.Notificaciones
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.asistencia.datos.NOMBRES_DIAS
import com.example.asistencia.datos.Perfil
import com.example.asistencia.datos.Permiso
import com.example.asistencia.datos.terminaDiaSiguiente
import com.example.asistencia.ui.theme.colorAviso
import com.example.asistencia.ui.theme.colorExito
import androidx.compose.ui.graphics.Color

// ---------- Historial ----------

@Composable
fun PantallaHistorial(marcas: List<Marca>, perfil: Perfil) {
    val dias = remember(marcas, perfil) { agruparPorDia(marcas, perfil) }
    val ahora = rememberAhora(60_000)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Titulo("Historial", "Tus registros guardados en este teléfono.") }
        if (dias.isEmpty()) {
            item { EstadoVacio("Sin registros todavía", "Tus entradas y salidas aparecerán aquí, agrupadas por día.") }
        }
        items(dias, key = { it.dia }) { TarjetaDia(it, ahora) }
    }
}

@Composable
private fun TarjetaDia(jornada: Jornada, ahora: Long) {
    Tarjeta {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatear("EEEE d 'de' MMMM", jornada.dia),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (jornada.actividad) Etiqueta("Actividad", MaterialTheme.colorScheme.secondary)
                else jornada.puntual?.let { EtiquetaPuntualidad(it) }
            }
            Row {
                Dato("Entrada", jornada.minutoEntrada?.let(::formatoHora) ?: "--:--", Modifier.weight(1f))
                Dato(
                    "Salida",
                    jornada.minutoSalida?.let { formatoHora(it) + if (jornada.salidaDiaSiguiente) " (+1)" else "" } ?: "--:--",
                    Modifier.weight(1f),
                )
                Dato(
                    "Horas", jornada.minutosTrabajados(ahora)?.let(::formatoDuracionCorta) ?: "—", Modifier.weight(1f),
                    colorValor = when (jornada.cumplioMeta(ahora)) {
                        true -> colorExito
                        false -> colorAviso
                        null -> Color.Unspecified
                    },
                )
            }
            if (jornada.cumplioMeta(ahora) == false && !jornada.actividad) {
                Text(
                    "No completó la meta de ${com.example.asistencia.datos.formatoDuracion(jornada.meta.toLong())}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorAviso,
                )
            }
            val extra = jornada.minutosExtra(ahora)
            if (extra > 0) {
                Text(
                    "Horas extra: ${com.example.asistencia.datos.formatoDuracion(extra)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorExito,
                )
            }
            jornada.libre?.let {
                Text("${it.tipo.etiqueta} ${it.detalle}".trim(), style = MaterialTheme.typography.bodySmall)
            }
            jornada.marcas.filter { it.observacion.isNotEmpty() }.forEach {
                Text(
                    "${etiquetaTipo(it)}: ${it.observacion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            jornada.marcas.filter { it.estado != EstadoMarca.ENVIADA }.forEach {
                val rechazada = it.estado == EstadoMarca.RECHAZADA
                Text(
                    "${etiquetaTipo(it)} ${formatear("HH:mm", it.fechaHora)} · ${it.estado.etiqueta}" +
                        if (rechazada && it.detalle.isNotEmpty()) ": ${it.detalle}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (rechazada) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** "6:00 p. m. – 2:00 a. m. (día sig.)". */
private fun textoHorario(entrada: String, salida: String) =
    "${horaAmigable(entrada)} – ${horaAmigable(salida)}" + if (terminaDiaSiguiente(entrada, salida)) " (día sig.)" else ""

private fun etiquetaTipo(marca: Marca) =
    TipoRegistro.entries.firstOrNull { it.name == marca.tipo }?.etiqueta ?: marca.tipo

// ---------- Perfil ----------

@Composable
fun PantallaPerfil(
    estado: EstadoUi,
    onRecordatorios: (Boolean) -> Unit,
    onPedirHuella: (Boolean) -> Unit,
    onCambiarMetodo: () -> Unit,
    onReintentar: () -> Unit,
    onCerrarSesion: () -> Unit,
    onQuitarCuenta: () -> Unit,
    onLiberarTelefono: () -> Unit,
    onError: (String) -> Unit,
    avisos: SnackbarHostState,
) {
    val context = LocalContext.current
    val pedirNotificaciones = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) onRecordatorios(true)
        else onError("Permite las notificaciones de la app en Ajustes para recibir recordatorios.")
    }
    val permitidas = Notificaciones.permitidas(context)
    var confirmarQuitar by remember { mutableStateOf(false) }
    var confirmarLiberar by remember { mutableStateOf(false) }

    Pantalla {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(estado.nombre, 76.dp)
            Spacer(Modifier.height(12.dp))
            Text(estado.nombre.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(estado.correo.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val perfil = estado.perfil
        if (!perfil.marca) {
            Seccion("Mi rol") {
                Fila("Rol", perfil.rol.nombre)
                Text(
                    descripcionRol(perfil.rol) + " Tus funciones están en la pestaña Admin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onReintentar, enabled = !estado.ocupado) { Text("Actualizar") }
            }
        } else Seccion("Mi jornada") {
            Fila("Rol", perfil.rol.nombre)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Fila("Jornada", perfil.horario.nombre)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Fila("Horario", textoHorario(perfil.horario.entrada, perfil.horario.salida))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Fila("Días", perfil.horario.textoDias)
            // Días con horario distinto al general.
            perfil.horario.porDia.toSortedMap().filterKeys { it in perfil.horario.dias }.forEach { (d, h) ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Fila(NOMBRES_DIAS[d].orEmpty(), textoHorario(h.entrada, h.salida))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Fila("Meta diaria", "${perfil.horasMeta} horas (o lo que dure tu horario)")
            Text(
                "El rol y la jornada los asigna la dirección. Lo que trabajes después de la meta suma como horas extra. " +
                    "Si cambian, toca \"Actualizar\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onReintentar, enabled = !estado.ocupado) { Text("Actualizar") }
        }
        // Ajustes del colegio (meta, tolerancia, ubicación y verificación): si el rol tiene esa función.
        if (perfil.puede(Permiso.AJUSTES)) AjustesDelColegio(avisos)

        if (perfil.marca) Seccion("Notificaciones") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Recordatorios y alertas", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Aviso 15 minutos antes de tu entrada y 10 antes de tu salida, alerta si no has marcado, " +
                            "logros de racha y resumen de tu semana con los destacados del personal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = estado.recordatorios && permitidas,
                    onCheckedChange = { activar ->
                        when {
                            !activar -> onRecordatorios(false)
                            permitidas -> onRecordatorios(true)
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                                pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> onError("Las notificaciones de la app están desactivadas en Ajustes del teléfono.")
                        }
                    },
                )
            }
        }

        Seccion("Seguridad") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Entrar con huella o rostro", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Al abrir la app solo pones tu huella; no hay que escribir nada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = estado.pedirHuella, onCheckedChange = onPedirHuella)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Fila("Confirmación", estado.metodo?.etiqueta.orEmpty(), Modifier.weight(1f))
                TextButton(onClick = onCambiarMetodo, enabled = !estado.ocupado) { Text("Cambiar") }
            }
        }

        if (estado.pendientes > 0) {
            OutlinedButton(
                onClick = onReintentar,
                enabled = !estado.ocupado,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Enviar ${estado.pendientes} pendiente(s) a la hoja")
            }
        }
        Seccion("Cuenta") {
            Text(
                "Tu cuenta queda guardada en este teléfono: para volver, tócala y pon tu huella.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onCerrarSesion,
                enabled = !estado.ocupado,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Cambiar de cuenta")
            }
            OutlinedButton(
                onClick = { confirmarLiberar = true },
                enabled = !estado.ocupado,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Liberar este teléfono")
            }
            Text(
                "¿Cambiaste de teléfono? Libera este y luego inicia sesión en el nuevo, sin pedírselo a la dirección.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { confirmarQuitar = true }, enabled = !estado.ocupado, modifier = Modifier.fillMaxWidth()) {
                Text("Quitar esta cuenta del teléfono", color = MaterialTheme.colorScheme.error)
            }
        }
        AcercaDe()
    }
    if (confirmarLiberar) {
        AlertDialog(
            onDismissRequest = { confirmarLiberar = false },
            title = { Text("¿Liberar este teléfono?") },
            text = {
                Text(
                    "Tu cuenta deja de estar atada a este teléfono y se cierra aquí. Después podrás iniciar sesión con " +
                        "${estado.correo} en otro teléfono (Android o iPhone). Tus registros siguen en la hoja del colegio."
                )
            },
            confirmButton = { TextButton(onClick = { confirmarLiberar = false; onLiberarTelefono() }) { Text("Liberar") } },
            dismissButton = { TextButton(onClick = { confirmarLiberar = false }) { Text("Cancelar") } },
        )
    }
    if (confirmarQuitar) {
        AlertDialog(
            onDismissRequest = { confirmarQuitar = false },
            title = { Text("¿Quitar tu cuenta de este teléfono?") },
            text = { Text("Se borra el historial guardado en este teléfono. Tus registros siguen en la hoja del colegio y puedes volver a agregar la cuenta.") },
            confirmButton = { TextButton(onClick = { confirmarQuitar = false; onQuitarCuenta() }) { Text("Quitar") } },
            dismissButton = { TextButton(onClick = { confirmarQuitar = false }) { Text("Cancelar") } },
        )
    }
}

/**
 * Créditos de la app. Según la licencia (LICENSE y NOTICE), toda copia o versión modificada debe conservar
 * el aviso "Creado por 31YC0ND3".
 */
@Composable
private fun AcercaDe() {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }
    Seccion("Acerca de") {
        Fila("Aplicación", "Mesón Asiste $version")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Fila("Creado por", Config.CREADOR)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Fila("Licencia", "Apache 2.0")
        Text(
            "© 2026 ${Config.CREADOR}. Todos los derechos de autor reservados según la licencia Apache 2.0.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Ajustes del colegio dentro del Perfil del administrador. */
@Composable
private fun AjustesDelColegio(avisos: SnackbarHostState, vm: AdminViewModel = viewModel()) {
    val estado by vm.estado.collectAsState()
    // Si el panel estaba ocupado cargando otra cosa, se intenta en cuanto quede libre (una vez).
    var intentado by remember { mutableStateOf(false) }
    LaunchedEffect(estado.cargando) {
        if (!estado.cargando && estado.config == null && !intentado) {
            intentado = true
            vm.cargarAjustes()
        }
    }
    LaunchedEffect(estado.mensaje) {
        val m = estado.mensaje ?: return@LaunchedEffect
        avisos.showSnackbar(VisualMensaje(m.texto, m.error))
        vm.mensajeMostrado(m)
    }
    Text(
        "Ajustes del colegio",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp),
    )
    when {
        estado.config != null -> SeccionAjustes(estado, vm)
        estado.cargando || !intentado -> Tarjeta {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Cargando ajustes…", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        else -> Tarjeta {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("No se pudieron cargar los ajustes.", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "Revisa el internet. Si el problema sigue, publica la versión más reciente del servidor " +
                        "(Apps Script → Implementar → Gestionar implementaciones → Nueva versión).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = vm::cargarAjustes, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
            }
        }
    }
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            titulo,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Tarjeta {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                contenido()
            }
        }
    }
}

@Composable
private fun Fila(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
