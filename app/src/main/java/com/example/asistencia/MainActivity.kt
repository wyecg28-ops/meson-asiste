/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.example.asistencia.ui.AppAsistencia
import com.example.asistencia.ui.AsistenciaViewModel
import com.example.asistencia.ui.theme.AsistenciaTheme

// FragmentActivity es necesaria para mostrar el diálogo biométrico (BiometricPrompt).
class MainActivity : FragmentActivity() {

    // El mismo ViewModel que usa la interfaz (viewModel() en Compose devuelve esta instancia).
    private val vm: AsistenciaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AsistenciaTheme {
                AppAsistencia(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.alVolverALaApp()
    }

    override fun onStop() {
        super.onStop()
        vm.alSalirDeLaApp()
    }
}
