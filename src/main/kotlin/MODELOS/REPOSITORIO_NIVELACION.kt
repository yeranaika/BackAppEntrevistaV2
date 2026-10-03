package MODELOS

import UTILIDADES.transaccion
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

// ─── Tests de nivelación (los arma un admin) ─────────────────────────────────

interface RepositorioTestNivelacion {
    suspend fun crear(nuevo: NuevoTestNivelacion): TestNivelacion
    /** false si no existe. */
    suspend fun actualizar(id: UUID, nuevo: NuevoTestNivelacion): Boolean
    suspend fun buscar(id: UUID): TestNivelacion?
    suspend fun listar(cargoId: UUID?, soloActivos: Boolean): List<TestNivelacion>
    /** Baja lógica (activo = false). false si no existe. */
    suspend fun desactivar(id: UUID): Boolean
    /** Un test activo para el cargo, o null. */
    suspend fun buscarActivoParaCargo(cargoId: UUID): TestNivelacion?
}

class RepositorioTestNivelacionExposed : RepositorioTestNivelacion {

    override suspend fun crear(nuevo: NuevoTestNivelacion): TestNivelacion = transaccion {
        val id = UUID.randomUUID()
        TablaTestNivelacion.insert {
            it[testId] = id
            it[activo] = true
            escribir(it, nuevo)
        }
        buscarEnTransaccion(id)!!
    }

    override suspend fun actualizar(id: UUID, nuevo: NuevoTestNivelacion): Boolean = transaccion {
        TablaTestNivelacion.update({ TablaTestNivelacion.testId eq id }) { escribir(it, nuevo) } > 0
    }

    override suspend fun buscar(id: UUID): TestNivelacion? = transaccion { buscarEnTransaccion(id) }

    override suspend fun listar(cargoId: UUID?, soloActivos: Boolean): List<TestNivelacion> = transaccion {
        val condiciones = listOfNotNull(
            cargoId?.let { TablaTestNivelacion.cargoId eq it },
            if (soloActivos) TablaTestNivelacion.activo eq true else null
        )
        val consulta = TablaTestNivelacion.selectAll()
        val filtrada = if (condiciones.isEmpty()) consulta else consulta.where { condiciones.reduce<Op<Boolean>, Op<Boolean>> { a, b -> a and b } }
        filtrada.orderBy(TablaTestNivelacion.titulo to SortOrder.ASC).map { it.aTest() }
    }

    override suspend fun desactivar(id: UUID): Boolean = transaccion {
        TablaTestNivelacion.update({ TablaTestNivelacion.testId eq id }) { it[activo] = false } > 0
    }

    override suspend fun buscarActivoParaCargo(cargoId: UUID): TestNivelacion? = transaccion {
        TablaTestNivelacion.selectAll()
            .where { (TablaTestNivelacion.cargoId eq cargoId) and (TablaTestNivelacion.activo eq true) }
            .orderBy(TablaTestNivelacion.titulo to SortOrder.ASC)
            .limit(1)
            .firstOrNull()?.aTest()
    }

    private fun buscarEnTransaccion(id: UUID) =
        TablaTestNivelacion.selectAll().where { TablaTestNivelacion.testId eq id }.firstOrNull()?.aTest()

    private fun escribir(fila: org.jetbrains.exposed.sql.statements.UpdateBuilder<*>, nuevo: NuevoTestNivelacion) {
        fila[TablaTestNivelacion.titulo] = nuevo.titulo
        fila[TablaTestNivelacion.cargoId] = nuevo.cargoId
        fila[TablaTestNivelacion.area] = nuevo.area
        fila[TablaTestNivelacion.nivelObjetivo] = nuevo.nivelObjetivo
        fila[TablaTestNivelacion.descripcion] = nuevo.descripcion
        fila[TablaTestNivelacion.preguntasIds] = nuevo.preguntasIds.map(UUID::toString)
    }

    private fun ResultRow.aTest() = TestNivelacion(
        id = this[TablaTestNivelacion.testId],
        titulo = this[TablaTestNivelacion.titulo],
        cargoId = this[TablaTestNivelacion.cargoId],
        area = this[TablaTestNivelacion.area],
        nivelObjetivo = this[TablaTestNivelacion.nivelObjetivo],
        descripcion = this[TablaTestNivelacion.descripcion],
        preguntasIds = this[TablaTestNivelacion.preguntasIds].map(UUID::fromString),
        estaActivo = this[TablaTestNivelacion.activo]
    )
}

// ─── Intentos y resultados de nivelación ─────────────────────────────────────

interface RepositorioNivelacion {
    suspend fun crearIntento(
        usuarioId: UUID,
        testId: UUID?,
        cargoId: UUID?,
        cargoObjetivo: String,
        preguntas: List<PreguntaServida>,
        inicio: Instant
    ): IntentoNivelacion

    suspend fun buscarIntento(id: UUID): IntentoNivelacion?

    /**
     * Cierra el intento con sus respuestas corregidas y guarda el resultado, todo junto.
     * false si el intento ya estaba terminado (dos envíos a la vez: solo uno gana).
     */
    suspend fun registrarResultado(
        intentoId: UUID,
        detalle: List<DetalleIntento>,
        resultado: ResultadoNivelacion,
        usuarioId: UUID
    ): Boolean

    suspend fun buscarResultado(intentoId: UUID): ResultadoNivelacion?
    suspend fun ultimoResultado(usuarioId: UUID): ResultadoNivelacion?
    suspend fun listarResumenes(usuarioId: UUID, limite: Int): List<ResumenPrueba>
}

class RepositorioNivelacionExposed : RepositorioNivelacion {

    override suspend fun crearIntento(
        usuarioId: UUID,
        testId: UUID?,
        cargoId: UUID?,
        cargoObjetivo: String,
        preguntas: List<PreguntaServida>,
        inicio: Instant
    ): IntentoNivelacion = transaccion {
        val id = UUID.randomUUID()
        TablaIntentoTest.insert {
            it[intentoId] = id
            it[TablaIntentoTest.usuarioId] = usuarioId
            it[TablaIntentoTest.testId] = testId
            it[tipoTest] = TIPO_TEST_NIVELACION
            it[TablaIntentoTest.cargoId] = cargoId
            it[TablaIntentoTest.cargoObjetivo] = cargoObjetivo
            it[detalle] = preguntas.map { pregunta -> DetalleIntento(pregunta) }
            it[fechaInicio] = inicio
        }
        buscarIntentoEnTransaccion(id)!!
    }

    override suspend fun buscarIntento(id: UUID): IntentoNivelacion? = transaccion { buscarIntentoEnTransaccion(id) }

    override suspend fun registrarResultado(
        intentoId: UUID,
        detalle: List<DetalleIntento>,
        resultado: ResultadoNivelacion,
        usuarioId: UUID
    ): Boolean = transaccion {
        val cerrados = TablaIntentoTest.update({ (TablaIntentoTest.intentoId eq intentoId) and TablaIntentoTest.fechaFin.isNull() }) {
            it[TablaIntentoTest.detalle] = detalle
            it[puntajeObtenido] = resultado.puntajeGlobal
            it[nivelAsignado] = resultado.nivelGlobal.valorBd
            it[fechaFin] = resultado.fecha
        }
        if (cerrados == 0) return@transaccion false
        TablaResultadoNivelacion.insert {
            it[resultadoId] = UUID.randomUUID()
            it[TablaResultadoNivelacion.intentoId] = intentoId
            it[TablaResultadoNivelacion.usuarioId] = usuarioId
            it[cargoId] = resultado.cargoId
            it[nivelGlobal] = resultado.nivelGlobal.valorBd
            it[puntajeGlobal] = resultado.puntajeGlobal
            it[skillsGap] = resultado.skillsBrecha
            it[skillsOk] = resultado.skillsOk
            it[resumen] = resultado.resumen
            it[fechaGeneracion] = resultado.fecha
        }
        true
    }

    override suspend fun buscarResultado(intentoId: UUID): ResultadoNivelacion? = transaccion {
        TablaResultadoNivelacion.selectAll().where { TablaResultadoNivelacion.intentoId eq intentoId }.firstOrNull()?.aResultado()
    }

    override suspend fun ultimoResultado(usuarioId: UUID): ResultadoNivelacion? = transaccion {
        TablaResultadoNivelacion.selectAll()
            .where { TablaResultadoNivelacion.usuarioId eq usuarioId }
            .orderBy(TablaResultadoNivelacion.fechaGeneracion to SortOrder.DESC)
            .limit(1)
            .firstOrNull()?.aResultado()
    }

    override suspend fun listarResumenes(usuarioId: UUID, limite: Int): List<ResumenPrueba> = transaccion {
        TablaIntentoTest.selectAll()
            .where { (TablaIntentoTest.usuarioId eq usuarioId) and (TablaIntentoTest.tipoTest eq TIPO_TEST_NIVELACION) }
            .orderBy(TablaIntentoTest.fechaInicio to SortOrder.DESC)
            .limit(limite)
            .map { fila ->
                val intento = fila.aIntento()
                ResumenPrueba(
                    id = intento.id,
                    tipo = TIPO_TEST_NIVELACION,
                    cargoObjetivo = intento.cargoObjetivo,
                    nivel = intento.nivelAsignado,
                    estado = if (intento.estaTerminado) "finalizada" else "en_progreso",
                    puntaje = if (intento.estaTerminado) intento.detalle.count { it.respuesta?.correcta == true } else null,
                    puntajeTotal = intento.detalle.size,
                    fechaInicio = intento.fechaInicio,
                    fechaFin = intento.fechaFin
                )
            }
    }

    private fun buscarIntentoEnTransaccion(id: UUID) =
        TablaIntentoTest.selectAll()
            .where { (TablaIntentoTest.intentoId eq id) and (TablaIntentoTest.tipoTest eq TIPO_TEST_NIVELACION) }
            .firstOrNull()?.aIntento()

    private fun ResultRow.aIntento() = IntentoNivelacion(
        id = this[TablaIntentoTest.intentoId],
        usuarioId = this[TablaIntentoTest.usuarioId],
        testId = this[TablaIntentoTest.testId],
        cargoId = this[TablaIntentoTest.cargoId],
        cargoObjetivo = this[TablaIntentoTest.cargoObjetivo],
        puntaje = this[TablaIntentoTest.puntajeObtenido],
        nivelAsignado = NivelExperiencia.desdeBd(this[TablaIntentoTest.nivelAsignado]),
        detalle = this[TablaIntentoTest.detalle],
        fechaInicio = this[TablaIntentoTest.fechaInicio],
        fechaFin = this[TablaIntentoTest.fechaFin]
    )

    private fun ResultRow.aResultado() = ResultadoNivelacion(
        intentoId = this[TablaResultadoNivelacion.intentoId],
        cargoId = this[TablaResultadoNivelacion.cargoId],
        nivelGlobal = NivelExperiencia.desdeBd(this[TablaResultadoNivelacion.nivelGlobal]) ?: NivelExperiencia.JUNIOR,
        puntajeGlobal = this[TablaResultadoNivelacion.puntajeGlobal],
        skillsBrecha = this[TablaResultadoNivelacion.skillsGap],
        skillsOk = this[TablaResultadoNivelacion.skillsOk],
        resumen = this[TablaResultadoNivelacion.resumen],
        fecha = this[TablaResultadoNivelacion.fechaGeneracion]
    )
}

// ─── Nivel acumulado por skill ───────────────────────────────────────────────

interface RepositorioNivelSkill {
    /** Promedia cada evaluación con las anteriores de esa skill. */
    suspend fun acumular(usuarioId: UUID, evaluaciones: List<EvaluacionNivelSkill>, fecha: Instant)
    suspend fun listar(usuarioId: UUID): List<NivelSkillUsuario>
}

class RepositorioNivelSkillExposed : RepositorioNivelSkill {

    override suspend fun acumular(usuarioId: UUID, evaluaciones: List<EvaluacionNivelSkill>, fecha: Instant) {
        if (evaluaciones.isEmpty()) return
        transaccion {
            // Bloquear al usuario serializa dos acumulaciones a la vez (si no, una pisaría el promedio de la otra).
            TablaUsuario.selectAll().where { TablaUsuario.usuarioId eq usuarioId }.forUpdate().toList()
            evaluaciones.forEach { acumularUna(usuarioId, it, fecha) }
        }
    }

    override suspend fun listar(usuarioId: UUID): List<NivelSkillUsuario> = transaccion {
        TablaNivelSkillUsuario.selectAll().where { TablaNivelSkillUsuario.usuarioId eq usuarioId }.map {
            NivelSkillUsuario(
                skillId = it[TablaNivelSkillUsuario.skillId],
                nivel = NivelExperiencia.desdeBd(it[TablaNivelSkillUsuario.nivelEvaluado]),
                puntaje = it[TablaNivelSkillUsuario.puntaje],
                evaluaciones = it[TablaNivelSkillUsuario.numEvaluaciones],
                fecha = it[TablaNivelSkillUsuario.fechaEvaluacion]
            )
        }
    }

    private fun acumularUna(usuarioId: UUID, evaluacion: EvaluacionNivelSkill, fecha: Instant) {
        val actual = TablaNivelSkillUsuario.selectAll()
            .where { (TablaNivelSkillUsuario.usuarioId eq usuarioId) and (TablaNivelSkillUsuario.skillId eq evaluacion.skillId) }
            .firstOrNull()
        if (actual == null) {
            TablaNivelSkillUsuario.insert {
                it[id] = UUID.randomUUID()
                it[TablaNivelSkillUsuario.usuarioId] = usuarioId
                it[skillId] = evaluacion.skillId
                it[nivelEvaluado] = evaluacion.nivel?.valorBd
                it[puntaje] = evaluacion.puntaje.setScale(2, RoundingMode.HALF_UP)
                it[numEvaluaciones] = 1
                it[fechaEvaluacion] = fecha
            }
            return
        }
        val evaluacionesPrevias = actual[TablaNivelSkillUsuario.numEvaluaciones]
        val promedio = (actual[TablaNivelSkillUsuario.puntaje] * BigDecimal(evaluacionesPrevias) + evaluacion.puntaje)
            .divide(BigDecimal(evaluacionesPrevias + 1), 2, RoundingMode.HALF_UP)
        TablaNivelSkillUsuario.update({ TablaNivelSkillUsuario.id eq actual[TablaNivelSkillUsuario.id] }) {
            it[puntaje] = promedio
            it[numEvaluaciones] = evaluacionesPrevias + 1
            it[fechaEvaluacion] = fecha
            evaluacion.nivel?.let { nivel -> it[nivelEvaluado] = nivel.valorBd }
        }
    }
}
