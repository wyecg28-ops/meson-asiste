/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.auth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class MetodoBiometrico(val etiqueta: String, val instruccion: String) {
    HUELLA("Huella digital", "Coloca tu dedo en el lector (o usa el PIN o patrón de tu teléfono)"),
    ROSTRO("Reconocimiento facial", "Mira a la cámara frontal (o usa el PIN o patrón de tu teléfono)"),
}

class BiometriaException(mensaje: String) : Exception(mensaje)

object Biometria {

    /**
     * Android no permite forzar huella o rostro: el sistema decide qué sensor usar.
     * Aquí comprobamos que el teléfono tenga el sensor elegido (o un PIN/patrón de respaldo) y que esté configurado.
     */
    fun disponible(context: Context, metodo: MetodoBiometrico): Boolean {
        val tieneSensor = when (metodo) {
            MetodoBiometrico.HUELLA -> context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
            // Muchos teléfonos no declaran el sensor facial aunque lo tengan: se ofrece siempre y,
            // si Android no deja usar el rostro (o el teléfono no tiene sensores), se usa el PIN/patrón.
            MetodoBiometrico.ROSTRO -> true
        }
        return tieneSensor &&
            BiometricManager.from(context).canAuthenticate(autenticadores(metodo)) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * PIN o patrón del teléfono como respaldo si la huella o el rostro fallan.
     * Android 9 y 10 no permiten "huella fuerte + PIN": ahí la huella va sola (con botón Cancelar).
     */
    private fun autenticadores(metodo: MetodoBiometrico): Int = when {
        metodo == MetodoBiometrico.ROSTRO -> BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        else -> BIOMETRIC_STRONG
    }

    suspend fun autenticar(activity: FragmentActivity, metodo: MetodoBiometrico, titulo: String) =
        suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resumeWithException(BiometriaException(errString.toString()))
                    }
                },
            )
            val permitidos = autenticadores(metodo)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(titulo)
                .setSubtitle(metodo.instruccion)
                .setAllowedAuthenticators(permitidos)
                // Con PIN/patrón como respaldo, Android pone su propio botón; "Cancelar" solo sin respaldo.
                .apply { if (permitidos and DEVICE_CREDENTIAL == 0) setNegativeButtonText("Cancelar") }
                .build()
            prompt.authenticate(info)
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }
}
