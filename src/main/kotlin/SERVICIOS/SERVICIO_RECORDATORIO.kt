package SERVICIOS

import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import MODELOS.Recordatorio
import MODELOS.RepositorioRecordatorio
import java.util.UUID

/** Códigos que usa la app Android; también se aceptan los nombres completos y se guardan como código. */
private val DIAS_VALIDOS = linkedMapOf(
    "LUN" to setOf("LUN", "LUNES"),
    "MAR" to setOf("MAR", "MARTES"),
    "MIE" to setOf("MIE", "MIERCOLES", "MIÉRCOLES"),
    "JUE" to setOf("JUE", "JUEVES"),
    "VIE" to setOf("VIE", "VIERNES"),
    "SAB" to setOf("SAB", "SABADO", "SÁBADO"),
    "DOM" to setOf("DOM", "DOMINGO")
)
private val REGEX_HORA = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")
private const val LARGO_MAXIMO_TIPO_PRACTICA = 32

class ServicioRecordatorio(private val repositorio: RepositorioRecordatorio) {

    suspend fun obtener(usuarioId: UUID): Recordatorio =
        repositorio.buscar(usuarioId)
            ?: throw ErrorNoEncontrado("recordatorio_no_configurado", "El usuario no tiene preferencias de recordatorios configuradas")

    suspend fun guardar(usuarioId: UUID, dias: List<String>, hora: String, tipoPractica: String, estaHabilitado: Boolean): Recordatorio {
        if (dias.isEmpty()) throw ErrorValidacion("dias_requeridos", "Elige al menos un día")
        val diasNormalizados = dias.map { dia ->
            val buscado = dia.trim().uppercase()
            DIAS_VALIDOS.entries.firstOrNull { buscado in it.value }?.key
                ?: throw ErrorValidacion("dia_invalido", "Día no válido: $dia")
        }
        // Se guardan sin repetir y en orden de la semana.
        val diasOrdenados = DIAS_VALIDOS.keys.filter { it in diasNormalizados }
        val horaLimpia = hora.trim()
        if (!REGEX_HORA.matches(horaLimpia)) throw ErrorValidacion("hora_invalida", "La hora debe tener formato HH:mm (00:00 a 23:59)")
        val tipo = tipoPractica.trim()
        if (tipo.isEmpty() || tipo.length > LARGO_MAXIMO_TIPO_PRACTICA) {
            throw ErrorValidacion("tipo_practica_invalido", "El tipo de práctica es obligatorio y de hasta $LARGO_MAXIMO_TIPO_PRACTICA caracteres")
        }
        return repositorio.guardar(usuarioId, Recordatorio(diasOrdenados, horaLimpia, tipo, estaHabilitado))
    }
}
