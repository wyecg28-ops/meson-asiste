/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.verificacion

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Cómo comprueba cada sede que la persona está físicamente ahí. */
enum class MetodoVerificacion(val etiqueta: String, val descripcion: String) {
    UBICACION("Solo ubicación", "Basta con estar dentro del radio de la sede."),
    WIFI("Wi-Fi del establecimiento", "El teléfono debe detectar el Wi-Fi registrado. Si no lo detecta, la marca queda por revisar."),
    QR("Código QR", "Hay que escanear el código que cambia cada 30 segundos en el teléfono o tablet de la entrada."),
    ;

    companion object {
        fun de(codigo: String?) = entries.firstOrNull { it.name == codigo } ?: UBICACION
    }
}

/** Código QR que cambia cada 30 segundos. El mismo cálculo lo hace el servidor (Apps Script) para validarlo. */
object CodigoQr {
    private const val PREFIJO = "MESON"
    const val SEGUNDOS = 30L

    fun ventana(epochSegundos: Long) = Math.floorDiv(epochSegundos, SEGUNDOS)

    /** Primeros 10 caracteres de HMAC-SHA256(ventana, secreto), en hexadecimal. */
    fun codigo(secreto: String, ventana: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secreto.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(ventana.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(10)
    }

    fun contenido(sedeId: String, codigo: String) = "$PREFIJO|$sedeId|$codigo"

    /** Devuelve el código si el texto escaneado es un QR de Mesón Asiste. */
    fun leer(texto: String?): String? {
        val partes = texto?.trim()?.split('|') ?: return null
        return if (partes.size == 3 && partes[0] == PREFIJO && partes[2].length == 10) partes[2] else null
    }

    /** Dibuja el QR como imagen. */
    fun imagen(texto: String, tamano: Int = 720): ImageBitmap {
        val matriz = QRCodeWriter().encode(
            texto, BarcodeFormat.QR_CODE, tamano, tamano,
            mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
        )
        val pixeles = IntArray(tamano * tamano) { i ->
            if (matriz[i % tamano, i / tamano]) 0xFF0B0F14.toInt() else 0xFFFFFFFF.toInt()
        }
        return Bitmap.createBitmap(pixeles, tamano, tamano, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /** Pide a Google Play que descargue el lector de QR (por si aún no está en el teléfono). */
    fun prepararLector(context: Context) {
        runCatching {
            ModuleInstall.getClient(context).installModules(
                ModuleInstallRequest.newBuilder().addApi(GmsBarcodeScanning.getClient(context)).build()
            )
        }
    }

    /** Abre la cámara con el lector de Google. Devuelve el texto leído o null si la persona canceló. */
    suspend fun escanear(context: Context): String? = suspendCancellableCoroutine { cont ->
        val opciones = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        GmsBarcodeScanning.getClient(context, opciones).startScan()
            .addOnSuccessListener { if (cont.isActive) cont.resume(it.rawValue) }
            .addOnCanceledListener { if (cont.isActive) cont.resume(null) }
            .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    }
}

/** Busca las redes Wi-Fi cercanas (no se conecta). Usa el permiso de ubicación que la app ya tiene. */
object WifiCercano {

    @SuppressLint("MissingPermission")
    private fun leer(context: Context): List<Pair<String, Int>> {
        val wm = context.applicationContext.getSystemService(WifiManager::class.java) ?: return emptyList()
        val vistas = runCatching { wm.scanResults.map { it.BSSID.lowercase() to it.level } }.getOrDefault(emptyList())
        @Suppress("DEPRECATION")
        val conectada = runCatching { wm.connectionInfo?.bssid?.lowercase() }.getOrNull()
        return (vistas + listOfNotNull(conectada?.let { it to 0 }))
            .filter { it.first.isNotBlank() && it.first != "02:00:00:00:00:00" }
    }

    @Suppress("DEPRECATION")
    private suspend fun buscar(context: Context): List<Pair<String, Int>> {
        val wm = context.applicationContext.getSystemService(WifiManager::class.java)
        runCatching { wm?.startScan() }
        delay(2_500)
        return leer(context)
    }

    /** Identificadores de todos los Wi-Fi que el teléfono ve ahora (para la marca). */
    suspend fun vistos(context: Context): List<String> = buscar(context).map { it.first }.distinct()

    /** Los Wi-Fi con mejor señal (para registrar los del establecimiento). */
    suspend fun cercanos(context: Context): List<String> =
        buscar(context).filter { it.second == 0 || it.second >= -75 }
            .sortedByDescending { if (it.second == 0) 0 else it.second }
            .map { it.first }.distinct().take(8)

    fun wifiEncendido(context: Context): Boolean =
        context.applicationContext.getSystemService(WifiManager::class.java)?.isWifiEnabled == true
}
