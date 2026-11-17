package VISTAS

import ESQUEMAS.RespuestaCoberturaBanco
import ESQUEMAS.RespuestaCoberturaCargo
import ESQUEMAS.RespuestaCoberturaSkill
import ESQUEMAS.RespuestaConteoNiveles
import MODELOS.NivelExperiencia
import SERVICIOS.CoberturaBanco
import SERVICIOS.CoberturaCargo
import SERVICIOS.CoberturaSkill

private fun Map<NivelExperiencia, Int>.aConteo() = RespuestaConteoNiveles(
    junior = this[NivelExperiencia.JUNIOR] ?: 0,
    semisenior = this[NivelExperiencia.SEMISENIOR] ?: 0,
    senior = this[NivelExperiencia.SENIOR] ?: 0
)

private fun CoberturaSkill.aRespuesta(minimo: Int) = RespuestaCoberturaSkill(
    skillId = requisito.skill.id.toString(),
    nombre = requisito.skill.nombre,
    categoria = requisito.skill.categoria,
    nivelRequerido = requisito.nivelRequerido.valorBd,
    peso = requisito.peso,
    obligatoria = requisito.esObligatoria,
    aprobadas = aprobadas.aConteo(),
    pendientes = pendientes.aConteo(),
    faltan = faltan(minimo).aConteo()
)

private fun CoberturaCargo.aRespuesta(minimo: Int): RespuestaCoberturaCargo {
    val faltantes = skills.map { it.faltan(minimo).values.sum() }
    return RespuestaCoberturaCargo(
        cargoId = cargo.id.toString(),
        nombre = cargo.nombre,
        area = cargo.area,
        skillsIncompletas = faltantes.count { it > 0 },
        preguntasFaltantes = faltantes.sum(),
        skills = skills.map { it.aRespuesta(minimo) }
    )
}

fun CoberturaBanco.aRespuesta() = RespuestaCoberturaBanco(
    minimo = minimo,
    ultimaSincronizacionMercado = ultimaSincronizacionMercado?.toString(),
    cargos = cargos.map { it.aRespuesta(minimo) }
)
