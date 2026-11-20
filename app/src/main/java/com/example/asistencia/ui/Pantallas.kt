/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import com.example.asistencia.datos.CuentaGuardada
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.asistencia.Config
import com.example.asistencia.R
import com.example.asistencia.auth.Biometria
import com.example.asistencia.auth.MetodoBiometrico
import com.example.asistencia.ui.theme.colorLogo

val PERMISOS_UBICACION = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/** En el primer inicio se piden juntos la ubicación (obligatoria) y las notificaciones (opcional). */
private val PERMISOS_INICIALES =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) PERMISOS_UBICACION + Manifest.permission.POST_NOTIFICATIONS
    else PERMISOS_UBICACION

fun ubicacionConcedida(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private enum class Pestana(val etiqueta: String, val icono: Int) {
    INICIO("Inicio", R.drawable.ic_inicio),
    ESTADISTICAS("Resumen", R.drawable.ic_estadisticas),
    PERMISOS("Permisos", R.drawable.ic_permiso),
    PERFIL("Perfil", R.drawable.ic_perfil),
    ADMIN("Admin", R.drawable.ic_admin),
}

internal class VisualMensaje(override val message: String, val error: Boolean) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction = true
    override val duration = if (error) SnackbarDuration.Long else SnackbarDuration.Short
}

@Composable
fun AppAsistencia(vm: AsistenciaViewModel = viewModel()) {
    var mostrarIntro by rememberSaveable { mutableStateOf(true) }
    Crossfade(targetState = mostrarIntro, animationSpec = tween(500), label = "intro") { intro ->
        if (intro) Intro(onTerminar = { mostrarIntro = false }) else Contenido(vm)
    }
}

@Composable
private fun Contenido(vm: AsistenciaViewModel) {
    val estado by vm.estado.collectAsState()
    val activity = LocalActivity.current as FragmentActivity
    val avisos = remember { SnackbarHostState() }
    var pestana by rememberSaveable { mutableStateOf(Pestana.INICIO) }
    // Según el rol (lo configura el administrador): todos ven Inicio (con el ranking); si marca asistencia ve
    // también Resumen y Permisos; si tiene funciones de administración ve Admin. Historial está dentro de Resumen.
    val pestanas = buildList {
        add(Pestana.INICIO)
        if (estado.perfil.marca) addAll(listOf(Pestana.ESTADISTICAS, Pestana.PERMISOS))
        if (estado.perfil.esGestor) add(Pestana.ADMIN)
        add(Pestana.PERFIL)
    }
    // Al volver a la app y cada 3 minutos: envía pendientes y trae los cambios de la hoja de Google y el ranking.
    AlVolverYCada(180_000, estado.correo to estado.listo) { if (estado.listo) vm.refrescar() }
    // Actualizaciones de la app (sin Google Play): revisa sola al abrir y al volver; si hay una, aparece el aviso.
    val actualizacion: ActualizacionViewModel = viewModel()
    AlVolverYCada(30 * 60_000L, Unit) { actualizacion.buscar() }
    DialogoActualizacion(actualizacion)
    if (pestana !in pestanas) pestana = pestanas.first()
    val accesoCerrado = estado.accesoCerrado != null

    LaunchedEffect(estado.mensaje) {
        val mensaje = estado.mensaje ?: return@LaunchedEffect
        avisos.showSnackbar(VisualMensaje(mensaje.texto, mensaje.error))
        vm.mensajeMostrado(mensaje)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(avisos) { datos ->
                val error = (datos.visuals as? VisualMensaje)?.error == true
                val fondo = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.inverseSurface
                val texto = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.inverseOnSurface
                Snackbar(datos, containerColor = fondo, contentColor = texto, dismissActionContentColor = texto)
            }
        },
        bottomBar = {
            if (estado.listo && !estado.bloqueado && !accesoCerrado) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    pestanas.forEach { p ->
                        NavigationBarItem(
                            selected = pestana == p,
                            onClick = { pestana = p },
                            icon = { Icon(painterResource(p.icono), contentDescription = null) },
                            label = { Text(p.etiqueta, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (accesoCerrado && estado.registrado) {
                PantallaAccesoCerrado(estado, onReintentar = vm::reintentarEnvio, onCerrarSesion = vm::cerrarSesion)
            } else if (!estado.listo) {
                Configuracion(estado, vm, activity)
            } else if (estado.bloqueado) {
                PantallaBloqueo(estado, onDesbloquear = { vm.desbloquear(activity) }, onCambiarCuenta = vm::cerrarSesion)
            } else when (pestana) {
                Pestana.INICIO -> if (!estado.perfil.marca) PantallaInicioGestor(estado, onMes = { vm.cargarRanking(it) })
                else PantallaInicio(
                    estado = estado,
                    onMarcar = { tipo, observacion, actividad -> vm.marcar(activity, tipo, observacion, actividad) },
                    onPermisoDenegado = vm::mostrarError,
                    onReintentar = vm::reintentarEnvio,
                    onVerEstadisticas = { pestana = Pestana.ESTADISTICAS },
                    onMesRanking = { vm.cargarRanking(it) },
                )
                Pestana.ESTADISTICAS -> ResumenEHistorial(estado)
                Pestana.PERMISOS -> PantallaPermisos(avisos)
                Pestana.ADMIN -> PantallaAdmin(avisos, estado.perfil, estado.correo.orEmpty())
                Pestana.PERFIL -> PantallaPerfil(
                    estado = estado,
                    onRecordatorios = vm::cambiarRecordatorios,
                    onPedirHuella = vm::cambiarPedirHuella,
                    onCambiarMetodo = { vm.cambiarMetodo(activity) },
                    onReintentar = vm::reintentarEnvio,
                    onCerrarSesion = vm::cerrarSesion,
                    onQuitarCuenta = { estado.correo?.let(vm::quitarCuenta) },
                    onLiberarTelefono = vm::liberarTelefono,
                    onError = vm::mostrarError,
                    avisos = avisos,
                )
            }
        }
    }

    estado.resultado?.let { DialogoResultado(it, onCerrar = vm::cerrarResultado) }
}

/** Pestaña Resumen: estadísticas e historial, con un selector arriba. */
@Composable
private fun ResumenEHistorial(estado: EstadoUi) {
    var historial by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        androidx.compose.material3.TabRow(selectedTabIndex = if (historial) 1 else 0) {
            androidx.compose.material3.Tab(selected = !historial, onClick = { historial = false }, text = { Text("Resumen") })
            androidx.compose.material3.Tab(selected = historial, onClick = { historial = true }, text = { Text("Historial") })
        }
        Box(Modifier.weight(1f)) {
            if (historial) PantallaHistorial(estado.marcas, estado.perfil)
            else PantallaEstadisticas(estado.marcas, estado.perfil)
        }
    }
}

// ---------- Inicio de sesión (cuentas guardadas en el teléfono) ----------

@Composable
private fun Configuracion(estado: EstadoUi, vm: AsistenciaViewModel, activity: FragmentActivity) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.logo_colegio),
            contentDescription = "Colegio Mesón",
            colorFilter = ColorFilter.tint(colorLogo),
            modifier = Modifier.width(120.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Mesón Asiste",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                "Control de asistencia · Colegio Mesón",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            !estado.registrado && estado.buscandoRegistro -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text("Buscando tu registro…")
            }
            !estado.registrado -> PantallaCuentas(
                estado = estado,
                onEntrar = { vm.entrarConCuenta(activity, it) },
                onQuitar = vm::quitarCuenta,
                onIniciar = vm::iniciarSesion,
                onCrear = vm::registrar,
            )
            !estado.permisosListos -> PasoPermisos(onConcedidos = vm::permisosConcedidos, onDenegados = vm::mostrarError)
            // Solo si el teléfono no tiene huella, rostro ni bloqueo de pantalla configurado.
            else -> PasoMetodo(estado, onElegir = { vm.elegirMetodo(activity, it) })
        }
        if (estado.ocupado) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text("Procesando…")
            }
        }
        estado.pedirClave?.let { (correo, mensaje) ->
            var clave by remember(correo) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = vm::cancelarClave,
                title = { Text("Clave de super administrador") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(mensaje)
                        OutlinedTextField(
                            value = clave,
                            onValueChange = { clave = it.take(100) },
                            label = { Text("Clave") },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { vm.iniciarSesion(correo, clave) }, enabled = clave.isNotBlank() && !estado.ocupado) {
                        Text("Entrar")
                    }
                },
                dismissButton = { TextButton(onClick = vm::cancelarClave) { Text("Cancelar") } },
            )
        }
        Text(
            "Tus registros se guardan en la hoja de asistencia del colegio.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
/** El administrador desactivó o eliminó a esta persona: no puede usar la app. */
@Composable
private fun PantallaAccesoCerrado(estado: EstadoUi, onReintentar: () -> Unit, onCerrarSesion: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painterResource(R.drawable.ic_candado), contentDescription = null,
            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp),
        )
        Text("Acceso cerrado", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            estado.accesoCerrado.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onReintentar,
            enabled = !estado.ocupado,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Volver a revisar") }
        OutlinedButton(onClick = onCerrarSesion, enabled = !estado.ocupado, modifier = Modifier.fillMaxWidth()) {
            Text("Cambiar de cuenta")
        }
    }
}

/** Pantalla de entrada: se abre con la huella o el rostro, sin escribir nada. */
@Composable
private fun PantallaBloqueo(estado: EstadoUi, onDesbloquear: () -> Unit, onCambiarCuenta: () -> Unit) {
    // Muestra el lector apenas aparece la pantalla.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(350)
        onDesbloquear()
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Image(
            painter = painterResource(R.drawable.logo_colegio),
            contentDescription = "Colegio Mesón",
            colorFilter = ColorFilter.tint(colorLogo),
            modifier = Modifier.width(140.dp),
        )
        Spacer(Modifier.height(16.dp))
        Avatar(estado.nombre, 84.dp)
        Text(
            "Hola, ${estado.nombre.orEmpty().substringBefore(' ')}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Usa tu ${estado.metodo?.etiqueta?.lowercase() ?: "huella"} para entrar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onDesbloquear,
            enabled = !estado.ocupado,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(painterResource(R.drawable.ic_candado), contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Entrar")
        }
        TextButton(onClick = onCambiarCuenta, enabled = !estado.ocupado) {
            Text("¿No eres ${estado.nombre.orEmpty().substringBefore(' ')}? Cambiar de cuenta")
        }
    }
}

/** Título y explicación corta de cada pantalla de acceso. */
@Composable
private fun EncabezadoAcceso(titulo: String, descripcion: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(descripcion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Qué se muestra en la pantalla de acceso. */
private enum class ModoAcceso { CUENTAS, INICIAR, CREAR }

/**
 * Pantalla de acceso:
 * - Cuentas guardadas en este teléfono: se toca una y se confirma con la huella (sin escribir nada).
 * - Iniciar sesión: para una cuenta que ya existe en el colegio, solo con el correo.
 * - Crear cuenta nueva: nombre y correo, para quien todavía no tiene cuenta.
 */
@Composable
private fun PantallaCuentas(
    estado: EstadoUi,
    onEntrar: (String) -> Unit,
    onQuitar: (String) -> Unit,
    onIniciar: (String) -> Unit,
    onCrear: (String, String) -> Unit,
) {
    val conCuentas = estado.cuentas.isNotEmpty()
    var modo by rememberSaveable { mutableStateOf(if (conCuentas) ModoAcceso.CUENTAS else ModoAcceso.INICIAR) }
    if (modo == ModoAcceso.CUENTAS && !conCuentas) modo = ModoAcceso.INICIAR
    var quitando by remember { mutableStateOf<CuentaGuardada?>(null) }

    when (modo) {
        ModoAcceso.CUENTAS -> {
            EncabezadoAcceso("Elige tu cuenta", "Toca tu cuenta y confirma con tu huella o rostro.")
            estado.cuentas.forEach { c ->
                Tarjeta(Modifier.clickable(enabled = !estado.ocupado) { onEntrar(c.correo) }) {
                    Row(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.nombre, 46.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(c.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.correo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Etiqueta(c.rolNombre, MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = { quitando = c }, enabled = !estado.ocupado) {
                            Text("Quitar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = { modo = ModoAcceso.INICIAR },
                enabled = !estado.ocupado,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Iniciar sesión con otra cuenta") }
            TextButton(onClick = { modo = ModoAcceso.CREAR }, enabled = !estado.ocupado) { Text("Crear cuenta nueva") }
        }

        ModoAcceso.INICIAR -> {
            EncabezadoAcceso("Iniciar sesión", "Escribe el correo del colegio con el que ya estás registrado.")
            FormularioIniciar(ocupado = estado.ocupado, onIniciar = onIniciar)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("¿Primera vez en Mesón Asiste?", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { modo = ModoAcceso.CREAR }) { Text("Crear cuenta") }
            }
            if (conCuentas) TextButton(onClick = { modo = ModoAcceso.CUENTAS }) { Text("Volver a mis cuentas") }
        }

        ModoAcceso.CREAR -> {
            EncabezadoAcceso("Crear cuenta", "Solo la primera vez. Después entras con tu huella.")
            FormularioCrear(ocupado = estado.ocupado, onCrear = onCrear)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("¿Ya tienes cuenta?", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { modo = ModoAcceso.INICIAR }) { Text("Iniciar sesión") }
            }
            if (conCuentas) TextButton(onClick = { modo = ModoAcceso.CUENTAS }) { Text("Volver a mis cuentas") }
        }
    }

    quitando?.let { c ->
        AlertDialog(
            onDismissRequest = { quitando = null },
            title = { Text("¿Quitar la cuenta de este teléfono?") },
            text = { Text("${c.nombre} (${c.correo}). No se borra del colegio: puedes volver a iniciar sesión cuando quieras.") },
            confirmButton = { TextButton(onClick = { quitando = null; onQuitar(c.correo) }) { Text("Quitar") } },
            dismissButton = { TextButton(onClick = { quitando = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CampoCorreo(correo: String, onCambio: (String) -> Unit) {
    OutlinedTextField(
        value = correo,
        onValueChange = { onCambio(it.trim().lowercase().take(100)) },
        label = { Text("Correo del colegio") },
        placeholder = { Text("usuario@${Config.DOMINIO}") },
        supportingText = { Text("Debe terminar en @${Config.DOMINIO}") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FormularioIniciar(ocupado: Boolean, onIniciar: (String) -> Unit) {
    var correo by rememberSaveable { mutableStateOf("") }
    Tarjeta {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CampoCorreo(correo) { correo = it }
            Button(
                onClick = { onIniciar(correo) },
                enabled = !ocupado && correo.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Iniciar sesión") }
        }
    }
}

@Composable
private fun FormularioCrear(ocupado: Boolean, onCrear: (String, String) -> Unit) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var correo by rememberSaveable { mutableStateOf("") }
    Tarjeta {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it.take(80) },
                label = { Text("Nombre completo") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            CampoCorreo(correo) { correo = it }
            Button(
                onClick = { onCrear(nombre, correo) },
                enabled = !ocupado && nombre.isNotBlank() && correo.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Crear cuenta") }
        }
    }
}
@Composable
private fun PasoPermisos(onConcedidos: () -> Unit, onDenegados: (String) -> Unit) {
    val context = LocalContext.current
    var denegado by remember { mutableStateOf(false) }
    val pedirPermisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (ubicacionConcedida(context)) {
            onConcedidos()
        } else {
            denegado = true
            onDenegados("Sin la ubicación precisa no se puede verificar que estás dentro del colegio.")
        }
    }
    // Se piden automáticamente al llegar a este paso; si ya estaban concedidos, se avanza solo.
    LaunchedEffect(Unit) {
        if (ubicacionConcedida(context)) onConcedidos() else pedirPermisos.launch(PERMISOS_INICIALES)
    }

    EncabezadoAcceso(
        "Un último permiso",
        "Tu ubicación precisa confirma que marcas dentro del colegio (solo se usa al marcar). " +
            "Las notificaciones te recuerdan marcar a tiempo. Se pide una sola vez en este teléfono.",
    )
    Button(
        onClick = { if (ubicacionConcedida(context)) onConcedidos() else pedirPermisos.launch(PERMISOS_INICIALES) },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(if (denegado) "Volver a intentar" else "Conceder permisos")
    }
    if (denegado) {
        Text(
            "Si no aparece la ventana, abre los ajustes, entra en Permisos → Ubicación y elige " +
                "\"Permitir solo mientras se usa la app\" con \"Usar ubicación precisa\" activado.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            )
        }) {
            Text("Abrir ajustes de la app")
        }
    }
}

@Composable
private fun PasoMetodo(estado: EstadoUi, onElegir: (MetodoBiometrico) -> Unit) {
    val context = LocalContext.current
    EncabezadoAcceso("Confirma tu identidad", "Este teléfono no tiene huella ni bloqueo de pantalla configurado. Configúralo en Ajustes del teléfono o elige una opción disponible.")
    MetodoBiometrico.entries.forEach { metodo ->
        val disponible = Biometria.disponible(context, metodo)
        OutlinedCard(
            onClick = { onElegir(metodo) },
            enabled = disponible && !estado.ocupado,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(metodo.etiqueta, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (disponible) metodo.instruccion
                    else "No disponible: el teléfono no tiene este sensor o no está configurado en Ajustes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
