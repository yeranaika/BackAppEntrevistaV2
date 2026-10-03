package MODELOS

import UTILIDADES.transaccion
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.Random
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.util.UUID

interface RepositorioPregunta {
    suspend fun crear(contenido: ContenidoPregunta, estado: EstadoPregunta): Pregunta
    suspend fun buscarPorId(id: UUID): Pregunta?
    suspend fun listar(filtro: FiltroPreguntas, pagina: Int, tamano: Int): PaginaPreguntas

    /** Preguntas al azar que cumplen el filtro (para servirlas a usuarios). */
    suspend fun listarAlAzar(filtro: FiltroPreguntas, cantidad: Int): List<Pregunta>

    /** Reemplaza enunciado, rúbrica y opciones; la pregunta vuelve a revisión. false si no existe. */
    suspend fun reemplazarContenido(id: UUID, contenido: ContenidoPregunta): Boolean

    /** También deja constancia en la trazabilidad de IA (estado_revision y quién revisó). */
    suspend fun cambiarEstado(id: UUID, estado: EstadoPregunta, motivoRechazo: String?, revisadoPor: UUID?): Boolean

    suspend fun eliminar(id: UUID): Boolean
}

class RepositorioPreguntaExposed : RepositorioPregunta {

    override suspend fun crear(contenido: ContenidoPregunta, estado: EstadoPregunta): Pregunta = transaccion {
        val id = UUID.randomUUID()
        insertarPreguntaConOpciones(id, contenido, estado, esGeneradaPorIa = false, OffsetDateTime.now())
        buscar(id)!!
    }

    override suspend fun buscarPorId(id: UUID): Pregunta? = transaccion { buscar(id) }

    override suspend fun listar(filtro: FiltroPreguntas, pagina: Int, tamano: Int): PaginaPreguntas = transaccion {
        val total = consulta(filtro).count()
        val filas = consulta(filtro)
            .orderBy(TablaPregunta.fechaCreacion to SortOrder.DESC)
            .limit(tamano, offset = ((pagina - 1).toLong() * tamano))
            .toList()
        PaginaPreguntas(conOpciones(filas), total, pagina, tamano)
    }

    override suspend fun listarAlAzar(filtro: FiltroPreguntas, cantidad: Int): List<Pregunta> = transaccion {
        conOpciones(consulta(filtro).orderBy(Random()).limit(cantidad).toList())
    }

    override suspend fun reemplazarContenido(id: UUID, contenido: ContenidoPregunta): Boolean = transaccion {
        val actualizadas = TablaPregunta.update({ TablaPregunta.preguntaId eq id }) {
            escribirContenido(it, contenido)
            it[estado] = EstadoPregunta.PENDIENTE.valorBd
            it[motivoRechazo] = null
        }
        if (actualizadas == 0) return@transaccion false
        TablaOpcionPregunta.deleteWhere { preguntaId eq id }
        insertarOpciones(id, contenido.opciones)
        true
    }

    override suspend fun cambiarEstado(id: UUID, estado: EstadoPregunta, motivoRechazo: String?, revisadoPor: UUID?): Boolean =
        transaccion {
            val actualizadas = TablaPregunta.update({ TablaPregunta.preguntaId eq id }) {
                it[TablaPregunta.estado] = estado.valorBd
                it[TablaPregunta.motivoRechazo] = motivoRechazo
            }
            TablaGeneracionPreguntaIa.update({ TablaGeneracionPreguntaIa.preguntaId eq id }) {
                it[estadoRevision] = estado.valorTrazabilidad
                it[TablaGeneracionPreguntaIa.revisadoPor] = revisadoPor
            }
            actualizadas > 0
        }

    override suspend fun eliminar(id: UUID): Boolean = transaccion {
        // En Postgres el FK ya borra en cascada; se hace explícito para no depender del esquema.
        TablaOpcionPregunta.deleteWhere { preguntaId eq id }
        TablaPregunta.deleteWhere { preguntaId eq id } > 0
    }

    // ---------- Apoyo ----------

    private fun consulta(filtro: FiltroPreguntas): Query {
        val condiciones = listOfNotNull(
            filtro.estado?.let { TablaPregunta.estado eq it.valorBd },
            filtro.tipo?.let { TablaPregunta.tipoPregunta eq it.valorBd },
            filtro.categoria?.let { TablaPregunta.categoriaHabilidad eq it.valorBd },
            filtro.nivel?.let { TablaPregunta.nivelDificultad eq it.valorBd },
            filtro.skillId?.let { TablaPregunta.skillId eq it },
            filtro.cargoId?.let { TablaPregunta.cargoId eq it },
            filtro.generadaPorIa?.let { TablaPregunta.generadaPorIa eq it }
        )
        val consulta = TablaPregunta.selectAll()
        return if (condiciones.isEmpty()) consulta else consulta.where { condiciones.reduce<Op<Boolean>, Op<Boolean>> { a, b -> a and b } }
    }

    private fun buscar(id: UUID): Pregunta? =
        TablaPregunta.selectAll().where { TablaPregunta.preguntaId eq id }.limit(1).toList().let(::conOpciones).firstOrNull()

    /** Carga las opciones de todas las preguntas en una sola consulta. */
    private fun conOpciones(filas: List<ResultRow>): List<Pregunta> {
        if (filas.isEmpty()) return emptyList()
        val ids = filas.map { it[TablaPregunta.preguntaId] }
        val opcionesPorPregunta = TablaOpcionPregunta
            .selectAll()
            .where { TablaOpcionPregunta.preguntaId inList ids }
            .orderBy(TablaOpcionPregunta.orden to SortOrder.ASC)
            .groupBy({ it[TablaOpcionPregunta.preguntaId] }) {
                OpcionPregunta(
                    id = it[TablaOpcionPregunta.opcionId],
                    texto = it[TablaOpcionPregunta.textoOpcion],
                    esCorrecta = it[TablaOpcionPregunta.esCorrecta],
                    explicacion = it[TablaOpcionPregunta.explicacion],
                    orden = it[TablaOpcionPregunta.orden].toInt()
                )
            }
        return filas.map { it.aPregunta(opcionesPorPregunta[it[TablaPregunta.preguntaId]].orEmpty()) }
    }

    private fun ResultRow.aPregunta(opciones: List<OpcionPregunta>) = Pregunta(
        id = this[TablaPregunta.preguntaId],
        skillId = this[TablaPregunta.skillId],
        cargoId = this[TablaPregunta.cargoId],
        tipo = TipoPregunta.desdeBd(this[TablaPregunta.tipoPregunta]) ?: error("tipo_pregunta desconocido"),
        categoria = CategoriaHabilidad.desdeBd(this[TablaPregunta.categoriaHabilidad]) ?: error("categoria desconocida"),
        nivel = NivelExperiencia.desdeBd(this[TablaPregunta.nivelDificultad]) ?: error("nivel_dificultad desconocido"),
        enunciado = this[TablaPregunta.enunciado],
        respuestaIdeal = this[TablaPregunta.respuestaIdeal],
        rubrica = this[TablaPregunta.rubricaEvaluacion],
        generadaPorIa = this[TablaPregunta.generadaPorIa],
        estado = EstadoPregunta.desdeBd(this[TablaPregunta.estado]) ?: error("estado de pregunta desconocido"),
        motivoRechazo = this[TablaPregunta.motivoRechazo],
        vecesUsada = this[TablaPregunta.vecesUsada],
        fechaCreacion = this[TablaPregunta.fechaCreacion],
        opciones = opciones
    )
}

/** Inserta la pregunta y sus opciones. Debe llamarse dentro de una transacción. */
internal fun insertarPreguntaConOpciones(
    id: UUID,
    contenido: ContenidoPregunta,
    estado: EstadoPregunta,
    esGeneradaPorIa: Boolean,
    ahora: OffsetDateTime
) {
    TablaPregunta.insert {
        it[preguntaId] = id
        escribirContenido(it, contenido)
        it[generadaPorIa] = esGeneradaPorIa
        it[TablaPregunta.estado] = estado.valorBd
        it[fechaCreacion] = ahora
    }
    insertarOpciones(id, contenido.opciones)
}

private fun escribirContenido(fila: UpdateBuilder<*>, contenido: ContenidoPregunta) {
    fila[TablaPregunta.skillId] = contenido.skillId
    fila[TablaPregunta.cargoId] = contenido.cargoId
    fila[TablaPregunta.tipoPregunta] = contenido.tipo.valorBd
    fila[TablaPregunta.categoriaHabilidad] = contenido.categoria.valorBd
    fila[TablaPregunta.nivelDificultad] = contenido.nivel.valorBd
    fila[TablaPregunta.enunciado] = contenido.enunciado
    fila[TablaPregunta.respuestaIdeal] = contenido.respuestaIdeal
    fila[TablaPregunta.rubricaEvaluacion] = contenido.rubrica
}

private fun insertarOpciones(preguntaId: UUID, opciones: List<NuevaOpcion>) {
    opciones.forEachIndexed { indice, opcion ->
        TablaOpcionPregunta.insert {
            it[opcionId] = UUID.randomUUID()
            it[TablaOpcionPregunta.preguntaId] = preguntaId
            it[textoOpcion] = opcion.texto
            it[esCorrecta] = opcion.esCorrecta
            it[explicacion] = opcion.explicacion
            it[orden] = (indice + 1).toShort()
        }
    }
}
