package SERVICIOS

import ERRORES.ErrorConflicto
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import ESQUEMAS.RespuestaCargo
import ESQUEMAS.RespuestaMatrizCargo
import ESQUEMAS.RespuestaTendencias
import ESQUEMAS.SolicitudCrearCargo
import INTEGRACIONES.Cache
import INTEGRACIONES.obtenerOCalcular
import MODELOS.CATEGORIA_BLANDA
import MODELOS.CATEGORIA_TECNICA
import MODELOS.Cargo
import MODELOS.EscritorMercado
import MODELOS.LectorMercado
import MODELOS.NuevoCargo
import MODELOS.Skill
import MODELOS.TendenciaSkill
import UTILIDADES.sinVacios
import VISTAS.aRespuesta
import VISTAS.aRespuestaMatriz
import VISTAS.aRespuestaTendencias
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

const val PREFIJO_CACHE_MERCADO = "mercado:"
private const val CLAVE_CARGOS = "${PREFIJO_CACHE_MERCADO}cargos"
private const val TTL_CARGOS_SEGUNDOS = 6 * 3600L
private const val TTL_MATRIZ_SEGUNDOS = 12 * 3600L
private const val TTL_TENDENCIAS_SEGUNDOS = 3600L
private const val LIMITE_TENDENCIAS_POR_DEFECTO = 20
private const val LIMITE_TENDENCIAS_MAXIMO = 100
private const val LIMITE_HISTORIAL = 10
private const val LARGO_MAXIMO_NOMBRE_CARGO = 150
private const val LARGO_MAXIMO_AREA = 50

/**
 * Consultas del catálogo de mercado con caché (cache-aside) y alta de cargos.
 * Toda escritura invalida la caché del mercado para no servir datos viejos.
 */
class ServicioMercado(
    private val lector: LectorMercado,
    private val escritor: EscritorMercado,
    private val requisitos: ServicioRequisitosCargo,
    private val cache: Cache
) {

    suspend fun listarCargos(): List<RespuestaCargo> =
        cache.obtenerOCalcular(CLAVE_CARGOS, TTL_CARGOS_SEGUNDOS, ListSerializer(RespuestaCargo.serializer())) {
            lector.listarCargosActivos().map { it.aRespuesta() }
        }

    suspend fun matrizDeCargo(cargoId: UUID): RespuestaMatrizCargo =
        cache.obtenerOCalcular("${PREFIJO_CACHE_MERCADO}cargo:$cargoId:skills", TTL_MATRIZ_SEGUNDOS, RespuestaMatrizCargo.serializer()) {
            val cargo = lector.buscarCargo(cargoId)?.takeIf { it.estaActivo } ?: throw cargoNoEncontrado()
            cargo.aRespuestaMatriz(lector.listarRequisitos(cargoId))
        }

    suspend fun tendencias(categoria: String?, limite: Int?): RespuestaTendencias {
        val categoriaValida = categoria.sinVacios()?.trim()?.lowercase()?.also {
            if (it !in setOf(CATEGORIA_TECNICA, CATEGORIA_BLANDA)) throw ErrorValidacion("categoria_invalida", "Categoría debe ser tecnica o blanda")
        }
        val limiteValido = limite ?: LIMITE_TENDENCIAS_POR_DEFECTO
        if (limiteValido !in 1..LIMITE_TENDENCIAS_MAXIMO) {
            throw ErrorValidacion("limite_invalido", "El límite debe estar entre 1 y $LIMITE_TENDENCIAS_MAXIMO")
        }
        val clave = "${PREFIJO_CACHE_MERCADO}tendencias:${categoriaValida ?: "todas"}:$limiteValido"
        return cache.obtenerOCalcular(clave, TTL_TENDENCIAS_SEGUNDOS, RespuestaTendencias.serializer()) {
            lector.skillsMasDemandadas(categoriaValida, limiteValido).aRespuestaTendencias(categoriaValida)
        }
    }

    suspend fun listarSkills(): List<Skill> = lector.listarSkillsActivas()

    suspend fun historialDeSkill(skillId: UUID): List<TendenciaSkill> {
        lector.buscarSkill(skillId) ?: throw ErrorNoEncontrado("skill_no_encontrada", "La skill no existe")
        return lector.historialTendencias(skillId, LIMITE_HISTORIAL)
    }

    /** Crea el cargo y, si se pide, genera sus requisitos consultando el mercado laboral. */
    suspend fun crearCargo(solicitud: SolicitudCrearCargo): Pair<Cargo, RequisitosGenerados?> {
        val nombre = solicitud.nombre.trim()
        val area = solicitud.area.trim()
        if (nombre.isEmpty() || nombre.length > LARGO_MAXIMO_NOMBRE_CARGO) {
            throw ErrorValidacion("nombre_invalido", "El nombre es obligatorio y de hasta $LARGO_MAXIMO_NOMBRE_CARGO caracteres")
        }
        if (area.isEmpty() || area.length > LARGO_MAXIMO_AREA) {
            throw ErrorValidacion("area_invalida", "El área es obligatoria y de hasta $LARGO_MAXIMO_AREA caracteres")
        }
        if (lector.buscarCargoPorNombre(nombre) != null) throw ErrorConflicto("cargo_existente", "Ya existe un cargo con ese nombre")

        val cargo = escritor.crearCargo(NuevoCargo(nombre, area, solicitud.descripcion.sinVacios(), leerNivel(solicitud.nivelBase)))
        val generados = if (solicitud.generarSkills) requisitos.generarPara(cargo.id) else null
        invalidarCache()
        return cargo to generados
    }

    suspend fun regenerarRequisitos(cargoId: UUID): RequisitosGenerados =
        requisitos.generarPara(cargoId).also { invalidarCache() }

    suspend fun regenerarTodos(): List<RequisitosGenerados> =
        requisitos.generarParaTodos().also { invalidarCache() }

    suspend fun invalidarCache() = cache.borrarPorPrefijo(PREFIJO_CACHE_MERCADO)

    private fun cargoNoEncontrado() = ErrorNoEncontrado("cargo_not_found", "No se encontró el cargo especificado")
}
