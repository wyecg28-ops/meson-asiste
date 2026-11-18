/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.asistencia.R
import com.example.asistencia.ui.theme.colorAviso
import com.example.asistencia.ui.theme.colorExito
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val LOCALE_GT: Locale = Locale.forLanguageTag("es-GT")

fun formatear(patron: String, millis: Long): String =
    SimpleDateFormat(patron, LOCALE_GT).format(Date(millis)).replaceFirstChar { it.uppercase() }

/** La hora actual, actualizada cada [intervalo] ms (alineada al segundo/minuto exacto). */
@Composable
fun rememberAhora(intervalo: Long = 60_000): Long {
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalo) {
        while (true) {
            ahora = System.currentTimeMillis()
            delay(intervalo - ahora % intervalo)
        }
    }
    return ahora
}

/** Contenedor de cada pestaña: desplazable y con márgenes uniformes. */
@Composable
fun Pantalla(contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = contenido,
    )
}

@Composable
fun Titulo(texto: String, subtitulo: String? = null) {
    Column {
        Text(texto, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        subtitulo?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Tarjeta blanca sin sombra sobre el fondo gris claro. */
@Composable
fun Tarjeta(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        content = contenido,
    )
}

/** Pastilla de color con texto, p. ej. "Puntual". */
@Composable
fun Etiqueta(texto: String, color: Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f)) {
        Text(
            texto,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun EtiquetaPuntualidad(puntual: Boolean) =
    if (puntual) Etiqueta("Puntual", colorExito) else Etiqueta("Tarde", colorAviso)

/** Etiqueta pequeña arriba y valor grande abajo. */
@Composable
fun Dato(etiqueta: String, valor: String, modifier: Modifier = Modifier, colorValor: Color = Color.Unspecified) {
    Column(modifier) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colorValor)
    }
}

@Composable
fun Avatar(nombre: String?, tamano: Dp) {
    val iniciales = nombre.orEmpty().split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }
    Box(
        modifier = Modifier.size(tamano).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            iniciales,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            style = if (tamano > 56.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
fun EstadoVacio(titulo: String, texto: String) {
    Tarjeta {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_estadisticas), null,
                tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp),
            )
            Text(titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                texto, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Diálogo después de marcar: "¡Felicidades, eres puntual!" o la alerta correspondiente. */
@Composable
fun DialogoResultado(resultado: Resultado, onCerrar: () -> Unit) {
    val color = if (resultado.positivo) colorExito else colorAviso
    val escala = remember(resultado) { Animatable(0.3f) }
    LaunchedEffect(resultado) {
        escala.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    AlertDialog(
        onDismissRequest = onCerrar,
        confirmButton = { Button(onClick = onCerrar) { Text("Entendido") } },
        icon = {
            Box(
                Modifier.size(80.dp).scale(escala.value).background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (resultado.positivo) R.drawable.ic_check else R.drawable.ic_alerta),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(44.dp),
                )
            }
        },
        title = { Text(resultado.titulo, textAlign = TextAlign.Center) },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(resultado.detalle, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                resultado.nota?.let {
                    Text(
                        it, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
