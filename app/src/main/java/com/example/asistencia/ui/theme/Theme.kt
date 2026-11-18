/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

private val EsquemaClaro = lightColorScheme(
    primary = Rojo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6DCDB),
    onPrimaryContainer = Granate,
    secondary = Granate,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE4E3),
    onSecondaryContainer = Granate,
    tertiary = Gris,
    background = GrisClaro,
    onBackground = Tinta,
    surface = GrisClaro,
    onSurface = Tinta,
    surfaceVariant = Color.White,
    onSurfaceVariant = GrisTexto,
    outline = Gris,
    outlineVariant = Color(0xFFD9D9D8),
    error = Rojo,
    errorContainer = Color(0xFFF6DCDB),
    onErrorContainer = Granate,
)

private val EsquemaOscuro = darkColorScheme(
    primary = Rojo,
    onPrimary = Color.White,
    primaryContainer = Granate,
    onPrimaryContainer = GrisClaro,
    secondary = RojoClaro,
    onSecondary = Granate,
    secondaryContainer = Color(0xFF3A2523),
    onSecondaryContainer = GrisClaro,
    tertiary = Gris,
    background = FondoOscuro,
    onBackground = GrisClaro,
    surface = FondoOscuro,
    onSurface = GrisClaro,
    surfaceVariant = SuperficieOscura,
    onSurfaceVariant = Color(0xFFBDBDBC),
    outline = Gris,
    outlineVariant = Color(0xFF4A4444),
    error = RojoClaro,
    errorContainer = Color(0xFF4A1A19),
    onErrorContainer = GrisClaro,
)

/** Color del logo según el fondo: granate sobre claro, gris claro sobre oscuro (nunca negro sobre oscuro). */
val colorLogo: Color
    @Composable @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) GrisClaro else Granate

/** Verde de "puntual" / "buen trabajo". */
val colorExito: Color
    @Composable @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) VerdeClaro else Verde

/** Ámbar de "tarde" / alertas. */
val colorAviso: Color
    @Composable @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) AmbarClaro else Ambar

@Composable
fun AsistenciaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) EsquemaOscuro else EsquemaClaro,
        typography = Typography,
        content = content
    )
}
