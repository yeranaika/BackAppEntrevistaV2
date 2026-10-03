package VISTAS

import ESQUEMAS.RespuestaCargo
import ESQUEMAS.RespuestaDemandaSkill
import ESQUEMAS.RespuestaHistorialTendencia
import ESQUEMAS.RespuestaMatrizCargo
import ESQUEMAS.RespuestaRequisito
import ESQUEMAS.RespuestaRequisitosGenerados
import ESQUEMAS.RespuestaSincronizacion
import ESQUEMAS.RespuestaSkill
import ESQUEMAS.RespuestaSkillTendencia
import ESQUEMAS.RespuestaTendencias
import MODELOS.Cargo
import MODELOS.RequisitoCargo
import MODELOS.Skill
import MODELOS.TendenciaSkill
import SERVICIOS.RequisitosGenerados
import SERVICIOS.ResultadoSincronizacion
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun Cargo.aRespuesta() = RespuestaCargo(
    cargoId = id.toString(),
    nombre = nombre,
    area = area,
    descripcion = descripcion,
    nivelBase = nivelBase.valorBd,
    activo = estaActivo
)

fun RequisitoCargo.aRespuesta() = RespuestaRequisito(
    skillId = skill.id.toString(),
    nombre = skill.nombre,
    categoria = skill.categoria,
    tipoArea = skill.tipoArea,
    nivelRequerido = nivelRequerido.valorBd,
    peso = peso,
    obligatoria = esObligatoria
)

fun Cargo.aRespuestaMatriz(requisitos: List<RequisitoCargo>) = RespuestaMatrizCargo(
    cargoId = id.toString(),
    cargoNombre = nombre,
    area = area,
    nivelBase = nivelBase.valorBd,
    totalSkills = requisitos.size,
    obligatoriasCount = requisitos.count { it.esObligatoria },
    opcionalesCount = requisitos.count { !it.esObligatoria },
    skills = requisitos.map { it.aRespuesta() }
)

fun Skill.aRespuesta() = RespuestaSkill(
    skillId = id.toString(),
    nombre = nombre,
    categoria = categoria,
    tipoArea = tipoArea,
    descripcion = descripcion,
    demandaScore = demandaScore,
    activo = estaActiva
)

fun List<Skill>.aRespuestaTendencias(categoria: String?) = RespuestaTendencias(
    total = size,
    categoriaFiltro = categoria,
    skills = map { RespuestaSkillTendencia(it.id.toString(), it.nombre, it.categoria, it.tipoArea, it.demandaScore, it.descripcion) }
)

fun TendenciaSkill.aRespuesta() = RespuestaHistorialTendencia(
    tendenciaId = id.toString(),
    skillId = skillId.toString(),
    frecuenciaOfertas = frecuenciaOfertas,
    nivelRequerido = nivelRequerido.valorBd,
    fechaActualizacion = fecha.toString()
)

fun RequisitosGenerados.aRespuesta() = RespuestaRequisitosGenerados(
    cargoId = cargo.id.toString(),
    cargoNombre = cargo.nombre,
    skillsLinkedCount = requisitos.size,
    skills = requisitos.map { it.aRespuesta() }
)

fun ResultadoSincronizacion.aRespuesta() = RespuestaSincronizacion(
    success = actualizadas.isNotEmpty(),
    message = if (actualizadas.isEmpty()) "No hay skills activas registradas" else "Sincronización de tendencias completada para ${actualizadas.size} skills",
    skillsUpdatedCount = actualizadas.size,
    durationMs = duracionMs,
    timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
    topSkills = actualizadas.map {
        RespuestaDemandaSkill(it.skill.id.toString(), it.skill.nombre, it.demanda, it.frecuenciaOfertas, it.nivelPredominante.valorBd)
    }
)
