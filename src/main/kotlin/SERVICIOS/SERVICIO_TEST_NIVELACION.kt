package SERVICIOS

import CONFIGURACION.PREGUNTAS_NIVELACION_MINIMO
import CONFIGURACION.PREGUNTAS_TEST_NIVELACION_MAXIMO
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import MODELOS.EstadoPregunta
import MODELOS.LectorMercado
import MODELOS.NivelExperiencia
import MODELOS.NuevoTestNivelacion
import MODELOS.RepositorioPregunta
import MODELOS.RepositorioTestNivelacion
import MODELOS.TestNivelacion
import MODELOS.TipoPregunta
import java.util.UUID

private const val LARGO_MAXIMO_TITULO = 150
private const val LARGO_MAXIMO_AREA = 50
private const val NIVEL_MIXTO = "mixto"

/** Datos de un test de nivelación tal como los envía el admin, sin validar. */
data class DatosTestNivelacion(
    val titulo: String,
    val cargoId: String?,
    val area: String,
    val nivelObjetivo: String?,
    val descripcion: String?,
    val preguntasIds: List<String>
)

/** Tests de nivelación que arma un admin con preguntas aprobadas del banco. */
class ServicioTestNivelacion(
    private val tests: RepositorioTestNivelacion,
    private val preguntas: RepositorioPregunta,
    private val mercado: LectorMercado
) {

    suspend fun crear(datos: DatosTestNivelacion): TestNivelacion = tests.crear(validar(datos))

    suspend fun actualizar(id: UUID, datos: DatosTestNivelacion): TestNivelacion {
        if (!tests.actualizar(id, validar(datos))) throw noEncontrado()
        return tests.buscar(id)!!
    }

    suspend fun obtener(id: UUID): TestNivelacion = tests.buscar(id) ?: throw noEncontrado()

    suspend fun listar(cargoId: UUID?, soloActivos: Boolean): List<TestNivelacion> = tests.listar(cargoId, soloActivos)

    suspend fun desactivar(id: UUID) {
        if (!tests.desactivar(id)) throw noEncontrado()
    }

    private suspend fun validar(datos: DatosTestNivelacion): NuevoTestNivelacion {
        val titulo = datos.titulo.trim()
        if (titulo.isEmpty() || titulo.length > LARGO_MAXIMO_TITULO) {
            throw ErrorValidacion("titulo_invalido", "El título es obligatorio (hasta $LARGO_MAXIMO_TITULO caracteres)")
        }
        val area = datos.area.trim()
        if (area.isEmpty() || area.length > LARGO_MAXIMO_AREA) {
            throw ErrorValidacion("area_invalida", "El área es obligatoria (hasta $LARGO_MAXIMO_AREA caracteres)")
        }
        val nivelObjetivo = datos.nivelObjetivo?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: NIVEL_MIXTO
        if (nivelObjetivo != NIVEL_MIXTO && NivelExperiencia.desdeBd(nivelObjetivo) == null) {
            throw ErrorValidacion("nivel_invalido", "El nivel objetivo debe ser junior, semisenior, senior o mixto")
        }
        val cargoId = datos.cargoId?.let { texto ->
            val id = runCatching { UUID.fromString(texto) }.getOrNull() ?: throw ErrorValidacion("cargo_id_invalido", "El id del cargo no es válido")
            mercado.buscarCargo(id)?.id ?: throw ErrorNoEncontrado("cargo_no_encontrado", "El cargo no existe")
        }
        return NuevoTestNivelacion(titulo, cargoId, area, nivelObjetivo, datos.descripcion?.trim()?.takeIf { it.isNotEmpty() }, validarPreguntas(datos.preguntasIds))
    }

    /** Preguntas aprobadas, sin repetir y escritas (la nivelación no usa video). */
    private suspend fun validarPreguntas(ids: List<String>): List<UUID> {
        if (ids.size !in PREGUNTAS_NIVELACION_MINIMO..PREGUNTAS_TEST_NIVELACION_MAXIMO) {
            throw ErrorValidacion(
                "cantidad_invalida",
                "El test lleva entre $PREGUNTAS_NIVELACION_MINIMO y $PREGUNTAS_TEST_NIVELACION_MAXIMO preguntas"
            )
        }
        val uuids = ids.map { runCatching { UUID.fromString(it) }.getOrNull() ?: throw ErrorValidacion("invalid_uuid", "Identificador con formato inválido") }
        if (uuids.toSet().size != uuids.size) throw ErrorValidacion("pregunta_repetida", "Hay preguntas repetidas")
        uuids.forEach { id ->
            val pregunta = preguntas.buscarPorId(id) ?: throw ErrorNoEncontrado("pregunta_no_encontrada", "La pregunta $id no existe")
            if (pregunta.estado != EstadoPregunta.APROBADA) {
                throw ErrorValidacion("pregunta_no_aprobada", "Solo se pueden usar preguntas aprobadas ($id)")
            }
            if (pregunta.tipo == TipoPregunta.SIMULACION_VIDEO) {
                throw ErrorValidacion("pregunta_no_permitida", "La nivelación es escrita: no admite preguntas de video ($id)")
            }
        }
        return uuids
    }

    private fun noEncontrado() = ErrorNoEncontrado("test_no_encontrado", "El test de nivelación no existe")
}
