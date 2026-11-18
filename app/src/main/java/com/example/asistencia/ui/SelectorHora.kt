/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.asistencia.datos.aMinutos
import kotlin.math.abs

private val ALTO_FILA = 48.dp
private const val FILAS_VISIBLES = 5

/** "13:05" -> "1:05 p. m." (formato que se usa en Guatemala). */
fun horaAmigable(valor: String): String {
    if (valor.length != 5) return "--:--"
    val m = aMinutos(valor)
    val h = m / 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m % 60, if (h < 12) "a. m." else "p. m.")
}

/**
 * Botón que muestra la hora elegida y abre ruedas deslizables (hora · minutos · a. m./p. m.),
 * como la alarma del celular. [valor] y [onCambio] usan el formato "HH:mm" ("" = sin elegir).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorHora(valor: String, etiqueta: String, modifier: Modifier = Modifier, onCambio: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { abierto = true }, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (valor.length == 5) horaAmigable(valor) else "Elegir",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
    if (!abierto) return

    val inicial = valor.takeIf { it.length == 5 }?.let { aMinutos(it) } ?: (8 * 60)
    val h = inicial / 60
    var hora12 by remember { mutableIntStateOf(if (h % 12 == 0) 12 else h % 12) }
    var minuto by remember { mutableIntStateOf(inicial % 60) }
    var pm by remember { mutableStateOf(h >= 12) }

    val hoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { abierto = false }, sheetState = hoja) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(etiqueta, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val h24 = (hora12 % 12) + if (pm) 12 else 0
            Text(
                horaAmigable("%02d:%02d".format(h24, minuto)),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // Franja que marca la fila elegida.
                Box(
                    Modifier.fillMaxWidth().height(ALTO_FILA)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Rueda(
                        valores = (1..12).map { it.toString() },
                        inicial = hora12 - 1,
                        ciclica = true,
                        modifier = Modifier.width(72.dp),
                    ) { hora12 = it + 1 }
                    Text(":", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Rueda(
                        valores = (0..59).map { "%02d".format(it) },
                        inicial = minuto,
                        ciclica = true,
                        modifier = Modifier.width(72.dp),
                    ) { minuto = it }
                    Rueda(
                        valores = listOf("a. m.", "p. m."),
                        inicial = if (pm) 1 else 0,
                        ciclica = false,
                        modifier = Modifier.width(88.dp),
                    ) { pm = it == 1 }
                }
            }

            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { abierto = false }, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                Button(onClick = {
                    onCambio("%02d:%02d".format((hora12 % 12) + if (pm) 12 else 0, minuto))
                    abierto = false
                }, modifier = Modifier.weight(1f)) { Text("Listo") }
            }
        }
    }
}

/** Columna deslizable que se detiene justo en un valor. Si es [ciclica], da la vuelta (59 → 00). */
@Composable
private fun Rueda(
    valores: List<String>,
    inicial: Int,
    ciclica: Boolean,
    modifier: Modifier = Modifier,
    onCambio: (Int) -> Unit,
) {
    val n = valores.size
    val total = if (ciclica) n * 1000 else n
    val inicio = if (ciclica) n * 500 + inicial else inicial
    val estado = rememberLazyListState(initialFirstVisibleItemIndex = inicio)
    val altoPx = with(LocalDensity.current) { ALTO_FILA.toPx() }
    val vibrar = LocalHapticFeedback.current

    val centro by remember {
        derivedStateOf {
            estado.firstVisibleItemIndex + if (estado.firstVisibleItemScrollOffset > altoPx / 2) 1 else 0
        }
    }
    var anterior by remember { mutableIntStateOf(inicio) }
    LaunchedEffect(centro) {
        if (centro != anterior) {
            anterior = centro
            vibrar.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onCambio(centro.coerceIn(0, total - 1) % n)
        }
    }

    LazyColumn(
        state = estado,
        flingBehavior = rememberSnapFlingBehavior(lazyListState = estado),
        contentPadding = PaddingValues(vertical = ALTO_FILA * (FILAS_VISIBLES / 2)),
        modifier = modifier.height(ALTO_FILA * FILAS_VISIBLES),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(total) { i ->
            val distancia = abs(i - centro)
            Box(Modifier.height(ALTO_FILA).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    valores[i % n],
                    textAlign = TextAlign.Center,
                    fontSize = if (distancia == 0) 26.sp else 20.sp,
                    fontWeight = if (distancia == 0) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = when (distancia) { 0 -> 1f; 1 -> 0.5f; else -> 0.22f }
                    ),
                )
            }
        }
    }
}
