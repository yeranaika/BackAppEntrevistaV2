package VISTAS

import ESQUEMAS.RespuestaOpcion
import ESQUEMAS.RespuestaOpcionPublica
import ESQUEMAS.RespuestaPaginaPreguntas
import ESQUEMAS.RespuestaPregunta
import ESQUEMAS.RespuestaPreguntaGenerada
import ESQUEMAS.RespuestaPreguntaPublica
import ESQUEMAS.RespuestaPreguntaRevisable
import ESQUEMAS.RespuestaGrupoSimilares
import ESQUEMAS.RespuestaPreguntaDescartada
import ESQUEMAS.RespuestaPreguntaEnGrupo
import ESQUEMAS.RespuestaPreguntaSimilar
import MODELOS.EstadoPregunta
import MODELOS.GrupoPreguntasSimilares
import MODELOS.PreguntaSimilar
import SERVICIOS.PreguntaDescartada
import MODELOS.PaginaPreguntas
import MODELOS.Pregunta
import ESQUEMAS.RespuestaContextoMercado
import ESQUEMAS.RespuestaFragmentoOferta
import ESQUEMAS.RespuestaSkillEnOfertas
import SERVICIOS.PreguntaCreadaPorIa
import SERVICIOS.ResultadoContextoMercado

/** Vista completa para el admin: incluye respuesta ideal, rúbrica y opción correcta. */
fun Pregunta.aRespuestaAdmin() = RespuestaPregunta(
    id = id.toString(),
    skillId = skillId?.toString(),
    cargoId = cargoId?.toString(),
    tipo = tipo.valorBd,
    categoria = categoria.valorBd,
    nivel = nivel.valorBd,
    enunciado = enunciado,
    respuestaIdeal = respuestaIdeal,
    rubrica = rubrica,
    generadaPorIa = generadaPorIa,
    estado = estado.valorBd,
    motivoRechazo = motivoRechazo,
    vecesUsada = vecesUsada,
    fechaCreacion = fechaCreacion.toString(),
    opciones = opciones.map { RespuestaOpcion(it.id.toString(), it.texto, it.esCorrecta, it.explicacion, it.orden) },
    similares = emptyList()
)

fun PreguntaSimilar.aRespuesta() = RespuestaPreguntaSimilar(id?.toString(), enunciado, estado?.valorBd, similitud)

fun PreguntaDescartada.aRespuesta() = RespuestaPreguntaDescartada(enunciado, similar.aRespuesta())

fun GrupoPreguntasSimilares.aRespuesta() = RespuestaGrupoSimilares(
    preguntas.map { RespuestaPreguntaEnGrupo(it.id.toString(), it.enunciado, it.estado.valorBd, it.vecesUsada) }, similitudMaxima
)

fun PaginaPreguntas.aRespuesta() = RespuestaPaginaPreguntas(
    elementos = elementos.map { it.aRespuestaAdmin() },
    total = total,
    pagina = pagina,
    tamano = tamano
)

/** Vista para quien responde: nunca muestra la respuesta ideal ni cuál opción es la correcta. */
fun Pregunta.aRespuestaPublica() = RespuestaPreguntaPublica(
    id = id.toString(),
    tipo = tipo.valorBd,
    categoria = categoria.valorBd,
    nivel = nivel.valorBd,
    enunciado = enunciado,
    opciones = opciones.map { RespuestaOpcionPublica(it.id.toString(), it.texto, it.orden) }
)

/** Para revisar sin conexión (T3.13): ESTA vista sí lleva la respuesta ideal y la rúbrica, porque la revisión se hace en el teléfono. */
fun Pregunta.aRespuestaRevisable() = RespuestaPreguntaRevisable(
    id = id.toString(),
    tipo = tipo.valorBd,
    categoria = categoria.valorBd,
    nivel = nivel.valorBd,
    enunciado = enunciado,
    respuestaIdeal = respuestaIdeal,
    rubrica = rubrica
)

fun PreguntaCreadaPorIa.aRespuesta() = RespuestaPreguntaGenerada(
    preguntaId = ids.preguntaId.toString(),
    generacionId = ids.generacionId.toString(),
    enunciado = enunciado,
    estado = EstadoPregunta.PENDIENTE.valorBd,
    tokensEntrada = tokensEntrada,
    tokensSalida = tokensSalida,
    costoUsd = costoUsd
)

fun ResultadoContextoMercado.aRespuesta(): RespuestaContextoMercado {
    val ctx = contexto ?: return RespuestaContextoMercado(
        usado = false, motivo = motivo?.codigo, mensaje = motivo?.mensaje, consulta = null, ofertasAnalizadas = 0,
        fuentes = emptyList(), esRespaldo = false, skillsFrecuentes = emptyList(), fragmentos = emptyList()
    )
    return RespuestaContextoMercado(
        usado = true,
        motivo = null,
        mensaje = null,
        consulta = ctx.consulta,
        ofertasAnalizadas = ctx.ofertasAnalizadas,
        fuentes = ctx.fuentes,
        esRespaldo = ctx.esRespaldo,
        skillsFrecuentes = ctx.skillsFrecuentes.map { RespuestaSkillEnOfertas(it.nombre, it.ofertas) },
        fragmentos = ctx.fragmentos.map { RespuestaFragmentoOferta(it.texto, it.tituloOferta, it.fuente) }
    )
}