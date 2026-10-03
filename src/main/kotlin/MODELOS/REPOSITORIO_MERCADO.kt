package MODELOS

import UTILIDADES.transaccion
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

/** Consultas del catálogo de mercado (cargos, skills, requisitos y tendencias). */
interface LectorMercado {
    suspend fun listarCargosActivos(): List<Cargo>
    suspend fun buscarCargo(id: UUID): Cargo?
    /** Sin distinguir mayúsculas ni espacios en los extremos. */
    suspend fun buscarCargoPorNombre(nombre: String): Cargo?
    suspend fun listarRequisitos(cargoId: UUID): List<RequisitoCargo>
    suspend fun listarSkillsActivas(): List<Skill>
    suspend fun buscarSkill(id: UUID): Skill?

    /** Skills activas ordenadas por demanda, opcionalmente de una categoría. */
    suspend fun skillsMasDemandadas(categoria: String?, limite: Int): List<Skill>
    suspend fun historialTendencias(skillId: UUID, limite: Int): List<TendenciaSkill>

    /** Fecha de la última sincronización con el mercado, o null si nunca se hizo. */
    suspend fun fechaUltimaTendencia(): LocalDateTime?
}

/** Escrituras que hacen el admin y la sincronización con el mercado laboral. */
interface EscritorMercado {
    suspend fun crearCargo(nuevo: NuevoCargo): Cargo

    /** Devuelve el id de la skill con ese nombre, creándola si no existe. */
    suspend fun obtenerOCrearSkill(nueva: NuevaSkill): UUID

    /** Crea o actualiza la relación cargo–skill. */
    suspend fun vincularSkill(cargoId: UUID, skillId: UUID, nivel: NivelExperiencia, peso: Short, esObligatoria: Boolean)

    /** Guarda la demanda nueva, deja historial y reajusta los pesos de los cargos que la usan. */
    suspend fun registrarDemanda(skillId: UUID, demanda: Short, frecuenciaOfertas: Int, nivel: NivelExperiencia)
}

private const val PESO_BASE_ACTUAL = 0.6
private const val PESO_DEMANDA_MERCADO = 0.4

class RepositorioMercadoExposed : LectorMercado, EscritorMercado {

    override suspend fun listarCargosActivos(): List<Cargo> = transaccion {
        TablaCargo.selectAll().where { TablaCargo.activo eq true }
            .orderBy(TablaCargo.nombre to SortOrder.ASC)
            .map { it.aCargo() }
    }

    override suspend fun buscarCargo(id: UUID): Cargo? = transaccion {
        TablaCargo.selectAll().where { TablaCargo.cargoId eq id }.limit(1).firstOrNull()?.aCargo()
    }

    override suspend fun buscarCargoPorNombre(nombre: String): Cargo? = transaccion {
        TablaCargo.selectAll().where { TablaCargo.nombre.lowerCase() eq nombre.trim().lowercase() }.limit(1).firstOrNull()?.aCargo()
    }

    override suspend fun listarRequisitos(cargoId: UUID): List<RequisitoCargo> = transaccion {
        (TablaCargoSkill innerJoin TablaSkill)
            .selectAll()
            .where { (TablaCargoSkill.cargoId eq cargoId) and (TablaSkill.activo eq true) }
            .orderBy(TablaCargoSkill.obligatoria to SortOrder.DESC, TablaCargoSkill.peso to SortOrder.DESC)
            .map {
                RequisitoCargo(
                    skill = it.aSkill(),
                    nivelRequerido = nivelDesdeBd(it[TablaCargoSkill.nivelRequerido]),
                    peso = it[TablaCargoSkill.peso],
                    esObligatoria = it[TablaCargoSkill.obligatoria]
                )
            }
    }

    override suspend fun listarSkillsActivas(): List<Skill> = transaccion {
        TablaSkill.selectAll().where { TablaSkill.activo eq true }
            .orderBy(TablaSkill.demandaScore to SortOrder.DESC)
            .map { it.aSkill() }
    }

    override suspend fun buscarSkill(id: UUID): Skill? = transaccion {
        TablaSkill.selectAll().where { TablaSkill.skillId eq id }.limit(1).firstOrNull()?.aSkill()
    }

    override suspend fun skillsMasDemandadas(categoria: String?, limite: Int): List<Skill> = transaccion {
        TablaSkill.selectAll()
            .where { if (categoria == null) TablaSkill.activo eq true else (TablaSkill.activo eq true) and (TablaSkill.categoria eq categoria) }
            .orderBy(TablaSkill.demandaScore to SortOrder.DESC)
            .limit(limite)
            .map { it.aSkill() }
    }

    override suspend fun historialTendencias(skillId: UUID, limite: Int): List<TendenciaSkill> = transaccion {
        TablaSkillTendencia.selectAll().where { TablaSkillTendencia.skillId eq skillId }
            .orderBy(TablaSkillTendencia.fechaActualizacion to SortOrder.DESC)
            .limit(limite)
            .map {
                TendenciaSkill(
                    id = it[TablaSkillTendencia.tendenciaId],
                    skillId = it[TablaSkillTendencia.skillId],
                    frecuenciaOfertas = it[TablaSkillTendencia.frecuenciaOfertas],
                    nivelRequerido = nivelDesdeBd(it[TablaSkillTendencia.nivelRequerido]),
                    fecha = it[TablaSkillTendencia.fechaActualizacion]
                )
            }
    }

    override suspend fun fechaUltimaTendencia(): LocalDateTime? = transaccion {
        TablaSkillTendencia.select(TablaSkillTendencia.fechaActualizacion.max()).firstOrNull()?.get(TablaSkillTendencia.fechaActualizacion.max())
    }

    override suspend fun crearCargo(nuevo: NuevoCargo): Cargo = transaccion {
        val id = UUID.randomUUID()
        TablaCargo.insert {
            it[cargoId] = id
            it[nombre] = nuevo.nombre
            it[area] = nuevo.area
            it[descripcion] = nuevo.descripcion
            it[nivelBase] = nuevo.nivelBase.valorBd
            it[activo] = true
        }
        Cargo(id, nuevo.nombre, nuevo.area, nuevo.descripcion, nuevo.nivelBase, estaActivo = true)
    }

    override suspend fun obtenerOCrearSkill(nueva: NuevaSkill): UUID = transaccion {
        TablaSkill.selectAll().where { TablaSkill.nombre eq nueva.nombre }.limit(1).firstOrNull()?.get(TablaSkill.skillId)
            ?: UUID.randomUUID().also { id ->
                TablaSkill.insert {
                    it[skillId] = id
                    it[nombre] = nueva.nombre
                    it[categoria] = nueva.categoria
                    it[tipoArea] = nueva.tipoArea
                    it[descripcion] = nueva.descripcion
                    it[demandaScore] = nueva.demandaScore.coerceIn(DEMANDA_MINIMA, DEMANDA_MAXIMA)
                    it[activo] = true
                }
            }
    }

    override suspend fun vincularSkill(cargoId: UUID, skillId: UUID, nivel: NivelExperiencia, peso: Short, esObligatoria: Boolean) {
        transaccion {
            val actualizadas = TablaCargoSkill.update({ (TablaCargoSkill.cargoId eq cargoId) and (TablaCargoSkill.skillId eq skillId) }) {
                it[nivelRequerido] = nivel.valorBd
                it[TablaCargoSkill.peso] = peso.coerceIn(DEMANDA_MINIMA, DEMANDA_MAXIMA)
                it[obligatoria] = esObligatoria
            }
            if (actualizadas == 0) {
                TablaCargoSkill.insert {
                    it[cargoSkillId] = UUID.randomUUID()
                    it[TablaCargoSkill.cargoId] = cargoId
                    it[TablaCargoSkill.skillId] = skillId
                    it[nivelRequerido] = nivel.valorBd
                    it[TablaCargoSkill.peso] = peso.coerceIn(DEMANDA_MINIMA, DEMANDA_MAXIMA)
                    it[obligatoria] = esObligatoria
                }
            }
        }
    }

    override suspend fun registrarDemanda(skillId: UUID, demanda: Short, frecuenciaOfertas: Int, nivel: NivelExperiencia) {
        transaccion {
            val demandaValida = demanda.coerceIn(DEMANDA_MINIMA, DEMANDA_MAXIMA)
            TablaSkill.update({ TablaSkill.skillId eq skillId }) { it[demandaScore] = demandaValida }
            TablaSkillTendencia.insert {
                it[tendenciaId] = UUID.randomUUID()
                it[TablaSkillTendencia.skillId] = skillId
                it[TablaSkillTendencia.frecuenciaOfertas] = frecuenciaOfertas
                it[nivelRequerido] = nivel.valorBd
                it[fechaActualizacion] = LocalDateTime.now()
            }
            // El peso de cada cargo combina su valor actual (60 %) con la demanda del mercado (40 %).
            TablaCargoSkill.selectAll().where { TablaCargoSkill.skillId eq skillId }.toList().forEach { relacion ->
                val ajustado = (relacion[TablaCargoSkill.peso] * PESO_BASE_ACTUAL + demandaValida * PESO_DEMANDA_MERCADO)
                    .toInt().toShort().coerceIn(DEMANDA_MINIMA, DEMANDA_MAXIMA)
                TablaCargoSkill.update({ TablaCargoSkill.cargoSkillId eq relacion[TablaCargoSkill.cargoSkillId] }) {
                    it[peso] = ajustado
                }
            }
        }
    }

    private fun nivelDesdeBd(valor: String) = NivelExperiencia.desdeBd(valor) ?: NivelExperiencia.SEMISENIOR

    private fun ResultRow.aCargo() = Cargo(
        id = this[TablaCargo.cargoId],
        nombre = this[TablaCargo.nombre],
        area = this[TablaCargo.area],
        descripcion = this[TablaCargo.descripcion],
        nivelBase = nivelDesdeBd(this[TablaCargo.nivelBase]),
        estaActivo = this[TablaCargo.activo]
    )

    private fun ResultRow.aSkill() = Skill(
        id = this[TablaSkill.skillId],
        nombre = this[TablaSkill.nombre],
        categoria = this[TablaSkill.categoria],
        tipoArea = this[TablaSkill.tipoArea],
        descripcion = this[TablaSkill.descripcion],
        demandaScore = this[TablaSkill.demandaScore],
        estaActiva = this[TablaSkill.activo]
    )
}
