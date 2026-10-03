package SERVICIOS

import ERRORES.ErrorNoEncontrado
import INTEGRACIONES.ClienteMercadoLaboral
import MODELOS.CATEGORIA_BLANDA
import MODELOS.CATEGORIA_TECNICA
import MODELOS.Cargo
import MODELOS.EscritorMercado
import MODELOS.LectorMercado
import MODELOS.NuevaSkill
import MODELOS.RequisitoCargo
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.math.roundToInt

private const val PESO_BASE = 35.0
private const val PESO_POR_PRESENCIA = 50.0
private const val PESO_POR_FRECUENCIA = 15.0
private const val PRESENCIA_OBLIGATORIA = 0.35
private const val PESO_OBLIGATORIO = 75
private const val AREA_HABILIDADES_BLANDAS = "hr"

data class RequisitosGenerados(val cargo: Cargo, val requisitos: List<RequisitoCargo>)

/**
 * Descubre qué skills pide el mercado para un cargo (buscando ofertas con el nombre del cargo)
 * y actualiza cargo_skill: peso según en cuántas ofertas aparece y obligatoria si es frecuente.
 */
class ServicioRequisitosCargo(
    private val lector: LectorMercado,
    private val escritor: EscritorMercado,
    private val clienteMercado: ClienteMercadoLaboral
) {
    private val log = LoggerFactory.getLogger(ServicioRequisitosCargo::class.java)

    suspend fun generarPara(cargoId: UUID): RequisitosGenerados {
        val cargo = lector.buscarCargo(cargoId) ?: throw ErrorNoEncontrado("cargo_no_encontrado", "El cargo no existe")

        // Antes se analizaba un texto vacío y siempre se terminaba usando las ofertas genéricas.
        var ofertas = clienteMercado.buscarOfertas(cargo.nombre)
        var estadisticas = NormalizadorSkill.analizarOfertas(ofertas)
        if (estadisticas.isEmpty()) {
            ofertas = clienteMercado.buscarOfertas(null)
            estadisticas = NormalizadorSkill.analizarOfertas(ofertas)
        }

        val totalOfertas = ofertas.size.coerceAtLeast(1)
        val maximoMenciones = (estadisticas.values.maxOfOrNull { it.menciones } ?: 1).coerceAtLeast(1)

        for ((nombreSkill, estadistica) in estadisticas) {
            val presencia = estadistica.ofertasConSkill.toDouble() / totalOfertas
            val peso = (PESO_BASE + presencia * PESO_POR_PRESENCIA + estadistica.menciones.toDouble() / maximoMenciones * PESO_POR_FRECUENCIA)
                .roundToInt().coerceIn(1, 100)
            val esBlanda = nombreSkill in NormalizadorSkill.SKILLS_BLANDAS
            val skillId = escritor.obtenerOCrearSkill(
                NuevaSkill(
                    nombre = nombreSkill,
                    categoria = if (esBlanda) CATEGORIA_BLANDA else CATEGORIA_TECNICA,
                    tipoArea = if (esBlanda) AREA_HABILIDADES_BLANDAS else cargo.area,
                    descripcion = "Habilidad del mercado requerida para el cargo ${cargo.nombre}",
                    demandaScore = peso.toShort()
                )
            )
            escritor.vincularSkill(
                cargoId = cargo.id,
                skillId = skillId,
                nivel = estadistica.nivelPredominante,
                peso = peso.toShort(),
                esObligatoria = presencia >= PRESENCIA_OBLIGATORIA || peso >= PESO_OBLIGATORIO
            )
        }
        log.info("Se vincularon {} skills al cargo '{}' a partir de {} ofertas", estadisticas.size, cargo.nombre, ofertas.size)
        return RequisitosGenerados(cargo, lector.listarRequisitos(cargo.id))
    }

    /** Recorre todos los cargos activos; un cargo que falla no detiene a los demás. */
    suspend fun generarParaTodos(): List<RequisitosGenerados> =
        lector.listarCargosActivos().mapNotNull { cargo ->
            runCatching { generarPara(cargo.id) }
                .onFailure { log.error("No se pudieron generar requisitos para '{}'", cargo.nombre, it) }
                .getOrNull()
        }
}
