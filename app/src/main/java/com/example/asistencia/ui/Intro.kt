/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ui

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.asistencia.R
import com.example.asistencia.ui.theme.Granate
import com.example.asistencia.ui.theme.GrisClaro
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Animación al abrir la app: logo en gris claro sobre granate (nunca el logo oscuro sobre fondo oscuro). */
@Composable
fun Intro(onTerminar: () -> Unit) {
    val escalaLogo = remember { Animatable(0.82f) }
    val opacidadLogo = remember { Animatable(0f) }
    val anchoLinea = remember { Animatable(0f) }
    val opacidadTexto = remember { Animatable(0f) }
    val desplazamientoTexto = remember { Animatable(16f) }

    IconosClarosEnBarraDeEstado()

    LaunchedEffect(Unit) {
        launch { opacidadLogo.animateTo(1f, tween(600)) }
        launch { escalaLogo.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        delay(450)
        launch { anchoLinea.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
        delay(250)
        launch { opacidadTexto.animateTo(1f, tween(500)) }
        desplazamientoTexto.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
        delay(900)
        onTerminar()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Granate),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Image(
                painter = painterResource(R.drawable.logo_colegio),
                contentDescription = "Colegio Mesón",
                colorFilter = ColorFilter.tint(GrisClaro),
                modifier = Modifier
                    .width(260.dp)
                    .scale(escalaLogo.value)
                    .alpha(opacidadLogo.value),
            )
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier
                    .width(140.dp * anchoLinea.value)
                    .height(2.dp)
                    .background(GrisClaro.copy(alpha = 0.7f))
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Mesón Asiste",
                style = MaterialTheme.typography.titleLarge,
                color = GrisClaro,
                modifier = Modifier
                    .alpha(opacidadTexto.value)
                    .graphicsLayer { translationY = desplazamientoTexto.value * density },
            )
        }
    }
}

/** Mientras dura la animación, los íconos de la barra de estado van en blanco sobre el granate. */
@Composable
private fun IconosClarosEnBarraDeEstado() {
    val vista = LocalView.current
    DisposableEffect(Unit) {
        val ventana = (vista.context as? Activity)?.window
        val controlador = ventana?.let { WindowCompat.getInsetsController(it, vista) }
        val anterior = controlador?.isAppearanceLightStatusBars
        controlador?.isAppearanceLightStatusBars = false
        onDispose { if (anterior != null) controlador.isAppearanceLightStatusBars = anterior }
    }
}
