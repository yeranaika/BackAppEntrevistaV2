package ESQUEMAS

import kotlinx.serialization.Serializable

// GET /api/v1/admin/preguntas/cobertura (D26). camelCase, como el resto de /api/v1/admin/preguntas.

@Serializable
data class RespuestaConteoNiveles(
    val junior: Int,
    val semisenior: Int,
    val senior: Int
)

@Serializable
data class RespuestaCoberturaSkill(
    val skillId: String,
    val nombre: String,
    val categoria: String,
    val nivelRequerido: String,
    val peso: Short,
    val obligatoria: Boolean,
    /** Aprobadas que sirven para el cargo (de la skill y del cargo, o de la skill sin cargo). */
    val aprobadas: RespuestaConteoNiveles,
    /** Pendientes de revisión: aprobarlas cubre lo que falta sin gastar en IA. */
    val pendientes: RespuestaConteoNiveles,
    /** Lo que falta para llegar al mínimo de aprobadas en cada nivel. */
    val faltan: RespuestaConteoNiveles
)

@Serializable
data class RespuestaCoberturaCargo(
    val cargoId: String,
    val nombre: String,
    val area: String,
    val skillsIncompletas: Int,
    val preguntasFaltantes: Int,
    val skills: List<RespuestaCoberturaSkill>
)

@Serializable
data class RespuestaCoberturaBanco(
    /** Aprobadas por skill y nivel que se consideran suficientes. */
    val minimo: Int,
    /** Última vez que se leyeron las ofertas de empleo (null si nunca). */
    val ultimaSincronizacionMercado: String?,
    val cargos: List<RespuestaCoberturaCargo>
)
