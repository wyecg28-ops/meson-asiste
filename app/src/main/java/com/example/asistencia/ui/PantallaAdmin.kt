/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.asistencia.datos.Evento
import com.example.asistencia.datos.Horario
import com.example.asistencia.datos.LETRAS_DIAS
import com.example.asistencia.datos.HoraDia
import com.example.asistencia.datos.NOMBRES_DIAS
import com.example.asistencia.datos.Perfil
import com.example.asistencia.datos.Permiso
import com.example.asistencia.datos.Sede
import com.example.asistencia.datos.duracionHorario
import com.example.asistencia.datos.formatoDuracion
import com.example.asistencia.datos.terminaDiaSiguiente
import com.example.asistencia.verificacion.MetodoVerificacion
import com.example.asistencia.datos.Rol
import com.example.asistencia.datos.TipoEvento
import com.example.asistencia.datos.aMinutos
import com.example.asistencia.datos.claveDia
import com.example.asistencia.ui.theme.colorAviso
import com.example.asistencia.ui.theme.colorExito
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Secciones del panel; cada una se ve si el rol tiene esa función (la configura el administrador en Roles). */
private enum class SeccionAdmin(val etiqueta: String, val permiso: Permiso) {
    HOY("Hoy", Permiso.HOY),
    SOLICITUDES("Solicitudes", Permiso.SOLICITUDES),
    MIEMBROS("Miembros", Permiso.MIEMBROS),
    JORNADAS("Jornadas", Permiso.JORNADAS),
    ROLES("Roles", Permiso.ROLES),
    CALENDARIO("Calendario", Permiso.CALENDARIO),
    ACTIVIDADES("Por revisar", Permiso.REVISION),
    REPORTES("Reportes", Permiso.REPORTES),
    // Los ajustes del colegio están en Perfil.
}

@Composable
fun PantallaAdmin(avisos: SnackbarHostState, perfil: Perfil, miCorreo: String = "", vm: AdminViewModel = viewModel()) {
    val estado by vm.estado.collectAsState()
    val administrador = perfil.esAdministrador
    val secciones = SeccionAdmin.entries.filter { perfil.puede(it.permiso) }
    var seccion by rememberSaveable { mutableStateOf(secciones.firstOrNull() ?: SeccionAdmin.HOY) }
    if (seccion !in secciones && secciones.isNotEmpty()) seccion = secciones.first()
    // Se cambia de pestaña deslizando la pantalla a los lados (o tocando las pestañas de arriba).
    val paginas = rememberPagerState(initialPage = secciones.indexOf(seccion).coerceAtLeast(0)) { secciones.size }
    val pestanas = rememberLazyListState()
    val alcance = rememberCoroutineScope()
    LaunchedEffect(paginas.settledPage, secciones.size) {
        secciones.getOrNull(paginas.settledPage)?.let { seccion = it }
    }
    LaunchedEffect(paginas.currentPage) {
        if (secciones.isNotEmpty()) pestanas.animateScrollToItem((paginas.currentPage - 1).coerceAtLeast(0))
    }

    LaunchedEffect(estado.mensaje) {
        val mensaje = estado.mensaje ?: return@LaunchedEffect
        avisos.showSnackbar(VisualMensaje(mensaje.texto, mensaje.error))
        vm.mensajeMostrado(mensaje)
    }
    val cargar = {
        when (seccion) {
            SeccionAdmin.HOY -> vm.cargarHoy()
            SeccionAdmin.SOLICITUDES -> vm.cargarSolicitudes()
            SeccionAdmin.MIEMBROS -> vm.cargarMiembros()
            SeccionAdmin.JORNADAS -> vm.cargarJornadas()
            SeccionAdmin.ROLES -> vm.cargarRoles()
            SeccionAdmin.CALENDARIO -> vm.cargarCalendario()
            SeccionAdmin.ACTIVIDADES -> vm.cargarActividades()
            SeccionAdmin.REPORTES -> Unit
        }
    }
    // Se actualiza al volver a la app y cada 2 minutos (cambios hechos en la hoja de Google).
    AlVolverYCada(120_000, seccion) { cargar() }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Titulo("Administración", if (administrador) "Control del personal en tiempo real." else "${perfil.rol.nombre}: tus funciones de administración.")
                }
                TextButton(onClick = { cargar() }, enabled = !estado.cargando) { Text("Actualizar") }
            }
            LazyRow(state = pestanas, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(secciones) { i, s ->
                    val pendientes = when (s) {
                        SeccionAdmin.ACTIVIDADES -> estado.actividades?.count { !it.correo.equals(miCorreo, true) } ?: 0
                        SeccionAdmin.SOLICITUDES -> estado.solicitudes?.count { it.pendiente && !it.correo.equals(miCorreo, true) } ?: 0
                        else -> 0
                    }
                    val etiqueta = if (pendientes > 0) "${s.etiqueta} ($pendientes)" else s.etiqueta
                    FilterChip(
                        selected = paginas.currentPage == i,
                        onClick = { alcance.launch { paginas.animateScrollToPage(i) } },
                        label = { Text(etiqueta) },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(4.dp)) {
                if (estado.cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        HorizontalPager(state = paginas, modifier = Modifier.weight(1f), beyondViewportPageCount = 0) { pagina ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (secciones[pagina]) {
                    SeccionAdmin.HOY -> SeccionHoy(estado.hoy, perfil, vm)
                    SeccionAdmin.SOLICITUDES -> SeccionSolicitudes(estado, vm, miCorreo)
                    SeccionAdmin.MIEMBROS -> SeccionMiembros(estado, vm, editable = perfil.puede(Permiso.MIEMBROS), perfil = perfil)
                    SeccionAdmin.JORNADAS -> SeccionJornadas(estado, vm, perfil.metaMinutos)
                    SeccionAdmin.ROLES -> SeccionRoles(estado, vm, perfil)
                    SeccionAdmin.CALENDARIO -> SeccionCalendario(estado, vm)
                    SeccionAdmin.ACTIVIDADES -> SeccionActividades(estado.actividades, vm, miCorreo)
                    SeccionAdmin.REPORTES -> SeccionReportes(estado, vm)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ---------- Hoy ----------

@Composable
private fun colorEstado(estado: EstadoAsistencia): Color = when (estado) {
    EstadoAsistencia.PUNTUAL -> colorExito
    EstadoAsistencia.TARDE -> colorAviso
    EstadoAsistencia.ACTIVIDAD -> MaterialTheme.colorScheme.secondary
    EstadoAsistencia.LIBRE -> MaterialTheme.colorScheme.outline
    EstadoAsistencia.SIN_MARCAR -> MaterialTheme.colorScheme.error
}

@Composable
private fun SeccionHoy(hoy: List<AsistenciaHoy>?, perfil: Perfil, vm: AdminViewModel) {
    // Acceso rápido al QR de la entrada (administrador o supervisor), si el colegio marca con QR.
    var mostrandoQr by remember { mutableStateOf<Sede?>(null) }
    perfil.sedes.filter { it.metodo == MetodoVerificacion.QR.name }.forEach { sede ->
        Button(
            onClick = { mostrandoQr = sede },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Mostrar QR de la entrada", maxLines = 1) }
    }
    mostrandoQr?.let { s -> PantallaQrSede(s, vm) { mostrandoQr = null } }
    if (hoy == null) return
    if (hoy.isEmpty()) {
        EstadoVacio("Aún no hay miembros", "Cuando alguien se registre en la app aparecerá aquí.")
        return
    }
    // Resumen en 2 × 2 para que los textos quepan en cualquier teléfono.
    listOf(
        listOf(EstadoAsistencia.PUNTUAL to "Puntuales", EstadoAsistencia.TARDE to "Tarde"),
        listOf(EstadoAsistencia.SIN_MARCAR to "Sin marcar", EstadoAsistencia.ACTIVIDAD to "Actividad"),
    ).forEach { fila ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fila.forEach { (e, texto) ->
                Tarjeta(Modifier.weight(1f)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${hoy.count { it.estado == e }}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colorEstado(e),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            texto, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
    hoy.forEach { a ->
        Tarjeta {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(a.nombre, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${a.rol} · ${a.jornada}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (a.entrada.isNotEmpty()) {
                        Text(
                            "Entrada ${a.entrada} · Salida ${a.salida.ifEmpty { "—" }}" +
                                if (a.horas.isNotEmpty()) " · ${a.horas} h" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (a.detalle.isNotEmpty()) {
                        Text(a.detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Etiqueta(a.estado.etiqueta, colorEstado(a.estado))
            }
        }
    }
}

// ---------- Miembros ----------

@Composable
private fun SeccionMiembros(estado: EstadoAdmin, vm: AdminViewModel, editable: Boolean, perfil: Perfil) {
    val miembros = estado.miembros ?: return
    var editando by remember { mutableStateOf<Miembro?>(null) }
    Text(
        if (editable) "Toca a una persona para cambiar su rol, su jornada o su acceso."
        else "Tu rol no puede cambiar roles, jornadas o accesos.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (miembros.isEmpty()) EstadoVacio("Aún no hay miembros", "Cuando alguien se registre en la app aparecerá aquí.")
    miembros.forEach { m ->
        Tarjeta(Modifier.selectable(selected = false, enabled = editable, onClick = { editando = m })) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(m.nombre, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        m.correo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (estado.roles.firstOrNull { it.codigo == m.rol }?.marca == false) m.rolNombre
                        else "${m.rolNombre} · ${nombreHorario(estado.horarios, m.jornada)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.width(8.dp))
                when {
                    !m.activo -> Etiqueta("Inactivo", MaterialTheme.colorScheme.error)
                    !m.conTelefono -> Etiqueta("Sin teléfono", MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
    editando?.let { m ->
        DialogoMiembro(
            miembro = m,
            horarios = estado.horarios,
            // Dar o quitar un rol con funciones de administración requiere la función "Gestionar roles".
            roles = estado.roles.filter { r -> r.codigo == m.rol || (if (r.protegido) perfil.rol.esSuperAdmin else perfil.puede(Permiso.ROLES) || !r.gestiona) },
            onCerrar = { editando = null },
            onGuardar = { rol, jornada, activo ->
                vm.guardarMiembro(m, rol, jornada, activo)
                editando = null
            },
            onLiberar = {
                vm.liberarTelefono(m)
                editando = null
            },
            onEliminar = {
                vm.eliminarMiembro(m)
                editando = null
            },
        )
    }
}

private fun nombreHorario(horarios: List<Horario>, codigo: String) =
    horarios.firstOrNull { it.codigo == codigo }?.let { "${it.nombre} (${horaAmigable(it.entrada)} – ${horaAmigable(it.salida)}, ${it.textoDias})" }
        ?: codigo

@Composable
private fun DialogoMiembro(
    miembro: Miembro,
    horarios: List<Horario>,
    roles: List<Rol>,
    onCerrar: () -> Unit,
    onGuardar: (String, String, Boolean) -> Unit,
    onLiberar: () -> Unit,
    onEliminar: () -> Unit,
) {
    var rol by remember { mutableStateOf(miembro.rol) }
    var jornada by remember { mutableStateOf(miembro.jornada) }
    var activo by remember { mutableStateOf(miembro.activo) }
    var confirmar by remember { mutableStateOf<String?>(null) } // "liberar" | "eliminar"

    confirmar?.let { accion ->
        val eliminar = accion == "eliminar"
        AlertDialog(
            onDismissRequest = { confirmar = null },
            title = { Text(if (eliminar) "¿Eliminar a ${miembro.nombre}?" else "¿Liberar teléfono?") },
            text = {
                Text(
                    if (eliminar) "Se le cierra el acceso a la app y deja de aparecer en la lista. " +
                        "Su historial de asistencia se conserva en la hoja."
                    else "${miembro.nombre} podrá registrarse en otro teléfono. Úsalo si cambió o perdió su teléfono."
                )
            },
            confirmButton = { Button(onClick = if (eliminar) onEliminar else onLiberar) { Text(if (eliminar) "Eliminar" else "Liberar") } },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(miembro.nombre) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Acceso activo", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (activo) "Puede usar la app." else "No podrá entrar a la app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (activo) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    }
                    Switch(checked = activo, onCheckedChange = { activo = it })
                }
                Spacer(Modifier.height(12.dp))
                Text("Rol", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                roles.forEach { r -> OpcionRadio(r.nombre, rol == r.codigo) { rol = r.codigo } }
                val elegido = roles.firstOrNull { it.codigo == rol }
                if (elegido != null) {
                    Text(
                        descripcionRol(elegido),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (elegido?.marca != false) {
                    Spacer(Modifier.height(12.dp))
                    Text("Jornada", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    horarios.forEach { h ->
                        OpcionRadio(
                            "${h.nombre} (${horaAmigable(h.entrada)} – ${horaAmigable(h.salida)}, ${h.textoDias})",
                            jornada == h.codigo,
                        ) { jornada = h.codigo }
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                if (miembro.conTelefono) {
                    TextButton(onClick = { confirmar = "liberar" }) { Text("Liberar teléfono (${miembro.modelo})") }
                }
                TextButton(onClick = { confirmar = "eliminar" }) {
                    Text("Eliminar miembro", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { Button(onClick = { onGuardar(rol, jornada, activo) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

@Composable
private fun OpcionRadio(texto: String, seleccionado: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .selectable(selected = seleccionado, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = seleccionado, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Hora elegida con ruedas (SelectorHora): siempre "HH:mm" o "" si no se eligió. */
private val HORA_VALIDA = Regex("^\\d{2}:\\d{2}$")

// ---------- Jornadas ----------

@Composable
private fun SeccionJornadas(estado: EstadoAdmin, vm: AdminViewModel, meta: Int) {
    var editando by remember { mutableStateOf<Horario?>(null) }
    var nueva by remember { mutableStateOf(false) }
    var eliminando by remember { mutableStateOf<Horario?>(null) }

    Button(
        onClick = { nueva = true },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { Text("+ Nueva jornada", maxLines = 1) }

    if (nueva || editando != null) {
        DialogoJornada(editando, meta, onCerrar = { nueva = false; editando = null }) {
            vm.guardarJornada(it)
            nueva = false
            editando = null
        }
    }
    eliminando?.let { h ->
        AlertDialog(
            onDismissRequest = { eliminando = null },
            title = { Text("¿Eliminar ${h.nombre}?") },
            text = { Text("Solo se puede eliminar si ningún miembro la tiene asignada.") },
            confirmButton = { Button(onClick = { vm.eliminarJornada(h); eliminando = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { eliminando = null }) { Text("Cancelar") } },
        )
    }

    val jornadas = estado.jornadas ?: return
    jornadas.forEach { (h, enUso) ->
        Tarjeta {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(h.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Entrada ${horaAmigable(h.entrada)} · Salida ${horaAmigable(h.salida)}" +
                        if (terminaDiaSiguiente(h.entrada, h.salida)) " (día siguiente)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Días: ${h.textoDias}", style = MaterialTheme.typography.bodySmall)
                h.porDia.toSortedMap().filterKeys { it in h.dias }.forEach { (d, hd) ->
                    Text(
                        "${NOMBRES_DIAS[d]}: ${horaAmigable(hd.entrada)} – ${horaAmigable(hd.salida)}" +
                            if (terminaDiaSiguiente(hd.entrada, hd.salida)) " (día siguiente)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Text(
                    if (enUso == 1) "1 miembro" else "$enUso miembros",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = { editando = h }) { Text("Editar") }
                    TextButton(onClick = { eliminando = h }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun DialogoJornada(original: Horario?, meta: Int, onCerrar: () -> Unit, onGuardar: (Horario) -> Unit) {
    // La salida se calcula sola: entrada + meta del colegio (p. ej. 18:00 + 8 h = 02:00 del día siguiente).
    var salidaAuto by remember {
        mutableStateOf(original == null || original.salida == sumarMinutos(original.entrada, meta))
    }
    var nombre by remember { mutableStateOf(original?.nombre.orEmpty()) }
    var entrada by remember { mutableStateOf(original?.entrada.orEmpty()) }
    var salida by remember { mutableStateOf(original?.salida.orEmpty()) }
    var dias by remember { mutableStateOf(original?.dias?.toSet() ?: setOf(1, 2, 3, 4, 5)) }
    // Horario distinto por día (p. ej. sábado más corto). Vacío = todos los días con el horario general.
    var distintoPorDia by remember { mutableStateOf(original?.porDia?.isNotEmpty() == true) }
    var porDia by remember { mutableStateOf(original?.porDia.orEmpty()) }
    fun horaDe(d: Int) = porDia[d] ?: HoraDia(entrada, salida)
    fun horarioValido(e: String, s: String) = HORA_VALIDA.matches(e) && HORA_VALIDA.matches(s) && aMinutos(e) != aMinutos(s)
    val diasValidos = !distintoPorDia || dias.all { horaDe(it).let { h -> horarioValido(h.entrada, h.salida) } }
    val valida = nombre.trim().length >= 2 && horarioValido(entrada, salida) && dias.isNotEmpty() && diasValidos
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(if (original == null) "Nueva jornada" else "Editar jornada") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it.take(40) },
                    label = { Text("Nombre") },
                    placeholder = { Text("Ej.: Jornada matutina") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectorHora(entrada, "Entrada", Modifier.weight(1f)) {
                        entrada = it
                        if (salidaAuto) salida = sumarMinutos(it, meta)
                    }
                    SelectorHora(salida, "Salida", Modifier.weight(1f)) {
                        salida = it
                        salidaAuto = HORA_VALIDA.matches(entrada) && it == sumarMinutos(entrada, meta)
                    }
                }
                val textoMeta = formatoDuracion(meta.toLong())
                if (salidaAuto) {
                    Text(
                        "La salida se calcula sola: entrada + $textoMeta (la meta del colegio). Puedes cambiarla si lo necesitas.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (HORA_VALIDA.matches(entrada)) {
                    TextButton(onClick = { salida = sumarMinutos(entrada, meta); salidaAuto = true }) {
                        Text("Calcular salida con $textoMeta")
                    }
                }
                AvisoHorario(entrada, salida)
                Text("Días laborales", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LETRAS_DIAS.forEach { (n, letra) ->
                        FilterChip(
                            selected = n in dias,
                            onClick = { dias = if (n in dias) dias - n else dias + n },
                            label = { Text(letra) },
                        )
                    }
                }
                if (dias.isEmpty()) {
                    Text("Elige al menos un día.", style = MaterialTheme.typography.bodySmall, color = colorAviso)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Horario distinto por día", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Por ejemplo, el sábado de 8:00 a 12:00.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = distintoPorDia, onCheckedChange = { distintoPorDia = it })
                }
                if (distintoPorDia) {
                    dias.sorted().forEach { d ->
                        val h = horaDe(d)
                        Text(NOMBRES_DIAS[d].orEmpty(), style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectorHora(h.entrada, "Entrada", Modifier.weight(1f)) {
                                // Si la salida de ese día estaba calculada con la meta, se recalcula.
                                val auto = !HORA_VALIDA.matches(h.entrada) || h.salida == sumarMinutos(h.entrada, meta)
                                porDia = porDia + (d to HoraDia(it, if (auto) sumarMinutos(it, meta) else h.salida))
                            }
                            SelectorHora(h.salida, "Salida", Modifier.weight(1f)) { porDia = porDia + (d to h.copy(salida = it)) }
                        }
                        AvisoHorario(h.entrada, h.salida)
                    }
                }
                Text(
                    "Cada día se pide la meta del colegio o lo que dure su horario, si es menos. Lo que pase de la meta suma como horas extra.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(enabled = valida, onClick = {
                // Solo se guardan los días que de verdad son distintos al horario general.
                val distintos = if (!distintoPorDia) emptyMap()
                else dias.associateWith { horaDe(it) }.filterValues { it != HoraDia(entrada, salida) }
                onGuardar(Horario(original?.codigo.orEmpty(), nombre.trim(), entrada, salida, dias.sorted(), distintos))
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

/** "18:00" + 480 min = "02:00" (da la vuelta a medianoche). */
private fun sumarMinutos(hora: String, minutos: Int): String {
    if (!HORA_VALIDA.matches(hora)) return ""
    val total = ((aMinutos(hora) + minutos) % 1440 + 1440) % 1440
    return "%02d:%02d".format(total / 60, total % 60)
}

/** Avisa si el horario termina al día siguiente (turno nocturno) o si entrada y salida son iguales. */
@Composable
private fun AvisoHorario(entrada: String, salida: String) {
    if (!HORA_VALIDA.matches(entrada) || !HORA_VALIDA.matches(salida)) return
    when {
        aMinutos(entrada) == aMinutos(salida) ->
            Text("La salida no puede ser a la misma hora que la entrada.", style = MaterialTheme.typography.bodySmall, color = colorAviso)
        terminaDiaSiguiente(entrada, salida) -> Text(
            "Termina al día siguiente (turno nocturno): ${formatoDuracion(duracionHorario(entrada, salida).toLong())}.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
        )
        else -> Text(
            "Duración: ${formatoDuracion(duracionHorario(entrada, salida).toLong())}.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- Roles ----------

/** "Marca asistencia · Hoy, Solicitudes" o "No marca asistencia · todas las funciones". */
fun descripcionRol(r: Rol): String {
    val marca = if (r.marca) "Marca entrada, salida y permisos" else "No marca asistencia"
    val funciones = when {
        r.esSuperAdmin -> "todas las funciones, y decide las del Administrador"
        r.permisos.isEmpty() -> "sin funciones de administración"
        else -> r.permisos.sortedBy { it.ordinal }.joinToString(", ") { it.etiqueta.lowercase() }
    }
    return "$marca · $funciones."
}

@Composable
private fun SeccionRoles(estado: EstadoAdmin, vm: AdminViewModel, perfil: Perfil) {
    var editando by remember { mutableStateOf<Rol?>(null) }
    var nuevo by remember { mutableStateOf(false) }
    var eliminando by remember { mutableStateOf<Rol?>(null) }
    val superAdmin = perfil.rol.esSuperAdmin

    Button(
        onClick = { nuevo = true },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { Text("+ Nuevo rol", maxLines = 1) }
    Text(
        "Elige qué hace cada rol: si marca asistencia y qué funciones de administración tiene. " +
            (if (superAdmin) "Tú decides también las funciones del Administrador. "
            else "Los roles Super administrador y Administrador solo los cambia el super administrador. ") +
            "Los roles fijos no se pueden eliminar.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (nuevo || editando != null) {
        DialogoRol(editando, superAdmin, onCerrar = { nuevo = false; editando = null }) {
            vm.guardarRol(it)
            nuevo = false
            editando = null
        }
    }
    eliminando?.let { r ->
        AlertDialog(
            onDismissRequest = { eliminando = null },
            title = { Text("¿Eliminar el rol ${r.nombre}?") },
            text = { Text("Solo se puede eliminar si nadie lo tiene asignado.") },
            confirmButton = { Button(onClick = { vm.eliminarRol(r); eliminando = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { eliminando = null }) { Text("Cancelar") } },
        )
    }

    val enUso = estado.rolesEnUso ?: return
    estado.roles.forEach { r ->
        Tarjeta {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Etiqueta(
                        if (r.marca) "Marca" else "No marca",
                        if (r.marca) colorExito else MaterialTheme.colorScheme.outline,
                    )
                }
                Text(descripcionRol(r), style = MaterialTheme.typography.bodySmall)
                val n = enUso[r.codigo] ?: 0
                Text(
                    if (n == 1) "1 persona" else "$n personas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    if (!r.protegido || superAdmin) TextButton(onClick = { editando = r }) { Text("Editar") }
                    else Text(
                        "Solo lo cambia el super administrador",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    if (!r.protegido && r.codigo != "DOCENTE") {
                        TextButton(onClick = { eliminando = r }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogoRol(original: Rol?, superAdmin: Boolean, onCerrar: () -> Unit, onGuardar: (Rol) -> Unit) {
    var nombre by remember { mutableStateOf(original?.nombre.orEmpty()) }
    var marca by remember { mutableStateOf(original?.marca ?: true) }
    var permisos by remember { mutableStateOf(original?.permisos.orEmpty()) }
    // El Super administrador tiene siempre todas las funciones.
    val administrador = original?.esSuperAdmin == true
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(if (original == null) "Nuevo rol" else "Editar rol") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it.take(40) },
                    label = { Text("Nombre del rol") },
                    placeholder = { Text("Ej.: Coordinador") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Marca asistencia", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (marca) "Marca entrada y salida, pide permisos y recibe recordatorios."
                            else "No marca: solo usa sus funciones de administración.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = marca, onCheckedChange = { marca = it })
                }
                Spacer(Modifier.height(8.dp))
                Text("Funciones de administración", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (administrador) {
                    Text(
                        "El Super administrador tiene siempre todas las funciones.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // "Gestionar roles" solo la puede dar el super administrador.
                    Permiso.entries.filter { it != Permiso.ROLES || superAdmin }.forEach { p ->
                        val marcado = p in permisos
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp)
                                .selectable(selected = marcado, onClick = { permisos = if (marcado) permisos - p else permisos + p }, role = Role.Checkbox),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = marcado, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(p.etiqueta, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (permisos.isEmpty()) {
                        Text(
                            "Sin funciones: solo marca su asistencia (como Docente).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = nombre.trim().length >= 2, onClick = {
                onGuardar(
                    Rol(
                        codigo = original?.codigo.orEmpty(),
                        nombre = nombre.trim(),
                        marca = marca,
                        permisos = if (administrador) Permiso.entries.toSet() else permisos,
                    )
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

// ---------- Calendario ----------

@Composable
private fun SeccionCalendario(estado: EstadoAdmin, vm: AdminViewModel) {
    var agregando by remember { mutableStateOf(false) }
    var eliminando by remember { mutableStateOf<Evento?>(null) }
    Button(
        onClick = { agregando = true },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Text("+ Agregar al calendario", maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (agregando) {
        // Solo personas cuyo rol marca asistencia.
        val marcan = estado.miembros.orEmpty().filter { m ->
            m.activo && estado.roles.firstOrNull { it.codigo == m.rol }?.marca != false
        }
        DialogoEvento(marcan, onCerrar = { agregando = false }) {
            vm.agregarEvento(it)
            agregando = false
        }
    }
    eliminando?.let { e ->
        AlertDialog(
            onDismissRequest = { eliminando = null },
            title = { Text("¿Eliminar ${e.tipo.etiqueta.lowercase()}?") },
            text = { Text("Se quitará del calendario de ${fechaLegible(e.desde)}.") },
            confirmButton = { Button(onClick = { vm.eliminarEvento(e); eliminando = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { eliminando = null }) { Text("Cancelar") } },
        )
    }
    val eventos = estado.eventos ?: return
    if (eventos.isEmpty()) {
        EstadoVacio("Calendario vacío", "Los días libres no cuentan como falta y la entrada especial cambia la hora de entrada.")
    }
    eventos.forEach { e ->
        Tarjeta {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Etiqueta(
                        if (e.porHoras) "Permiso por horas" else e.tipo.etiqueta,
                        if (e.tipo.diaLibre) MaterialTheme.colorScheme.secondary else colorAviso,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { eliminando = e }) { Text("Eliminar") }
                }
                Text(
                    if (e.desde == e.hasta) fechaLegible(e.desde) else "${fechaLegible(e.desde)} al ${fechaLegible(e.hasta)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (e.porHoras) Text("De ${e.horaInicio} a ${e.horaFin}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Para: " + if (e.paraTodos) "todo el personal"
                    else e.aplicaA.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ") { c ->
                        estado.miembros?.firstOrNull { it.correo == c }?.nombre ?: c.substringBefore('@')
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (e.horaEntrada.isNotEmpty()) {
                    Text(
                        "Entrada ${e.horaEntrada}" + if (e.horaSalida.isNotEmpty()) " · Salida ${e.horaSalida}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (e.detalle.isNotEmpty()) {
                    Text(e.detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private val FORMATO_CLAVE = SimpleDateFormat("yyyy-MM-dd", Locale.US)

/** "2026-11-04" → "Miércoles 4 de noviembre". */
fun fechaLegible(clave: String): String =
    runCatching { formatear("EEEE d 'de' MMMM", FORMATO_CLAVE.parse(clave)!!.time) }.getOrDefault(clave)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoEvento(miembros: List<Miembro>, onCerrar: () -> Unit, onAgregar: (Evento) -> Unit) {
    val hoy = claveDia(System.currentTimeMillis())
    var tipo by remember { mutableStateOf(TipoEvento.VACACIONES) }
    var desde by remember { mutableStateOf(hoy) }
    var hasta by remember { mutableStateOf(hoy) }
    var paraTodos by remember { mutableStateOf(true) }
    var elegidos by remember { mutableStateOf(setOf<String>()) }
    var menuPersonas by remember { mutableStateOf(false) }
    var porHoras by remember { mutableStateOf(false) }
    var horaInicio by remember { mutableStateOf("") }
    var horaFin by remember { mutableStateOf("") }
    var hora by remember { mutableStateOf("") }
    var horaSalida by remember { mutableStateOf("") }
    var detalle by remember { mutableStateOf("") }
    var eligiendo by remember { mutableStateOf<String?>(null) } // "desde" | "hasta"

    eligiendo?.let { cual ->
        // El selector trabaja con medianoche UTC.
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

    val esPermisoPorHoras = tipo == TipoEvento.PERMISO && porHoras
    val horasValidas = !esPermisoPorHoras ||
        (HORA_VALIDA.matches(horaInicio) && HORA_VALIDA.matches(horaFin) && aMinutos(horaFin) > aMinutos(horaInicio))
    val entradaValida = tipo != TipoEvento.ENTRADA_ESPECIAL || (HORA_VALIDA.matches(hora) &&
        (horaSalida.isEmpty() || (HORA_VALIDA.matches(horaSalida) && aMinutos(horaSalida) != aMinutos(hora))))
    val personasValidas = paraTodos || elegidos.isNotEmpty()

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Agregar al calendario") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TipoEvento.entries.forEach { t -> OpcionRadio(t.etiqueta, tipo == t) { tipo = t } }

                // Permiso: de día completo o por horas.
                if (tipo == TipoEvento.PERMISO) {
                    Spacer(Modifier.height(4.dp))
                    Text("Duración del permiso", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    OpcionRadio("Día completo", !porHoras) { porHoras = false }
                    OpcionRadio("Por horas", porHoras) { porHoras = true }
                    if (porHoras) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectorHora(horaInicio, "Desde", Modifier.weight(1f)) { horaInicio = it }
                            SelectorHora(horaFin, "Hasta", Modifier.weight(1f)) { horaFin = it }
                        }
                        Text(
                            "Las horas del permiso cuentan para la meta. Si cubre la hora de entrada, no cuenta como tarde.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (tipo == TipoEvento.ENTRADA_ESPECIAL) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectorHora(hora, "Entrada", Modifier.weight(1f)) { hora = it }
                        SelectorHora(horaSalida, "Salida", Modifier.weight(1f)) { horaSalida = it }
                    }
                    if (HORA_VALIDA.matches(hora) && HORA_VALIDA.matches(horaSalida) && terminaDiaSiguiente(hora, horaSalida)) {
                        Text(
                            "Termina al día siguiente: ${formatoDuracion(duracionHorario(hora, horaSalida).toLong())} de turno.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Text(
                        "La salida es opcional y puede ser de madrugada. Si la pones, la meta de ese día es lo que dure " +
                            "ese horario (como máximo la meta del colegio).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { eligiendo = "desde" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Desde: ${fechaLegible(desde)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = { eligiendo = "hasta" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Hasta: ${fechaLegible(hasta)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                // Para quién: todo el personal o algunas personas (lista desplegable con casillas).
                Spacer(Modifier.height(4.dp))
                Text("¿Para quién?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                OpcionRadio("Todo el personal", paraTodos) { paraTodos = true }
                OpcionRadio("Solo algunas personas", !paraTodos) { paraTodos = false }
                if (!paraTodos) {
                    Box {
                        OutlinedButton(onClick = { menuPersonas = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (elegidos.isEmpty()) "Elegir personas ▾" else "${elegidos.size} seleccionada(s) ▾",
                                maxLines = 1,
                            )
                        }
                        DropdownMenu(expanded = menuPersonas, onDismissRequest = { menuPersonas = false }) {
                            if (miembros.isEmpty()) {
                                DropdownMenuItem(text = { Text("No hay miembros registrados") }, onClick = { menuPersonas = false })
                            }
                            miembros.forEach { m ->
                                val marcado = m.correo in elegidos
                                DropdownMenuItem(
                                    text = { Text(m.nombre, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingIcon = { Checkbox(checked = marcado, onCheckedChange = null) },
                                    // El menú queda abierto para marcar varias personas.
                                    onClick = { elegidos = if (marcado) elegidos - m.correo else elegidos + m.correo },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Listo", fontWeight = FontWeight.SemiBold) }, onClick = { menuPersonas = false })
                        }
                    }
                    if (elegidos.isEmpty()) {
                        Text("Elige al menos una persona.", style = MaterialTheme.typography.bodySmall, color = colorAviso)
                    } else {
                        Text(
                            miembros.filter { it.correo in elegidos }.joinToString(", ") { it.nombre },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OutlinedTextField(
                    value = detalle,
                    onValueChange = { detalle = it.take(200) },
                    label = { Text("Detalle (opcional)") },
                    placeholder = { Text("Ej.: cita médica") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = horasValidas && entradaValida && personasValidas,
                onClick = {
                    onAgregar(
                        Evento(
                            id = "",
                            desde = desde,
                            hasta = hasta,
                            tipo = tipo,
                            aplicaA = if (paraTodos) "Todos" else elegidos.joinToString(", "),
                            horaEntrada = if (tipo == TipoEvento.ENTRADA_ESPECIAL) hora else "",
                            detalle = detalle.trim(),
                            horaInicio = if (esPermisoPorHoras) horaInicio else "",
                            horaFin = if (esPermisoPorHoras) horaFin else "",
                            horaSalida = if (tipo == TipoEvento.ENTRADA_ESPECIAL) horaSalida else "",
                        )
                    )
                },
            ) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

// ---------- Actividades especiales ----------

@Composable
private fun SeccionActividades(actividades: List<ActividadPorRevisar>?, vm: AdminViewModel, miCorreo: String) {
    actividades ?: return
    val enlaces = LocalUriHandler.current
    if (actividades.isEmpty()) {
        EstadoVacio("Nada por revisar", "Aquí aparecen los días en que alguien marcó fuera del colegio por una actividad especial.")
        return
    }
    Text(
        "Al aprobar, las horas de ese día quedan aceptadas en la hoja de la persona.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    actividades.forEach { a ->
        Tarjeta {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(a.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(fechaLegible(a.fecha), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Entrada ${a.entrada.ifEmpty { "—" }} · Salida ${a.salida.ifEmpty { "—" }}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (a.descripcion.isNotEmpty()) Text("“${a.descripcion}”", style = MaterialTheme.typography.bodyMedium)
                if (a.ubicacion.isNotEmpty()) {
                    TextButton(onClick = { runCatching { enlaces.openUri(a.ubicacion) } }) { Text("Ver ubicación en el mapa") }
                }
                if (miCorreo.isNotBlank() && a.correo.equals(miCorreo, ignoreCase = true)) {
                    Text(
                        "Es tu actividad: debe revisarla otro supervisor o el administrador.",
                        style = MaterialTheme.typography.bodySmall, color = colorAviso, fontWeight = FontWeight.Medium,
                    )
                } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { vm.revisarActividad(a, aprobar = false) }, modifier = Modifier.weight(1f)) {
                        Text("Rechazar", color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = { vm.revisarActividad(a, aprobar = true) }, modifier = Modifier.weight(1f)) {
                        Text("Aprobar")
                    }
                }
            }
        }
    }
}
