/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.asistencia.datos.Solicitud
import com.example.asistencia.datos.formatoDuracion
import com.example.asistencia.datos.formatoDuracionCorta
import com.example.asistencia.ui.theme.colorAviso
import com.example.asistencia.ui.theme.colorExito
import java.io.OutputStream
import java.util.Calendar

// ---------- Descargas ----------

/**
 * Devuelve una función para guardar un PDF: se le pasa el nombre sugerido y cómo escribirlo, y la persona elige
 * dónde guardarlo (Descargas, Drive…).
 */
@Composable
fun rememberGuardarPdf(): (String, (OutputStream) -> Unit) -> Unit {
    val context = LocalContext.current
    var pendiente by remember { mutableStateOf<((OutputStream) -> Unit)?>(null) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val escribir = pendiente
        pendiente = null
        if (uri != null && escribir != null) guardarArchivo(context, uri, "PDF", escribir)
    }
    return { nombre, escribir ->
        pendiente = escribir
        lanzador.launch(nombre)
    }
}

/** Nombre de archivo sin espacios ni símbolos. */
fun nombreArchivo(vararg partes: String) =
    partes.filter { it.isNotBlank() }.joinToString("_") { it.replace(Regex("[^\\p{L}0-9-]+"), "_").trim('_') }

fun nombreArchivoSolicitud(s: Solicitud) = nombreArchivo("Solicitud", s.tipoTexto, s.nombre, s.desde) + ".pdf"

// ---------- Solicitudes de permiso (administrador y supervisor) ----------

@Composable
fun SeccionSolicitudes(estado: EstadoAdmin, vm: AdminViewModel, miCorreo: String) {
    val lista = estado.solicitudes ?: return
    val context = LocalContext.current
    var revisando by remember { mutableStateOf<Pair<Solicitud, Boolean>?>(null) }
    val guardarPdf = rememberGuardarPdf()
    val guardarExcel = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri -> if (uri != null) guardarArchivo(context, uri, "Excel") { Exportar.xlsx(tablaSolicitudes(lista), it) } }
    val pendientes = lista.filter { it.pendiente }
    val revisadas = lista.filter { !it.pendiente }
    val esMia = { s: Solicitud -> miCorreo.isNotBlank() && s.correo.equals(miCorreo, ignoreCase = true) }
    val botonPdf = @Composable { s: Solicitud ->
        TextButton(onClick = { guardarPdf(nombreArchivoSolicitud(s)) { Exportar.solicitudPdf(s, it) } }) {
            Text("Descargar PDF para firmar")
        }
    }

    Text(
        "Al aprobar una solicitud, el permiso se agrega solo al calendario de la persona. Descarga el PDF para " +
            "imprimirlo, firmarlo y archivarlo.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (lista.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { guardarPdf(nombreArchivo("Solicitudes", "Lista") + ".pdf") { Exportar.pdf(tablaSolicitudes(lista), it) } },
                shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f),
            ) { Text("Lista en PDF") }
            OutlinedButton(
                onClick = { guardarExcel.launch(nombreArchivo("Solicitudes", "Lista") + ".xlsx") },
                shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f),
            ) { Text("Lista en Excel") }
        }
    }
    if (pendientes.isEmpty()) EstadoVacio("Nada pendiente", "Las solicitudes de permiso del personal aparecerán aquí.")
    pendientes.forEach { s ->
        TarjetaSolicitud(s, mostrarNombre = true) {
            // Vacaciones: cuántos días laborales son y si ya tiene vacaciones en esas fechas, antes de aprobar.
            s.vista?.let { VistaVacacionesTexto(it, conNombre = false) }
            if (esMia(s)) {
                Text(
                    "Es tu solicitud: debe revisarla otro supervisor o el administrador.",
                    style = MaterialTheme.typography.bodySmall, color = colorAviso, fontWeight = FontWeight.Medium,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { revisando = s to false }, enabled = !estado.cargando, modifier = Modifier.weight(1f)) {
                        Text("Rechazar", color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = { revisando = s to true }, enabled = !estado.cargando, modifier = Modifier.weight(1f)) {
                        Text("Aprobar")
                    }
                }
            }
            botonPdf(s)
        }
    }
    if (revisadas.isNotEmpty()) {
        Text("Revisadas recientemente", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        revisadas.take(30).forEach { s -> TarjetaSolicitud(s, mostrarNombre = true) { botonPdf(s) } }
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

/** Lista de solicitudes para archivar (PDF con firma o Excel). */
fun tablaSolicitudes(lista: List<Solicitud>): TablaReporte {
    val f = { x: String -> x.split("-").takeIf { it.size == 3 }?.reversed()?.joinToString("/") ?: x }
    return TablaReporte(
        titulo = "Solicitudes de permiso y vacaciones",
        subtitulo = "Colegio Mesón · ${lista.size} solicitudes · " +
            "${lista.count { it.estado == "APROBADA" }} aprobadas · ${lista.count { it.pendiente }} pendientes · " +
            "${lista.count { it.estado == "RECHAZADA" }} rechazadas",
        encabezados = listOf("Pedida el", "Nombre", "Rol", "Tipo", "Fechas", "Horario", "Motivo", "Estado", "Revisada por", "Respuesta"),
        filas = lista.map { s ->
            listOf(Celda(f(s.creada)), Celda(s.nombre), Celda(s.rol), Celda(s.tipoTexto),
                Celda(if (s.desde == s.hasta) f(s.desde) else "${f(s.desde)} al ${f(s.hasta)}"),
                Celda(if (s.porHoras) "${s.horaInicio}–${s.horaFin}" else "Día completo"),
                Celda(s.motivo), Celda(s.estadoTexto), Celda(s.revisadaPor), Celda(s.respuesta))
        },
        firmas = listOf("Elaborado por", "Firma del Director o Supervisor"),
    )
}

// ---------- Reportes mensuales y anuales ----------

@Composable
fun SeccionReportes(estado: EstadoAdmin, vm: AdminViewModel) {
    val context = LocalContext.current
    val hoy = Calendar.getInstance()
    val anioActual = hoy.get(Calendar.YEAR).toString()
    var anio by rememberSaveable { mutableStateOf(anioActual) }
    // null = todo el año; si no, "2026-11".
    var mes by rememberSaveable { mutableStateOf<String?>("%04d-%02d".format(hoy.get(Calendar.YEAR), hoy.get(Calendar.MONTH) + 1)) }
    // null = todo el personal; si no, el nombre de la persona.
    var persona by rememberSaveable { mutableStateOf<String?>(null) }
    // Vista en tarjetas (sin deslizar de lado) o la tabla completa (la misma que se descarga).
    var verTabla by rememberSaveable { mutableStateOf(false) }
    var menuMes by remember { mutableStateOf(false) }
    var menuPersona by remember { mutableStateOf(false) }

    val ultimoMes = if (anio == anioActual) hoy.get(Calendar.MONTH) + 1 else 12
    val mesesDelAnio = (1..ultimoMes).map { "$anio-%02d".format(it) }
    LaunchedEffect(anio, mes) {
        if (mes == null) vm.cargarAnual(anio) else vm.generarReporte(mes!!)
    }

    // Año, mes y persona
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(anioActual, (anioActual.toInt() - 1).toString()).forEach { a ->
            FilterChip(selected = anio == a, onClick = {
                anio = a
                if (mes != null && !mes!!.startsWith(a)) mes = null
            }, label = { Text(a) })
        }
        Box(Modifier.weight(1f))
        FilterChip(selected = !verTabla, onClick = { verTabla = false }, label = { Text("Resumen") })
        FilterChip(selected = verTabla, onClick = { verTabla = true }, label = { Text("Tabla") })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { menuMes = true }, modifier = Modifier.fillMaxWidth()) {
                Text((mes?.let { nombreMes(it) } ?: "Todo el año $anio") + " ▾", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = menuMes, onDismissRequest = { menuMes = false }) {
                DropdownMenuItem(text = { Text("Todo el año $anio", fontWeight = FontWeight.SemiBold) }, onClick = { menuMes = false; mes = null })
                mesesDelAnio.reversed().forEach { m ->
                    DropdownMenuItem(text = { Text(nombreMes(m)) }, onClick = { menuMes = false; mes = m })
                }
            }
        }
        val nombres = (if (mes == null) estado.anual?.filas else estado.reporte?.filas).orEmpty().map { it.nombre }.distinct().sorted()
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { menuPersona = true }, modifier = Modifier.fillMaxWidth()) {
                Text((persona ?: "Todo el personal") + " ▾", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = menuPersona, onDismissRequest = { menuPersona = false }) {
                DropdownMenuItem(text = { Text("Todo el personal", fontWeight = FontWeight.SemiBold) }, onClick = { menuPersona = false; persona = null })
                nombres.forEach { n -> DropdownMenuItem(text = { Text(n) }, onClick = { menuPersona = false; persona = n }) }
            }
        }
    }

    // Lo elegido, como tabla (la misma que se descarga).
    val anual = estado.anual?.takeIf { mes == null && it.anio == anio }
    val mensual = estado.reporte?.takeIf { mes != null && it.mes == mes }
    val tabla: TablaReporte? = remember(anual, mensual, persona) {
        anual?.let { tablaAnual(it, persona) } ?: mensual?.let { tablaMensual(it, persona) }
    }

    // Descargas
    val guardarPdf = rememberGuardarPdf()
    val guardarExcel = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri ->
        if (uri != null && tabla != null) guardarArchivo(context, uri, "Excel") { Exportar.xlsx(tabla, it) }
    }
    val archivo = nombreArchivo("Asistencia", mes?.let { nombreMes(it) } ?: "Año $anio", persona ?: "General")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { tabla?.let { t -> guardarPdf("$archivo.pdf") { Exportar.pdf(t, it) } } },
            enabled = tabla != null && !estado.cargando,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).height(46.dp),
        ) { Text("Descargar PDF") }
        OutlinedButton(
            onClick = { guardarExcel.launch("$archivo.xlsx") },
            enabled = tabla != null && !estado.cargando,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).height(46.dp),
        ) { Text("Descargar Excel") }
    }

    if (tabla == null) return
    Text(tabla.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    if (tabla.filas.isEmpty()) {
        EstadoVacio("Sin datos", "No hay registros para lo que elegiste.")
    } else if (verTabla) {
        Text(tabla.subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TablaEnPantalla(tabla)
    } else {
        // Vista en tarjetas: todo a lo alto, sin deslizar de lado.
        when {
            mensual != null && persona == null -> {
                ResumenTotales(FilaReporte.total("Total", mensual.filas))
                mensual.filas.sortedByDescending { it.minutos }.forEach { f -> TarjetaPersona(f) { persona = f.nombre } }
            }
            mensual != null -> {
                mensual.filas.firstOrNull { it.nombre == persona }?.let { ResumenTotales(it) }
                Tarjeta {
                    mensual.detalle.filter { it.nombre == persona }.sortedBy { it.fecha }.forEachIndexed { i, d -> FilaDia(d, i % 2 == 1) }
                }
            }
            anual != null && persona == null -> {
                val porPersona = anual.filas.groupBy { it.nombre }.map { (nombre, filas) ->
                    FilaReporte.total(nombre, filas).copy(rol = filas.last().rol, jornada = filas.last().jornada)
                }.sortedByDescending { it.minutos }
                ResumenTotales(FilaReporte.total("Total", porPersona))
                porPersona.forEach { f -> TarjetaPersona(f) { persona = f.nombre } }
            }
            anual != null -> {
                val meses = anual.filas.filter { it.nombre == persona }.sortedBy { it.mes }
                ResumenTotales(FilaReporte.total("Total", meses))
                meses.forEach { f -> TarjetaPersona(f, titulo = nombreMes(f.mes)) { mes = f.mes } }
            }
        }
        if (persona != null) {
            TextButton(onClick = { persona = null }) { Text("← Ver todo el personal") }
        }
    }

    OutlinedButton(
        onClick = { vm.actualizarHoja() },
        enabled = !estado.cargando,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(46.dp),
    ) { Text("Actualizar pestañas de la hoja") }
}

/** Totales en mosaicos de 3 columnas. */
@Composable
private fun ResumenTotales(t: FilaReporte) {
    val evaluados = t.puntuales + t.tardes
    val puntualidad = if (evaluados > 0) "${t.puntuales * 100 / evaluados}%" else "—"
    val datos = listOf(
        Triple("Horas", formatoDuracionCorta(t.minutos), Color.Unspecified),
        Triple("Días", t.dias.toString(), Color.Unspecified),
        Triple("Puntualidad", puntualidad, colorExito),
        Triple("Tardes", t.tardes.toString(), if (t.tardes > 0) colorAviso else Color.Unspecified),
        Triple("Faltas", t.faltas.toString(), if (t.faltas > 0) MaterialTheme.colorScheme.error else Color.Unspecified),
        Triple("Extra", formatoDuracionCorta(t.minutosExtra), Color.Unspecified),
    )
    datos.chunked(3).forEach { fila ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fila.forEach { (etiqueta, valor, color) ->
                Tarjeta(Modifier.weight(1f)) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(valor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
                        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Una persona (o un mes) en una tarjeta compacta. Al tocarla se ve su detalle. */
@Composable
private fun TarjetaPersona(f: FilaReporte, titulo: String = f.nombre, onClick: () -> Unit) {
    Tarjeta(Modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (titulo == f.nombre) {
                        Text("${f.rol} · ${f.jornada}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatoDuracionCorta(f.minutos), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${f.dias} días", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (f.puntuales > 0) Etiqueta("${f.puntuales} puntuales", colorExito)
                if (f.tardes > 0) Etiqueta("${f.tardes} tardes · ${f.minutosTarde} min", colorAviso)
                if (f.faltas > 0) Etiqueta("${f.faltas} faltas", MaterialTheme.colorScheme.error)
                if (f.salidasAnticipadas > 0) Etiqueta("${f.salidasAnticipadas} salidas antes", colorAviso)
                if (f.minutosExtra > 0) Etiqueta("+${formatoDuracionCorta(f.minutosExtra)} extra", MaterialTheme.colorScheme.primary)
                if (f.minutosPermiso > 0) Etiqueta("${formatoDuracionCorta(f.minutosPermiso)} de permiso", MaterialTheme.colorScheme.outline)
                if (f.porRevisar > 0) Etiqueta("${f.porRevisar} por revisar", MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

/** Un día del detalle mensual de una persona, en un renglón. */
@Composable
private fun FilaDia(d: DiaReporte, fondo: Boolean) {
    val (texto, color) = when {
        d.estado == "Falta" -> "Falta" to MaterialTheme.colorScheme.error
        d.estado == "Día libre" -> d.estadoDia to MaterialTheme.colorScheme.outline
        d.estado == "Actividad especial" -> "Actividad" to MaterialTheme.colorScheme.secondary
        d.estado == "Tarde" -> "Tarde ${d.minutosTarde} min" to colorAviso
        else -> "Puntual" to colorExito
    }
    val detalle = listOfNotNull(
        d.horas.takeIf { it.isNotBlank() }?.let { "$it h" },
        d.minutosAntes.toIntOrNull()?.takeIf { it > 0 }?.let { "salió $it min antes" },
        d.extra.takeIf { it.isNotBlank() && it != "0:00" }?.let { "+$it extra" },
        d.permiso.takeIf { it.isNotBlank() }?.let { "permiso $it" },
        d.observacion.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth()
            .background(if (fondo) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(fechaCorta(d.fecha), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (d.entrada.isBlank()) "—" else "${d.entrada} – ${d.salida.ifBlank { "sin salida" }}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (detalle.isNotBlank()) {
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Etiqueta(texto, color)
    }
}

private val DIAS_CORTOS = listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")

/** "2026-11-05" → "Jue 05". */
private fun fechaCorta(fecha: String): String = runCatching {
    val (a, m, d) = fecha.split("-").map { it.toInt() }
    val c = Calendar.getInstance().apply { set(a, m - 1, d) }
    "${DIAS_CORTOS[c.get(Calendar.DAY_OF_WEEK) - 1]} %02d".format(d)
}.getOrDefault(fecha)

/** Muestra la tabla con desplazamiento horizontal: encabezados, filas y totales. */
@Composable
private fun TablaEnPantalla(tabla: TablaReporte) {
    val anchos = tabla.encabezados.indices.map { c ->
        val largo = (listOf(tabla.encabezados[c]) + tabla.filas.map { it.getOrNull(c)?.texto.orEmpty() }).maxOf { it.length }
        (largo * 7 + 16).coerceIn(56, 200).dp
    }
    Tarjeta {
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            FilaTabla(tabla.encabezados.map { Celda(it) }, anchos, encabezado = true)
            tabla.filas.forEachIndexed { i, f -> FilaTabla(f, anchos, fondo = i % 2 == 1) }
            tabla.totales?.let { FilaTabla(it, anchos, encabezado = true) }
        }
    }
}

@Composable
private fun FilaTabla(celdas: List<Celda>, anchos: List<androidx.compose.ui.unit.Dp>, encabezado: Boolean = false, fondo: Boolean = false) {
    val color = when {
        encabezado -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        fondo -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
        else -> Color.Transparent
    }
    Row(Modifier.background(color).padding(horizontal = 8.dp, vertical = 6.dp)) {
        celdas.forEachIndexed { i, c ->
            Text(
                c.texto,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (encabezado) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(anchos.getOrElse(i) { 80.dp }),
            )
        }
    }
}

/** Guarda el archivo elegido y avisa si salió bien. */
fun guardarArchivo(context: Context, uri: Uri, tipo: String, escribir: (OutputStream) -> Unit) {
    Exportar.cargarLogo(context)
    val ok = runCatching {
        context.contentResolver.openOutputStream(uri)?.use(escribir) ?: error("sin acceso")
    }.isSuccess
    Toast.makeText(context, if (ok) "$tipo guardado." else "No se pudo guardar el $tipo.", Toast.LENGTH_LONG).show()
}

// ---------------------------------------------------------------- Tablas de los reportes

private fun horas(min: Long) = Celda("%d:%02d".format(min / 60, min % 60), Math.round(min / 60.0 * 100) / 100.0)
private fun num(n: Int) = Celda(n.toString(), n.toDouble())
private fun num(n: Long) = Celda(n.toString(), n.toDouble())

private val ENCABEZADOS_RESUMEN = listOf("Días", "Puntuales", "Tardes", "Min tarde", "Salidas antic.", "Faltas",
    "Incompletos", "Actividades", "Horas", "Horas extra", "Por revisar", "Horas permiso")

private fun celdasResumen(f: FilaReporte) = listOf(num(f.dias), num(f.puntuales), num(f.tardes), num(f.minutosTarde),
    num(f.salidasAnticipadas), num(f.faltas), num(f.incompletos), num(f.actividades), horas(f.minutos),
    horas(f.minutosExtra), num(f.porRevisar), horas(f.minutosPermiso))

private fun subtituloTotales(t: FilaReporte) = "Total: ${formatoDuracion(t.minutos)} trabajadas · ${t.dias} días · " +
    "${t.puntuales} puntuales · ${t.tardes} tardes · ${t.faltas} faltas · ${formatoDuracion(t.minutosExtra)} extra"

private val FIRMAS_GENERAL = listOf("Elaborado por", "Vo. Bo. Director")
private fun firmasPersona(nombre: String) = listOf("Firma del colaborador\n$nombre", "Firma del Director o Supervisor")

/** Mes elegido: todo el personal (una fila por persona) o una persona (una fila por día). */
fun tablaMensual(r: ReporteMes, persona: String?): TablaReporte {
    val colegio = "Colegio Mesón"
    if (persona == null) {
        val filas = r.filas.sortedByDescending { it.minutos }
        val total = FilaReporte.total("Total", filas)
        return TablaReporte(
            titulo = "Resumen general · ${nombreMes(r.mes)}",
            subtitulo = "$colegio · ${filas.size} personas\n${subtituloTotales(total)}",
            encabezados = listOf("Nombre", "Rol", "Jornada") + ENCABEZADOS_RESUMEN,
            filas = filas.map { listOf(Celda(it.nombre), Celda(it.rol), Celda(it.jornada)) + celdasResumen(it) },
            totales = listOf(Celda("Total"), Celda(""), Celda("")) + celdasResumen(total),
            firmas = FIRMAS_GENERAL,
        )
    }
    val resumen = r.filas.firstOrNull { it.nombre == persona }
    val dias = r.detalle.filter { it.nombre == persona }.sortedBy { it.fecha }
    return TablaReporte(
        titulo = "$persona · ${nombreMes(r.mes)}",
        subtitulo = colegio + (resumen?.let { " · ${it.rol} · ${it.jornada}\n${subtituloTotales(it)}" } ?: ""),
        encabezados = listOf("Fecha", "Entrada", "Salida", "Horas", "Estado", "Ent. status", "Min tarde", "Min antes",
            "Extra", "Permiso", "Revisión", "Observación"),
        filas = dias.map { d ->
            listOf(Celda(d.fecha.split("-").reversed().joinToString("/")), Celda(d.entrada), Celda(d.salida), Celda(d.horas),
                Celda(d.estadoDia), Celda(if (d.estado == "Puntual" || d.estado == "Tarde") d.estado else "—"),
                Celda(d.minutosTarde, d.minutosTarde.toDoubleOrNull()), Celda(d.minutosAntes, d.minutosAntes.toDoubleOrNull()),
                Celda(d.extra), Celda(d.permiso), Celda(d.revision), Celda(d.observacion))
        },
        totales = resumen?.let {
            listOf(Celda("Total"), Celda(""), Celda(""), horas(it.minutos), Celda("${it.dias} días"), Celda("${it.tardes} tardes"),
                num(it.minutosTarde), Celda("${it.salidasAnticipadas} salidas"), horas(it.minutosExtra), horas(it.minutosPermiso), Celda(""), Celda(""))
        },
        firmas = firmasPersona(persona),
    )
}

/** Año elegido: todo el personal (totales del año por persona) o una persona (una fila por mes). */
fun tablaAnual(r: ReporteAnual, persona: String?): TablaReporte {
    val colegio = "Colegio Mesón"
    if (persona == null) {
        val porPersona = r.filas.groupBy { it.nombre }.map { (nombre, filas) ->
            FilaReporte.total(nombre, filas).copy(rol = filas.last().rol, jornada = filas.last().jornada)
        }.sortedByDescending { it.minutos }
        val total = FilaReporte.total("Total", porPersona)
        return TablaReporte(
            titulo = "Resumen anual general · ${r.anio}",
            subtitulo = "$colegio · ${porPersona.size} personas · ${r.meses.size} meses\n${subtituloTotales(total)}",
            encabezados = listOf("Nombre", "Rol", "Jornada") + ENCABEZADOS_RESUMEN,
            filas = porPersona.map { listOf(Celda(it.nombre), Celda(it.rol), Celda(it.jornada)) + celdasResumen(it) },
            totales = listOf(Celda("Total"), Celda(""), Celda("")) + celdasResumen(total),
            firmas = FIRMAS_GENERAL,
        )
    }
    val meses = r.filas.filter { it.nombre == persona }.sortedBy { it.mes }
    val total = FilaReporte.total("Total", meses)
    return TablaReporte(
        titulo = "$persona · Resumen anual ${r.anio}",
        subtitulo = colegio + (meses.lastOrNull()?.let { " · ${it.rol} · ${it.jornada}" } ?: "") + "\n${subtituloTotales(total)}",
        encabezados = listOf("Mes") + ENCABEZADOS_RESUMEN,
        filas = meses.map { listOf(Celda(nombreMes(it.mes))) + celdasResumen(it) },
        totales = listOf(Celda("Total")) + celdasResumen(total),
        firmas = firmasPersona(persona),
    )
}
