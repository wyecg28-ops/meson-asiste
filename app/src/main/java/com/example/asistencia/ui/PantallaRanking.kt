/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.asistencia.datos.Ranking
import com.example.asistencia.datos.formatoDuracion
import java.util.Calendar

/** "2026-11" del mes actual y de los 11 anteriores, el más reciente primero. */
fun ultimosMeses(cantidad: Int = 12): List<String> {
    val c = Calendar.getInstance()
    return (0 until cantidad).map {
        val clave = "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
        c.add(Calendar.MONTH, -1)
        clave
    }
}

private val NOMBRES_MES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/** "2026-11" → "Noviembre 2026". */
fun nombreMes(clave: String): String {
    val p = clave.split("-")
    val m = p.getOrNull(1)?.toIntOrNull() ?: return clave
    return NOMBRES_MES[m - 1].replaceFirstChar { it.uppercase() } + " " + p[0]
}

/**
 * Ranking de horas del mes: quién va en primer lugar y en qué lugar va cada uno, con su total de horas.
 * Lo ve todo el personal en la pantalla de inicio. [onMes] pide otro mes.
 */
@Composable
fun TarjetaRanking(ranking: Ranking?, cargando: Boolean, onMes: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val mes = ranking?.mes ?: ultimosMeses(1).first()
    Tarjeta {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🏆 Ranking de horas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (ranking != null && ranking.miPosicion > 0) {
                        Text(
                            "Vas en el lugar ${ranking.miPosicion} de ${ranking.lista.size}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Box {
                    OutlinedButton(onClick = { menu = true }) { Text("${nombreMes(mes)} ▾", maxLines = 1) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ultimosMeses().forEach { m ->
                            DropdownMenuItem(text = { Text(nombreMes(m)) }, onClick = { menu = false; onMes(m) })
                        }
                    }
                }
            }
            if (cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                ranking == null && !cargando -> Text(
                    "No se pudo cargar el ranking. Revisa la conexión.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ranking != null && ranking.lista.isEmpty() -> Text(
                    "Todavía no hay horas registradas en ${nombreMes(ranking.mes).lowercase()}.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ranking != null -> ranking.lista.forEach { p ->
                    val fondo = if (p.yo) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                    Row(
                        Modifier.fillMaxWidth().background(fondo, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                            when (p.posicion) {
                                1 -> Text("🥇", fontSize = 24.sp)
                                2 -> Text("🥈", fontSize = 24.sp)
                                3 -> Text("🥉", fontSize = 24.sp)
                                else -> Text("${p.posicion}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.nombre + if (p.yo) " (tú)" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (p.yo || p.posicion == 1) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${p.dias} ${if (p.dias == 1) "día" else "días"} · ${p.puntuales} puntuales" +
                                    if (p.extra > 0) " · ${formatoDuracion(p.extra)} extra" else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(formatoDuracion(p.minutos), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** Pantalla de inicio de quien no marca asistencia (por ejemplo, el administrador): el ranking del personal. */
@Composable
fun PantallaInicioGestor(estado: EstadoUi, onMes: (String) -> Unit) {
    Pantalla {
        Titulo("Hola, ${estado.nombre.orEmpty().substringBefore(' ')}", "Así va el personal este mes.")
        TarjetaRanking(estado.ranking, estado.cargandoRanking, onMes)
        estado.perfil.destacados?.takeIf { it.hayDatos }?.let { DestacadosSemana(it) }
    }
}
