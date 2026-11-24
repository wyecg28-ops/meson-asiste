/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.asistencia.datos.DiaLibreAsignado
import com.example.asistencia.datos.SheetsApi
import com.example.asistencia.datos.Solicitud
import com.example.asistencia.datos.aMinutos
import com.example.asistencia.datos.claveDia
import com.example.asistencia.datos.datosDelTelefono
import com.example.asistencia.datos.mensajeDeError
import com.example.asistencia.datos.textoDiasLaborales
import com.example.asistencia.ui.theme.colorAviso
import com.example.asistencia.ui.theme.colorExito
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class EstadoPermisos(
    val cargando: Boolean = false,
    val solicitudes: List<Solicitud>? = null,
    val mensaje: Mensaje? = null,
)

/** Solicitudes de permiso de la persona que marca (se guardan en la hoja "Solicitudes"). */
class PermisosViewModel(app: Application) : AndroidViewModel(app) {
    private val _estado = MutableStateFlow(EstadoPermisos())
    val estado = _estado.asStateFlow()

    fun cargar() = ejecutar { leer() }

    private suspend fun leer() {
        val r = SheetsApi.enviar("misSolicitudes", datosDelTelefono(getApplication()))
        val a = r.getJSONArray("solicitudes")
        _estado.update { it.copy(solicitudes = (0 until a.length()).map { i -> Solicitud.deJson(a.getJSONObject(i)) }) }
    }

    fun solicitar(tipo: String, desde: String, hasta: String, porHoras: Boolean, inicio: String, fin: String, motivo: String) {
        when {
            motivo.trim().length < 3 -> return error("Escribe el motivo.")
            hasta < desde -> return error("La fecha final es anterior a la inicial.")
            porHoras && (inicio.isBlank() || fin.isBlank()) -> return error("Elige la hora de inicio y de fin.")
            porHoras && aMinutos(fin) <= aMinutos(inicio) -> return error("La hora final debe ser después de la inicial.")
        }
        ejecutar {
            val solicitud = JSONObject()
                .put("tipo", tipo)
                .put("desde", desde)
                .put("hasta", if (porHoras) desde else hasta)
                .put("horaInicio", if (porHoras) inicio else "")
                .put("horaFin", if (porHoras) fin else "")
                .put("motivo", motivo.trim())
            val r = SheetsApi.enviar("crearSolicitud", datosDelTelefono(getApplication()).put("solicitud", solicitud))
            aviso(r.optString("mensaje", "Solicitud enviada."))
            leer()
        }
    }

    fun cancelar(s: Solicitud) = ejecutar {
        SheetsApi.enviar("cancelarSolicitud", datosDelTelefono(getApplication()).put("id", s.id))
        aviso("Solicitud cancelada.")
        leer()
    }

    fun mensajeMostrado(m: Mensaje) = _estado.update { if (it.mensaje === m) it.copy(mensaje = null) else it }

    private fun aviso(t: String) = _estado.update { it.copy(mensaje = Mensaje(t, error = false)) }
    private fun error(t: String) = _estado.update { it.copy(mensaje = Mensaje(t, error = true)) }

    private fun ejecutar(bloque: suspend () -> Unit) {
        if (_estado.value.cargando) return
        viewModelScope.launch {
            _estado.update { it.copy(cargando = true) }
            try {
                bloque()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error(mensajeDeError(e))
            } finally {
                _estado.update { it.copy(cargando = false) }
            }
        }
    }
}

@Composable
fun PantallaPermisos(avisos: SnackbarHostState, diasLibres: List<DiaLibreAsignado>? = null, vm: PermisosViewModel = viewModel()) {
    val estado by vm.estado.collectAsState()
    var nueva by remember { mutableStateOf(false) }
    var cancelando by remember { mutableStateOf<Solicitud?>(null) }
    val guardarPdf = rememberGuardarPdf()

    LaunchedEffect(Unit) { vm.cargar() }
    LaunchedEffect(estado.mensaje) {
        val m = estado.mensaje ?: return@LaunchedEffect
        avisos.showSnackbar(VisualMensaje(m.texto, m.error))
        vm.mensajeMostrado(m)
    }

    Pantalla {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Titulo("Permisos", "Pide permisos o vacaciones a la dirección.") }
            TextButton(onClick = vm::cargar, enabled = !estado.cargando) { Text("Actualizar") }
        }
        diasLibres?.let { TarjetaDiasLibres(it) }
        Button(
            onClick = { nueva = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("+ Solicitar permiso") }
        if (estado.cargando) LinearProgressIndicator(Modifier.fillMaxWidth())

        val lista = estado.solicitudes
        if (lista != null && lista.isEmpty()) {
            EstadoVacio("Sin solicitudes", "Cuando pidas un permiso aparecerá aquí con su estado.")
        }
        lista?.forEach { s ->
            TarjetaSolicitud(s, onCancelar = if (s.pendiente) ({ cancelando = s }) else null) {
                // Formato para imprimir, firmar y entregar a la dirección.
                TextButton(onClick = { guardarPdf(nombreArchivoSolicitud(s)) { Exportar.solicitudPdf(s, it) } }) {
                    Text("Descargar PDF para firmar")
                }
            }
        }
    }

    if (nueva) DialogoSolicitud(onCerrar = { nueva = false }) { tipo, desde, hasta, porHoras, inicio, fin, motivo ->
        nueva = false
        vm.solicitar(tipo, desde, hasta, porHoras, inicio, fin, motivo)
    }
    cancelando?.let { s ->
        AlertDialog(
            onDismissRequest = { cancelando = null },
            title = { Text("¿Cancelar la solicitud?") },
            text = { Text("${s.tipoTexto} del ${fechaLegible(s.desde)}.") },
            confirmButton = { TextButton(onClick = { cancelando = null; vm.cancelar(s) }) { Text("Cancelar solicitud") } },
            dismissButton = { TextButton(onClick = { cancelando = null }) { Text("Volver") } },
        )
    }
}

/**
 * Mis vacaciones y asuetos: los que la dirección me asignó (pedidos o puestos directamente en el calendario) y que aún
 * no terminan, con cuántos días laborales son. La app no calcula cuántos días le corresponden a nadie.
 */
@Composable
private fun TarjetaDiasLibres(lista: List<DiaLibreAsignado>) {
    val hoy = claveDia(System.currentTimeMillis())
    Tarjeta {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🌴 Mis vacaciones y asuetos", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
            lista.forEach { d ->
                val ahora = d.desde <= hoy
                Text(
                    (if (ahora) "🟢 " else "📅 ") + "${d.tipoTexto}: " +
                        (if (d.desde == d.hasta) fechaLegible(d.desde) else "${fechaLegible(d.desde)} al ${fechaLegible(d.hasta).lowercase()}") +
                        " · ${textoDiasLaborales(d.dias)}" + if (d.detalle.isNotBlank()) " (${d.detalle})" else "",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = if (ahora) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            if (lista.isEmpty()) {
                Text("No tienes vacaciones ni asuetos próximos.", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Las fechas las asigna la dirección.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun colorSolicitud(s: Solicitud): Color = when (s.estado) {
    "APROBADA" -> colorExito
    "RECHAZADA" -> MaterialTheme.colorScheme.error
    "PENDIENTE" -> colorAviso
    else -> MaterialTheme.colorScheme.outline
}

fun textoFechasSolicitud(s: Solicitud): String = when {
    s.porHoras -> "${fechaLegible(s.desde)}, de ${horaAmigable(s.horaInicio)} a ${horaAmigable(s.horaFin)}"
    s.desde == s.hasta -> fechaLegible(s.desde)
    else -> "${fechaLegible(s.desde)} al ${fechaLegible(s.hasta)}"
}

/** Tarjeta de una solicitud. [mostrarNombre] para el panel de administración. */
@Composable
fun TarjetaSolicitud(
    s: Solicitud,
    mostrarNombre: Boolean = false,
    onCancelar: (() -> Unit)? = null,
    acciones: (@Composable () -> Unit)? = null,
) {
    Tarjeta {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (mostrarNombre) s.nombre else s.tipoTexto,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Etiqueta(s.estadoTexto, colorSolicitud(s))
            }
            if (mostrarNombre) Text(s.tipoTexto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Text(textoFechasSolicitud(s), style = MaterialTheme.typography.bodyMedium)
            Text("“${s.motivo}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.respuesta.isNotBlank()) {
                Text("Respuesta: ${s.respuesta}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
            if (onCancelar != null) {
                TextButton(onClick = onCancelar) { Text("Cancelar solicitud", color = MaterialTheme.colorScheme.error) }
            }
            acciones?.invoke()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoSolicitud(
    onCerrar: () -> Unit,
    onEnviar: (String, String, String, Boolean, String, String, String) -> Unit,
) {
    val hoy = claveDia(System.currentTimeMillis())
    var vacaciones by remember { mutableStateOf(false) }
    var porHoras by remember { mutableStateOf(false) }
    var desde by remember { mutableStateOf(hoy) }
    var hasta by remember { mutableStateOf(hoy) }
    var inicio by remember { mutableStateOf("") }
    var fin by remember { mutableStateOf("") }
    var motivo by remember { mutableStateOf("") }
    var eligiendo by remember { mutableStateOf<String?>(null) }

    eligiendo?.let { cual ->
        val utc = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") } }
        val selector = rememberDatePickerState(initialSelectedDateMillis = utc.parse(if (cual == "desde") desde else hasta)?.time)
        DatePickerDialog(
            onDismissRequest = { eligiendo = null },
            confirmButton = {
                TextButton(onClick = {
                    selector.selectedDateMillis?.let { ms ->
                        val clave = utc.format(ms)
                        if (cual == "desde") {
                            desde = clave
                            if (hasta < clave) hasta = clave
                        } else hasta = clave
                    }
                    eligiendo = null
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { eligiendo = null }) { Text("Cancelar") } },
        ) { DatePicker(state = selector) }
        return
    }

    val horasPedidas = !vacaciones && porHoras
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Solicitar permiso") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(!vacaciones, { vacaciones = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Permiso") }
                    SegmentedButton(vacaciones, { vacaciones = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Vacaciones") }
                }
                if (!vacaciones) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(!porHoras, { porHoras = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Día completo") }
                        SegmentedButton(porHoras, { porHoras = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Por horas") }
                    }
                }
                OutlinedButton(onClick = { eligiendo = "desde" }, modifier = Modifier.fillMaxWidth()) {
                    Text((if (horasPedidas) "Día: " else "Desde: ") + fechaLegible(desde), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!horasPedidas) {
                    OutlinedButton(onClick = { eligiendo = "hasta" }, modifier = Modifier.fillMaxWidth()) {
                        Text("Hasta: ${fechaLegible(hasta)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectorHora(inicio, "Desde", Modifier.weight(1f)) { inicio = it }
                        SelectorHora(fin, "Hasta", Modifier.weight(1f)) { fin = it }
                    }
                    Text(
                        "Si se aprueba, esas horas cuentan para tu meta y no llegarás tarde si cubren tu entrada.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = motivo,
                    onValueChange = { motivo = it.take(300) },
                    label = { Text("Motivo") },
                    placeholder = { Text("Ej.: cita médica") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            Button(onClick = {
                onEnviar(if (vacaciones) "VACACIONES" else "PERMISO", desde, hasta, horasPedidas, inicio, fin, motivo)
            }) { Text("Enviar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}
