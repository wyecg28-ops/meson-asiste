/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.datos

import android.content.Context
import androidx.core.content.edit
import com.example.asistencia.auth.MetodoBiometrico
import org.json.JSONArray
import org.json.JSONObject

/** Una cuenta guardada en este teléfono (se entra tocándola y poniendo la huella). */
data class CuentaGuardada(val correo: String, val nombre: String, val rolNombre: String)

/**
 * Datos guardados en este teléfono.
 * - Del teléfono (valen para todas las cuentas): permisos, huella/rostro, recordatorios, pedir huella al abrir.
 * - De cada cuenta (se guardan con el correo): nombre, rol/jornada/calendario, registro en el servidor, acceso.
 * Se pueden guardar varias cuentas (por ejemplo, el administrador y un docente de prueba) y cambiar entre ellas.
 */
class Sesion(private val context: Context) {
    private val prefs = context.getSharedPreferences("sesion", Context.MODE_PRIVATE)

    init {
        migrarCuentaUnica()
    }

    /** Cuenta abierta ahora. null = se muestra la pantalla de inicio de sesión. */
    var correo: String?
        get() = prefs.getString("correo", null)
        set(valor) = prefs.edit { putString("correo", valor) }

    private fun clave(base: String, cuenta: String? = correo) = "$base@$cuenta"

    var nombre: String?
        get() = correo?.let { prefs.getString(clave("nombre"), null) }
        set(valor) {
            val c = correo ?: return
            prefs.edit { putString(clave("nombre", c), valor) }
            guardarEnLista(c)
        }

    /** Si el registro de esta cuenta ya se envió a la hoja "Personal". */
    var enviadoAlServidor: Boolean
        get() = correo?.let { prefs.getBoolean(clave("enviado"), false) } ?: false
        set(valor) {
            val c = correo ?: return
            prefs.edit { putBoolean(clave("enviado", c), valor) }
        }

    /** Historial de esta cuenta tal como está en la hoja (con correcciones de la administración), en JSON. */
    var historialHoja: String?
        get() = correo?.let { prefs.getString(clave("historial"), null) }
        set(valor) {
            val c = correo ?: return
            prefs.edit { putString(clave("historial", c), valor).putLong(clave("historialHora", c), System.currentTimeMillis()) }
        }

    /** Cuándo se descargó [historialHoja]. */
    val historialHojaHora: Long get() = correo?.let { prefs.getLong(clave("historialHora"), 0L) } ?: 0L

    /** Rol, jornada y calendario de esta cuenta, descargados del servidor. */
    var perfil: Perfil
        get() = correo?.let { prefs.getString(clave("perfil"), null) }
            ?.let { runCatching { Perfil.deJson(JSONObject(it)) }.getOrNull() }
            ?: Perfil.POR_DEFECTO
        set(valor) {
            val c = correo ?: return
            prefs.edit { putString(clave("perfil", c), valor.aJson().toString()) }
            guardarEnLista(c)
        }

    /** Si el administrador desactivó o eliminó esta cuenta: el mensaje del servidor. null = acceso normal. */
    var accesoCerrado: String?
        get() = correo?.let { prefs.getString(clave("acceso"), null) }
        set(valor) {
            val c = correo ?: return
            prefs.edit { putString(clave("acceso", c), valor) }
        }

    // ---------- Del teléfono ----------

    var metodo: MetodoBiometrico?
        get() = prefs.getString("metodo", null)?.let { runCatching { MetodoBiometrico.valueOf(it) }.getOrNull() }
        set(valor) = prefs.edit { putString("metodo", valor?.name) }

    var permisosListos: Boolean
        get() = prefs.getBoolean("permisosListos", false)
        set(valor) = prefs.edit { putBoolean("permisosListos", valor) }

    /** Recordatorios de entrada/salida y resumen semanal. Activados por defecto. */
    var recordatorios: Boolean
        get() = prefs.getBoolean("recordatorios", true)
        set(valor) = prefs.edit { putBoolean("recordatorios", valor) }

    /** Pedir huella o rostro al abrir la app. Activado por defecto. */
    var pedirHuella: Boolean
        get() = prefs.getBoolean("pedirHuella", true)
        set(valor) = prefs.edit { putBoolean("pedirHuella", valor) }

    // ---------- Cuentas guardadas ----------

    /** Cuentas guardadas en este teléfono, en el orden en que se usaron por última vez. */
    val cuentas: List<CuentaGuardada>
        get() {
            val a = runCatching { JSONArray(prefs.getString("cuentas", "[]")) }.getOrDefault(JSONArray())
            return (0 until a.length()).map { i ->
                val j = a.getJSONObject(i)
                val rol = j.optString("rolNombre").ifBlank {
                    // Versión anterior: solo el código del rol.
                    j.optString("rol").lowercase().replaceFirstChar { it.uppercase() }.ifBlank { "Docente" }
                }
                CuentaGuardada(j.getString("correo"), j.optString("nombre"), rol)
            }
        }

    /** Agrega o actualiza la cuenta en la lista (con su nombre y rol actuales) y la pone primero. */
    private fun guardarEnLista(cuenta: String) {
        val nombreCuenta = prefs.getString(clave("nombre", cuenta), null) ?: cuenta
        val rol = prefs.getString(clave("perfil", cuenta), null)
            ?.let { runCatching { Perfil.deJson(JSONObject(it)).rol.nombre }.getOrNull() } ?: Rol.DOCENTE.nombre
        val lista = listOf(CuentaGuardada(cuenta, nombreCuenta, rol)) + cuentas.filter { it.correo != cuenta }
        escribirLista(lista)
    }

    private fun escribirLista(lista: List<CuentaGuardada>) {
        val a = JSONArray().apply {
            lista.forEach { put(JSONObject().put("correo", it.correo).put("nombre", it.nombre).put("rolNombre", it.rolNombre)) }
        }
        prefs.edit { putString("cuentas", a.toString()) }
    }

    /** Abre una cuenta guardada (o recién registrada) y la pone primera en la lista. */
    fun abrir(cuenta: String) {
        correo = cuenta
        guardarEnLista(cuenta)
    }

    /** Sale de la cuenta, pero la deja guardada para volver a entrar con la huella. */
    fun salir() {
        correo = null
    }

    /** Quita la cuenta de este teléfono: sus datos guardados y su historial local. */
    fun quitarCuenta(cuenta: String) {
        prefs.edit {
            listOf("nombre", "enviado", "perfil", "acceso", "historial", "historialHora").forEach { remove(clave(it, cuenta)) }
        }
        escribirLista(cuentas.filter { it.correo != cuenta })
        Marcas(context, cuenta).borrarTodo()
        if (correo == cuenta) correo = null
    }

    /** Versión anterior de la app: una sola cuenta con claves sin correo. Se pasa al formato de varias cuentas. */
    private fun migrarCuentaUnica() {
        if (prefs.getBoolean("migrado", false)) return
        val anterior = prefs.getString("correo", null)
        if (anterior != null) {
            prefs.edit {
                prefs.getString("nombre", null)?.let { putString(clave("nombre", anterior), it) }
                prefs.getString("perfil", null)?.let { putString(clave("perfil", anterior), it) }
                prefs.getString("accesoCerrado", null)?.let { putString(clave("acceso", anterior), it) }
                putBoolean(clave("enviado", anterior), prefs.getBoolean("enviadoAlServidor", false))
            }
            Marcas.migrarHistorialAnterior(context, anterior)
            guardarEnLista(anterior)
        }
        prefs.edit {
            listOf("nombre", "perfil", "accesoCerrado", "enviadoAlServidor", "nombreRecordado", "correoRecordado")
                .forEach { remove(it) }
            putBoolean("migrado", true)
        }
    }
}
