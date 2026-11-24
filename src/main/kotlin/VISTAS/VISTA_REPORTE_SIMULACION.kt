package VISTAS

import MODELOS.ReporteSimulacion
import java.util.Locale

private const val ANCHO_HORA = 15
private const val ANCHO_ACCION = 30
private const val MS_POR_SEGUNDO = 1000.0
private const val MENSAJE_SIN_DATOS = "sin predicciones registradas para esta sesion"
private const val MENSAJE_SIN_ACCIONES = "sin acciones reconocidas en esta sesion"

private const val FORMATO_LINEA_TIEMPO = "%-${ANCHO_HORA}s%-${ANCHO_HORA}s%-${ANCHO_ACCION}s%7s"
private const val FORMATO_FILA_TIEMPO = "%-${ANCHO_HORA}s%-${ANCHO_HORA}s%-${ANCHO_ACCION}s%6.1fs"
private const val FORMATO_RESUMEN = "%-${ANCHO_ACCION}s%7s%11s%9s%9s"
private const val FORMATO_FILA_RESUMEN = "%-${ANCHO_ACCION}s%7d%11.1f%9.1f%9.1f"

/** Reporte de simulación en texto tabulado simple (sin HTML ni emojis), de ancho fijo y con punto decimal. */
fun ReporteSimulacion.aTexto(): String {
    val lineas = mutableListOf("== REPORTE DE SIMULACION ==")
    if (predicciones == 0) {
        lineas += "Sesion: $sesionId"
        lineas += ""
        lineas += MENSAJE_SIN_DATOS
        return lineas.joinToString("\n", postfix = "\n")
    }
    lineas += "Sesion: $sesionId   Duracion: ${hhmmss(duracionMs)}   Predicciones: $predicciones"
    lineas += ""
    lineas += "== LINEA DE TIEMPO =="
    lineas += formato(FORMATO_LINEA_TIEMPO, "hh:mm:ss", "hh:mm:ss", "ACCION", "DUR")
    tramos.forEach {
        lineas += formato(FORMATO_FILA_TIEMPO, hhmmss(it.inicioMs), hhmmss(it.finMs), recortar(it.accion), segundos(it.duracionMs))
    }
    lineas += ""
    lineas += "== RESUMEN POR ACCION =="
    if (resumen.isEmpty()) {
        lineas += MENSAJE_SIN_ACCIONES
    } else {
        lineas += formato(FORMATO_RESUMEN, "ACCION", "VECES", "TOTAL(s)", "MAX(s)", "PROM(s)")
        resumen.forEach {
            lineas += formato(
                FORMATO_FILA_RESUMEN, recortar(it.accion), it.veces, segundos(it.totalMs), segundos(it.maximoMs), it.promedioMs / MS_POR_SEGUNDO
            )
        }
    }
    return lineas.joinToString("\n", postfix = "\n")
}

private fun formato(plantilla: String, vararg valores: Any): String = String.format(Locale.ROOT, plantilla, *valores)

private fun segundos(ms: Long): Double = ms / MS_POR_SEGUNDO

/** Deja un espacio entre columnas aunque el nombre llene todo el ancho. */
private fun recortar(accion: String): String = accion.take(ANCHO_ACCION - 1)

private fun hhmmss(ms: Long): String {
    val total = ms / 1000
    return formato("%02d:%02d:%02d", total / 3600, total % 3600 / 60, total % 60)
}
