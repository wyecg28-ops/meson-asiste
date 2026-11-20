/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.asistencia.Config
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Una versión publicada de la app (archivo version.json de la web del proyecto). */
data class VersionPublicada(val codigo: Long, val nombre: String, val apk: String, val sha256: String, val notas: String)

data class EstadoActualizacion(
    /** Versión nueva disponible (se muestra el aviso), o null. */
    val disponible: VersionPublicada? = null,
    val descargando: Boolean = false,
    /** 0..1 mientras se descarga. */
    val progreso: Float = 0f,
    /** Archivo ya descargado y comprobado, listo para instalar. */
    val listo: File? = null,
    /** Mensaje para la persona (sin conexión, ya está al día…). */
    val mensaje: String? = null,
    val buscando: Boolean = false,
)

/**
 * Actualizaciones sin Google Play: la app revisa la web del proyecto, descarga la versión nueva y abre el instalador
 * de Android (la persona solo toca "Instalar"). Android solo la acepta si viene con la misma firma de la app.
 */
class ActualizacionViewModel(app: Application) : AndroidViewModel(app) {
    private val _estado = MutableStateFlow(EstadoActualizacion())
    val estado = _estado.asStateFlow()
    private var ultimaRevision = 0L
    /** "Más tarde": no se vuelve a mostrar el aviso antes de esta hora. */
    private var avisarDesde = 0L

    private val versionInstalada: Long
        get() = runCatching {
            val app = getApplication<Application>()
            PackageInfoCompat.getLongVersionCode(app.packageManager.getPackageInfo(app.packageName, 0))
        }.getOrDefault(0L)

    /**
     * Revisa en silencio si hay una versión nueva (al abrir la app y al volver a ella, como máximo cada 30 minutos).
     * Si la hay, aparece el aviso; si la persona eligió "Más tarde", se vuelve a mostrar una hora después.
     */
    fun buscar() = viewModelScope.launch {
        val ahora = System.currentTimeMillis()
        if (ahora < avisarDesde || _estado.value.disponible != null) return@launch
        if (ahora - ultimaRevision < 30 * 60_000L || _estado.value.buscando || _estado.value.descargando) return@launch
        ultimaRevision = ahora
        _estado.update { it.copy(buscando = true) }
        try {
            val v = withContext(Dispatchers.IO) { leerVersion() }
            if (v.codigo > versionInstalada) _estado.update { it.copy(disponible = v, mensaje = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin conexión: se intenta la próxima vez.
        } finally {
            _estado.update { it.copy(buscando = false) }
        }
    }

    /** "Más tarde": el aviso vuelve a aparecer en una hora (al usar la app). */
    fun masTarde() {
        avisarDesde = System.currentTimeMillis() + 60 * 60_000L
        ultimaRevision = 0L
        _estado.update { it.copy(disponible = null, mensaje = null) }
    }

    /** Descarga la versión nueva, comprueba que llegó completa y abre el instalador. */
    fun actualizar(context: Context) = viewModelScope.launch {
        val v = _estado.value.disponible ?: return@launch
        _estado.value.listo?.let { return@launch instalar(context, it) }
        _estado.update { it.copy(descargando = true, progreso = 0f, mensaje = null) }
        try {
            val archivo = withContext(Dispatchers.IO) { descargar(v) }
            _estado.update { it.copy(listo = archivo) }
            instalar(context, archivo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _estado.update { it.copy(mensaje = e.message ?: "No se pudo descargar la actualización.") }
        } finally {
            _estado.update { it.copy(descargando = false) }
        }
    }

    private fun instalar(context: Context, archivo: File) {
        // Android 8+: la primera vez hay que permitir que Mesón Asiste instale sus actualizaciones.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            _estado.update {
                it.copy(mensaje = "Activa «Permitir de esta fuente» para Mesón Asiste, vuelve y toca Instalar otra vez.")
            }
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".archivos", archivo)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun leerVersion(): VersionPublicada {
        val con = (URL(Config.ACTUALIZACIONES_URL + "?t=" + System.currentTimeMillis()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            useCaches = false
        }
        try {
            val j = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
            return VersionPublicada(
                codigo = j.getLong("versionCode"),
                nombre = j.optString("versionName"),
                apk = j.getString("apk"),
                sha256 = j.optString("sha256").lowercase(),
                notas = j.optString("notas"),
            )
        } finally {
            con.disconnect()
        }
    }

    private fun descargar(v: VersionPublicada): File {
        val carpeta = File(getApplication<Application>().cacheDir, "actualizaciones").apply { mkdirs() }
        carpeta.listFiles()?.forEach { it.delete() } // solo se guarda la última
        val destino = File(carpeta, "MesonAsiste-${v.nombre}.apk")
        val con = (URL(v.apk).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        val resumen = MessageDigest.getInstance("SHA-256")
        try {
            if (con.responseCode !in 200..299) throw IllegalStateException("No se pudo descargar la actualización (${con.responseCode}).")
            val total = con.contentLengthLong
            var leidos = 0L
            con.inputStream.use { entrada ->
                destino.outputStream().use { salida ->
                    val bufer = ByteArray(64 * 1024)
                    while (true) {
                        val n = entrada.read(bufer)
                        if (n < 0) break
                        salida.write(bufer, 0, n)
                        resumen.update(bufer, 0, n)
                        leidos += n
                        if (total > 0) _estado.update { it.copy(progreso = leidos.toFloat() / total) }
                    }
                }
            }
        } finally {
            con.disconnect()
        }
        val sha = resumen.digest().joinToString("") { "%02x".format(it) }
        if (v.sha256.isNotBlank() && sha != v.sha256) {
            destino.delete()
            throw IllegalStateException("El archivo descargado no coincide con la versión publicada. Inténtalo de nuevo.")
        }
        return destino
    }
}

/** Aviso de versión nueva (y su descarga). Va en la pantalla principal. */
@Composable
fun DialogoActualizacion(vm: ActualizacionViewModel = viewModel()) {
    val estado by vm.estado.collectAsState()
    val context = LocalContext.current
    val v = estado.disponible ?: return
    AlertDialog(
        onDismissRequest = { if (!estado.descargando) vm.masTarde() },
        title = { Text("Hay una actualización") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ya está disponible la versión ${v.nombre} de Mesón Asiste.", fontWeight = FontWeight.SemiBold)
                if (v.notas.isNotBlank()) Text(v.notas)
                if (estado.descargando) {
                    Text("Descargando… ${(estado.progreso * 100).toInt()} %", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { estado.progreso }, modifier = Modifier.fillMaxWidth())
                } else if (estado.listo != null) {
                    Text("Descarga lista. Toca «Instalar» y confirma en la pantalla de Android.", style = MaterialTheme.typography.bodySmall)
                }
                estado.mensaje?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.actualizar(context) }, enabled = !estado.descargando) {
                Text(if (estado.listo != null) "Instalar" else "Actualizar ahora")
            }
        },
        dismissButton = { TextButton(onClick = vm::masTarde, enabled = !estado.descargando) { Text("Más tarde") } },
    )
}

