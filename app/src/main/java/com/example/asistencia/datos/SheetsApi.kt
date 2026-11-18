/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.datos

import com.example.asistencia.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** El servidor procesó la solicitud y la rechazó (por ejemplo, fuera del colegio). No se reintenta. */
class ServidorException(mensaje: String, val codigo: String = "") : Exception(mensaje) {
    /** El administrador desactivó o eliminó a esta persona. */
    val accesoCerrado get() = codigo == "ACCESO_CERRADO"

    /** El servidor no reconoce este teléfono con este correo (por ejemplo, después de vaciar la hoja). */
    val noRegistrado get() = codigo == "NO_REGISTRADO" || message?.contains("no está registrado con tu correo") == true
}

/** No se pudo hablar con el servidor (sin internet o sin configurar). Se reintenta más tarde. */
class SinConexionException(mensaje: String) : IOException(mensaje)

/**
 * Cliente del Web App de Google Apps Script que escribe en la hoja de cálculo.
 * El servidor valida el teléfono y la geocerca.
 */
object SheetsApi {

    val configurado get() = !Config.SCRIPT_URL.contains("REEMPLAZAR")

    /** Envía una acción y devuelve la respuesta si fue exitosa. */
    suspend fun enviar(accion: String, datos: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        if (!configurado) throw SinConexionException("El servidor todavía no está configurado (SCRIPT_URL en Config.kt).")
        datos.put("accion", accion)
        val respuesta = try {
            JSONObject(post(Config.SCRIPT_URL, datos.toString()))
        } catch (e: JSONException) {
            throw SinConexionException("El servidor respondió algo inesperado. Revisa la implementación del script.")
        }
        if (!respuesta.optBoolean("ok")) {
            throw ServidorException(
                respuesta.optString("mensaje", "Error desconocido del servidor."),
                respuesta.optString("codigo"),
            )
        }
        respuesta
    }

    private fun abrir(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        // Apps Script responde con un 302 hacia googleusercontent.com; lo seguimos manualmente.
        instanceFollowRedirects = false
        connectTimeout = 15_000
        readTimeout = 30_000
    }

    private fun get(url: String) = leer(abrir(url))

    private fun post(url: String, cuerpo: String): String {
        val con = abrir(url).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        con.outputStream.use { it.write(cuerpo.toByteArray()) }
        return leer(con)
    }

    private fun leer(con: HttpURLConnection): String {
        try {
            val codigo = con.responseCode
            if (codigo in 300..399) {
                val destino = con.getHeaderField("Location") ?: throw IOException("Redirección sin destino")
                return get(destino)
            }
            if (codigo !in 200..299) throw IOException("El servidor respondió con el código $codigo")
            return con.inputStream.bufferedReader().use { it.readText() }
        } finally {
            con.disconnect()
        }
    }
}
