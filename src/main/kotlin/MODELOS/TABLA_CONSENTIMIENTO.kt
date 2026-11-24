package MODELOS

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.json.jsonb

private val listaDeTextos = ListSerializer(String.serializer())

/** Versión inmutable del texto legal (EULA) que el usuario acepta. */
object TablaTextoConsentimiento : Table("consentimiento_texto") {
    val version = varchar("version", 20)
    val titulo = text("titulo")
    val cuerpo = text("cuerpo")
    val alcancesPosibles = jsonb("alcances_posibles", Json, listaDeTextos).default(emptyList())
    val fechaPublicacion = timestampWithTimeZone("fecha_publicacion")
    val vigente = bool("vigente").default(true)

    override val primaryKey = PrimaryKey(version)
}

/** Aceptación de un usuario a una versión, con los alcances que eligió. */
object TablaConsentimiento : Table("consentimiento") {
    val id = uuid("consentimiento_id")
    /** NULL cuando la cuenta se eliminó: el registro se conserva como prueba de aceptación. */
    val usuarioId = uuid("usuario_id").nullable()
    /** Correo del usuario copiado al eliminar la cuenta (quién aceptó). */
    val identidadRef = varchar("identidad_ref", 320).nullable()
    val version = varchar("version", 20)
    /** Lista de alcances aceptados, ej: ["uso_datos", "ia_entrenamiento"]. */
    val alcancesAceptados = jsonb("alcances_aceptados", Json, listaDeTextos).default(emptyList())
    val aceptaEntrenamientoIa = bool("acepta_entrenamiento_ia").default(false)
    val fechaOtorgado = timestamp("fecha_otorgado")
    val fechaRevocado = timestamp("fecha_revocado").nullable()
    /** NULL al eliminar la cuenta: para probar que aceptó basta lo demás (D38). */
    val ipOrigen = varchar("ip_origen", 45).nullable()
    /** Cuándo se eliminó la cuenta: desde ahí corre el plazo de conservación (D38, migración 029). */
    val cuentaEliminadaEn = timestamp("cuenta_eliminada_en").nullable()
    /** TRUE mientras un reclamo, procedimiento o juicio necesite esta prueba: no se borra aunque venza el plazo. */
    val retencionLegal = bool("retencion_legal").default(false)

    override val primaryKey = PrimaryKey(id)
}
