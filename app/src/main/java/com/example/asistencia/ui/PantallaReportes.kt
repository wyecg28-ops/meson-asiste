/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.asistencia.datos.Solicitud
import com.example.asistencia.datos.formatoDuracion
import com.example.asistencia.ui.theme.colorAviso
import com.example.asistencia.ui.theme.colorExito
import java.io.File

// ---------- Solicitudes de permiso (administrador y supervisor) ----------

@Composable
fun SeccionSolicitudes(estado: EstadoAdmin, vm: AdminViewModel) {
    val lista = estado.solicitudes ?: return
    var revisando by remember { mutableStateOf<Pair<Solicitud, Boolean>?>(null) }
    val pendientes = lista.filter { it.pendiente }
    val revisadas = lista.filter { !it.pendiente }

    Text(
        "Al aprobar una solicitud, el permiso se agrega solo al calendario de la persona.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (pendientes.isEmpty()) EstadoVacio("Nada pendiente", "Las solicitudes de permiso del personal aparecerán aquí.")
    pendientes.forEach { s ->
        TarjetaSolicitud(s, mostrarNombre = true) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { revisando = s to false }, enabled = !estado.cargando, modifier = Modifier.weight(1f)) {
                    Text("Rechazar", color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = { revisando = s to true }, enabled = !estado.cargando, modifier = Modifier.weight(1f)) {
                    Text("Aprobar")
                }
            }
        }
    }
    if (revisadas.isNotEmpty()) {
        Text("Revisadas recientemente", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        revisadas.take(20).forEach { TarjetaSolicitud(it, mostrarNombre = true) }
    }

    revisando?.let { (s, aprobar) ->
        var respuesta by remember(s.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { revisando = null },
            title = { Text(if (aprobar) "Aprobar solicitud" else "Rechazar solicitud") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${s.nombre} · ${s.tipoTexto}\n${textoFechasSolicitud(s)}")
                    OutlinedTextField(
                        value = respuesta,
                        onValueChange = { respuesta = it.take(200) },
                        label = { Text("Mensaje para la persona (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { revisando = null; vm.revisarSolicitud(s, aprobar, respuesta) }) {
                    Text(if (aprobar) "Aprobar" else "Rechazar")
                }
            },
            dismissButton = { TextButton(onClick = { revisando = null }) { Text("Cancelar") } },
        )
    }
}

// ---------- Reportes mensuales ----------

@Composable
fun SeccionReportes(estado: EstadoAdmin, vm: AdminViewModel) {
    val context = LocalContext.current
    var mes by remember { mutableIntStateOf(0) }

    Text(
        "Resumen del mes por persona: días asistidos, puntualidad, faltas y horas. " +
            "Puedes compartirlo como archivo para abrirlo en Excel.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0 to "Este mes", 1 to "Mes pasado", 2 to "Hace 2 meses").forEach { (m, texto) ->
            FilterChip(selected = mes == m, onClick = { mes = m }, label = { Text(texto) })
        }
    }
    Button(
        onClick = { vm.generarReporte(mes) },
        enabled = !estado.cargando,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { Text("Generar reporte") }

    val reporte = estado.reporte ?: return
    Text(
        "Reporte de ${reporte.titulo}",
        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
    )
    if (reporte.filas.isEmpty()) EstadoVacio("Sin datos", "Todavía no hay personal que marque asistencia.")
    reporte.filas.forEach { f ->
        Tarjeta {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(formatoDuracion(f.minutos), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text("${f.rol} · ${f.jornada}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Etiqueta("${f.dias} días", MaterialTheme.colorScheme.primary)
                    Etiqueta("${f.puntuales} puntual", colorExito)
                    if (f.tardes > 0) Etiqueta("${f.tardes} tarde", colorAviso)
                    if (f.faltas > 0) Etiqueta("${f.faltas} falta", MaterialTheme.colorScheme.error)
                }
                if (f.minutosExtra > 0 || f.porRevisar > 0 || f.actividades > 0) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (f.minutosExtra > 0) Etiqueta("Extra: ${formatoDuracion(f.minutosExtra)}", MaterialTheme.colorScheme.primary)
                        if (f.actividades > 0) Etiqueta("${f.actividades} actividad(es)", MaterialTheme.colorScheme.secondary)
                        if (f.porRevisar > 0) Etiqueta("${f.porRevisar} por revisar", colorAviso)
                    }
                }
            }
        }
    }
    Button(
        onClick = { compartirCsv(context, reporte.archivo, reporte.csv) },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { Text("Compartir para Excel") }
}

/** Guarda el CSV en la caché y abre el menú de compartir (Excel, Drive, WhatsApp, correo…). */
fun compartirCsv(context: Context, nombre: String, contenido: String) {
    val carpeta = File(context.cacheDir, "reportes").apply { mkdirs() }
    val archivo = File(carpeta, nombre).apply { writeText(contenido, Charsets.UTF_8) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.archivos", archivo)
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, nombre)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Compartir reporte"))
}
