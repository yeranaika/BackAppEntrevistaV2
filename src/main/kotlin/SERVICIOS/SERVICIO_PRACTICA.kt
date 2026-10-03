package SERVICIOS

import CONFIGURACION.HISTORIAL_PRUEBAS_LIMITE
import CONFIGURACION.INTENTOS_OFFLINE_MAXIMOS_POR_LOTE
import CONFIGURACION.LARGO_MAXIMO_ENUNCIADO_OFFLINE
import CONFIGURACION.LARGO_MAXIMO_ID_LOCAL
import CONFIGURACION.PREGUNTAS_PRACTICA_MAXIMO
import CONFIGURACION.PREGUNTAS_PRACTICA_MINIMO
import CONFIGURACION.PREGUNTAS_PRACTICA_POR_DEFECTO
import CONFIGURACION.RESPUESTAS_OFFLINE_MAXIMAS_POR_INTENTO
import CONFIGURACION.SESIONES_PRACTICA_SIN_REPETIR
import ERRORES.ErrorAplicacion
import ERRORES.ErrorConflicto
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import MODELOS.CategoriaHabilidad
import MODELOS.EstadoPractica
import MODELOS.EvaluacionNivelSkill
import MODELOS.IntentoOffline
import MODELOS.LectorMercado
import MODELOS.ModoPractica
import MODELOS.NivelExperiencia
import MODELOS.NuevaSesionPractica
import MODELOS.PreguntaServida
import MODELOS.RepositorioNivelSkill
import MODELOS.RepositorioPractica
import MODELOS.RepositorioPregunta
import MODELOS.RespuestaCorregida
import MODELOS.RespuestaPractica
import MODELOS.ResultadoRegistroRespuestas
import MODELOS.ResumenPrueba
import MODELOS.SesionPractica
import MODELOS.TipoPregunta
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

private const val PUNTAJE_OFFLINE_MAXIMO = 10.0
private const val ESCALA_PUNTAJE = 10.0
private const val TIEMPO_RESPUESTA_MAXIMO_MS = 3_600_000
private val TOLERANCIA_RELOJ_CLIENTE = Duration.ofMinutes(5)

/** Qué quiere practicar el usuario. Lo que venga en null se completa con su perfil y objetivo. */
data class PedidoPractica(
    /** Practicar una skill puntual (si no viene, se practica el cargo). */
    val skillId: String? = null,
    val cargoId: String? = null,
    val nombreCargo: String? = null,
    /** tecnica | blanda (con skill se usa la categoría de la skill) */
    val categoria: String? = null,
    /** opcion_multiple | abierta_texto | mixto */
    val modo: String? = null,
    val nivel: String? = null,
    val cantidadPreguntas: Int? = null
)

/** Respuesta de un intento hecho sin conexión, tal como la manda la app. */
data class RespuestaOfflineEntrada(
    val preguntaId: String,
    val enunciado: String,
    val opcionElegidaId: String?,
    val respuestaTexto: String?,
    val esCorrecta: Boolean,
    val puntaje: Double,
    val tiempoRespuestaMs: Int?,
    val orden: Int
)

data class IntentoOfflineEntrada(
    val idLocal: String,
    val skillId: String,
    val cargoId: String?,
    val modo: String,
    val categoria: String,
    val nivel: String,
    val fechaCreacion: String?,
    val respuestas: List<RespuestaOfflineEntrada>
)

/**
 * Práctica (Flujo 2): rondas de preguntas escritas con feedback inmediato por respuesta.
 * La opción múltiple se corrige contra su opción correcta y la abierta con el motor freemium.
 * Una sola práctica en curso: empezar otra abandona la anterior.
 */
class ServicioPractica(
    private val practicas: RepositorioPractica,
    private val preguntas: RepositorioPregunta,
    private val selector: SelectorPreguntas,
    private val contexto: ResolutorContextoPrueba,
    private val mercado: LectorMercado,
    private val niveles: RepositorioNivelSkill,
    private val corrector: CorrectorRespuestas,
    private val reloj: Clock = Clock.systemUTC()
) {

    suspend fun iniciar(usuarioId: UUID, pedido: PedidoPractica): SesionPractica {
        val cantidad = pedido.cantidadPreguntas ?: PREGUNTAS_PRACTICA_POR_DEFECTO
        if (cantidad !in PREGUNTAS_PRACTICA_MINIMO..PREGUNTAS_PRACTICA_MAXIMO) {
            throw ErrorValidacion("cantidad_invalida", "La práctica debe tener entre $PREGUNTAS_PRACTICA_MINIMO y $PREGUNTAS_PRACTICA_MAXIMO preguntas")
        }
        val modo = pedido.modo?.let { ModoPractica.desdeBd(it.trim().lowercase()) ?: throw modoInvalido() } ?: ModoPractica.MIXTO
        val skill = pedido.skillId?.let { texto ->
            val id = runCatching { UUID.fromString(texto) }.getOrNull() ?: throw ErrorValidacion("skill_id_invalido", "El id de la skill no es válido")
            mercado.buscarSkill(id) ?: throw ErrorNoEncontrado("skill_no_encontrada", "La skill no existe")
        }
        val categoria = skill?.let { CategoriaHabilidad.desdeBd(it.categoria) } ?: categoriaDe(pedido.categoria)
        // Con una skill el cargo es opcional; sin skill se practica el cargo (o el objetivo del onboarding).
        val cargo = if (skill != null && pedido.cargoId == null && pedido.nombreCargo == null) null
            else contexto.cargo(usuarioId, pedido.cargoId, pedido.nombreCargo)
        val nivel = contexto.nivel(usuarioId, pedido.nivel, cargo?.cargo)

        val recientes = practicas.preguntasRecientes(usuarioId, SESIONES_PRACTICA_SIN_REPETIR)
        val seleccion = ContextoSeleccion(cargo?.cargo, nivel, recientes, modo.tipos, skill?.id)
        val elegidas = selector.seleccionar(seleccion, categoria, cantidad)
        if (elegidas.isEmpty()) {
            throw ErrorConflicto("preguntas_insuficientes", "Aún no hay preguntas aprobadas para practicar esto en nivel ${nombreNivel(nivel)}")
        }
        return practicas.crear(
            NuevaSesionPractica(
                usuarioId = usuarioId,
                skillId = skill?.id,
                cargoId = cargo?.cargo?.id,
                cargoObjetivo = cargo?.nombre ?: skill?.nombre,
                modo = modo,
                categoria = categoria,
                nivel = nivel,
                preguntas = elegidas.mapIndexed { indice, pregunta -> pregunta.aServida(indice + 1) },
                inicio = reloj.instant()
            )
        )
    }

    suspend fun obtener(usuarioId: UUID, sesionId: UUID): SesionPractica =
        buscarPropia(usuarioId, sesionId) ?: throw ErrorNoEncontrado("practica_no_encontrada", "La práctica no existe")

    suspend fun buscarPropia(usuarioId: UUID, sesionId: UUID): SesionPractica? =
        practicas.buscar(sesionId)?.takeIf { it.usuarioId == usuarioId }

    suspend fun historial(usuarioId: UUID): List<ResumenPrueba> = practicas.listarResumenes(usuarioId, HISTORIAL_PRUEBAS_LIMITE)

    /** Corrige y guarda una respuesta; devuelve el feedback inmediato. */
    suspend fun responder(usuarioId: UUID, sesionId: UUID, respuesta: RespuestaPrueba): RespuestaPractica =
        responderVarias(usuarioId, sesionId, listOf(respuesta)).respuestaDe(respuesta.preguntaServidaId)!!

    /** Todas o ninguna. */
    suspend fun responderVarias(usuarioId: UUID, sesionId: UUID, respuestas: List<RespuestaPrueba>): SesionPractica {
        if (respuestas.isEmpty()) throw sinRespuestas()
        if (respuestas.map { it.preguntaServidaId }.toSet().size != respuestas.size) {
            throw ErrorValidacion("pregunta_repetida", "Cada pregunta se responde una sola vez")
        }
        val sesion = obtener(usuarioId, sesionId)
        if (sesion.estado != EstadoPractica.EN_PROGRESO) throw practicaNoActiva()
        val ahora = reloj.instant()
        val corregidas = respuestas.map { respuesta ->
            val pregunta = sesion.preguntas.porId(respuesta.preguntaServidaId)
                ?: throw ErrorNoEncontrado("pregunta_no_encontrada", "La pregunta no pertenece a esta práctica")
            validarTiempo(respuesta.tiempoRespuestaMs)
            aRespuestaPractica(pregunta, corrector.corregir(pregunta, respuesta.opcionId, respuesta.texto), respuesta.tiempoRespuestaMs, ahora)
        }
        when (practicas.registrarRespuestas(sesionId, corregidas)) {
            ResultadoRegistroRespuestas.REGISTRADAS -> Unit
            ResultadoRegistroRespuestas.SESION_NO_ACTIVA -> throw practicaNoActiva()
            ResultadoRegistroRespuestas.YA_RESPONDIDA -> throw ErrorConflicto("pregunta_ya_respondida", "Esa pregunta ya fue respondida")
        }
        return obtener(usuarioId, sesionId)
    }

    /** Cierra la práctica con el promedio de lo respondido y lo acumula en el nivel por skill. */
    suspend fun finalizar(usuarioId: UUID, sesionId: UUID): SesionPractica {
        val sesion = obtener(usuarioId, sesionId)
        if (sesion.estado != EstadoPractica.EN_PROGRESO) throw practicaNoActiva()
        if (sesion.respuestas.isEmpty()) throw sinRespuestas()
        val fin = reloj.instant()
        if (!practicas.finalizar(sesionId, promedio(sesion.respuestas.map { it.puntaje }), fin)) throw practicaNoActiva()
        niveles.acumular(usuarioId, evaluacionesPorSkill(sesion.preguntas, sesion.respuestas), fin)
        return obtener(usuarioId, sesionId)
    }

    /** Rendición de una sola vez (app Android): guarda lo respondido y finaliza. */
    suspend fun responderYFinalizar(usuarioId: UUID, sesionId: UUID, respuestas: List<RespuestaPrueba>): SesionPractica {
        responderVarias(usuarioId, sesionId, respuestas)
        return finalizar(usuarioId, sesionId)
    }

    // ---------- Sincronización de intentos hechos sin conexión ----------

    /**
     * Guarda intentos que la app hizo offline. Idempotente por id local: reenviar el mismo lote no duplica.
     * Si la pregunta existe en el banco se vuelve a corregir en el servidor; si no, se guarda lo que calculó la app.
     * Devuelve id local → id en el servidor.
     */
    suspend fun sincronizarOffline(usuarioId: UUID, lote: List<IntentoOfflineEntrada>): List<Pair<String, UUID>> {
        if (lote.isEmpty() || lote.size > INTENTOS_OFFLINE_MAXIMOS_POR_LOTE) {
            throw ErrorValidacion("lote_invalido", "Envía entre 1 y $INTENTOS_OFFLINE_MAXIMOS_POR_LOTE intentos por solicitud")
        }
        if (lote.map { it.idLocal.trim() }.toSet().size != lote.size) throw ErrorValidacion("id_local_repetido", "Hay intentos repetidos en el lote")
        val intentos = lote.map { prepararOffline(usuarioId, it) }
        return intentos.map { intento ->
            val puntaje = promedio(intento.respuestas.map { it.puntaje })
            val guardado = practicas.guardarOffline(intento, puntaje)
            if (guardado.esNuevo) niveles.acumular(usuarioId, evaluacionesPorSkill(intento.sesion.preguntas, intento.respuestas), intento.fin)
            intento.idLocal to guardado.id
        }
    }

    private suspend fun prepararOffline(usuarioId: UUID, entrada: IntentoOfflineEntrada): IntentoOffline {
        val idLocal = entrada.idLocal.trim()
        if (idLocal.isEmpty() || idLocal.length > LARGO_MAXIMO_ID_LOCAL) {
            throw ErrorValidacion("id_local_invalido", "localAttemptId es obligatorio (hasta $LARGO_MAXIMO_ID_LOCAL caracteres)")
        }
        val skillId = runCatching { UUID.fromString(entrada.skillId) }.getOrNull()
            ?.takeIf { mercado.buscarSkill(it) != null }
            ?: throw ErrorValidacion("skill_no_encontrada", "La skill del intento $idLocal no existe")
        val modo = ModoPractica.desdeBd(entrada.modo.trim().lowercase()) ?: throw modoInvalido()
        val nivel = NivelExperiencia.desdeTexto(entrada.nivel) ?: throw ErrorValidacion("nivel_invalido", "El nivel debe ser junior, semisenior o senior")
        val respuestas = entrada.respuestas
        if (respuestas.isEmpty() || respuestas.size > RESPUESTAS_OFFLINE_MAXIMAS_POR_INTENTO) {
            throw ErrorValidacion("respuestas_invalidas", "Cada intento lleva entre 1 y $RESPUESTAS_OFFLINE_MAXIMAS_POR_INTENTO respuestas")
        }
        if (respuestas.map { it.orden }.toSet().size != respuestas.size || respuestas.any { it.orden < 1 }) {
            throw ErrorValidacion("orden_invalido", "El orden de las respuestas debe ser único y desde 1")
        }
        val fin = fechaDelCliente(entrada.fechaCreacion)
        val corregidas = respuestas.map { corregirOffline(it, modo, nivel, skillId, fin) }
        val cargoId = entrada.cargoId?.let { runCatching { UUID.fromString(it) }.getOrNull() }?.takeIf { mercado.buscarCargo(it) != null }
        return IntentoOffline(
            idLocal = idLocal,
            sesion = NuevaSesionPractica(
                usuarioId = usuarioId,
                skillId = skillId,
                cargoId = cargoId,
                cargoObjetivo = null,
                modo = modo,
                categoria = categoriaDe(entrada.categoria),
                nivel = nivel,
                preguntas = corregidas.map { it.first },
                inicio = fin
            ),
            respuestas = corregidas.map { it.second },
            fin = fin
        )
    }

    private suspend fun corregirOffline(
        entrada: RespuestaOfflineEntrada,
        modo: ModoPractica,
        nivel: NivelExperiencia,
        skillId: UUID,
        fecha: Instant
    ): Pair<PreguntaServida, RespuestaPractica> {
        validarTiempo(entrada.tiempoRespuestaMs)
        val delBanco = runCatching { UUID.fromString(entrada.preguntaId) }.getOrNull()?.let { preguntas.buscarPorId(it) }
        if (delBanco != null) {
            val servida = delBanco.aServida(entrada.orden)
            // La opción pudo cambiar desde que la app descargó la pregunta: si ya no existe, cuenta como incorrecta.
            val corregida = try {
                corrector.corregir(servida, entrada.opcionElegidaId, entrada.respuestaTexto)
            } catch (e: ErrorValidacion) {
                RespuestaCorregida(opcionId = null, texto = entrada.respuestaTexto?.trim(), correcta = false, puntaje = 0.0)
            }
            return servida to aRespuestaPractica(servida, corregida, entrada.tiempoRespuestaMs, fecha)
        }
        val enunciado = entrada.enunciado.trim()
        if (enunciado.isEmpty() || enunciado.length > LARGO_MAXIMO_ENUNCIADO_OFFLINE) {
            throw ErrorValidacion("enunciado_invalido", "El enunciado es obligatorio (hasta $LARGO_MAXIMO_ENUNCIADO_OFFLINE caracteres)")
        }
        val tipo = if (modo == ModoPractica.OPCION_MULTIPLE) TipoPregunta.OPCION_MULTIPLE else TipoPregunta.ABIERTA_TEXTO
        val servida = PreguntaServida(
            id = UUID.randomUUID().toString(), preguntaId = null, orden = entrada.orden, enunciado = enunciado,
            tipo = tipo.valorBd, categoria = CategoriaHabilidad.TECNICA.valorBd, nivel = nivel.valorBd, skillId = skillId.toString()
        )
        val puntaje = entrada.puntaje.coerceIn(0.0, PUNTAJE_OFFLINE_MAXIMO) * ESCALA_PUNTAJE
        val respuesta = RespuestaCorregida(texto = entrada.respuestaTexto?.trim(), correcta = entrada.esCorrecta, puntaje = puntaje)
        return servida to aRespuestaPractica(servida, respuesta, entrada.tiempoRespuestaMs, fecha)
    }

    /** La fecha que manda la app; si no sirve (o está en el futuro), la del servidor. */
    private fun fechaDelCliente(texto: String?): Instant {
        val ahora = reloj.instant()
        val fecha = texto?.let { try { Instant.parse(it) } catch (e: DateTimeParseException) { null } } ?: return ahora
        return if (fecha.isAfter(ahora.plus(TOLERANCIA_RELOJ_CLIENTE))) ahora else fecha
    }

    // ---------- Apoyo ----------

    private fun aRespuestaPractica(pregunta: PreguntaServida, corregida: RespuestaCorregida, tiempoMs: Int?, fecha: Instant) =
        RespuestaPractica(
            preguntaServidaId = pregunta.id,
            orden = pregunta.orden,
            opcionElegidaId = corregida.opcionId,
            texto = corregida.texto,
            esCorrecta = corregida.correcta,
            puntaje = BigDecimal.valueOf(corregida.puntaje).setScale(2, RoundingMode.HALF_UP),
            feedback = corregida.feedback,
            tiempoRespuestaMs = tiempoMs,
            fecha = fecha
        )

    /** Promedio por skill de lo respondido. La práctica suma puntaje pero no cambia el nivel (eso lo decide la nivelación). */
    private fun evaluacionesPorSkill(preguntas: List<PreguntaServida>, respuestas: List<RespuestaPractica>): List<EvaluacionNivelSkill> {
        val porId = preguntas.associateBy { it.id }
        return respuestas.mapNotNull { r -> porId[r.preguntaServidaId]?.skillId?.let { UUID.fromString(it) to r.puntaje } }
            .groupBy({ it.first }) { it.second }
            .map { (skillId, puntajes) -> EvaluacionNivelSkill(skillId, promedio(puntajes), nivel = null) }
    }

    private fun promedio(puntajes: List<BigDecimal>): BigDecimal =
        if (puntajes.isEmpty()) BigDecimal.ZERO
        else puntajes.fold(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal(puntajes.size), 2, RoundingMode.HALF_UP)

    private fun categoriaDe(texto: String?): CategoriaHabilidad =
        texto?.let { CategoriaHabilidad.desdeBd(it.trim().lowercase()) ?: throw ErrorValidacion("categoria_invalida", "La categoría debe ser tecnica o blanda") }
            ?: CategoriaHabilidad.TECNICA

    private fun validarTiempo(tiempoMs: Int?) {
        if (tiempoMs != null && tiempoMs !in 0..TIEMPO_RESPUESTA_MAXIMO_MS) {
            throw ErrorValidacion("tiempo_invalido", "El tiempo de respuesta debe estar entre 0 y 1 hora")
        }
    }

    private fun modoInvalido(): ErrorAplicacion = ErrorValidacion("modo_invalido", "El modo debe ser opcion_multiple, abierta_texto o mixto")
    private fun practicaNoActiva() = ErrorConflicto("practica_no_activa", "La práctica ya fue finalizada o abandonada")
    private fun sinRespuestas() = ErrorValidacion("sin_respuestas", "Responde al menos una pregunta")
}
