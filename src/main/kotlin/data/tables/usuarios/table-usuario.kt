package data.tables.usuarios

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.date
import java.time.LocalDateTime

object UsuarioTable : Table("usuario") {

    val usuarioId      = uuid("usuario_id")
    val correo         = varchar("correo", 320).uniqueIndex("usuario_correo_unique")
    val contrasenaHash = varchar("contrasena_hash", 255)
    val nombre         = varchar("nombre", 120).nullable()

    // Preferencias / estado de la cuenta
    val idioma         = varchar("idioma", 10).default("es")
    val estado         = varchar("estado", 19).default("activo")
    val rol            = varchar("rol", 10).default("user")

    // Nuevos campos
    val telefono        = varchar("telefono", 20).nullable()
    val origenRegistro  = varchar("origen_registro", 20).default("local")

    val fechaCreacion   = datetime("fecha_creacion")
        .clientDefault { LocalDateTime.now() }   // o dejar que la BD use su DEFAULT now()

    val fechaUltimoLogin = datetime("fecha_ultimo_login").nullable()
    val fechaNacimiento  = date("fecha_nacimiento").nullable()
    val genero           = varchar("genero", 20).nullable()

    override val primaryKey = PrimaryKey(usuarioId, name = "usuario_pk")
}
