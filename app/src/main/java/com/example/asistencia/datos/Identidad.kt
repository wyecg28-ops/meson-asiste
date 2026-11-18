/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.datos

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import org.json.JSONObject

/**
 * Correo + identificador del teléfono: el servidor los usa para saber quién envía cada solicitud.
 * Por defecto, el correo de la cuenta abierta; [correo] permite usar otro (por ejemplo, al iniciar sesión).
 */
@SuppressLint("HardwareIds")
fun datosDelTelefono(context: Context, correo: String? = Sesion(context).correo): JSONObject = JSONObject()
    .put("correo", correo)
    .put("dispositivo", Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID))
    .put("modelo", "${Build.MANUFACTURER} ${Build.MODEL}")
