package PRUEBAS.DOBLES

import MODELOS.CategoriaHabilidad
import MODELOS.ContenidoPregunta
import MODELOS.EstadoPregunta
import MODELOS.NivelExperiencia
import MODELOS.NuevaOpcion
import MODELOS.Pregunta
import MODELOS.SesionEntrevista
import MODELOS.TipoPregunta
import SERVICIOS.ProcesadorEntrevistaFinalizada
import kotlinx.coroutines.runBlocking
import java.util.UUID

/** Guarda las entrevistas que se le pidió procesar (en vez de generar el reporte). */
class ProcesadorEntrevistaGrabador : ProcesadorEntrevistaFinalizada {
    val procesadas = mutableListOf<SesionEntrevista>()
    var error: Exception? = null

    override suspend fun procesar(sesion: SesionEntrevista) {
        error?.let { throw it }
        procesadas += sesion
    }
}

/** Inserta una pregunta APROBADA en el banco. */
fun SistemaPrueba.sembrarPregunta(
    categoria: CategoriaHabilidad = CategoriaHabilidad.TECNICA,
    nivel: NivelExperiencia = NivelExperiencia.JUNIOR,
    tipo: TipoPregunta = if (categoria == CategoriaHabilidad.TECNICA) TipoPregunta.OPCION_MULTIPLE else TipoPregunta.ABIERTA_TEXTO,
    cargoId: UUID? = null,
    skillId: UUID? = null,
    enunciado: String = "Pregunta ${UUID.randomUUID()}",
    estado: EstadoPregunta = EstadoPregunta.APROBADA
): Pregunta = runBlocking {
    val opciones = if (tipo == TipoPregunta.OPCION_MULTIPLE) {
        listOf(NuevaOpcion("Correcta", true, null), NuevaOpcion("Incorrecta A", false, null), NuevaOpcion("Incorrecta B", false, null))
    } else {
        emptyList()
    }
    preguntas.crear(
        ContenidoPregunta(skillId, cargoId, tipo, categoria, nivel, enunciado, "Respuesta ideal", null, opciones),
        estado
    )
}

/** Banco mínimo para una entrevista general de nivel junior: [tecnicas] de opción múltiple + [blandas] abiertas. */
fun SistemaPrueba.sembrarBancoGeneral(tecnicas: Int = 5, blandas: Int = 3, nivel: NivelExperiencia = NivelExperiencia.JUNIOR) {
    repeat(tecnicas) { sembrarPregunta(CategoriaHabilidad.TECNICA, nivel) }
    repeat(blandas) { sembrarPregunta(CategoriaHabilidad.BLANDA, nivel) }
}
